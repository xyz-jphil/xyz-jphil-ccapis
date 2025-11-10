package xyz.jphil.ccapis.proxy;

import io.javalin.Javalin;
import io.javalin.http.Context;
import com.fasterxml.jackson.databind.ObjectMapper;
import xyz.jphil.ccapis.CCAPIClient;
import xyz.jphil.ccapis.CCAPIClientWithHealthCheck;
import xyz.jphil.ccapis.config.CredentialsManager;
import xyz.jphil.ccapis.model.CCAPICredential;
import xyz.jphil.ccapis.proxy.anthropic.*;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static xyz.jphil.ccapis.util.Console.printLines;
import xyz.jphil.windows_console_set_unicode_output.WindowsConsoleSetUnicodeOutput;

/**
 * Anthropic-Compatible API Proxy for CCAPI
 *
 * <p>Implements Anthropic Messages API (/v1/messages) that proxies to CCAPI:
 * <ul>
 *   <li>Converts Anthropic request format to CCAPI prompt format</li>
 *   <li>Creates temporary CCAPI conversations</li>
 *   <li>Streams responses in Anthropic SSE format</li>
 *   <li>Handles both streaming and non-streaming requests</li>
 * </ul>
 *
 * <p>Usage:
 * <pre>
 * // Start proxy on port 8080 with settings from default location
 * var proxy = new CCAPIProxy(8080);
 * proxy.start();
 * </pre>
 *
 * <p>Compatible with Claude Code and other Anthropic API clients.
 */
public class CCAPIProxy {

    private final Javalin app;
    private final CCAPIClient ccapiClient;
    private final CCAPIClientWithHealthCheck healthCheckClient;
    private final List<CCAPICredential> activeCredentials;
    private int currentCredentialIndex = 0;
    private final ObjectMapper objectMapper;
    private final DebugLogger debugLogger;
    private final int port;
    private final boolean individualMessagesVisible;
    private final xyz.jphil.ccapis.config.Settings.ProxySettings proxySettings;

    /**
     * Create proxy with default settings location
     */
    public CCAPIProxy(int port) throws Exception {
        this(port, (String) null);
    }

    /**
     * Create proxy with specific settings file path
     */
    public CCAPIProxy(int port, String settingsPath) throws Exception {
        this.port = port;

        // Load credentials from shared CCAPIsCredentials.xml
        var credentialsManager = new CredentialsManager();
        var credentials = credentialsManager.load();

        // Get all active credentials for rotation
        this.activeCredentials = credentials.getActiveCredentials();
        if (activeCredentials.isEmpty()) {
            throw new IllegalStateException("No active credentials found in CCAPIsCredentials.xml");
        }

        System.out.println("[PROXY] Loaded " + activeCredentials.size() + " active credential(s):");
        for (var cred : activeCredentials) {
            System.out.println("  - " + cred.id() + " (" + cred.name() + ")");
        }

        // Load proxy-specific settings from CCAPIProxySettings.xml
        var proxySettingsManager = settingsPath != null
            ? new ProxySettingsManager(settingsPath)
            : new ProxySettingsManager();

        this.proxySettings = proxySettingsManager.load();

        // Get visibility setting from proxy settings (default to true if not configured)
        this.individualMessagesVisible = this.proxySettings != null
            && this.proxySettings.ccapi() != null
            ? this.proxySettings.ccapi().individualMessagesVisible()
            : true;

        // Create JSON mapper with proper configuration
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        this.objectMapper.configure(
            com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
            false
        );

        // Create debug logger with loaded settings
        this.debugLogger = new DebugLogger(this.proxySettings, objectMapper);

        // Create CCAPI client with file logger if individual file logging is enabled
        this.ccapiClient = debugLogger.isIndividualFileLoggingEnabled()
            ? new CCAPIClient(debugLogger.getFileLogger())
            : new CCAPIClient();

        // Create health check client with circuit breaker from credentials config
        var circuitBreakerConfig = credentials.getCircuitBreakerConfig();
        this.healthCheckClient = debugLogger.isIndividualFileLoggingEnabled()
            ? new CCAPIClientWithHealthCheck(circuitBreakerConfig, debugLogger.getFileLogger())
            : new CCAPIClientWithHealthCheck(circuitBreakerConfig);

        System.out.println("[PROXY] Circuit Breaker: " +
            (circuitBreakerConfig.enabled() ? "ENABLED" : "DISABLED") +
            " (threshold: " + circuitBreakerConfig.failureThreshold() + " failures)");

        // Create Javalin app
        this.app = Javalin.create(config -> {
            config.showJavalinBanner = false;
            config.http.asyncTimeout = 300000L; // 5 minute timeout for long responses
        });

        setupRoutes();
    }

    /**
     * Get the current credential (with health-aware selection and round-robin fallback)
     */
    private synchronized CCAPICredential getCurrentCredential() {
        // Try to get best available account based on health and usage
        var bestAccount = healthCheckClient.selectBestAccount(activeCredentials);

        if (bestAccount != null) {
            debugLogger.logInfo("Selected account: " + bestAccount.id() +
                " (usage: " + getUsagePercent(bestAccount) + "%)");
            return bestAccount;
        }

        // Fallback to round-robin if circuit breaker disabled or no accounts available
        debugLogger.logInfo("No healthy accounts available, using round-robin fallback");
        var credential = activeCredentials.get(currentCredentialIndex);
        currentCredentialIndex = (currentCredentialIndex + 1) % activeCredentials.size();
        return credential;
    }

    /**
     * Get usage percentage for an account (for logging)
     */
    private int getUsagePercent(CCAPICredential credential) {
        var health = healthCheckClient.getHealthMonitor().getHealth(credential.id());
        if (health != null && health.latestUsage() != null && health.latestUsage().fiveHour() != null) {
            return (int) health.latestUsage().fiveHour().utilization();
        }
        return 0;
    }

    /**
     * Setup all HTTP routes
     */
    private void setupRoutes() {
        // Health check
        app.get("/health", this::handleHealth);

        // Circuit breaker health status
        app.get("/health/accounts", this::handleAccountsHealth);

        // Anthropic-compatible Messages API
        app.post("/v1/messages", this::handleMessages);

        // Legacy completion endpoint (minimal implementation)
        app.post("/v1/complete", this::handleComplete);
    }

    /**
     * Health check endpoint
     */
    private void handleHealth(Context ctx) {
        var credentialIds = activeCredentials.stream()
                .map(c -> c.id() + " (" + c.name() + ")")
                .toList();

        ctx.json(Map.of(
            "status", "ok",
            "service", "CCAPI Anthropic-Compatible Proxy",
            "version", "1.0",
            "active_credentials", credentialIds,
            "credential_count", activeCredentials.size(),
            "timestamp", Instant.now().toString()
        ));
    }

    /**
     * Account health status endpoint - shows circuit breaker state
     */
    private void handleAccountsHealth(Context ctx) {
        // Return health summary as plain text
        ctx.contentType("text/plain");
        ctx.result(healthCheckClient.getHealthSummary());
    }

    /**
     * Main /v1/messages endpoint - Anthropic Messages API compatible
     */
    private void handleMessages(Context ctx) {
        CCAPICredential credential = null;
        try {
            // Start new conversation log file
            debugLogger.startNewConversation();

            // Get credential for this request (round-robin rotation)
            credential = getCurrentCredential();
            debugLogger.logInfo("Using credential: " + credential.id() + " (" + credential.name() + ")");

            // Get raw JSON body for logging
            var rawBody = ctx.body();

            // Parse Anthropic request
            var request = objectMapper.readValue(rawBody, AnthropicRequest.class);

            // Log incoming request
            debugLogger.logAnthropicRequest(request, rawBody);

            // Validate request
            if (request.getMessages() == null || request.getMessages().isEmpty()) {
                ctx.status(400).json(Map.of(
                    "error", "invalid_request_error",
                    "message", "messages array is required"
                ));
                return;
            }

            // Check if streaming is requested
            boolean streaming = request.getStream() != null && request.getStream();

            if (streaming) {
                handleStreamingMessages(ctx, request, credential);
            } else {
                handleNonStreamingMessages(ctx, request, credential);
            }

        } catch (Exception e) {
            var credId = credential != null ? credential.id() : "UNKNOWN";
            debugLogger.logError("Error handling /v1/messages request [Credential: " + credId + "]", e);
            e.printStackTrace();
            ctx.status(500).json(Map.of(
                "error", "internal_server_error",
                "message", e.getMessage() != null ? e.getMessage() : "An error occurred"
            ));
        }
    }

    /**
     * Handle streaming /v1/messages request
     */
    private void handleStreamingMessages(Context ctx, AnthropicRequest request, CCAPICredential credential) throws IOException {
        // Convert Anthropic request to CCAPI prompt
        var prompt = MessageToPromptConverter.convert(request);

        // Log converted prompt
        debugLogger.logConvertedPrompt(prompt);

        // Terse account routing info
        var health = healthCheckClient.getHealthMonitor().getHealth(credential.id());
        var usageStr = health.latestUsage() != null && health.latestUsage().fiveHour() != null
            ? String.format("%d%%", (int) health.latestUsage().fiveHour().utilization())
            : "?";
        System.out.println(String.format("\n[PROXY] Streaming via %s [%s, usage:%s]",
            credential.id(), health.state(), usageStr));

        // Create temporary conversation (or visible conversation based on settings)
        var isTemporary = !individualMessagesVisible;

        // Create conversation (doesn't need health check - just metadata)
        var conversation = ccapiClient.createChat(
            credential,
            "Proxy-" + Instant.now().toEpochMilli(),
            isTemporary
        );

        // Generate message ID
        var messageId = "msg_" + System.currentTimeMillis();
        var model = request.getModel() != null ? request.getModel() : "claude-3-opus-20240229";

        // Set SSE headers
        ctx.contentType("text/event-stream");
        ctx.header("Cache-Control", "no-cache");
        ctx.header("Connection", "keep-alive");

        var outputStream = ctx.outputStream();

        try {
            // Send message_start event
            writeSSE(outputStream, StreamingChunk.messageStart(messageId, model));

            // Send content_block_start event
            writeSSE(outputStream, StreamingChunk.contentBlockStart(0));

            // Track output tokens (approximate)
            int outputTokens = 0;

            // Track full response for logging
            var fullResponse = new StringBuilder();

            // Send message with streaming callbacks
            // Note: Streaming doesn't go through healthCheckClient because callbacks need to be immediate
            // Health tracking happens via usage monitoring in healthCheckClient.selectBestAccount()
            ccapiClient.sendMessageStreaming(credential, conversation.uuid(), prompt, text -> {
                try {
                    // Log streaming chunk
                    debugLogger.logStreamingChunk(text);
                    fullResponse.append(text);

                    // Send content_block_delta event for each text chunk
                    writeSSE(outputStream, StreamingChunk.contentBlockDelta(0, text));
                    outputStream.flush();
                } catch (IOException e) {
                    throw new RuntimeException("Failed to write streaming chunk", e);
                }
            });

            // Log full response
            debugLogger.logCcapiResponse(fullResponse.toString());

            // Parse tool calls from the full response
            var parseResult = xyz.jphil.ccapis.proxy.toolcalls.ToolCallParser.parse(fullResponse.toString(), debugLogger);

            // Check for tool call failure nudge (PRP-15: diagnostic logging)
            checkForToolCallFailure(request, fullResponse.toString(), parseResult.hasToolCalls());

            // Send content_block_stop event for text
            writeSSE(outputStream, StreamingChunk.contentBlockStop(0));

            // If there are tool calls, send them as additional content blocks
            int toolBlockIndex = 1;
            if (parseResult.hasToolCalls()) {
                for (var toolUse : parseResult.getToolUses()) {
                    // Send content_block_start for tool_use
                    writeSSE(outputStream, StreamingChunk.toolUseBlockStart(toolBlockIndex, toolUse));
                    // Send content_block_stop for tool_use
                    writeSSE(outputStream, StreamingChunk.contentBlockStop(toolBlockIndex));
                    toolBlockIndex++;
                }
            }

            // Determine stop reason
            String stopReason = parseResult.hasToolCalls() ? "tool_use" : "end_turn";

            // Send message_delta event with stop reason
            var usage = AnthropicUsage.of(
                estimateTokens(prompt),
                outputTokens
            );
            writeSSE(outputStream, StreamingChunk.messageDelta(stopReason, usage));

            // Send message_stop event
            writeSSE(outputStream, StreamingChunk.messageStop());

            outputStream.flush();

            // Log the converted response (build for logging)
            var response = parseResult.hasToolCalls()
                ? AnthropicResponse.createWithToolUses(
                    messageId, model,
                    parseResult.getTextBeforeToolCalls(),
                    parseResult.getToolUses(),
                    usage
                )
                : AnthropicResponse.create(messageId, model, fullResponse.toString(), usage);
            debugLogger.logConvertedAnthropicResponse(response);

            // Also log to individual file if file logging is enabled
            if (debugLogger.isIndividualFileLoggingEnabled()) {
                try {
                    var json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(response);
                    var seqStr = String.format("%04d", debugLogger.getFileLogger().getCurrentSequence() - 1);
                    debugLogger.getFileLogger().logConvertedAnthropicResponse(seqStr, json);
                } catch (Exception ex) {
                    System.err.println("[PROXY] Failed to log converted response to file: " + ex.getMessage());
                }
            }

        } catch (Exception e) {
            // Send error event
            writeSSE(outputStream, StreamingChunk.error("api_error", e.getMessage()));
            outputStream.flush();
        }
    }

    /**
     * Handle non-streaming /v1/messages request
     */
    private void handleNonStreamingMessages(Context ctx, AnthropicRequest request, CCAPICredential credential) throws IOException {
        // Convert Anthropic request to CCAPI prompt
        var prompt = MessageToPromptConverter.convert(request);

        // Log converted prompt
        debugLogger.logConvertedPrompt(prompt);

        // Terse account routing info
        var health = healthCheckClient.getHealthMonitor().getHealth(credential.id());
        var usageStr = health.latestUsage() != null && health.latestUsage().fiveHour() != null
            ? String.format("%d%%", (int) health.latestUsage().fiveHour().utilization())
            : "?";
        System.out.println(String.format("\n[PROXY] Non-streaming via %s [%s, usage:%s]",
            credential.id(), health.state(), usageStr));

        // Create temporary conversation (or visible conversation based on settings)
        var isTemporary = !individualMessagesVisible;

        // Create conversation (doesn't need health check - just metadata)
        var conversation = ccapiClient.createChat(
            credential,
            "Proxy-" + Instant.now().toEpochMilli(),
            isTemporary
        );

        // Send message and get response (with health check wrapper)
        var responseText = healthCheckClient.sendMessage(credential, conversation.uuid(), prompt);

        // Log raw SSE response
        debugLogger.logCcapiResponse(responseText);

        // Parse the SSE response to extract text
        var parser = new xyz.jphil.ccapis.streaming.StreamingEventParser(objectMapper);
        var fullText = parser.parse(responseText);

        // Parse tool calls from the response
        var parseResult = xyz.jphil.ccapis.proxy.toolcalls.ToolCallParser.parse(fullText, debugLogger);

        // Check for tool call failure nudge (PRP-15: diagnostic logging)
        checkForToolCallFailure(request, fullText, parseResult.hasToolCalls());

        // Generate response
        var messageId = "msg_" + System.currentTimeMillis();
        var model = request.getModel() != null ? request.getModel() : "claude-3-opus-20240229";

        var usage = AnthropicUsage.of(
            estimateTokens(prompt),
            estimateTokens(fullText)
        );

        // Create response with tool calls if present
        var response = parseResult.hasToolCalls()
            ? AnthropicResponse.createWithToolUses(
                messageId, model,
                parseResult.getTextBeforeToolCalls(),
                parseResult.getToolUses(),
                usage
            )
            : AnthropicResponse.create(messageId, model, fullText, usage);

        // Log the converted response
        debugLogger.logConvertedAnthropicResponse(response);

        // Also log to individual file if file logging is enabled
        if (debugLogger.isIndividualFileLoggingEnabled()) {
            try {
                var json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(response);
                var seqStr = String.format("%04d", debugLogger.getFileLogger().getCurrentSequence() - 1);
                debugLogger.getFileLogger().logConvertedAnthropicResponse(seqStr, json);
            } catch (Exception e) {
                System.err.println("[PROXY] Failed to log converted response to file: " + e.getMessage());
            }
        }

        ctx.json(response);
    }

    /**
     * Legacy completion endpoint (basic implementation)
     */
    private void handleComplete(Context ctx) {
        ctx.status(501).json(Map.of(
            "error", "not_implemented",
            "message", "Legacy /v1/complete endpoint is not implemented. Use /v1/messages instead."
        ));
    }

    /**
     * Write SSE event to output stream
     */
    private void writeSSE(java.io.OutputStream out, StreamingChunk chunk) throws IOException {
        var json = objectMapper.writeValueAsString(chunk);
        var event = "event: " + chunk.getType() + "\n" +
                    "data: " + json + "\n\n";
        out.write(event.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /**
     * Estimate token count (rough approximation)
     * TODO: Use proper token counter when available
     */
    private int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        // Rough estimate: ~4 characters per token
        return Math.max(1, text.length() / 4);
    }

    /**
     * Check for tool call failure pattern (PRP-15: diagnostic logging)
     * Uses scoring system on last sentence to detect likely failures:
     * - Last sentence ends with colon: +1 point
     * - Last sentence contains intent phrase (I'll, Let me, etc.): +1 point
     * - Score >= 2: Log warning (likely tool call failure)
     * - Score < 2: Ignore (probably legitimate response)
     *
     * Note: "I'll now" / "Let me now" are stronger indicators (future enhancement)
     */
    private void checkForToolCallFailure(AnthropicRequest request, String responseText, boolean hasToolCalls) {
        // Only check if tools were available in the request
        if (request.getTools() == null || request.getTools().isEmpty()) {
            return;
        }

        // If tool calls were successfully generated, no issue
        if (hasToolCalls) {
            return;
        }

        var trimmedResponse = responseText.trim();

        // Extract last sentence (split by . ! ?)
        var sentences = trimmedResponse.split("[.!?]");
        if (sentences.length == 0) {
            return;
        }

        var lastSentence = sentences[sentences.length - 1].trim();
        if (lastSentence.isEmpty()) {
            return;
        }

        var lowerLastSentence = lastSentence.toLowerCase();
        int score = 0;
        var reasons = new java.util.ArrayList<String>();

        // Pattern 1: Last sentence ends with colon (with optional whitespace)
        // Matches: ":" or ": " or " :" or " : "
        if (lastSentence.matches(".*\\s?:\\s?$")) {
            score++;
            reasons.add("ends with colon");
        }

        // Pattern 2: Last sentence contains intent indicators (case-insensitive)
        // Matches: "I'll", "Let me", "I will", "I'm going to", etc.
        if (lowerLastSentence.matches(".*(i'll|let me|i will|i'm going to|i am going to).*")) {
            score++;
            reasons.add("contains intent phrase");
        }

        // Log warning only if score >= 2 (likely failure)
        if (score >= 2) {
            debugLogger.logWarning("[PRP-15] Tool call failure detected! " +
                "Score: " + score + "/3, " +
                "Reasons: " + String.join(", ", reasons) + ". " +
                "Model: " + request.getModel() + ", " +
                "Response length: " + trimmedResponse.length() + " chars, " +
                "Last sentence: \"" + lastSentence + "\"");
        }
    }

    /**
     * Start the proxy server
     */
    public void start() {
        WindowsConsoleSetUnicodeOutput.enable();
        app.start(port);
        printLines(
            "╔════════════════════════════════════════════════════════════════╗",
"║  CCAPI Anthropic-Compatible Proxy Server                      ║", "╚════════════════════════════════════════════════════════════════╝",
            "",
            "Server listening on: http://localhost:" + port,
            "Active Credentials: " + activeCredentials.size(),
            activeCredentials.stream()
                .map(c -> "  - " + c.id() + " (" + c.name() + ")")
                .reduce("", (a, b) -> a + "\n" + b),
            "",
            "Endpoints:",
            "  POST http://localhost:" + port + "/v1/messages",
            "  GET  http://localhost:" + port + "/health",
            "",
            "Compatible with:",
            "  - Claude Code",
            "  - Anthropic API clients",
            "  - Any tool using Anthropic Messages API format",
            "",
            "Features:",
            "  ✓ Streaming responses",
            "  ✓ Multi-turn conversations",
            "  ✓ System prompts",
            "  ✗ Temperature control (CCAPI limitation)",
            "",
            "Press Ctrl+C to stop the server",
            ""
        );
    }

    /**
     * Stop the proxy server
     */
    public void stop() {
        app.stop();
        ccapiClient.shutdown();
    }

    /**
     * Main entry point
     */
    public static void main(String[] args) {

        int port = 8080;
        String settingsPath = null;
        boolean noTray = false;

        // Parse command line arguments
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--port") && i + 1 < args.length) {
                port = Integer.parseInt(args[i + 1]);
                i++;
            } else if (args[i].equals("--settings") && i + 1 < args.length) {
                settingsPath = args[i + 1];
                i++;
            } else if (args[i].equals("--no-tray")) {
                noTray = true;
            } else if (args[i].equals("--help")) {
                printHelp();
                return;
            }
        }

        try {
            var proxy = new CCAPIProxy(port, settingsPath);
            proxy.start();

            // Create system tray icon (unless disabled)
            SystemTrayManager trayManager = null;
            if (!noTray && SystemTrayManager.isSupported()) {
                trayManager = new SystemTrayManager(port);
                final var finalTrayManager = trayManager;
                trayManager.show(() -> {
                    System.out.println("\nShutting down proxy server (via system tray)...");
                    finalTrayManager.hide();
                    proxy.stop();
                    System.out.println("Server stopped.");
                    System.exit(0);
                });
            } else if (!noTray) {
                System.out.println("[PROXY] System tray not available (use Ctrl+C to stop server)");
            }

            // Add shutdown hook for graceful shutdown (Ctrl+C, etc.)
            final var finalTrayManagerForHook = trayManager;
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\nShutting down proxy server...");
                if (finalTrayManagerForHook != null) {
                    finalTrayManagerForHook.hide();
                }
                proxy.stop();
                System.out.println("Server stopped.");
            }));

        } catch (Exception e) {
            System.err.println("Failed to start proxy: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Print usage help
     */
    private static void printHelp() {
        printLines(
            "CCAPI Anthropic-Compatible Proxy Server",
            "",
            "Usage: java xyz.jphil.ccapis.proxy.CCAPIProxy [options]",
            "",
            "Options:",
            "  --port <port>           Port to listen on (default: 8080)",
            "  --settings <path>       Path to CCAPIsSettings.xml file",
            "  --no-tray               Disable system tray icon",
            "  --help                  Show this help message",
            "",
            "Examples:",
            "  java xyz.jphil.ccapis.proxy.CCAPIProxy",
            "  java xyz.jphil.ccapis.proxy.CCAPIProxy --port 8080",
            "  java xyz.jphil.ccapis.proxy.CCAPIProxy --settings /path/to/CCAPIsSettings.xml",
            "  javaw xyz.jphil.ccapis.proxy.CCAPIProxy --port 8080",
            "",
            "System Tray:",
            "  When running with javaw.exe (Windows GUI mode), a system tray icon will appear.",
            "  Right-click the icon to exit the server gracefully.",
            "  The system tray provides an easy way to stop the server when no console is visible."
        );
    }
}

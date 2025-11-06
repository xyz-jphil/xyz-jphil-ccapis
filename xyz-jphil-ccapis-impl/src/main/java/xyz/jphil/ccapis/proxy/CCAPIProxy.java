package xyz.jphil.ccapis.proxy;

import io.javalin.Javalin;
import io.javalin.http.Context;
import com.fasterxml.jackson.databind.ObjectMapper;
import xyz.jphil.ccapis.CCAPIClient;
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

        // Create CCAPI client (now stateless)
        this.ccapiClient = new CCAPIClient();

        // Create JSON mapper with proper configuration
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        this.objectMapper.configure(
            com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
            false
        );

        // Create debug logger with loaded settings
        this.debugLogger = new DebugLogger(this.proxySettings, objectMapper);

        // Create Javalin app
        this.app = Javalin.create(config -> {
            config.showJavalinBanner = false;
            config.http.asyncTimeout = 300000L; // 5 minute timeout for long responses
        });

        setupRoutes();
    }

    /**
     * Get the current credential (with round-robin rotation)
     */
    private synchronized CCAPICredential getCurrentCredential() {
        var credential = activeCredentials.get(currentCredentialIndex);
        // Rotate to next credential for the next request
        currentCredentialIndex = (currentCredentialIndex + 1) % activeCredentials.size();
        return credential;
    }

    /**
     * Setup all HTTP routes
     */
    private void setupRoutes() {
        // Health check
        app.get("/health", this::handleHealth);

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

        // Debug logging (legacy - keeping for console output)
        System.out.println("\n[PROXY] Streaming request received");
        System.out.println("[PROXY] Converted prompt length: " + prompt.length() + " chars");

        // Create temporary conversation (or visible conversation based on settings)
        var isTemporary = !individualMessagesVisible;

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

            // Send content_block_stop event
            writeSSE(outputStream, StreamingChunk.contentBlockStop(0));

            // Send message_delta event with stop reason
            var usage = AnthropicUsage.of(
                estimateTokens(prompt),
                outputTokens
            );
            writeSSE(outputStream, StreamingChunk.messageDelta("end_turn", usage));

            // Send message_stop event
            writeSSE(outputStream, StreamingChunk.messageStop());

            outputStream.flush();

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

        // Debug logging (legacy - keeping for console output)
        System.out.println("\n[PROXY] Non-streaming request received");
        System.out.println("[PROXY] Converted prompt length: " + prompt.length() + " chars");

        // Create temporary conversation (or visible conversation based on settings)
        var isTemporary = !individualMessagesVisible;

        var conversation = ccapiClient.createChat(
            credential,
            "Proxy-" + Instant.now().toEpochMilli(),
            isTemporary
        );

        // Send message and get response
        var responseText = ccapiClient.sendMessage(credential, conversation.uuid(), prompt);

        // Log raw SSE response
        debugLogger.logCcapiResponse(responseText);

        // Parse the SSE response to extract text
        var parser = new xyz.jphil.ccapis.streaming.StreamingEventParser(objectMapper);
        var fullText = parser.parse(responseText);

        // Generate response
        var messageId = "msg_" + System.currentTimeMillis();
        var model = request.getModel() != null ? request.getModel() : "claude-3-opus-20240229";

        var usage = AnthropicUsage.of(
            estimateTokens(prompt),
            estimateTokens(fullText)
        );

        var response = AnthropicResponse.create(messageId, model, fullText, usage);

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

        // Parse command line arguments
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--port") && i + 1 < args.length) {
                port = Integer.parseInt(args[i + 1]);
                i++;
            } else if (args[i].equals("--settings") && i + 1 < args.length) {
                settingsPath = args[i + 1];
                i++;
            } else if (args[i].equals("--help")) {
                printHelp();
                return;
            }
        }

        try {
            var proxy = new CCAPIProxy(port, settingsPath);
            proxy.start();

            // Add shutdown hook
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\nShutting down proxy server...");
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
            "  --help                  Show this help message",
            "",
            "Examples:",
            "  java xyz.jphil.ccapis.proxy.CCAPIProxy",
            "  java xyz.jphil.ccapis.proxy.CCAPIProxy --port 8080",
            "  java xyz.jphil.ccapis.proxy.CCAPIProxy --settings /path/to/CCAPIsSettings.xml"
        );
    }
}

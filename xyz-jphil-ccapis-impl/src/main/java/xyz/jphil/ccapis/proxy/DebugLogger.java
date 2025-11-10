package xyz.jphil.ccapis.proxy;

import com.fasterxml.jackson.databind.ObjectMapper;
import xyz.jphil.ccapis.config.Settings;
import xyz.jphil.ccapis.proxy.anthropic.AnthropicRequest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Debug logging utility for CCAPI Proxy
 *
 * <p>Handles logging of:
 * <ul>
 *   <li>Incoming Anthropic API requests</li>
 *   <li>Converted CCAPI prompts</li>
 *   <li>CCAPI responses</li>
 * </ul>
 *
 * <p>Features:
 * <ul>
 *   <li>Console output with configurable limits</li>
 *   <li>File output with timestamps (one file per conversation)</li>
 *   <li>Pretty-printed JSON</li>
 *   <li>Controlled by CCAPIsSettings.xml configuration</li>
 * </ul>
 */
public class DebugLogger {

    private final Settings.ProxySettings.ConversationDebug debugSettings;
    private final ObjectMapper objectMapper;
    private final Path conversationLogsDir;
    private Path currentConversationFile;
    private final RequestResponseFileLogger fileLogger;

    private static final DateTimeFormatter TIMESTAMP_DISPLAY =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
                         .withZone(ZoneId.of("UTC"));

    private static final DateTimeFormatter FILE_NAME_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm")
                         .withZone(ZoneId.of("UTC"));

    private static final DateTimeFormatter RUN_FOLDER_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss")
                         .withZone(ZoneId.of("UTC"));

    /**
     * Create debug logger with settings
     */
    public DebugLogger(Settings.ProxySettings proxySettings, ObjectMapper objectMapper) {
        this.debugSettings = proxySettings != null && proxySettings.conversationDebug() != null
            ? proxySettings.conversationDebug()
            : null;
        this.objectMapper = objectMapper;

        // Set up conversation logs directory with timestamped folder per server run
        // <userhome>\xyz-jphil\ccapis\conversations-logs\yyyy-MM-dd_HHmmss\
        var userHome = System.getProperty("user.home");
        var baseLogsDir = Path.of(userHome, "xyz-jphil", "ccapis", "conversations-logs");
        var runTimestamp = RUN_FOLDER_FORMAT.format(Instant.now());
        this.conversationLogsDir = baseLogsDir.resolve(runTimestamp);

        // Create directory for this run
        try {
            Files.createDirectories(conversationLogsDir);
            System.out.println("[DEBUG] Conversation logs directory: " + conversationLogsDir);
        } catch (IOException e) {
            System.err.println("[DEBUG] Failed to create conversations-logs directory: " + e.getMessage());
        }

        // Clear old run folders if configured (keeps only current run)
        if (isFileOutputEnabled() && debugSettings.outputToFile().clearPreviousAtStart()) {
            try {
                if (Files.exists(baseLogsDir)) {
                    Files.list(baseLogsDir)
                         .filter(Files::isDirectory)
                         .filter(p -> !p.equals(conversationLogsDir)) // Don't delete current run
                         .forEach(p -> {
                             try {
                                 // Delete the entire old run folder
                                 Files.walk(p)
                                      .sorted((a, b) -> b.compareTo(a)) // Delete files before dirs
                                      .forEach(path -> {
                                          try {
                                              Files.delete(path);
                                          } catch (IOException e) {
                                              System.err.println("[DEBUG] Failed to delete: " + path);
                                          }
                                      });
                             } catch (IOException e) {
                                 System.err.println("[DEBUG] Failed to delete run folder: " + p);
                             }
                         });
                }
            } catch (IOException e) {
                System.err.println("[DEBUG] Failed to clear old run folders: " + e.getMessage());
            }
        }

        // Initialize individual file logger if enabled
        if (isFileOutputEnabled() && debugSettings.outputToFile().saveAsIndividualFiles()) {
            var createIndex = debugSettings.outputToFile().createIndexFile();
            this.fileLogger = new RequestResponseFileLogger(conversationLogsDir, createIndex);
            System.out.println("[DEBUG] Individual file logging enabled with index=" + createIndex);
        } else {
            this.fileLogger = null;
        }
    }

    /**
     * Start a new conversation log file
     * Creates file with name format: yyyy-MM-dd_HHmm.log (UTC time)
     */
    public void startNewConversation() {
        if (!isFileOutputEnabled()) {
            return;
        }

        var timestamp = ZonedDateTime.now(ZoneId.of("UTC"));
        var fileName = FILE_NAME_FORMAT.format(timestamp) + ".log";
        this.currentConversationFile = conversationLogsDir.resolve(fileName);

        // Write conversation start header
        var header = "=".repeat(80) + "\n" +
                     "CONVERSATION LOG - Started at " + TIMESTAMP_DISPLAY.format(timestamp) + " UTC\n" +
                     "=".repeat(80) + "\n\n";
        logToFile(header);
    }

    /**
     * Check if debug logging is enabled
     */
    public boolean isEnabled() {
        return debugSettings != null && debugSettings.enabled();
    }

    /**
     * Check if input logging is enabled
     */
    public boolean isInputLoggingEnabled() {
        return isEnabled() && debugSettings.showInput();
    }

    /**
     * Check if output logging is enabled
     */
    public boolean isOutputLoggingEnabled() {
        return isEnabled() && debugSettings.showOutput();
    }

    /**
     * Check if file output is enabled
     */
    public boolean isFileOutputEnabled() {
        return isEnabled()
            && debugSettings.outputToFile() != null
            && debugSettings.outputToFile().enabled();
    }

    /**
     * Log incoming Anthropic request
     */
    public void logAnthropicRequest(AnthropicRequest request, String rawJson) {
        if (!isInputLoggingEnabled()) {
            return;
        }

        var sb = new StringBuilder();
        sb.append("\n").append("=".repeat(80)).append("\n");
        sb.append("[").append(timestamp()).append("] INCOMING ANTHROPIC REQUEST\n");
        sb.append("=".repeat(80)).append("\n");

        // Log model
        sb.append("Model: ").append(request.getModel()).append("\n");
        sb.append("Stream: ").append(request.getStream()).append("\n");
        sb.append("Max Tokens: ").append(request.getMaxTokens()).append("\n\n");

        // Log system prompt
        var systemPrompt = request.getSystemPrompt();
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            sb.append("--- SYSTEM PROMPT ---\n");
            sb.append(truncateForConsole(systemPrompt)).append("\n");
            sb.append("System prompt length: ").append(systemPrompt.length()).append(" chars\n\n");
        }

        // Log messages
        sb.append("--- MESSAGES (").append(request.getMessages().size()).append(") ---\n");
        for (int i = 0; i < request.getMessages().size(); i++) {
            var msg = request.getMessages().get(i);
            var content = msg.getTextContent();
            sb.append("\n[").append(i).append("] Role: ").append(msg.getRole()).append("\n");
            sb.append(truncateForConsole(content)).append("\n");
            sb.append("Content length: ").append(content.length()).append(" chars\n");
        }

        // Log raw JSON
        sb.append("\n--- RAW JSON ---\n");
        try {
            var prettyJson = objectMapper.writerWithDefaultPrettyPrinter()
                                        .writeValueAsString(request);
            sb.append(truncateForConsole(prettyJson)).append("\n");
        } catch (Exception e) {
            sb.append(truncateForConsole(rawJson)).append("\n");
        }

        sb.append("=".repeat(80)).append("\n");

        logToConsole(sb.toString());
        logToFile(sb.toString());
    }

    /**
     * Log converted CCAPI prompt
     */
    public void logConvertedPrompt(String prompt) {
        if (!isInputLoggingEnabled()) {
            return;
        }

        var sb = new StringBuilder();
        sb.append("\n").append("=".repeat(80)).append("\n");
        sb.append("[").append(timestamp()).append("] CONVERTED CCAPI PROMPT\n");
        sb.append("=".repeat(80)).append("\n");
        sb.append("Prompt length: ").append(prompt.length()).append(" chars\n\n");
        sb.append("--- FULL PROMPT ---\n");
        sb.append(truncateForConsole(prompt)).append("\n");
        sb.append("=".repeat(80)).append("\n");

        logToConsole(sb.toString());
        logToFile(sb.toString());
    }

    /**
     * Log CCAPI response
     */
    public void logCcapiResponse(String response) {
        if (!isOutputLoggingEnabled()) {
            return;
        }

        var sb = new StringBuilder();
        sb.append("\n").append("=".repeat(80)).append("\n");
        sb.append("[").append(timestamp()).append("] CCAPI RESPONSE\n");
        sb.append("=".repeat(80)).append("\n");
        sb.append("Response length: ").append(response != null ? response.length() : 0).append(" chars\n\n");
        sb.append("--- RESPONSE ---\n");
        sb.append(truncateForConsole(response)).append("\n");
        sb.append("=".repeat(80)).append("\n");

        logToConsole(sb.toString());
        logToFile(sb.toString());
    }

    /**
     * Log converted Anthropic response (final response sent to Claude Code)
     */
    public void logConvertedAnthropicResponse(Object response) {
        if (!isOutputLoggingEnabled()) {
            return;
        }

        var sb = new StringBuilder();
        sb.append("\n").append("=".repeat(80)).append("\n");
        sb.append("[").append(timestamp()).append("] CONVERTED ANTHROPIC RESPONSE\n");
        sb.append("=".repeat(80)).append("\n");

        try {
            var prettyJson = objectMapper.writerWithDefaultPrettyPrinter()
                                        .writeValueAsString(response);
            sb.append("--- JSON RESPONSE ---\n");
            sb.append(truncateForConsole(prettyJson)).append("\n");
            sb.append("Response length: ").append(prettyJson.length()).append(" chars\n");
        } catch (Exception e) {
            sb.append("Failed to serialize response: ").append(e.getMessage()).append("\n");
        }

        sb.append("=".repeat(80)).append("\n");

        logToConsole(sb.toString());
        logToFile(sb.toString());
    }

    /**
     * Log streaming chunk
     */
    public void logStreamingChunk(String chunk) {
        if (!isOutputLoggingEnabled()) {
            return;
        }

        // Only log to file for streaming to avoid console spam
        var sb = new StringBuilder();
        sb.append("[").append(timestamp()).append("] STREAM CHUNK: ");
        sb.append(chunk != null ? chunk : "(null)").append("\n");

        logToFile(sb.toString());
    }

    /**
     * Log informational message
     */
    public void logInfo(String message) {
        var sb = new StringBuilder();
        sb.append("[").append(timestamp()).append("] INFO: ");
        sb.append(message).append("\n");

        logToFile(sb.toString());
    }

    /**
     * Log warning message
     */
    public void logWarning(String message) {
        var sb = new StringBuilder();
        sb.append("[").append(timestamp()).append("] WARNING: ");
        sb.append(message).append("\n");

        logToConsole(sb.toString());
        logToFile(sb.toString());
    }

    /**
     * Log type conversion decision (XML string to typed value)
     * Creates dedicated type-conversion.log file in conversation directory
     */
    public void logTypeConversion(String message) {
        var sb = new StringBuilder();
        sb.append("[").append(timestamp()).append("] ");
        sb.append(message).append("\n");

        // Log to dedicated type conversion file
        logToTypeConversionFile(sb.toString());
    }

    /**
     * Log error
     */
    public void logError(String message, Throwable error) {
        var sb = new StringBuilder();
        sb.append("\n").append("=".repeat(80)).append("\n");
        sb.append("[").append(timestamp()).append("] ERROR\n");
        sb.append("=".repeat(80)).append("\n");
        sb.append(message).append("\n");
        if (error != null) {
            sb.append("Exception: ").append(error.getClass().getName()).append("\n");
            sb.append("Message: ").append(error.getMessage()).append("\n");
        }
        sb.append("=".repeat(80)).append("\n");

        logToConsole(sb.toString());
        logToFile(sb.toString());
    }

    /**
     * Truncate text for console output based on stdOutLimit
     */
    private String truncateForConsole(String text) {
        if (text == null) {
            return "(null)";
        }

        int limit = debugSettings != null ? debugSettings.stdOutLimit() : 1000;

        if (text.length() <= limit) {
            return text;
        }

        return text.substring(0, limit) + "\n... (truncated " + (text.length() - limit) + " chars)";
    }

    /**
     * Log to console
     */
    private void logToConsole(String message) {
        System.out.print(message);
        System.out.flush();
    }

    /**
     * Log to file
     */
    private void logToFile(String message) {
        if (!isFileOutputEnabled() || currentConversationFile == null) {
            return;
        }

        try {
            Files.writeString(
                currentConversationFile,
                message,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
            );
        } catch (IOException e) {
            System.err.println("[DEBUG] Failed to write to log file: " + e.getMessage());
        }
    }

    /**
     * Get current timestamp (UTC)
     */
    private String timestamp() {
        return TIMESTAMP_DISPLAY.format(Instant.now());
    }

    /**
     * Get the file logger (for direct HTTP request/response logging)
     * Returns null if individual file logging is not enabled
     */
    public RequestResponseFileLogger getFileLogger() {
        return fileLogger;
    }

    /**
     * Check if individual file logging is enabled
     */
    public boolean isIndividualFileLoggingEnabled() {
        return fileLogger != null;
    }

    /**
     * Log to dedicated type conversion file (type-conversion.log in conversation directory)
     */
    private void logToTypeConversionFile(String message) {
        if (!isIndividualFileLoggingEnabled()) {
            return;
        }

        try {
            var typeConversionFile = fileLogger.getLogsDirectory().resolve("type-conversion.log");
            Files.writeString(
                typeConversionFile,
                message,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
            );
        } catch (IOException e) {
            System.err.println("Failed to write to type-conversion.log: " + e.getMessage());
        }
    }
}

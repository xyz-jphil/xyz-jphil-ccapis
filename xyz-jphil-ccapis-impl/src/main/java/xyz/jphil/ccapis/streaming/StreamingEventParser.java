package xyz.jphil.ccapis.streaming;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/**
 * Parser for Server-Sent Events (SSE) streaming responses from CCAPI
 *
 * <p>Handles the CCAPI-specific SSE format:
 * <pre>
 * data: ping
 * data: {"completion":"Hello","stop_reason":null,...}
 * data: {"completion":" there","stop_reason":null,...}
 * data: {"completion":"!","stop_reason":"end_turn",...}
 * </pre>
 *
 * <p>Usage:
 * <pre>
 * var parser = new StreamingEventParser();
 * parser.onText(text -> System.out.print(text));
 * parser.onComplete(() -> System.out.println("\nDone!"));
 * parser.parse(sseResponseString);
 * </pre>
 */
public class StreamingEventParser {

    private final ObjectMapper objectMapper;
    private Consumer<String> onTextCallback;
    private Consumer<StreamingEvent> onEventCallback;
    private Runnable onCompleteCallback;
    private Consumer<String> onErrorCallback;

    /**
     * Create a new parser with default ObjectMapper
     */
    public StreamingEventParser() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.configure(
            com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
            false
        );
    }

    /**
     * Create a new parser with custom ObjectMapper
     */
    public StreamingEventParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Set callback for text chunks
     * Called for each piece of text received
     */
    public StreamingEventParser onText(Consumer<String> callback) {
        this.onTextCallback = callback;
        return this;
    }

    /**
     * Set callback for raw streaming events
     * Called for each parsed event with full details
     */
    public StreamingEventParser onEvent(Consumer<StreamingEvent> callback) {
        this.onEventCallback = callback;
        return this;
    }

    /**
     * Set callback for completion
     * Called when streaming is complete
     */
    public StreamingEventParser onComplete(Runnable callback) {
        this.onCompleteCallback = callback;
        return this;
    }

    /**
     * Set callback for errors
     * Called when an error occurs during parsing
     */
    public StreamingEventParser onError(Consumer<String> callback) {
        this.onErrorCallback = callback;
        return this;
    }

    /**
     * Parse SSE response from a String
     *
     * @param sseResponse the complete SSE response as a string
     * @return the accumulated text from all events
     */
    public String parse(String sseResponse) throws IOException {
        return parse(new BufferedReader(new StringReader(sseResponse)));
    }

    /**
     * Parse SSE response from an InputStream
     *
     * @param inputStream the SSE response stream
     * @return the accumulated text from all events
     */
    public String parse(InputStream inputStream) throws IOException {
        return parse(new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8)));
    }

    /**
     * Parse SSE response from a BufferedReader
     *
     * @param reader the reader containing SSE data
     * @return the accumulated text from all events
     */
    public String parse(BufferedReader reader) throws IOException {
        var fullText = new StringBuilder();
        String line;
        boolean hasContent = false;

        while ((line = reader.readLine()) != null) {
            // SSE format: "data: {json}" or "data: ping"
            if (line.startsWith("data: ")) {
                var data = line.substring(6); // Remove "data: " prefix

                // Skip ping events
                if (data.equals("ping") || data.trim().isEmpty()) {
                    continue;
                }

                try {
                    // Parse JSON event
                    JsonNode json = objectMapper.readTree(data);
                    var event = parseEvent(json);

                    // Notify event callback if registered
                    if (onEventCallback != null) {
                        onEventCallback.accept(event);
                    }

                    // Handle completion field (CCAPI format)
                    if (json.has("completion")) {
                        var text = json.get("completion").asText();
                        if (!text.isEmpty()) {
                            fullText.append(text);
                            hasContent = true;

                            // Notify text callback
                            if (onTextCallback != null) {
                                onTextCallback.accept(text);
                            }
                        }
                    }

                    // Handle text field (alternative format)
                    if (json.has("text") && !json.has("completion")) {
                        var text = json.get("text").asText();
                        if (!text.isEmpty()) {
                            fullText.append(text);
                            hasContent = true;

                            // Notify text callback
                            if (onTextCallback != null) {
                                onTextCallback.accept(text);
                            }
                        }
                    }

                    // Check for completion
                    if (json.has("stop_reason") && !json.get("stop_reason").isNull()) {
                        event.setStopReason(json.get("stop_reason").asText());
                        break;
                    }

                    // Handle error events
                    if (json.has("error")) {
                        var error = json.get("error").asText();
                        if (onErrorCallback != null) {
                            onErrorCallback.accept(error);
                        }
                    }

                } catch (Exception e) {
                    // If JSON parsing fails, try simple regex extraction (fallback)
                    var text = extractTextWithRegex(data);
                    if (!text.isEmpty()) {
                        fullText.append(text);
                        hasContent = true;

                        if (onTextCallback != null) {
                            onTextCallback.accept(text);
                        }
                    }
                }
            }
        }

        // Notify completion callback
        if (onCompleteCallback != null && hasContent) {
            onCompleteCallback.run();
        }

        return fullText.toString();
    }

    /**
     * Parse event details from JSON
     */
    private StreamingEvent parseEvent(JsonNode json) {
        var event = new StreamingEvent();

        if (json.has("completion")) {
            event.setText(json.get("completion").asText());
        }

        if (json.has("text") && !json.has("completion")) {
            event.setText(json.get("text").asText());
        }

        if (json.has("stop_reason") && !json.get("stop_reason").isNull()) {
            event.setStopReason(json.get("stop_reason").asText());
        }

        if (json.has("model")) {
            event.setModel(json.get("model").asText());
        }

        // Store raw JSON for advanced use cases
        event.setRawJson(json);

        return event;
    }

    /**
     * Fallback regex-based text extraction (for robustness)
     * Used when JSON parsing fails
     */
    private String extractTextWithRegex(String data) {
        // Look for "completion":"text" pattern
        var startIdx = data.indexOf("\"completion\":\"");
        if (startIdx >= 0) {
            startIdx += 14; // Length of "completion":""
            var endIdx = data.indexOf("\"", startIdx);
            if (endIdx > startIdx) {
                var text = data.substring(startIdx, endIdx);
                // Unescape basic characters
                return text.replace("\\n", "\n")
                           .replace("\\\"", "\"")
                           .replace("\\\\", "\\")
                           .replace("\\t", "\t");
            }
        }

        // Look for "text":"..." pattern
        startIdx = data.indexOf("\"text\":\"");
        if (startIdx >= 0) {
            startIdx += 8; // Length of "text":""
            var endIdx = data.indexOf("\"", startIdx);
            if (endIdx > startIdx) {
                var text = data.substring(startIdx, endIdx);
                return text.replace("\\n", "\n")
                           .replace("\\\"", "\"")
                           .replace("\\\\", "\\")
                           .replace("\\t", "\t");
            }
        }

        return "";
    }

    /**
     * Streaming event data
     */
    public static class StreamingEvent {
        private String text;
        private String stopReason;
        private String model;
        private JsonNode rawJson;

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }

        public String getStopReason() {
            return stopReason;
        }

        public void setStopReason(String stopReason) {
            this.stopReason = stopReason;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public JsonNode getRawJson() {
            return rawJson;
        }

        public void setRawJson(JsonNode rawJson) {
            this.rawJson = rawJson;
        }

        public boolean isComplete() {
            return stopReason != null && !stopReason.isEmpty();
        }
    }
}

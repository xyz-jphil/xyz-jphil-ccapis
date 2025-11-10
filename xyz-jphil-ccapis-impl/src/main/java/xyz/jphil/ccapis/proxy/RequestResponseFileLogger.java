package xyz.jphil.ccapis.proxy;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Logs individual HTTP requests and responses as separate files.
 *
 * <p>For each request/response pair, creates:
 * <ul>
 *   <li>NNNN-req.header - Raw request headers</li>
 *   <li>NNNN-req.body - Raw request body</li>
 *   <li>NNNN-res.header - Raw response headers</li>
 *   <li>NNNN-res.body - Raw response body</li>
 *   <li>NNNN-meta.properties - Metadata (URL, timing, status, etc.)</li>
 * </ul>
 *
 * <p>Also maintains an index.txt file with one-line summaries for quick scanning.
 *
 * <p>File timestamps (creation/modification) provide timing information.
 */
public class RequestResponseFileLogger {

    private final Path logsDirectory;
    private final AtomicInteger sequenceNumber;
    private final boolean createIndexFile;

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
                         .withZone(ZoneId.of("UTC"));

    /**
     * Create a new request/response file logger
     *
     * @param logsDirectory directory where files will be saved
     * @param createIndexFile whether to maintain an index.txt summary file
     */
    public RequestResponseFileLogger(Path logsDirectory, boolean createIndexFile) {
        this.logsDirectory = logsDirectory;
        this.sequenceNumber = new AtomicInteger(0);
        this.createIndexFile = createIndexFile;

        try {
            Files.createDirectories(logsDirectory);
        } catch (IOException e) {
            System.err.println("[RequestResponseFileLogger] Failed to create logs directory: " + e.getMessage());
        }

        if (createIndexFile) {
            initializeIndexFile();
        }
    }

    /**
     * Initialize the index file with header
     */
    private void initializeIndexFile() {
        var indexFile = logsDirectory.resolve("index.txt");
        try {
            var header = "# Request/Response Index - Created " + TIMESTAMP_FORMAT.format(Instant.now()) + " UTC\n" +
                        "# Format: [NNNN] Timestamp | Method | URL | Status | Size\n" +
                        "# " + "=".repeat(100) + "\n";
            Files.writeString(indexFile, header, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            System.err.println("[RequestResponseFileLogger] Failed to initialize index file: " + e.getMessage());
        }
    }

    /**
     * Log a complete request/response transaction
     *
     * @param request the request information
     */
    public void logTransaction(Transaction request) {
        var seq = sequenceNumber.getAndIncrement();
        var seqStr = String.format("%04d", seq);

        try {
            // Write request files
            writeFile(seqStr + "-req.header", request.requestHeaders);
            writeFile(seqStr + "-req.body", request.requestBody != null ? request.requestBody : "");

            // Write response files
            writeFile(seqStr + "-res.header", request.responseHeaders != null ? request.responseHeaders : "");
            writeFile(seqStr + "-res.body", request.responseBody != null ? request.responseBody : "");

            // Write metadata
            writeMetadata(seqStr, request);

            // Update index
            if (createIndexFile) {
                updateIndex(seqStr, request);
            }

        } catch (IOException e) {
            System.err.println("[RequestResponseFileLogger] Failed to log transaction " + seqStr + ": " + e.getMessage());
        }
    }

    /**
     * Write a file with the given name and content
     */
    private void writeFile(String fileName, String content) throws IOException {
        var filePath = logsDirectory.resolve(fileName);
        Files.writeString(filePath, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    /**
     * Write metadata properties file
     */
    private void writeMetadata(String seqStr, Transaction tx) throws IOException {
        var props = new Properties();

        // Request metadata
        props.setProperty("request.method", tx.method != null ? tx.method : "UNKNOWN");
        props.setProperty("request.url", tx.url != null ? tx.url : "");
        props.setProperty("request.timestamp", tx.requestTimestamp != null ? tx.requestTimestamp : "");

        // Response metadata
        props.setProperty("response.status", String.valueOf(tx.responseStatus));
        props.setProperty("response.timestamp", tx.responseTimestamp != null ? tx.responseTimestamp : "");

        // Timing
        if (tx.durationMs != null) {
            props.setProperty("duration.ms", String.valueOf(tx.durationMs));
        }

        // Sizes
        props.setProperty("request.body.size", String.valueOf(tx.requestBody != null ? tx.requestBody.length() : 0));
        props.setProperty("response.body.size", String.valueOf(tx.responseBody != null ? tx.responseBody.length() : 0));

        // Domain/Host extraction
        if (tx.url != null) {
            try {
                var uri = java.net.URI.create(tx.url);
                props.setProperty("request.host", uri.getHost() != null ? uri.getHost() : "");
                props.setProperty("request.path", uri.getPath() != null ? uri.getPath() : "");
            } catch (Exception e) {
                // Ignore URI parsing errors
            }
        }

        // Additional metadata
        if (tx.metadata != null) {
            tx.metadata.forEach(props::setProperty);
        }

        // Write properties file
        var metaPath = logsDirectory.resolve(seqStr + "-meta.properties");
        try (var out = Files.newOutputStream(metaPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            props.store(out, "Request/Response Metadata - Transaction " + seqStr);
        }
    }

    /**
     * Update the index file with a summary line
     */
    private void updateIndex(String seqStr, Transaction tx) {
        var indexFile = logsDirectory.resolve("index.txt");
        try {
            var method = tx.method != null ? tx.method : "???";
            var url = tx.url != null ? tx.url : "???";
            var status = tx.responseStatus != 0 ? String.valueOf(tx.responseStatus) : "???";
            var size = tx.responseBody != null ? tx.responseBody.length() : 0;
            var timestamp = tx.requestTimestamp != null ? tx.requestTimestamp : "???";

            // Truncate URL if too long
            if (url.length() > 80) {
                url = url.substring(0, 77) + "...";
            }

            var summary = String.format("[%s] %s | %-6s | %-80s | %s | %d bytes\n",
                                       seqStr, timestamp, method, url, status, size);

            Files.writeString(indexFile, summary, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.err.println("[RequestResponseFileLogger] Failed to update index: " + e.getMessage());
        }
    }

    /**
     * Log the converted Claude-Code-compatible response (what was sent back to Claude Code)
     *
     * @param seqStr sequence number string (e.g., "0004")
     * @param anthropicResponseJson JSON string of the converted Anthropic response
     */
    public void logConvertedAnthropicResponse(String seqStr, String anthropicResponseJson) {
        try {
            writeFile(seqStr + "-ccr.body", anthropicResponseJson);
        } catch (IOException e) {
            System.err.println("[RequestResponseFileLogger] Failed to log converted response: " + e.getMessage());
        }
    }

    /**
     * Get the current sequence number (useful for testing/debugging)
     */
    public int getCurrentSequence() {
        return sequenceNumber.get();
    }

    /**
     * Get the logs directory (conversation directory)
     */
    public Path getLogsDirectory() {
        return logsDirectory;
    }

    /**
     * Represents a complete HTTP transaction (request + response)
     */
    @Data
    @Accessors(fluent = true, chain = true)
    public static class Transaction {
        String method;
        String url;
        String requestHeaders;
        String requestBody;
        String requestTimestamp;

        String responseHeaders;
        String responseBody;
        int responseStatus;
        String responseTimestamp;

        Long durationMs;
        Map<String, String> metadata;
    }
}

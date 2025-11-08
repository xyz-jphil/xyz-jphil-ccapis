package xyz.jphil.ccapis.proxy;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;

/**
 * OkHttp interceptor that logs requests and responses to individual files
 * via RequestResponseFileLogger.
 *
 * <p>Captures:
 * <ul>
 *   <li>Request headers and body</li>
 *   <li>Response headers and body</li>
 *   <li>Timing information</li>
 *   <li>Status codes</li>
 * </ul>
 */
public class RequestResponseLoggingInterceptor implements Interceptor {

    private final RequestResponseFileLogger fileLogger;

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
                         .withZone(ZoneId.of("UTC"));

    public RequestResponseLoggingInterceptor(RequestResponseFileLogger fileLogger) {
        this.fileLogger = fileLogger;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        var request = chain.request();
        var startTime = System.currentTimeMillis();
        var requestTimestamp = TIMESTAMP_FORMAT.format(Instant.now());

        // Capture request details
        var requestHeaders = captureRequestHeaders(request);
        var requestBody = captureRequestBody(request);

        // Execute request
        Response response;
        String responseBody = null;
        String responseHeaders = null;
        int responseStatus = 0;

        try {
            response = chain.proceed(request);
            responseStatus = response.code();

            // Capture response details
            responseHeaders = captureResponseHeaders(response);

            // Read and cache response body
            var originalBody = response.body();
            if (originalBody != null) {
                responseBody = originalBody.string();

                // Recreate response with buffered body
                response = response.newBuilder()
                    .body(ResponseBody.create(responseBody, originalBody.contentType()))
                    .build();
            }

        } catch (IOException e) {
            // Log failed request
            logTransaction(request, requestTimestamp, requestHeaders, requestBody,
                          null, null, 0, startTime);
            throw e;
        }

        // Log successful transaction
        logTransaction(request, requestTimestamp, requestHeaders, requestBody,
                      responseHeaders, responseBody, responseStatus, startTime);

        return response;
    }

    /**
     * Log the complete transaction
     */
    private void logTransaction(Request request, String requestTimestamp,
                               String requestHeaders, String requestBody,
                               String responseHeaders, String responseBody,
                               int responseStatus, long startTime) {
        var endTime = System.currentTimeMillis();
        var responseTimestamp = TIMESTAMP_FORMAT.format(Instant.now());
        var duration = endTime - startTime;

        var metadata = new HashMap<String, String>();
        metadata.put("request.scheme", request.url().scheme());

        var transaction = new RequestResponseFileLogger.Transaction()
            .method(request.method())
            .url(request.url().toString())
            .requestHeaders(requestHeaders)
            .requestBody(requestBody)
            .requestTimestamp(requestTimestamp)
            .responseHeaders(responseHeaders)
            .responseBody(responseBody)
            .responseStatus(responseStatus)
            .responseTimestamp(responseTimestamp)
            .durationMs(duration)
            .metadata(metadata);

        fileLogger.logTransaction(transaction);
    }

    /**
     * Capture request headers as string
     */
    private String captureRequestHeaders(Request request) {
        var sb = new StringBuilder();
        sb.append(request.method()).append(" ").append(request.url()).append("\n");

        var headers = request.headers();
        for (var i = 0; i < headers.size(); i++) {
            sb.append(headers.name(i)).append(": ").append(headers.value(i)).append("\n");
        }

        return sb.toString();
    }

    /**
     * Capture request body as string
     */
    private String captureRequestBody(Request request) {
        try {
            var requestCopy = request.newBuilder().build();
            var buffer = new Buffer();
            var body = requestCopy.body();

            if (body != null) {
                body.writeTo(buffer);
                return buffer.readUtf8();
            }
        } catch (IOException e) {
            return "[Error reading request body: " + e.getMessage() + "]";
        }

        return "";
    }

    /**
     * Capture response headers as string
     */
    private String captureResponseHeaders(Response response) {
        var sb = new StringBuilder();
        // Use actual protocol from response (could be HTTP/1.1, HTTP/2, etc.)
        sb.append(response.protocol()).append(" ").append(response.code()).append(" ").append(response.message()).append("\n");

        var headers = response.headers();
        for (var i = 0; i < headers.size(); i++) {
            sb.append(headers.name(i)).append(": ").append(headers.value(i)).append("\n");
        }

        return sb.toString();
    }
}

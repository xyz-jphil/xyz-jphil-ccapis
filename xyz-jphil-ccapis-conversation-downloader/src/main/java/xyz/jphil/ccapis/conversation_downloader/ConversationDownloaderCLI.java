package xyz.jphil.ccapis.conversation_downloader;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import xyz.jphil.ccapis.CCAPIClient;
import xyz.jphil.ccapis.config.CredentialsManager;
import xyz.jphil.ccapis.conversation_downloader.model.ChatMessage;
import xyz.jphil.ccapis.conversation_downloader.model.ConversationDetail;
import xyz.jphil.ccapis.model.CCAPICredential;
import xyz.jphil.ccapis.model.CCAPIsCredentials;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static luvml.E.*;
import static luvml.A.*;
import static luvml.T.text;
import static luvml.Frags.*;
import static luvml.ProcessingInstruction.xmlDeclaration;
import static xyz.jphil.ccapis.conversation_downloader.ConversationDSL.*;
import luvml.DocType;
import luvml.o.XHtmlStringRenderer;

/**
 * CLI tool to download conversation history from Claude Compatible APIs
 *
 * Usage:
 *   java -jar conversation-downloader.jar <url> [options]
 *
 * Parameters:
 *   <url>            - Conversation URL in format: https://example.com/chat/<conversation-id>
 *   --account <id>   - (Optional) Account ID from CCAPIsCredentials.xml to use
 *   --name <name>    - (Optional) Custom name for saved file (defaults to conversation ID)
 *   --output <dir>   - (Optional) Output directory (defaults to current directory)
 */
public class ConversationDownloaderCLI {

    private static final Pattern URL_PATTERN = Pattern.compile(".*?/chat/([a-zA-Z0-9-]+)");

    public static void main(String[] args) {
        if (args.length == 0) {
            printUsage();
            System.exit(1);
        }

        try {
            var options = parseArguments(args);
            downloadConversation(options);
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void printUsage() {
        System.out.println("Conversation Downloader CLI");
        System.out.println();
        System.out.println("Usage:");
        System.out.println("  java -jar conversation-downloader.jar <url> [options]");
        System.out.println();
        System.out.println("Parameters:");
        System.out.println("  <url>            Conversation URL (e.g., https://example.com/chat/abc-123)");
        System.out.println("  --account <id>   Account ID from CCAPIsCredentials.xml (optional)");
        System.out.println("  --name <name>    Custom name for saved file (optional, defaults to conversation ID)");
        System.out.println("  --output <dir>   Output directory (optional, defaults to current directory)");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  java -jar conversation-downloader.jar https://example.com/chat/abc-123");
        System.out.println("  java -jar conversation-downloader.jar https://example.com/chat/abc-123 --name my-conversation");
        System.out.println("  java -jar conversation-downloader.jar https://example.com/chat/abc-123 --account work --output ./downloads");
    }

    private static DownloadOptions parseArguments(String[] args) {
        var options = new DownloadOptions();
        options.url = args[0];

        for (int i = 1; i < args.length; i++) {
            switch (args[i]) {
                case "--account":
                    if (i + 1 < args.length) {
                        options.accountId = args[++i];
                    }
                    break;
                case "--name":
                    if (i + 1 < args.length) {
                        options.customName = args[++i];
                    }
                    break;
                case "--output":
                    if (i + 1 < args.length) {
                        options.outputDir = args[++i];
                    }
                    break;
            }
        }

        return options;
    }

    private static void downloadConversation(DownloadOptions options) throws Exception {
        // Extract conversation ID from URL
        var conversationId = extractConversationId(options.url);
        System.out.println("Conversation ID: " + conversationId);

        // Load credentials
        var credentialsManager = new CredentialsManager();
        if (!credentialsManager.credentialsFileExists()) {
            throw new IOException("Credentials file not found at: " + credentialsManager.getCredentialsPath());
        }

        CCAPIsCredentials credentials = credentialsManager.load();
        System.out.println("Loaded credentials from: " + credentialsManager.getCredentialsPath());

        // Get credentials to try
        List<CCAPICredential> credentialsToTry;
        if (options.accountId != null) {
            // Use specific account
            var credential = findCredentialById(credentials, options.accountId);
            if (credential == null) {
                throw new IllegalArgumentException("Account ID '" + options.accountId + "' not found in credentials");
            }
            credentialsToTry = List.of(credential);
            System.out.println("Using account: " + credential.id() + " (" + credential.name() + ")");
        } else {
            // Try all active credentials
            credentialsToTry = credentials.getActiveCredentials();
            System.out.println("Trying all " + credentialsToTry.size() + " active credentials...");
        }

        // Try fetching conversation with each credential
        var client = new CCAPIClient();
        ConversationDetail conversation = null;
        CCAPICredential successfulCredential = null;

        for (var credential : credentialsToTry) {
            try {
                System.out.println("Trying credential: " + credential.id() + " (" + credential.name() + ")");
                var conversationData = client.getConversation(credential, conversationId);

                // If we got here, it worked - fetch the detailed conversation
                conversation = fetchDetailedConversation(client, credential, conversationId);
                successfulCredential = credential;
                System.out.println("✓ Successfully fetched conversation using: " + credential.id());
                break;
            } catch (Exception e) {
                System.out.println("✗ Failed with credential " + credential.id() + ": " + e.getMessage());
            }
        }

        if (conversation == null) {
            throw new IOException("Failed to fetch conversation with any available credentials");
        }

        // Save conversation to files (both JSON and XHTML)
        var baseName = options.customName != null ? options.customName : conversationId;
        var outputDir = options.outputDir != null ? options.outputDir : ".";

        var jsonPath = Paths.get(outputDir, baseName + ".json");
        var xhtmlPath = Paths.get(outputDir, baseName + ".conversation.xhtml");

        saveConversationAsJson(conversation, jsonPath);
        saveConversationAsXHtml(conversation, xhtmlPath, successfulCredential, conversationId);

        System.out.println();
        System.out.println("Conversation saved:");
        System.out.println("  JSON: " + jsonPath.toAbsolutePath());
        System.out.println("  XHTML: " + xhtmlPath.toAbsolutePath());
        System.out.println();
        System.out.println("Conversation: " + conversation.getName());
        System.out.println("Messages: " + (conversation.getChatMessages() != null ? conversation.getChatMessages().size() : 0));
        System.out.println("Created: " + conversation.getCreatedAt());
    }

    private static String extractConversationId(String url) {
        Matcher matcher = URL_PATTERN.matcher(url);
        if (matcher.matches()) {
            return matcher.group(1);
        }
        throw new IllegalArgumentException("Invalid conversation URL format. Expected: .../chat/<conversation-id>");
    }

    private static CCAPICredential findCredentialById(CCAPIsCredentials credentials, String id) {
        return credentials.credentials().stream()
                .filter(c -> c.id().equalsIgnoreCase(id))
                .findFirst()
                .orElse(null);
    }

    private static ConversationDetail fetchDetailedConversation(CCAPIClient client, CCAPICredential credential,
                                                                String conversationId) throws IOException {
        var mapper = new ObjectMapper();
        mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        mapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        // Fetch using raw HTTP to get complete conversation with messages
        var baseUrl = ensureHttpsPrefix(credential.resolvedCcapiBaseUrl());
        var url = String.format("%s/api/organizations/%s/chat_conversations/%s",
                baseUrl, credential.orgId(), conversationId);

        var httpClient = new OkHttpClient();
        var request = new Request.Builder()
                .url(url)
                .addHeader("Cookie", "sessionKey=" + credential.key())
                .addHeader("User-Agent", credential.ua() != null ? credential.ua() :
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .addHeader("Accept", "*/*")
                .addHeader("Origin", baseUrl)
                .addHeader("Referer", baseUrl + "/")
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to fetch conversation: HTTP " + response.code());
            }

            var responseBody = response.body();
            if (responseBody == null) {
                throw new IOException("Empty response body");
            }

            return mapper.readValue(responseBody.string(), ConversationDetail.class);
        }
    }

    private static String ensureHttpsPrefix(String url) {
        if (url == null || url.isEmpty()) {
            throw new IllegalArgumentException("Base URL cannot be null or empty");
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return "https://" + url;
        }
        return url;
    }

    private static void saveConversationAsJson(ConversationDetail conversation, Path outputPath) throws IOException {
        // Ensure output directory exists
        var parentDir = outputPath.getParent();
        if (parentDir != null && !Files.exists(parentDir)) {
            Files.createDirectories(parentDir);
        }

        // Write pretty-printed JSON
        var mapper = new ObjectMapper();
        mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        mapper.writeValue(outputPath.toFile(), conversation);
    }

    private static void saveConversationAsXHtml(ConversationDetail conversation, Path outputPath,
                                                 CCAPICredential credential, String conversationId) throws IOException {
        // Ensure output directory exists
        var parentDir = outputPath.getParent();
        if (parentDir != null && !Files.exists(parentDir)) {
            Files.createDirectories(parentDir);
        }

        // Build metadata section
        var metadataSection = metadata(
            h2(text("Conversation Metadata")),
            div(text("UUID: " + conversation.getUuid())),
            div(text("Name: " + conversation.getName())),
            if_(conversation.getSummary() != null && !conversation.getSummary().isEmpty(), () ->
                div(text("Summary: " + conversation.getSummary()))),
            div(text("Created: " + conversation.getCreatedAt())),
            div(text("Updated: " + conversation.getUpdatedAt())),
            div(text("Organization ID: " + credential.orgId())),
            div(text("Account: " + credential.id() + " (" + credential.name() + ")")),
            div(text("View online: "),
                a(href("https://claude.ai/chat/" + conversationId), text("https://claude.ai/chat/" + conversationId)))
        );

        // Build messages
        var messageFrags = frags();
        if (conversation.getChatMessages() != null) {
            for (int i = 0; i < conversation.getChatMessages().size(); i++) {
                var message = conversation.getChatMessages().get(i);
                var role = message.getSender() != null ? message.getSender() : "unknown";
                var serialNum = i + 1;

                // Create custom element for message (human or assistant)
                if ("human".equals(role)) {
                    messageFrags.____(
                        human(
                            serialNum(serialNum),
                            if_(message.getUuid() != null, () -> uuid(message.getUuid())),
                            if_(message.getCreatedAt() != null, () -> created_at(message.getCreatedAt())),
                            // Raw markdown text - preserved as-is
                            text(message.getText() != null ? message.getText() : "")
                        )
                    );
                } else if ("assistant".equals(role)) {
                    messageFrags.____(
                        assistant(
                            serialNum(serialNum),
                            if_(message.getUuid() != null, () -> uuid(message.getUuid())),
                            if_(message.getCreatedAt() != null, () -> created_at(message.getCreatedAt())),
                            // Raw markdown text - preserved as-is
                            text(message.getText() != null ? message.getText() : "")
                        )
                    );
                }
            }
        }

        var messagesSection = messages(messageFrags);

        // Build complete XHTML document
        var xhtmlDoc = frags(
            xmlDeclaration("UTF-8"),
            DocType.html5(),
            html(xmlns("http://www.w3.org/1999/xhtml"),
                head(
                    meta(charset("UTF-8")),
                    meta(name("viewport"), content("width=device-width, initial-scale=1.0")),
                    meta(name("description"), content(ConversationSemanticXHtml.DEFINITION)),
                    meta(name("trivia"), content(ConversationSemanticXHtml.TRIVIA)),
                    meta(name("date"), content(LocalDateTime.now().atZone(ZoneId.systemDefault())
                        .withZoneSameInstant(ZoneOffset.UTC).toString())),
                    title(conversation.getName() + " (" + ConversationSemanticXHtml.TYPE + ")"),
                    link(rel("stylesheet"), href(ConversationSemanticXHtml.CSS)),
                    script(src(ConversationSemanticXHtml.JS))
                ),
                body(
                    // Define custom elements as block-level
                    style("human, assistant, metadata, messages { display: block; }"),
                    h1(text(conversation.getName())),
                    metadataSection,
                    messagesSection
                )
            )
        );

        // Render and save
        var xhtmlContent = XHtmlStringRenderer.asFormatted(xhtmlDoc, "");
        Files.writeString(outputPath, xhtmlContent, StandardCharsets.UTF_8);
    }

    private static class DownloadOptions {
        String url;
        String accountId;
        String customName;
        String outputDir;
    }
}

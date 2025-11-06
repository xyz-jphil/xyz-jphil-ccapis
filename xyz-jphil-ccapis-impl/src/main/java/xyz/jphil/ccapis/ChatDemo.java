package xyz.jphil.ccapis;

import xyz.jphil.ccapis.config.CredentialsManager;
import xyz.jphil.ccapis.model.CCAPICredential;
import xyz.jphil.ccapis.model.SingleConversation;
import xyz.jphil.ccapis.model.UsageData;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;

/**
 * Simple demo showing how to use the CCAPI client
 *
 * Usage: java ChatDemo [<credential-id>] [<message>]
 *
 * Examples:
 *   java ChatDemo
 *   java ChatDemo "Hello, how are you?"
 *   java ChatDemo LGL "Hello, how are you?"
 */
public class ChatDemo {

    public static void main(String[] args) {
        String credentialId = null;
        String userMessage = "Hello! Please respond with a brief greeting.";

        // Parse arguments
        if (args.length >= 1) {
            if (args.length == 1) {
                userMessage = args[0];
            } else {
                credentialId = args[0];
                userMessage = args[1];
            }
        }

        try {
            System.out.println("=== CCAPI Chat Demo ===\n");
            System.out.println("Loading credentials from shared CCAPIsCredentials.xml");

            // Load credentials
            var credentialsManager = new CredentialsManager();
            var credentials = credentialsManager.load();

            // Get credential
            CCAPICredential credential;
            if (credentialId != null) {
                credential = credentials.getById(credentialId);
                if (credential == null) {
                    System.err.println("Credential not found: " + credentialId);
                    System.exit(1);
                    return;
                }
            } else {
                credential = credentials.credentials().stream()
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("No credentials found"));
            }

            System.out.println("Using credential: " + credential.id() + " (" + credential.name() + ")");
            System.out.println("Base URL: " + credential.resolvedCcapiBaseUrl());

            // Create client (now stateless)
            var client = new CCAPIClient();
            System.out.println("Client initialized successfully\n");

            // Check usage
            System.out.println("--- Checking Usage ---");
            var usage = client.fetchUsage(credential);
            printUsage(usage);

            // Create a new chat
            System.out.println("\n--- Creating Chat Conversation ---");
            var chat = client.createChat(credential, "API Test Chat", false);
            System.out.println("Chat created successfully!");
            System.out.println("  UUID: " + chat.uuid());
            System.out.println("  Name: " + chat.name());

            // Count tokens before sending
            System.out.println("\n--- Token Counting ---");
            try {
                var tokenResult = client.countTokens(userMessage);
                System.out.println("Message tokens: " + tokenResult.totalTokens());
                System.out.println("  Content tokens: " + tokenResult.contentTokens());
                System.out.println("  Estimated overhead: " + tokenResult.estimatedPayloadOverhead());
                System.out.println("  Tokens per word: " + String.format("%.2f", tokenResult.totalTokensPerWord()));
            } catch (Exception e) {
                System.out.println("  (Token counting unavailable: " + e.getMessage() + ")");
            }

            // Send a message
            System.out.println("\n--- Sending Message ---");
            System.out.println("User: " + userMessage);

            var response = client.sendMessage(credential, chat.uuid(), userMessage);

            System.out.println("\n--- Response ---");
            parseAndPrintEventStream(response);

            // List conversations
            System.out.println("\n--- Listing Conversations (first 5) ---");
            var conversations = client.listConversations(credential);
            var count = Math.min(5, conversations.size());
            for (int i = 0; i < count; i++) {
                var conv = conversations.get(i);
                System.out.printf("%d. %s (ID: %s)%n",
                    i + 1,
                    conv.name() != null ? conv.name() : "Untitled",
                    conv.uuid());
            }
            System.out.println("Total conversations: " + conversations.size());

            // Cleanup
            client.shutdown();
            System.out.println("\n=== Demo Complete ===");

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void printUsage(UsageData usage) {
        if (usage.fiveHour() != null) {
            System.out.println("  5-Hour Window:");
            System.out.println("    Utilization: " + usage.fiveHour().utilization() + "%");
            System.out.println("    Resets at: " + usage.fiveHour().resetsAt());
        }

        if (usage.sevenDay() != null) {
            System.out.println("  7-Day Window:");
            System.out.println("    Utilization: " + usage.sevenDay().utilization() + "%");
            System.out.println("    Resets at: " + usage.sevenDay().resetsAt());
        }

        if (usage.sevenDayOpus() != null) {
            System.out.println("  7-Day Opus Window:");
            System.out.println("    Utilization: " + usage.sevenDayOpus().utilization() + "%");
            System.out.println("    Resets at: " + usage.sevenDayOpus().resetsAt());
        }
    }

    /**
     * Parse Server-Sent Events (SSE) stream and extract text content
     */
    private static void parseAndPrintEventStream(String eventStream) throws IOException {
        var reader = new BufferedReader(new StringReader(eventStream));
        var fullText = new StringBuilder();
        String line;
        boolean isStreaming = false;

        while ((line = reader.readLine()) != null) {
            // SSE format: "data: {json}"
            if (line.startsWith("data: ")) {
                var data = line.substring(6); // Remove "data: " prefix

                // Skip ping events
                if (data.equals("ping") || data.trim().isEmpty()) {
                    continue;
                }

                // Handle completion event
                if (data.contains("\"completion\":")) {
                    // Simple extraction of completion text (not full JSON parsing)
                    var startIdx = data.indexOf("\"completion\":\"") + 14;
                    var endIdx = data.indexOf("\"", startIdx);
                    if (startIdx > 13 && endIdx > startIdx) {
                        var text = data.substring(startIdx, endIdx);
                        // Unescape basic characters
                        text = text.replace("\\n", "\n")
                                   .replace("\\\"", "\"")
                                   .replace("\\\\", "\\");
                        fullText.append(text);
                        isStreaming = true;
                    }
                }

                // Handle done event
                if (data.contains("\"stop_reason\":")) {
                    break;
                }
            }
        }

        if (isStreaming && fullText.length() > 0) {
            System.out.println("Assistant: " + fullText.toString());
        } else {
            System.out.println("(Received response but could not parse text content)");
            System.out.println("Raw response length: " + eventStream.length() + " bytes");
        }
    }
}

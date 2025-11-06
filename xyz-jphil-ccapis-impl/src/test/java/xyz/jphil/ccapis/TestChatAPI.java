package xyz.jphil.ccapis;

import xyz.jphil.ccapis.CCAPIClient;
import xyz.jphil.ccapis.model.ChatCreationResponse;

/**
 * Test class to verify the chat API functionality
 */
public class TestChatAPI {
    
    public static void main(String[] args) {
        System.out.println("Testing Chat API functionality...");
        
        CCAPIClient client = new CCAPIClient();
        
        // Test creating a persistent chat
        System.out.println("\n1. Creating a persistent chat...");
        ChatCreationResponse chat = client.createChat("Test Chat from API", false);
        if (chat != null) {
            System.out.println("Successfully created chat with UUID: " + chat.uuid());
            
            // Test getting the conversation details
            System.out.println("\n2. Getting conversation details...");
            var conversation = client.getConversation(chat.uuid());
            if (conversation != null) {
                System.out.println("Retrieved conversation: " + conversation.name());
            } else {
                System.out.println("Failed to retrieve conversation");
            }
            
            // Test sending a message (commented out to avoid actual API calls during testing)
            // System.out.println("\n3. Sending a message...");
            // String response = client.sendMessage(chat.uuid(), "Hello Claude, this is a test message from the API client.");
            // if (response != null) {
            //     System.out.println("Message sent successfully");
            // } else {
            //     System.out.println("Failed to send message");
            // }
        } else {
            System.out.println("Failed to create chat");
        }
        
        System.out.println("\nChat API functionality test completed.");
    }
}
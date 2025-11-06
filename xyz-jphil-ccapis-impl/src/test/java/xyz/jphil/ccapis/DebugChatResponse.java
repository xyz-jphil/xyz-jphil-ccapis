package xyz.jphil.ccapis;

import com.fasterxml.jackson.databind.ObjectMapper;
import xyz.jphil.ccapis.model.ChatCreationResponse;

import java.time.OffsetDateTime;

/**
 * Simple test to debug the ChatCreationResponse POJO
 */
public class DebugChatResponse {
    
    public static void main(String[] args) throws Exception {
        String jsonResponse = "{\"uuid\":\"a1a88cee-14b5-4c85-8439-4d6a441be122\",\"name\":\"API Test Conversation - 2025-10-19T12-54-18-996082100Z\",\"summary\":\"\",\"model\":null,\"created_at\":\"2025-10-19T12:54:19.701865Z\",\"updated_at\":\"2025-10-19T12:54:19.701865Z\",\"settings\":{\"enabled_bananagrams\":null,\"enabled_web_search\":null,\"enabled_compass\":null,\"enabled_sourdough\":null,\"enabled_foccacia\":null,\"enabled_mcp_tools\":null,\"compass_mode\":null,\"paprika_mode\":null,\"enabled_monkeys_in_a_barrel\":false,\"enabled_saffron\":null,\"create_mode\":null,\"preview_feature_uses_artifacts\":true,\"preview_feature_uses_latex\":null,\"preview_feature_uses_citations\":null,\"enabled_drive_search\":null,\"enabled_artifacts_attachments\":null,\"enabled_turmeric\":null},\"is_starred\":false,\"is_temporary\":false,\"project_uuid\":null,\"session_id\":null,\"current_leaf_message_uuid\":null,\"user_uuid\":null}";
        
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        
        ChatCreationResponse response = objectMapper.readValue(jsonResponse, ChatCreationResponse.class);
        
        System.out.println("UUID: " + response.uuid());
        System.out.println("Name: " + response.name());
        System.out.println("Created At: " + response.createdAt());
        
        if (response.uuid() == null) {
            System.out.println("ERROR: UUID is null!");
        } else {
            System.out.println("SUCCESS: UUID is properly parsed!");
        }
    }
}
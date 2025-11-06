package xyz.jphil.ccapis.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * POJO for CCAPI message sending response
 * Ignores unknown properties to handle API changes gracefully
 */
@Data
@Accessors(fluent = true, chain = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class MessageResponse {
    @JsonProperty("uuid")
    String uuid;
    @JsonProperty("text")
    String text;
    @JsonProperty("model_version")
    String modelVersion;
    @JsonProperty("created_at")
    String createdAt;
    @JsonProperty("updated_at")
    String updatedAt;
    @JsonProperty("text")
    String type; // 'human' or 'assistant'
    
    @JsonProperty("chat_conversation_uuid")
    String chatConversationUuid;
    
    // Additional fields that might be in the response
    @JsonProperty("completion")
    Object completion;
    @JsonProperty("metadata")
    Object metadata;
}
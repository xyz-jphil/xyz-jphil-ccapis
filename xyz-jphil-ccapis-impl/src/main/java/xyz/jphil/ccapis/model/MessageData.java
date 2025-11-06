package xyz.jphil.ccapis.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.time.OffsetDateTime;
import lombok.experimental.Accessors;

/**
 * POJO for CCAPI chat message data
 * Ignores unknown properties to handle API changes gracefully
 */
@Data
@Accessors(fluent = true, chain = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class MessageData {
    @JsonProperty("uuid")
    String uuid;
    @JsonProperty("text")
    String text;
    @JsonProperty("model_version")
    String modelVersion;
    @JsonProperty("created_at")
    OffsetDateTime createdAt;
    @JsonProperty("updated_at")
    OffsetDateTime updatedAt;
    @JsonProperty("type")
    String type; // 'human' or 'assistant'
    
    @JsonProperty("chat_conversation_uuid")
    String chatConversationUuid;
}
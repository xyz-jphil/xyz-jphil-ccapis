package xyz.jphil.ccapis.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.OffsetDateTime;

/**
 * POJO for CCAPI conversation data (individual conversation)
 * Ignores unknown properties to handle API changes gracefully
 */
@Data
@Accessors(fluent = true, chain = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class Conversation {
    @JsonProperty("uuid")
    String uuid;
    @JsonProperty("name")
    String name;
    @JsonProperty("summary")
    String summary;
    @JsonProperty("model")
    String model;
    @JsonProperty("created_at")
    OffsetDateTime createdAt;
    @JsonProperty("updated_at")
    OffsetDateTime updatedAt;
    ConversationSettings settings;
    @JsonProperty("is_starred")
    boolean isStarred;
    @JsonProperty("is_temporary")
    boolean isTemporary;
    @JsonProperty("project_uuid")
    String projectUuid;
    @JsonProperty("session_id")
    String sessionId;
    @JsonProperty("current_leaf_message_uuid")
    String currentLeafMessageUuid;
    @JsonProperty("user_uuid")
    String userUuid;
    @JsonProperty("project")
    Object project; // Could be null or an object
}
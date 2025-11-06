package xyz.jphil.ccapis.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;
import lombok.experimental.Accessors;

/**
 * POJO for a single conversation response
 * Ignores unknown properties to handle API changes gracefully
 */
@Data
@Accessors(fluent = true, chain = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class SingleConversation {
    @JsonProperty("uuid")
    String uuid;
    @JsonProperty("name")
    String name;
    @JsonProperty("is_temporary")
    boolean isTemporary;
    @JsonProperty("created_at")
    OffsetDateTime createdAt;
    @JsonProperty("updated_at")
    OffsetDateTime updatedAt;
    @JsonProperty("messages")
    List<MessageData> messages;
}
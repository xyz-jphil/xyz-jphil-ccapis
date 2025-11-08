
package xyz.jphil.ccapis.conversation_downloader.model;

import java.util.ArrayList;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "uuid",
    "name",
    "summary",
    "created_at",
    "updated_at",
    "settings",
    "is_starred",
    "is_temporary",
    "current_leaf_message_uuid",
    "chat_messages"
})
public class ConversationDetail {

    @JsonProperty("uuid")
    private String uuid;
    @JsonProperty("name")
    private String name;
    @JsonProperty("summary")
    private String summary;
    @JsonProperty("created_at")
    private String createdAt;
    @JsonProperty("updated_at")
    private String updatedAt;
    @JsonProperty("settings")
    private Settings settings;
    @JsonProperty("is_starred")
    private Boolean isStarred;
    @JsonProperty("is_temporary")
    private Boolean isTemporary;
    @JsonProperty("current_leaf_message_uuid")
    private String currentLeafMessageUuid;
    @JsonProperty("chat_messages")
    private List<ChatMessage> chatMessages = new ArrayList<ChatMessage>();

    @JsonProperty("uuid")
    public String getUuid() {
        return uuid;
    }

    @JsonProperty("uuid")
    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    @JsonProperty("name")
    public String getName() {
        return name;
    }

    @JsonProperty("name")
    public void setName(String name) {
        this.name = name;
    }

    @JsonProperty("summary")
    public String getSummary() {
        return summary;
    }

    @JsonProperty("summary")
    public void setSummary(String summary) {
        this.summary = summary;
    }

    @JsonProperty("created_at")
    public String getCreatedAt() {
        return createdAt;
    }

    @JsonProperty("created_at")
    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    @JsonProperty("updated_at")
    public String getUpdatedAt() {
        return updatedAt;
    }

    @JsonProperty("updated_at")
    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }

    @JsonProperty("settings")
    public Settings getSettings() {
        return settings;
    }

    @JsonProperty("settings")
    public void setSettings(Settings settings) {
        this.settings = settings;
    }

    @JsonProperty("is_starred")
    public Boolean getIsStarred() {
        return isStarred;
    }

    @JsonProperty("is_starred")
    public void setIsStarred(Boolean isStarred) {
        this.isStarred = isStarred;
    }

    @JsonProperty("is_temporary")
    public Boolean getIsTemporary() {
        return isTemporary;
    }

    @JsonProperty("is_temporary")
    public void setIsTemporary(Boolean isTemporary) {
        this.isTemporary = isTemporary;
    }

    @JsonProperty("current_leaf_message_uuid")
    public String getCurrentLeafMessageUuid() {
        return currentLeafMessageUuid;
    }

    @JsonProperty("current_leaf_message_uuid")
    public void setCurrentLeafMessageUuid(String currentLeafMessageUuid) {
        this.currentLeafMessageUuid = currentLeafMessageUuid;
    }

    @JsonProperty("chat_messages")
    public List<ChatMessage> getChatMessages() {
        return chatMessages;
    }

    @JsonProperty("chat_messages")
    public void setChatMessages(List<ChatMessage> chatMessages) {
        this.chatMessages = chatMessages;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(ConversationDetail.class.getName()).append('@').append(Integer.toHexString(System.identityHashCode(this))).append('[');
        sb.append("uuid");
        sb.append('=');
        sb.append(((this.uuid == null)?"<null>":this.uuid));
        sb.append(',');
        sb.append("name");
        sb.append('=');
        sb.append(((this.name == null)?"<null>":this.name));
        sb.append(',');
        sb.append("summary");
        sb.append('=');
        sb.append(((this.summary == null)?"<null>":this.summary));
        sb.append(',');
        sb.append("createdAt");
        sb.append('=');
        sb.append(((this.createdAt == null)?"<null>":this.createdAt));
        sb.append(',');
        sb.append("updatedAt");
        sb.append('=');
        sb.append(((this.updatedAt == null)?"<null>":this.updatedAt));
        sb.append(',');
        sb.append("settings");
        sb.append('=');
        sb.append(((this.settings == null)?"<null>":this.settings));
        sb.append(',');
        sb.append("isStarred");
        sb.append('=');
        sb.append(((this.isStarred == null)?"<null>":this.isStarred));
        sb.append(',');
        sb.append("isTemporary");
        sb.append('=');
        sb.append(((this.isTemporary == null)?"<null>":this.isTemporary));
        sb.append(',');
        sb.append("currentLeafMessageUuid");
        sb.append('=');
        sb.append(((this.currentLeafMessageUuid == null)?"<null>":this.currentLeafMessageUuid));
        sb.append(',');
        sb.append("chatMessages");
        sb.append('=');
        sb.append(((this.chatMessages == null)?"<null>":this.chatMessages));
        sb.append(',');
        if (sb.charAt((sb.length()- 1)) == ',') {
            sb.setCharAt((sb.length()- 1), ']');
        } else {
            sb.append(']');
        }
        return sb.toString();
    }

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.summary == null)? 0 :this.summary.hashCode()));
        result = ((result* 31)+((this.createdAt == null)? 0 :this.createdAt.hashCode()));
        result = ((result* 31)+((this.settings == null)? 0 :this.settings.hashCode()));
        result = ((result* 31)+((this.chatMessages == null)? 0 :this.chatMessages.hashCode()));
        result = ((result* 31)+((this.name == null)? 0 :this.name.hashCode()));
        result = ((result* 31)+((this.isStarred == null)? 0 :this.isStarred.hashCode()));
        result = ((result* 31)+((this.isTemporary == null)? 0 :this.isTemporary.hashCode()));
        result = ((result* 31)+((this.uuid == null)? 0 :this.uuid.hashCode()));
        result = ((result* 31)+((this.currentLeafMessageUuid == null)? 0 :this.currentLeafMessageUuid.hashCode()));
        result = ((result* 31)+((this.updatedAt == null)? 0 :this.updatedAt.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof ConversationDetail) == false) {
            return false;
        }
        ConversationDetail rhs = ((ConversationDetail) other);
        return (((((((((((this.summary == rhs.summary)||((this.summary!= null)&&this.summary.equals(rhs.summary)))&&((this.createdAt == rhs.createdAt)||((this.createdAt!= null)&&this.createdAt.equals(rhs.createdAt))))&&((this.settings == rhs.settings)||((this.settings!= null)&&this.settings.equals(rhs.settings))))&&((this.chatMessages == rhs.chatMessages)||((this.chatMessages!= null)&&this.chatMessages.equals(rhs.chatMessages))))&&((this.name == rhs.name)||((this.name!= null)&&this.name.equals(rhs.name))))&&((this.isStarred == rhs.isStarred)||((this.isStarred!= null)&&this.isStarred.equals(rhs.isStarred))))&&((this.isTemporary == rhs.isTemporary)||((this.isTemporary!= null)&&this.isTemporary.equals(rhs.isTemporary))))&&((this.uuid == rhs.uuid)||((this.uuid!= null)&&this.uuid.equals(rhs.uuid))))&&((this.currentLeafMessageUuid == rhs.currentLeafMessageUuid)||((this.currentLeafMessageUuid!= null)&&this.currentLeafMessageUuid.equals(rhs.currentLeafMessageUuid))))&&((this.updatedAt == rhs.updatedAt)||((this.updatedAt!= null)&&this.updatedAt.equals(rhs.updatedAt))));
    }

}

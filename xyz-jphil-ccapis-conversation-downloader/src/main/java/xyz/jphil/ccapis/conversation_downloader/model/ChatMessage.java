
package xyz.jphil.ccapis.conversation_downloader.model;

import java.util.ArrayList;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "uuid",
    "text",
    "sender",
    "index",
    "created_at",
    "updated_at",
    "truncated",
    "attachments",
    "files",
    "files_v2",
    "sync_sources",
    "parent_message_uuid",
    "stop_reason"
})
public class ChatMessage {

    @JsonProperty("uuid")
    private String uuid;
    @JsonProperty("text")
    private String text;
    @JsonProperty("sender")
    private String sender;
    @JsonProperty("index")
    private Integer index;
    @JsonProperty("created_at")
    private String createdAt;
    @JsonProperty("updated_at")
    private String updatedAt;
    @JsonProperty("truncated")
    private Boolean truncated;
    @JsonProperty("attachments")
    private List<Attachment> attachments = new ArrayList<Attachment>();
    @JsonProperty("files")
    private List<Object> files = new ArrayList<Object>();
    @JsonProperty("files_v2")
    private List<Object> filesV2 = new ArrayList<Object>();
    @JsonProperty("sync_sources")
    private List<Object> syncSources = new ArrayList<Object>();
    @JsonProperty("parent_message_uuid")
    private String parentMessageUuid;
    @JsonProperty("stop_reason")
    private String stopReason;

    @JsonProperty("uuid")
    public String getUuid() {
        return uuid;
    }

    @JsonProperty("uuid")
    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    @JsonProperty("text")
    public String getText() {
        return text;
    }

    @JsonProperty("text")
    public void setText(String text) {
        this.text = text;
    }

    @JsonProperty("sender")
    public String getSender() {
        return sender;
    }

    @JsonProperty("sender")
    public void setSender(String sender) {
        this.sender = sender;
    }

    @JsonProperty("index")
    public Integer getIndex() {
        return index;
    }

    @JsonProperty("index")
    public void setIndex(Integer index) {
        this.index = index;
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

    @JsonProperty("truncated")
    public Boolean getTruncated() {
        return truncated;
    }

    @JsonProperty("truncated")
    public void setTruncated(Boolean truncated) {
        this.truncated = truncated;
    }

    @JsonProperty("attachments")
    public List<Attachment> getAttachments() {
        return attachments;
    }

    @JsonProperty("attachments")
    public void setAttachments(List<Attachment> attachments) {
        this.attachments = attachments;
    }

    @JsonProperty("files")
    public List<Object> getFiles() {
        return files;
    }

    @JsonProperty("files")
    public void setFiles(List<Object> files) {
        this.files = files;
    }

    @JsonProperty("files_v2")
    public List<Object> getFilesV2() {
        return filesV2;
    }

    @JsonProperty("files_v2")
    public void setFilesV2(List<Object> filesV2) {
        this.filesV2 = filesV2;
    }

    @JsonProperty("sync_sources")
    public List<Object> getSyncSources() {
        return syncSources;
    }

    @JsonProperty("sync_sources")
    public void setSyncSources(List<Object> syncSources) {
        this.syncSources = syncSources;
    }

    @JsonProperty("parent_message_uuid")
    public String getParentMessageUuid() {
        return parentMessageUuid;
    }

    @JsonProperty("parent_message_uuid")
    public void setParentMessageUuid(String parentMessageUuid) {
        this.parentMessageUuid = parentMessageUuid;
    }

    @JsonProperty("stop_reason")
    public String getStopReason() {
        return stopReason;
    }

    @JsonProperty("stop_reason")
    public void setStopReason(String stopReason) {
        this.stopReason = stopReason;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(ChatMessage.class.getName()).append('@').append(Integer.toHexString(System.identityHashCode(this))).append('[');
        sb.append("uuid");
        sb.append('=');
        sb.append(((this.uuid == null)?"<null>":this.uuid));
        sb.append(',');
        sb.append("text");
        sb.append('=');
        sb.append(((this.text == null)?"<null>":this.text));
        sb.append(',');
        sb.append("sender");
        sb.append('=');
        sb.append(((this.sender == null)?"<null>":this.sender));
        sb.append(',');
        sb.append("index");
        sb.append('=');
        sb.append(((this.index == null)?"<null>":this.index));
        sb.append(',');
        sb.append("createdAt");
        sb.append('=');
        sb.append(((this.createdAt == null)?"<null>":this.createdAt));
        sb.append(',');
        sb.append("updatedAt");
        sb.append('=');
        sb.append(((this.updatedAt == null)?"<null>":this.updatedAt));
        sb.append(',');
        sb.append("truncated");
        sb.append('=');
        sb.append(((this.truncated == null)?"<null>":this.truncated));
        sb.append(',');
        sb.append("attachments");
        sb.append('=');
        sb.append(((this.attachments == null)?"<null>":this.attachments));
        sb.append(',');
        sb.append("files");
        sb.append('=');
        sb.append(((this.files == null)?"<null>":this.files));
        sb.append(',');
        sb.append("filesV2");
        sb.append('=');
        sb.append(((this.filesV2 == null)?"<null>":this.filesV2));
        sb.append(',');
        sb.append("syncSources");
        sb.append('=');
        sb.append(((this.syncSources == null)?"<null>":this.syncSources));
        sb.append(',');
        sb.append("parentMessageUuid");
        sb.append('=');
        sb.append(((this.parentMessageUuid == null)?"<null>":this.parentMessageUuid));
        sb.append(',');
        sb.append("stopReason");
        sb.append('=');
        sb.append(((this.stopReason == null)?"<null>":this.stopReason));
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
        result = ((result* 31)+((this.stopReason == null)? 0 :this.stopReason.hashCode()));
        result = ((result* 31)+((this.attachments == null)? 0 :this.attachments.hashCode()));
        result = ((result* 31)+((this.index == null)? 0 :this.index.hashCode()));
        result = ((result* 31)+((this.truncated == null)? 0 :this.truncated.hashCode()));
        result = ((result* 31)+((this.uuid == null)? 0 :this.uuid.hashCode()));
        result = ((result* 31)+((this.parentMessageUuid == null)? 0 :this.parentMessageUuid.hashCode()));
        result = ((result* 31)+((this.createdAt == null)? 0 :this.createdAt.hashCode()));
        result = ((result* 31)+((this.sender == null)? 0 :this.sender.hashCode()));
        result = ((result* 31)+((this.files == null)? 0 :this.files.hashCode()));
        result = ((result* 31)+((this.filesV2 == null)? 0 :this.filesV2 .hashCode()));
        result = ((result* 31)+((this.syncSources == null)? 0 :this.syncSources.hashCode()));
        result = ((result* 31)+((this.text == null)? 0 :this.text.hashCode()));
        result = ((result* 31)+((this.updatedAt == null)? 0 :this.updatedAt.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof ChatMessage) == false) {
            return false;
        }
        ChatMessage rhs = ((ChatMessage) other);
        return ((((((((((((((this.stopReason == rhs.stopReason)||((this.stopReason!= null)&&this.stopReason.equals(rhs.stopReason)))&&((this.attachments == rhs.attachments)||((this.attachments!= null)&&this.attachments.equals(rhs.attachments))))&&((this.index == rhs.index)||((this.index!= null)&&this.index.equals(rhs.index))))&&((this.truncated == rhs.truncated)||((this.truncated!= null)&&this.truncated.equals(rhs.truncated))))&&((this.uuid == rhs.uuid)||((this.uuid!= null)&&this.uuid.equals(rhs.uuid))))&&((this.parentMessageUuid == rhs.parentMessageUuid)||((this.parentMessageUuid!= null)&&this.parentMessageUuid.equals(rhs.parentMessageUuid))))&&((this.createdAt == rhs.createdAt)||((this.createdAt!= null)&&this.createdAt.equals(rhs.createdAt))))&&((this.sender == rhs.sender)||((this.sender!= null)&&this.sender.equals(rhs.sender))))&&((this.files == rhs.files)||((this.files!= null)&&this.files.equals(rhs.files))))&&((this.filesV2 == rhs.filesV2)||((this.filesV2 != null)&&this.filesV2 .equals(rhs.filesV2))))&&((this.syncSources == rhs.syncSources)||((this.syncSources!= null)&&this.syncSources.equals(rhs.syncSources))))&&((this.text == rhs.text)||((this.text!= null)&&this.text.equals(rhs.text))))&&((this.updatedAt == rhs.updatedAt)||((this.updatedAt!= null)&&this.updatedAt.equals(rhs.updatedAt))));
    }

}

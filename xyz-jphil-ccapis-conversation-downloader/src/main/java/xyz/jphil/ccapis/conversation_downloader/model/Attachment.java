
package xyz.jphil.ccapis.conversation_downloader.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "id",
    "file_name",
    "file_size",
    "file_type",
    "extracted_content",
    "created_at"
})
public class Attachment {

    @JsonProperty("id")
    private String id;
    @JsonProperty("file_name")
    private String fileName;
    @JsonProperty("file_size")
    private Integer fileSize;
    @JsonProperty("file_type")
    private String fileType;
    @JsonProperty("extracted_content")
    private String extractedContent;
    @JsonProperty("created_at")
    private String createdAt;

    @JsonProperty("id")
    public String getId() {
        return id;
    }

    @JsonProperty("id")
    public void setId(String id) {
        this.id = id;
    }

    @JsonProperty("file_name")
    public String getFileName() {
        return fileName;
    }

    @JsonProperty("file_name")
    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    @JsonProperty("file_size")
    public Integer getFileSize() {
        return fileSize;
    }

    @JsonProperty("file_size")
    public void setFileSize(Integer fileSize) {
        this.fileSize = fileSize;
    }

    @JsonProperty("file_type")
    public String getFileType() {
        return fileType;
    }

    @JsonProperty("file_type")
    public void setFileType(String fileType) {
        this.fileType = fileType;
    }

    @JsonProperty("extracted_content")
    public String getExtractedContent() {
        return extractedContent;
    }

    @JsonProperty("extracted_content")
    public void setExtractedContent(String extractedContent) {
        this.extractedContent = extractedContent;
    }

    @JsonProperty("created_at")
    public String getCreatedAt() {
        return createdAt;
    }

    @JsonProperty("created_at")
    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(Attachment.class.getName()).append('@').append(Integer.toHexString(System.identityHashCode(this))).append('[');
        sb.append("id");
        sb.append('=');
        sb.append(((this.id == null)?"<null>":this.id));
        sb.append(',');
        sb.append("fileName");
        sb.append('=');
        sb.append(((this.fileName == null)?"<null>":this.fileName));
        sb.append(',');
        sb.append("fileSize");
        sb.append('=');
        sb.append(((this.fileSize == null)?"<null>":this.fileSize));
        sb.append(',');
        sb.append("fileType");
        sb.append('=');
        sb.append(((this.fileType == null)?"<null>":this.fileType));
        sb.append(',');
        sb.append("extractedContent");
        sb.append('=');
        sb.append(((this.extractedContent == null)?"<null>":this.extractedContent));
        sb.append(',');
        sb.append("createdAt");
        sb.append('=');
        sb.append(((this.createdAt == null)?"<null>":this.createdAt));
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
        result = ((result* 31)+((this.createdAt == null)? 0 :this.createdAt.hashCode()));
        result = ((result* 31)+((this.fileName == null)? 0 :this.fileName.hashCode()));
        result = ((result* 31)+((this.extractedContent == null)? 0 :this.extractedContent.hashCode()));
        result = ((result* 31)+((this.id == null)? 0 :this.id.hashCode()));
        result = ((result* 31)+((this.fileSize == null)? 0 :this.fileSize.hashCode()));
        result = ((result* 31)+((this.fileType == null)? 0 :this.fileType.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof Attachment) == false) {
            return false;
        }
        Attachment rhs = ((Attachment) other);
        return (((((((this.createdAt == rhs.createdAt)||((this.createdAt!= null)&&this.createdAt.equals(rhs.createdAt)))&&((this.fileName == rhs.fileName)||((this.fileName!= null)&&this.fileName.equals(rhs.fileName))))&&((this.extractedContent == rhs.extractedContent)||((this.extractedContent!= null)&&this.extractedContent.equals(rhs.extractedContent))))&&((this.id == rhs.id)||((this.id!= null)&&this.id.equals(rhs.id))))&&((this.fileSize == rhs.fileSize)||((this.fileSize!= null)&&this.fileSize.equals(rhs.fileSize))))&&((this.fileType == rhs.fileType)||((this.fileType!= null)&&this.fileType.equals(rhs.fileType))));
    }

}

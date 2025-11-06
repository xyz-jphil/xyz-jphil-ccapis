package xyz.jphil.ccapis.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * POJO for CCAPI attachment data
 * Ignores unknown properties to handle API changes gracefully
 */
@Data
@Accessors(fluent = true, chain = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class AttachmentData {
    
    @JsonProperty("file_name")
    String fileName;
    
    @JsonProperty("file_type")
    String fileType;
    
    @JsonProperty("file_size")
    Long fileSize;
    
    @JsonProperty("extracted_content")
    String extractedContent;
    
    @JsonProperty("id")
    String id; // Might be used as an identifier for the uploaded file
}
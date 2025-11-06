package xyz.jphil.ccapis.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * POJO for CCAPI completion response
 * Ignores unknown properties to handle API changes gracefully
 */
@Data
@Accessors(fluent = true, chain = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class CompletionResponse {
    @JsonProperty("completion")
    String completion;
    @JsonProperty("stop_reason")
    String stopReason;
    @JsonProperty("model")
    String model;
    @JsonProperty("stop")
    String stopSequence;
    
    // Metadata fields
    @JsonProperty("metdata")
    Object metadata;
}
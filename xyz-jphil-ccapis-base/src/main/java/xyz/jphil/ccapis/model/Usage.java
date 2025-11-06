package xyz.jphil.ccapis.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.OffsetDateTime;

/**
 * POJO for CCAPI usage data
 * Ignores unknown properties to handle API changes gracefully
 */
@Data
@Accessors(fluent = true, chain = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class Usage {
    @JsonProperty("utilization")
    int utilization;

    @JsonProperty("resets_at")
    OffsetDateTime resetsAt;
}
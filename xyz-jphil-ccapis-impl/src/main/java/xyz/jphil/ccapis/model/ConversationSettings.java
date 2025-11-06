package xyz.jphil.ccapis.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * POJO for conversation settings
 * Ignores unknown properties to handle API changes gracefully
 */
@Data
@Accessors(fluent = true, chain = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ConversationSettings {
    @JsonProperty("enabled_bananagrams")
    Object enabledBananagrams;
    @JsonProperty("enabled_web_search")
    Object enabledWebSearch;
    @JsonProperty("enabled_compass")
    Object enabledCompass;
    @JsonProperty("enabled_sourdough")
    Object enabledSourdough;
    @JsonProperty("enabled_foccacia")
    Object enabledFoccacia;
    @JsonProperty("enabled_mcp_tools")
    Object enabledMcpTools;
    @JsonProperty("compass_mode")
    Object compassMode;
    @JsonProperty("paprika_mode")
    Object paprikaMode;
    @JsonProperty("enabled_monkeys_in_a_barrel")
    boolean enabledMonkeysInABarrel;
    @JsonProperty("enabled_saffron")
    Object enabledSaffron;
    @JsonProperty("create_mode")
    Object createMode;
    @JsonProperty("preview_feature_uses_artifacts")
    boolean previewFeatureUsesArtifacts;
    @JsonProperty("preview_feature_uses_latex")
    Object previewFeatureUsesLatex;
    @JsonProperty("preview_feature_uses_citations")
    Object previewFeatureUsesCitations;
    @JsonProperty("enabled_drive_search")
    Object enabledDriveSearch;
    @JsonProperty("enabled_artifacts_attachments")
    Object enabledArtifactsAttachments;
    @JsonProperty("enabled_turmeric")
    Object enabledTurmeric;
}

package xyz.jphil.ccapis.conversation_downloader.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "enabled_web_search",
    "enabled_monkeys_in_a_barrel",
    "preview_feature_uses_artifacts",
    "enabled_turmeric"
})
public class Settings {

    @JsonProperty("enabled_web_search")
    private Boolean enabledWebSearch;
    @JsonProperty("enabled_monkeys_in_a_barrel")
    private Boolean enabledMonkeysInABarrel;
    @JsonProperty("preview_feature_uses_artifacts")
    private Boolean previewFeatureUsesArtifacts;
    @JsonProperty("enabled_turmeric")
    private Boolean enabledTurmeric;

    @JsonProperty("enabled_web_search")
    public Boolean getEnabledWebSearch() {
        return enabledWebSearch;
    }

    @JsonProperty("enabled_web_search")
    public void setEnabledWebSearch(Boolean enabledWebSearch) {
        this.enabledWebSearch = enabledWebSearch;
    }

    @JsonProperty("enabled_monkeys_in_a_barrel")
    public Boolean getEnabledMonkeysInABarrel() {
        return enabledMonkeysInABarrel;
    }

    @JsonProperty("enabled_monkeys_in_a_barrel")
    public void setEnabledMonkeysInABarrel(Boolean enabledMonkeysInABarrel) {
        this.enabledMonkeysInABarrel = enabledMonkeysInABarrel;
    }

    @JsonProperty("preview_feature_uses_artifacts")
    public Boolean getPreviewFeatureUsesArtifacts() {
        return previewFeatureUsesArtifacts;
    }

    @JsonProperty("preview_feature_uses_artifacts")
    public void setPreviewFeatureUsesArtifacts(Boolean previewFeatureUsesArtifacts) {
        this.previewFeatureUsesArtifacts = previewFeatureUsesArtifacts;
    }

    @JsonProperty("enabled_turmeric")
    public Boolean getEnabledTurmeric() {
        return enabledTurmeric;
    }

    @JsonProperty("enabled_turmeric")
    public void setEnabledTurmeric(Boolean enabledTurmeric) {
        this.enabledTurmeric = enabledTurmeric;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(Settings.class.getName()).append('@').append(Integer.toHexString(System.identityHashCode(this))).append('[');
        sb.append("enabledWebSearch");
        sb.append('=');
        sb.append(((this.enabledWebSearch == null)?"<null>":this.enabledWebSearch));
        sb.append(',');
        sb.append("enabledMonkeysInABarrel");
        sb.append('=');
        sb.append(((this.enabledMonkeysInABarrel == null)?"<null>":this.enabledMonkeysInABarrel));
        sb.append(',');
        sb.append("previewFeatureUsesArtifacts");
        sb.append('=');
        sb.append(((this.previewFeatureUsesArtifacts == null)?"<null>":this.previewFeatureUsesArtifacts));
        sb.append(',');
        sb.append("enabledTurmeric");
        sb.append('=');
        sb.append(((this.enabledTurmeric == null)?"<null>":this.enabledTurmeric));
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
        result = ((result* 31)+((this.enabledMonkeysInABarrel == null)? 0 :this.enabledMonkeysInABarrel.hashCode()));
        result = ((result* 31)+((this.enabledTurmeric == null)? 0 :this.enabledTurmeric.hashCode()));
        result = ((result* 31)+((this.previewFeatureUsesArtifacts == null)? 0 :this.previewFeatureUsesArtifacts.hashCode()));
        result = ((result* 31)+((this.enabledWebSearch == null)? 0 :this.enabledWebSearch.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof Settings) == false) {
            return false;
        }
        Settings rhs = ((Settings) other);
        return (((((this.enabledMonkeysInABarrel == rhs.enabledMonkeysInABarrel)||((this.enabledMonkeysInABarrel!= null)&&this.enabledMonkeysInABarrel.equals(rhs.enabledMonkeysInABarrel)))&&((this.enabledTurmeric == rhs.enabledTurmeric)||((this.enabledTurmeric!= null)&&this.enabledTurmeric.equals(rhs.enabledTurmeric))))&&((this.previewFeatureUsesArtifacts == rhs.previewFeatureUsesArtifacts)||((this.previewFeatureUsesArtifacts!= null)&&this.previewFeatureUsesArtifacts.equals(rhs.previewFeatureUsesArtifacts))))&&((this.enabledWebSearch == rhs.enabledWebSearch)||((this.enabledWebSearch!= null)&&this.enabledWebSearch.equals(rhs.enabledWebSearch))));
    }

}

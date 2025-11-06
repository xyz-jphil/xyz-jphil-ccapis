package xyz.jphil.ccapis.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * POJO for CCAPI usage data
 * Ignores unknown properties to handle API changes gracefully
 */
@Data
@Accessors(fluent = true, chain = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class UsageData {
    
    @JsonProperty("five_hour")
    Usage fiveHour;
    
    @JsonProperty("seven_day")
    Usage sevenDay;
    
    @JsonProperty("seven_day_oauth_apps")
    Usage sevenDayOauthApps;
    
    @JsonProperty("seven_day_opus")
    Usage sevenDayOpus;
    
}
package xyz.jphil.ccapis.oauth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.Instant;

/**
 * OAuth tokens response from token endpoint
 */
@Data
@Accessors(fluent = true, chain = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class OAuthTokens {

    @JsonProperty("access_token")
    private String accessToken;

    @JsonProperty("refresh_token")
    private String refreshToken;

    @JsonProperty("expires_in")
    private Long expiresIn;

    @JsonProperty("token_type")
    private String tokenType;

    /**
     * Computed expiration timestamp (not from API)
     * Set after receiving token response
     */
    @JsonProperty("expires_at")
    private Long expiresAt;

    /**
     * Check if access token is expired or about to expire
     * @param bufferSeconds seconds before expiration to consider token expired (default: 60)
     */
    public boolean isExpired(int bufferSeconds) {
        if (expiresAt == null) {
            return true;
        }
        var now = Instant.now().getEpochSecond();
        var expiryWithBuffer = expiresAt - bufferSeconds;
        return now >= expiryWithBuffer;
    }

    /**
     * Check if access token is expired (with 60 second buffer)
     */
    public boolean isExpired() {
        return isExpired(60);
    }

    /**
     * Calculate and set expiration timestamp based on expires_in
     * Call this after receiving token response
     */
    public OAuthTokens calculateExpiresAt() {
        if (expiresIn != null) {
            this.expiresAt = Instant.now().getEpochSecond() + expiresIn;
        }
        return this;
    }

    /**
     * Set expiration timestamp directly (for loading from storage)
     */
    public OAuthTokens expiresAt(Long expiresAt) {
        this.expiresAt = expiresAt;
        return this;
    }
}

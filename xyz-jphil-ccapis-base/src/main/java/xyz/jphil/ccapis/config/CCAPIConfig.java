package xyz.jphil.ccapis.config;

import lombok.Data;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import lombok.experimental.Accessors;

/**
 * Configuration class for CCAPI credentials and settings
 */
@Data
@Accessors(fluent = true, chain = true)
public class CCAPIConfig {
    
    List<Account> accounts;
    String defaultOrgId;
    String defaultSessionKey;
    
    public CCAPIConfig() {
        this.accounts = new ArrayList<>();
    }
    
    /**
     * Add a new account with org ID and session key
     */
    public void addAccount(String orgId, String sessionKey) {
        accounts.add(new Account(orgId, sessionKey));
        if (accounts.size() == 1) {
            // Set the first account as default
            this.defaultOrgId = orgId;
            this.defaultSessionKey = sessionKey;
        }
    }
    
    /**
     * Add a new account with org ID, session key, user agent, and cloudflare cookies
     */
    public void addAccount(String orgId, String sessionKey, String userAgent, String cloudflareCookies) {
        accounts.add(new Account(orgId, sessionKey, userAgent, cloudflareCookies));
        if (accounts.size() == 1) {
            // Set the first account as default
            this.defaultOrgId = orgId;
            this.defaultSessionKey = sessionKey;
        }
    }
    
    /**
     * Get an account by org ID
     */
    public Account getAccountByOrgId(String orgId) {
        return accounts().stream()
                .filter(account -> account.orgId().equals(orgId))
                .findFirst()
                .orElse(null);
    }
    
    /**
     * Get the default account
     */
    public Account getDefaultAccount() {
        if (defaultOrgId() != null && defaultSessionKey() != null) {
            return new Account(defaultOrgId(), defaultSessionKey());
        }
        return null;
    }
    
    /**
     * Get a random account for load balancing
     */
    public Account getRandomAccount() {
        if (accounts().isEmpty()) {
            return null;
        }
        int randomIndex = (int) (Math.random() * accounts().size());
        return accounts().get(randomIndex);
    }
    
    /**
     * Inner class representing a CCAPI account
     */
    @Data
    @Accessors(fluent = true, chain = true)
    public static class Account {
        String orgId;
        String key;
        String ua;
        String cloudflareCookies;
        Map<String, String> additionalHeaders;
        Map<String, String> cookies;
        
        public Account(String orgId, String key) {
            this.orgId = orgId;
            this.key = key;
            // Default user agent matching the Chrome 100 version from the batch files
            this.ua = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/100.0.4896.75 Safari/537.36";
            this.additionalHeaders = new HashMap<>();
            this.cookies = new HashMap<>();
        }
        
        public Account(String orgId, String sessionKey, String userAgent, String cloudflareCookies) {
            this.orgId = orgId;
            this.key = sessionKey;
            this.ua = userAgent;
            this.cloudflareCookies = cloudflareCookies;
            this.additionalHeaders = new HashMap<>();
            this.cookies = new HashMap<>();
        }
    }
}
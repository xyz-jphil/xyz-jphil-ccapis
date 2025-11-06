package xyz.jphil.ccapis;

import xyz.jphil.ccapis.config.AccountConfig;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class AccountManager {
    private final AccountConfig config;
    private final CCApiService apiService;
    
    public AccountManager() {
        this.config = new AccountConfig();
        this.apiService = new CCApiService();
    }
    
    public void addAccount(String accountId, String sessionId) {
        config.addAccount(accountId, sessionId);
        config.saveConfig();
    }
    
    public CCApiService.UsageData getUsageForAccount(String accountId) throws IOException {
        String sessionId = config.getSessionId(accountId);
        if (sessionId == null) {
            throw new IllegalArgumentException("Account not found: " + accountId);
        }
        return apiService.fetchUsage(sessionId);
    }
    
    public Map<String, CCApiService.UsageData> getAllUsage() {
        Map<String, CCApiService.UsageData> results = new HashMap<>();
        
        String[] accountIds = config.getAccountIds();
        for (String accountId : accountIds) {
            try {
                String sessionId = config.getSessionId(accountId);
                if (sessionId != null) {
                    CCApiService.UsageData usage = apiService.fetchUsage(sessionId);
                    results.put(accountId, usage);
                }
            } catch (IOException e) {
                System.err.println("Error fetching usage for account " + accountId + ": " + e.getMessage());
            }
        }
        
        return results;
    }
    
    public int getAccountCount() {
        return config.getAccountIds().length;
    }
}
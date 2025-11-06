package xyz.jphil.ccapis.config;

import java.io.*;
import java.util.Properties;

public class AccountConfig {
    private static final String CONFIG_FILE = "accounts.properties";
    private final Properties properties;
    
    public AccountConfig() {
        this.properties = new Properties();
        loadConfig();
    }
    
    private void loadConfig() {
        File configFile = new File(CONFIG_FILE);
        if (configFile.exists()) {
            try (FileInputStream fis = new FileInputStream(configFile)) {
                properties.load(fis);
            } catch (IOException e) {
                System.err.println("Could not load config file: " + e.getMessage());
            }
        }
    }
    
    public void saveConfig() {
        try (FileOutputStream fos = new FileOutputStream(CONFIG_FILE)) {
            properties.store(fos, "CCAPI Account Configuration");
        } catch (IOException e) {
            System.err.println("Could not save config file: " + e.getMessage());
        }
    }
    
    public void addAccount(String accountId, String sessionId) {
        properties.setProperty("account." + accountId + ".sessionId", sessionId);
    }
    
    public String getSessionId(String accountId) {
        return properties.getProperty("account." + accountId + ".sessionId");
    }
    
    public String[] getAccountIds() {
        return properties.keySet().stream()
                .filter(key -> key.toString().startsWith("account."))
                .map(key -> ((String) key).split("\\.")[1])
                .distinct()
                .toArray(String[]::new);
    }
}
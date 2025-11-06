package xyz.jphil.ccapis;

import okhttp3.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;

import java.io.IOException;
import java.time.LocalDateTime;

public class CCApiService {
    private static final String getUsageUrl() {
        String baseUrl = System.getenv("CCAPI_BASE_URL");
        if (baseUrl == null) {
            throw new RuntimeException("CCAPI_BASE_URL environment variable must be set");
        }
        // Ensure the URL starts with https://
        if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
            baseUrl = "https://" + baseUrl;
        }
        return baseUrl + "/settings/usage";
    }
    private final OkHttpClient client;
    private final ObjectMapper objectMapper;
    
    public CCApiService() {
        this.client = new OkHttpClient();
        this.objectMapper = new ObjectMapper();
    }
    
    public UsageData fetchUsage(String sessionId) throws IOException {
        Request request = new Request.Builder()
                .url(getUsageUrl())
                .addHeader("accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7")
                .addHeader("accept-language", "en-GB,en-US;q=0.9,en;q=0.8")
                .addHeader("cache-control", "max-age=0")
                .addHeader("priority", "u=0, i")
                .addHeader("sec-ch-ua", "\"Chromium\";v=\"140\", \"Not=A?Brand\";v=\"24\", \"Google Chrome\";v=\"140\"")
                .addHeader("sec-ch-ua-arch", "\"x86\"")
                .addHeader("sec-ch-ua-bitness", "\"64\"")
                .addHeader("sec-ch-ua-full-version", "\"140.0.7339.242\"")
                .addHeader("sec-ch-ua-full-version-list", "\"Chromium\";v=\"140.0.7339.242\", \"Not=A?Brand\";v=\"24.0.0.0\", \"Google Chrome\";v=\"140.0.7339.242\"")
                .addHeader("sec-ch-ua-mobile", "?0")
                .addHeader("sec-ch-ua-model", "\"\"")
                .addHeader("sec-ch-ua-platform", "\"Windows\"")
                .addHeader("sec-ch-ua-platform-version", "\"19.0.0\"")
                .addHeader("sec-fetch-dest", "document")
                .addHeader("sec-fetch-mode", "navigate")
                .addHeader("sec-fetch-site", "same-origin")
                .addHeader("sec-fetch-user", "?1")
                .addHeader("upgrade-insecure-requests", "1")
                .addHeader("user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36")
                .addHeader("cookie", "sessionKey=" + sessionId)
                .build();
        
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Unexpected response code: " + response.code());
            }
            
            String responseBody = response.body().string();
            
            // Parse the HTML response to extract usage information
            // This is a simplified example - in reality, you'd need to parse the HTML properly
            // For now, we'll simulate the usage data
            return parseUsageFromHtml(responseBody);
        }
    }
    
    private UsageData parseUsageFromHtml(String html) {
        // This is a placeholder implementation
        // In a real implementation, you would parse the HTML to extract the usage data
        // For now, we'll return a simulated usage data
        
        // Simulate 3 hours used out of 5 hours
        long totalMinutes = 300; // 5 hours in minutes
        long usedMinutes = 180;  // 3 hours in minutes
        long remainingMinutes = totalMinutes - usedMinutes;
        
        int hours = (int) (remainingMinutes / 60);
        int minutes = (int) (remainingMinutes % 60);
        
        return new UsageData(hours, minutes, totalMinutes / 60d, LocalDateTime.now());
    }
    
    @Data
    public static class UsageData {
        final double remainingHours;
        final double remainingMinutes;
        final double totalHours;
        final LocalDateTime lastUpdated;
                
        public String getFormattedUsage() {
            return String.format("%dh %dm / %dh 0m", remainingHours, remainingMinutes, totalHours);
        }
    }
}
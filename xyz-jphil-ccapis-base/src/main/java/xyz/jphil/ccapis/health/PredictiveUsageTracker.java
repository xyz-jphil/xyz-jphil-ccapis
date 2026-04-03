package xyz.jphil.ccapis.health;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks predicted quota usage per account based on PRP-19 formula.
 * Maintains running prediction alongside API-reported usage for validation.
 *
 * Thread-safe for concurrent access.
 */
public class PredictiveUsageTracker {

    private final Map<String, PredictedUsage> predictions = new ConcurrentHashMap<>();

    /**
     * Record a predicted quota increase for an account.
     *
     * @param accountId Account identifier
     * @param quotaIncrease Predicted quota increase as percentage (e.g., 5.2 for +5.2%)
     */
    public void recordPredictedUsage(String accountId, double quotaIncrease) {
        predictions.compute(accountId, (id, existing) -> {
            if (existing == null) {
                existing = new PredictedUsage(accountId);
            }
            existing.addPrediction(quotaIncrease);
            return existing;
        });
    }

    /**
     * Get current predicted usage for an account.
     *
     * @param accountId Account identifier
     * @return Predicted usage percentage, or 0.0 if no predictions recorded
     */
    public double getPredictedUsage(String accountId) {
        var prediction = predictions.get(accountId);
        return prediction != null ? prediction.predictedUsage : 0.0;
    }

    /**
     * Get number of consecutive requests for an account since last API sync.
     *
     * @param accountId Account identifier
     * @return Request count
     */
    public int getRequestCount(String accountId) {
        var prediction = predictions.get(accountId);
        return prediction != null ? prediction.requestCount : 0;
    }

    /**
     * Sync predicted usage with actual API-reported usage.
     * Calculates prediction accuracy and resets accumulator.
     *
     * @param accountId Account identifier
     * @param apiUsage Actual usage from API (percentage)
     */
    public void syncWithApiUsage(String accountId, double apiUsage) {
        predictions.compute(accountId, (id, existing) -> {
            if (existing == null) {
                existing = new PredictedUsage(accountId);
            }
            existing.syncWithApi(apiUsage);
            return existing;
        });
    }

    /**
     * Reset prediction for an account (e.g., when quota resets).
     *
     * @param accountId Account identifier
     */
    public void reset(String accountId) {
        predictions.remove(accountId);
    }

    /**
     * Reset all predictions.
     */
    public void resetAll() {
        predictions.clear();
    }

    /**
     * Get comparison log showing predicted vs actual usage.
     *
     * @param accountId Account identifier
     * @param apiUsage Current API-reported usage
     * @return Formatted comparison string
     */
    public String getComparisonLog(String accountId, double apiUsage) {
        var prediction = predictions.get(accountId);
        if (prediction == null) {
            return String.format("API: %.1f%%, Predicted: N/A", apiUsage);
        }

        var diff = prediction.predictedUsage - apiUsage;
        var diffSign = diff >= 0 ? "+" : "";

        return String.format(
            "API: %.1f%%, Predicted: %.1f%% (diff: %s%.1f%%, accuracy: %.1f%%, requests: %d)",
            apiUsage,
            prediction.predictedUsage,
            diffSign,
            diff,
            prediction.getAccuracy(),
            prediction.requestCount
        );
    }

    /**
     * Get prediction details for logging.
     *
     * @param accountId Account identifier
     * @return Prediction details or null if not tracked
     */
    public PredictedUsage getPrediction(String accountId) {
        return predictions.get(accountId);
    }

    /**
     * Internal class to track prediction state per account.
     */
    public static class PredictedUsage {
        private final String accountId;
        private double predictedUsage = 0.0;
        private double lastApiUsage = 0.0;
        private int requestCount = 0;
        private OffsetDateTime lastSyncTime;
        private double lastPredictionError = 0.0;

        public PredictedUsage(String accountId) {
            this.accountId = accountId;
            this.lastSyncTime = OffsetDateTime.now();
        }

        /**
         * Add a prediction increment.
         */
        void addPrediction(double quotaIncrease) {
            predictedUsage += quotaIncrease;
            requestCount++;
        }

        /**
         * Sync with API-reported usage and calculate accuracy.
         */
        void syncWithApi(double apiUsage) {
            lastPredictionError = predictedUsage - apiUsage;
            lastApiUsage = apiUsage;
            predictedUsage = apiUsage; // Reset to API baseline
            requestCount = 0; // Reset counter
            lastSyncTime = OffsetDateTime.now();
        }

        /**
         * Get prediction accuracy as percentage (100% = perfect, 0% = completely wrong).
         */
        public double getAccuracy() {
            if (lastApiUsage == 0) {
                return 100.0; // No baseline to compare
            }

            var errorPercent = Math.abs(lastPredictionError / lastApiUsage) * 100.0;
            return Math.max(0, 100.0 - errorPercent);
        }

        public String getAccountId() {
            return accountId;
        }

        public double getPredictedUsage() {
            return predictedUsage;
        }

        public double getLastApiUsage() {
            return lastApiUsage;
        }

        public int getRequestCount() {
            return requestCount;
        }

        public OffsetDateTime getLastSyncTime() {
            return lastSyncTime;
        }

        public double getLastPredictionError() {
            return lastPredictionError;
        }
    }
}

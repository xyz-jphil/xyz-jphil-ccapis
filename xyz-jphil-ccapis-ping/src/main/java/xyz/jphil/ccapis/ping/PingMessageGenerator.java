package xyz.jphil.ccapis.ping;

import java.time.LocalTime;
import java.util.Random;

/**
 * Generates friendly ping messages with time-based greetings.
 * Messages are simple and polite, asking for time and greeting appropriately.
 */
public class PingMessageGenerator {

    private static final Random RANDOM = new Random();

    /**
     * Generate a friendly ping message based on current time
     */
    public static String generatePingMessage() {
        var now = LocalTime.now();
        var hour = now.getHour();

        String greeting;
        if (hour >= 5 && hour < 12) {
            greeting = "Good morning";
        } else if (hour >= 12 && hour < 17) {
            greeting = "Good afternoon";
        } else if (hour >= 17 && hour < 21) {
            greeting = "Good evening";
        } else {
            greeting = "Hello";
        }

        // Variations of simple time-related questions
        var messages = new String[] {
            greeting + "! Could you tell me what time it is?",
            greeting + "! What's the current time, please?",
            greeting + "! May I know the time?",
            greeting + "! Could you share the current time?",
            "Hi! " + greeting + "! What time is it now?",
            greeting + "! I hope you're doing well. What's the time?",
        };

        return messages[RANDOM.nextInt(messages.length)];
    }

}

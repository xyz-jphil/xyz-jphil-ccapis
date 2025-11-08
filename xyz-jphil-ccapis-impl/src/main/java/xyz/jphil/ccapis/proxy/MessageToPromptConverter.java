package xyz.jphil.ccapis.proxy;

import xyz.jphil.ccapis.proxy.anthropic.AnthropicRequest;
import xyz.jphil.ccapis.proxy.anthropic.Message;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Converts Anthropic Messages API format to CCAPI prompt format
 *
 * <p>Implements the collision-free XML tag strategy from PRP 02:
 * <ul>
 *   <li>Single-turn: Send prompt directly (with optional system prompt)</li>
 *   <li>Multi-turn: Use XML tags with numbered suffixes to avoid collisions</li>
 *   <li>Tags: custom_system_prompt, formatting_instructions, user, ai_assistant</li>
 *   <li>Algorithm: Scan all content, increment suffix if collision detected</li>
 * </ul>
 *
 * <p>Example multi-turn output:
 * <pre>
 * &lt;custom_system_prompt&gt;You are a Python expert.&lt;/custom_system_prompt&gt;
 * &lt;formatting_instructions&gt;Use XML tags. Respond as ai_assistant_2 without tags.&lt;/formatting_instructions&gt;
 * &lt;user&gt;What is 2+2?&lt;/user&gt;
 * &lt;ai_assistant&gt;4&lt;/ai_assistant&gt;
 * &lt;user_1&gt;What about 3+3?&lt;/user_1&gt;
 * </pre>
 *
 * <p>Example single-turn output:
 * <pre>
 * &lt;custom_system_prompt&gt;You are a Python expert.&lt;/custom_system_prompt&gt;
 *
 * What is 2+2?
 * </pre>
 */
public class MessageToPromptConverter {

    private static final String TAG_SYSTEM = "custom_system_prompt";
    private static final String TAG_FORMATTING = "formatting_instructions";
    private static final String TAG_USER = "user";
    private static final String TAG_ASSISTANT = "ai_assistant";

    /**
     * Convert Anthropic request to CCAPI prompt format
     *
     * @param request the Anthropic API request
     * @return formatted prompt string for CCAPI
     */
    public static String convert(AnthropicRequest request) {
        var messages = request.getMessages();
        if (messages == null || messages.isEmpty()) {
            return "";
        }

        // Check if this is a single-turn request (optimization)
        if (isSingleTurn(messages)) {
            return convertSingleTurn(request);
        }

        // Multi-turn: Use XML formatting
        return convertMultiTurn(request);
    }

    /**
     * Check if this is a single-turn conversation
     * Single-turn = only 1 user message (no assistant responses)
     */
    private static boolean isSingleTurn(List<Message> messages) {
        // Count messages by role
        long userCount = messages.stream().filter(m -> "user".equals(m.getRole())).count();
        long assistantCount = messages.stream().filter(m -> "assistant".equals(m.getRole())).count();

        return userCount == 1 && assistantCount == 0;
    }

    /**
     * Convert single-turn request (optimized format)
     */
    private static String convertSingleTurn(AnthropicRequest request) {
        var result = new StringBuilder();
        var allContent = collectAllContent(request);

        // Add system prompt if present (with tools appended)
        var systemPrompt = buildSystemPromptWithTools(request);
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            var systemTag = findNonCollidingTag(TAG_SYSTEM, allContent);
            result.append("<").append(systemTag).append(">")
                  .append(systemPrompt)
                  .append("</").append(systemTag).append(">\n\n");
        }

        // Add the user message directly (no XML wrapper)
        var userMessage = request.getMessages().stream()
                .filter(m -> "user".equals(m.getRole()))
                .findFirst()
                .orElse(null);

        if (userMessage != null) {
            result.append(userMessage.getTextContent());
        }

        return result.toString();
    }

    /**
     * Convert multi-turn request (full XML formatting)
     */
    private static String convertMultiTurn(AnthropicRequest request) {
        var result = new StringBuilder();
        var messages = request.getMessages();
        var allContent = collectAllContent(request);

        // Determine the final assistant tag (for formatting instructions)
        int nextAssistantIndex = countMessagesWithRole(messages, "assistant");
        var finalAssistantTag = findNonCollidingTag(TAG_ASSISTANT, allContent, nextAssistantIndex);

        // Add system prompt if present (with tools appended)
        var systemPrompt = buildSystemPromptWithTools(request);
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            var systemTag = findNonCollidingTag(TAG_SYSTEM, allContent);
            result.append("<").append(systemTag).append(">")
                  .append(systemPrompt)
                  .append("</").append(systemTag).append(">\n\n");
        }

        // Add formatting instructions
        var formattingTag = findNonCollidingTag(TAG_FORMATTING, allContent);
        result.append("<").append(formattingTag).append(">")
              .append("This conversation uses XML-style tags for message boundaries.\n")
              .append("You are fulfilling the role of ").append(finalAssistantTag).append(".\n")
              .append("Respond with ONLY your answer as plain text.\n")
              .append("Do NOT include XML tags in your response.")
              .append("</").append(formattingTag).append(">\n\n");

        // Add conversation messages with collision-free tags
        int userIndex = 0;
        int assistantIndex = 0;

        for (var message : messages) {
            var content = message.getTextContent();
            if (content.isEmpty()) {
                continue;
            }

            if ("user".equals(message.getRole())) {
                var tag = findNonCollidingTag(TAG_USER, allContent, userIndex);
                result.append("<").append(tag).append(">")
                      .append(content)
                      .append("</").append(tag).append(">\n\n");
                userIndex++;
            } else if ("assistant".equals(message.getRole())) {
                var tag = findNonCollidingTag(TAG_ASSISTANT, allContent, assistantIndex);
                result.append("<").append(tag).append(">")
                      .append(content)
                      .append("</").append(tag).append(">\n\n");
                assistantIndex++;
            }
        }

        return result.toString().trim();
    }

    /**
     * Count messages with a specific role
     */
    private static int countMessagesWithRole(List<Message> messages, String role) {
        return (int) messages.stream()
                .filter(m -> role.equals(m.getRole()))
                .count();
    }

    /**
     * Collect all content from the request for collision detection
     */
    private static Set<String> collectAllContent(AnthropicRequest request) {
        var content = new HashSet<String>();

        // Add system prompt
        var systemPrompt = request.getSystemPrompt();
        if (systemPrompt != null) {
            content.add(systemPrompt);
        }

        // Add all message content
        if (request.getMessages() != null) {
            for (var message : request.getMessages()) {
                var text = message.getTextContent();
                if (!text.isEmpty()) {
                    content.add(text);
                }
            }
        }

        return content;
    }

    /**
     * Find a non-colliding tag name for the base tag
     * Checks if baseTag or &lt;baseTag&gt; appears in any content
     * If collision detected, tries baseTag_1, baseTag_2, etc.
     */
    private static String findNonCollidingTag(String baseTag, Set<String> allContent) {
        return findNonCollidingTag(baseTag, allContent, 0);
    }

    /**
     * Find a non-colliding tag name starting from a specific index
     * Useful for numbered tags in conversation history
     */
    private static String findNonCollidingTag(String baseTag, Set<String> allContent, int startIndex) {
        // For index 0, try the base tag without suffix first
        if (startIndex == 0) {
            if (!hasCollision(baseTag, allContent)) {
                return baseTag;
            }
        }

        // Try with numbered suffixes
        int index = Math.max(1, startIndex);
        while (index < 1000) { // Safety limit
            var candidateTag = baseTag + "_" + index;
            if (!hasCollision(candidateTag, allContent)) {
                return candidateTag;
            }
            index++;
        }

        // Fallback (should never happen with reasonable content)
        return baseTag + "_" + System.currentTimeMillis();
    }

    /**
     * Check if a tag collides with any content
     * A collision occurs if the tag name or &lt;tag&gt; or &lt;/tag&gt; appears in content
     */
    private static boolean hasCollision(String tag, Set<String> allContent) {
        var openTag = "<" + tag + ">";
        var closeTag = "</" + tag + ">";

        for (var content : allContent) {
            if (content.contains(tag) || content.contains(openTag) || content.contains(closeTag)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Build system prompt with tools appended
     * Combines the original system prompt with tool definitions
     */
    private static String buildSystemPromptWithTools(AnthropicRequest request) {
        var systemPrompt = request.getSystemPrompt();
        var toolsText = ToolsConverter.convertToolsToText(request.getTools());

        if (systemPrompt == null || systemPrompt.isEmpty()) {
            return toolsText != null ? toolsText : "";
        }

        if (toolsText == null || toolsText.isEmpty()) {
            return systemPrompt;
        }

        return systemPrompt + "\n\n" + toolsText;
    }

    /**
     * Escape special characters in prompt text
     * Currently a no-op as we preserve content exactly, but provided for future use
     */
    private static String escapePromptText(String text) {
        // No escaping needed - content is preserved exactly as per design
        return text;
    }
}

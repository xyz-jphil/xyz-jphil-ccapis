package xyz.jphil.ccapis.proxy.toolcalls;

import org.jsoup.parser.Parser;

/**
 * Test to understand JSoup sourceRange() behavior with streaming chunks
 */
public class ToolCallParserSourceRangeTest {

    public static void main(String[] args) {
        // Simulate the actual CCAPI response structure
        String fullText = "<tool_uses><tool_use name=\"Write\">" +
                "<parameter name=\"file_path\">test.xml</parameter>" +
                "<parameter name=\"content\"><?xml version=\"1.0\"?>\n<project>\n    <build>\n        <configuration>\n            <source>21</source>\n            <target>21</target>\n        </configuration>\n    </build>\n</project></parameter>" +
                "</tool_use></tool_uses>";

        // Parse with position tracking (same as production code)
        var parser = Parser.htmlParser();
        parser.settings(org.jsoup.parser.ParseSettings.preserveCase);
        parser.setTrackPosition(true);
        var doc = parser.parseInput(fullText, "");
        doc.outputSettings().prettyPrint(false);

        // Find the "content" parameter
        var paramElements = doc.select("parameter[name=content]");
        if (!paramElements.isEmpty()) {
            var paramElem = paramElements.first();
            var sourceRange = paramElem.sourceRange();

            System.out.println("=== Testing sourceRange() behavior ===");
            System.out.println("Full text length: " + fullText.length());
            System.out.println();

            if (sourceRange != null) {
                int startPos = sourceRange.start().pos();
                int endPos = sourceRange.end().pos();

                System.out.println("sourceRange.start().pos() = " + startPos);
                System.out.println("sourceRange.end().pos() = " + endPos);
                System.out.println();

                // Extract using substring
                String extracted = fullText.substring(startPos, endPos);
                System.out.println("substring(start, end) extracts:");
                System.out.println(extracted);
                System.out.println();

                // Check what character is at endPos
                if (endPos < fullText.length()) {
                    System.out.println("Character at endPos: '" + fullText.charAt(endPos) + "'");
                    System.out.println("Next 20 chars from endPos: " + fullText.substring(endPos, Math.min(endPos + 20, fullText.length())));
                }
                System.out.println();

                // Check if extracted ends with '>'
                System.out.println("Does extracted end with '>': " + extracted.endsWith(">"));
                System.out.println();

                // Try finding closing tag
                String tagName = paramElem.tagName();
                String closingTag = "</" + tagName + ">";
                System.out.println("Looking for closing tag: " + closingTag);

                int closingTagStart = fullText.indexOf(closingTag, startPos);
                if (closingTagStart != -1) {
                    int actualEndPos = closingTagStart + closingTag.length();
                    String fullElementExtraction = fullText.substring(startPos, actualEndPos);
                    System.out.println("\nFull element extraction (including closing tag):");
                    System.out.println(fullElementExtraction.substring(0, Math.min(200, fullElementExtraction.length())));
                    System.out.println("...[truncated, total length: " + fullElementExtraction.length() + "]");
                }

                // Now test the actual extraction logic from ToolCallParser
                System.out.println("\n=== Testing current ToolCallParser logic ===");
                String rawOuterHtml = fullText.substring(startPos, endPos);
                int contentStart = rawOuterHtml.indexOf('>');
                if (contentStart != -1) {
                    contentStart++;
                    int contentEnd = rawOuterHtml.lastIndexOf('<');
                    System.out.println("contentStart (after '>'): " + contentStart);
                    System.out.println("contentEnd (at last '<'): " + contentEnd);

                    if (contentEnd > contentStart) {
                        String extractedContent = rawOuterHtml.substring(contentStart, contentEnd);
                        System.out.println("Extracted content: " + extractedContent.substring(0, Math.min(100, extractedContent.length())));
                    } else {
                        System.out.println("ERROR: contentEnd <= contentStart, would fall back!");
                    }
                }
            }
        }
    }
}

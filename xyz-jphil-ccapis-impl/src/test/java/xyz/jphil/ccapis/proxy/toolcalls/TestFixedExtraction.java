package xyz.jphil.ccapis.proxy.toolcalls;

import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;

/**
 * Test the fixed extraction logic
 */
public class TestFixedExtraction {

    public static void main(String[] args) {
        String fullText = """
            <tool_uses><tool_use name="Write"><parameter name="file_path">test.xml</parameter><parameter name="content"><?xml version="1.0"?>
            <project>
                <modelVersion>4.0.0</modelVersion>
                <build>
                    <plugins>
                        <plugin>
                            <configuration>
                                <source>21</source>
                                <target>21</target>
                            </configuration>
                        </plugin>
                    </plugins>
                </build>
            </project></parameter></tool_use></tool_uses>
            """;

        System.out.println("=== Testing Fixed Extraction Logic ===");
        System.out.println();

        // Parse with HTML parser + position tracking (like production)
        var parser = Parser.htmlParser();
        parser.settings(org.jsoup.parser.ParseSettings.preserveCase);
        parser.setTrackPosition(true);
        var doc = parser.parseInput(fullText, "");
        doc.outputSettings().prettyPrint(false);

        var paramElem = doc.select("parameter[name=content]").first();
        if (paramElem != null) {
            // Simulate the fixed extraction logic
            var sourceRange = paramElem.sourceRange();
            if (sourceRange != null) {
                int startPos = sourceRange.start().pos();
                int openingTagEnd = sourceRange.end().pos();

                System.out.println("sourceRange.start().pos() = " + startPos);
                System.out.println("sourceRange.end().pos() (after opening tag) = " + openingTagEnd);
                System.out.println();

                // Find closing tag
                String tagName = paramElem.tagName();
                String closingTag = "</" + tagName + ">";
                int closingTagStart = fullText.indexOf(closingTag, openingTagEnd);

                System.out.println("Looking for closing tag: " + closingTag);
                System.out.println("Found at position: " + closingTagStart);
                System.out.println();

                if (closingTagStart != -1) {
                    int actualEndPos = closingTagStart + closingTag.length();

                    // Extract full outer HTML
                    String rawOuterHtml = fullText.substring(startPos, actualEndPos);

                    System.out.println("Extracted rawOuterHtml length: " + rawOuterHtml.length());
                    System.out.println("First 100 chars: " + rawOuterHtml.substring(0, Math.min(100, rawOuterHtml.length())));
                    System.out.println("Last 50 chars: " + rawOuterHtml.substring(Math.max(0, rawOuterHtml.length() - 50)));
                    System.out.println();

                    // Extract inner content
                    int contentStart = rawOuterHtml.indexOf('>') + 1;
                    int contentEnd = rawOuterHtml.lastIndexOf('<');

                    if (contentEnd > contentStart) {
                        String rawContent = rawOuterHtml.substring(contentStart, contentEnd);
                        String decodedContent = org.jsoup.parser.Parser.unescapeEntities(rawContent, false);

                        System.out.println("=== Extracted Content ===");
                        System.out.println(decodedContent);
                        System.out.println();

                        System.out.println("=== Validation ===");
                        if (decodedContent.contains("<?xml version=\"1.0\"?>")) {
                            System.out.println("✓ XML declaration preserved");
                        } else {
                            System.out.println("✗ XML declaration missing");
                        }

                        if (decodedContent.contains("modelVersion")) {
                            System.out.println("✓ Case preserved (modelVersion)");
                        }

                        if (decodedContent.contains("<source>21</source>")) {
                            System.out.println("✓ <source> closing tag PRESERVED!");
                        } else {
                            System.out.println("✗ <source> closing tag missing");
                        }

                        if (decodedContent.contains("<target>21</target>")) {
                            System.out.println("✓ <target> closing tag preserved");
                        }
                    }
                }
            }
        }
    }
}

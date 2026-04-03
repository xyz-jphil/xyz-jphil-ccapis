package xyz.jphil.ccapis.proxy.toolcalls;

import org.jsoup.parser.Parser;

/**
 * Test what fallbackExtract actually returns
 */
public class TestFallbackExtract {

    public static void main(String[] args) {
        // Simulate what happens with the actual pom.xml content
        String pomContent = """
            <parameter name="content"><?xml version="1.0"?>
            <project>
                <modelVersion>4.0.0</modelVersion>
                <groupId>com.example</groupId>
                <artifactId>test</artifactId>
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
            </project></parameter>
            """;

        System.out.println("=== Testing fallbackExtract (paramElem.html()) ===");
        System.out.println();

        // Parse with HTML parser + preserveCase (like production)
        var parser = Parser.htmlParser();
        parser.settings(org.jsoup.parser.ParseSettings.preserveCase);
        parser.setTrackPosition(true);
        var doc = parser.parseInput(pomContent, "");
        doc.outputSettings().prettyPrint(false);

        var paramElem = doc.select("parameter[name=content]").first();
        if (paramElem != null) {
            // This is what fallbackExtract does
            String content = paramElem.html();

            System.out.println("paramElem.html() returns:");
            System.out.println(content);
            System.out.println();

            // Check specific things
            System.out.println("=== Checking specific issues ===");

            if (content.contains("modelVersion")) {
                System.out.println("✓ Case preserved: 'modelVersion' found (not 'modelversion')");
            } else {
                System.out.println("✗ Case NOT preserved: 'modelVersion' not found");
            }

            if (content.contains("<?xml version=\"1.0\"?>")) {
                System.out.println("✓ XML declaration preserved correctly");
            } else if (content.contains("<!--?xml")) {
                System.out.println("✗ XML declaration converted to comment");
            } else {
                System.out.println("✗ XML declaration missing entirely");
            }

            if (content.contains("<source>21</source>")) {
                System.out.println("✓ <source> closing tag preserved");
            } else if (content.contains("<source>21")) {
                System.out.println("✗ <source> closing tag MISSING!");
                // Show what's actually there
                int idx = content.indexOf("<source>");
                if (idx != -1) {
                    System.out.println("  Found: " + content.substring(idx, Math.min(idx + 50, content.length())));
                }
            } else {
                System.out.println("✗ <source> tag not found at all");
            }

            if (content.contains("<target>21</target>")) {
                System.out.println("✓ <target> closing tag preserved");
            }
        }
    }
}

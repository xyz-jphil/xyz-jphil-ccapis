package xyz.jphil.ccapis.proxy.toolcalls;

import org.jsoup.parser.Parser;

/**
 * Test what JSoup does to the <source> tag specifically
 */
public class TestJSoupSourceTag {

    public static void main(String[] args) {
        String xmlContent = """
            <configuration>
                <source>21</source>
                <target>21</target>
            </configuration>
            """;

        System.out.println("=== Original XML ===");
        System.out.println(xmlContent);
        System.out.println();

        // Parse with HTML parser (like ToolCallParser does)
        var parser = Parser.htmlParser();
        parser.settings(org.jsoup.parser.ParseSettings.preserveCase);
        parser.setTrackPosition(true);
        var doc = parser.parseInput(xmlContent, "");
        doc.outputSettings().prettyPrint(false);

        System.out.println("=== After JSoup HTML parser ===");
        System.out.println(doc.body().html());
        System.out.println();

        // Check what happened to <source> specifically
        var sourceElems = doc.select("source");
        System.out.println("=== <source> element ===");
        if (!sourceElems.isEmpty()) {
            var sourceElem = sourceElems.first();
            System.out.println("outerHtml: " + sourceElem.outerHtml());
            System.out.println("html: " + sourceElem.html());
            System.out.println("text: " + sourceElem.text());
        } else {
            System.out.println("No <source> element found!");
        }
        System.out.println();

        // Check <target> too
        var targetElems = doc.select("target");
        System.out.println("=== <target> element ===");
        if (!targetElems.isEmpty()) {
            var targetElem = targetElems.first();
            System.out.println("outerHtml: " + targetElem.outerHtml());
            System.out.println("html: " + targetElem.html());
            System.out.println("text: " + targetElem.text());
        }
        System.out.println();

        // Now test with the full pom.xml structure
        String pomXml = """
            <parameter name="content"><?xml version="1.0"?>
            <project>
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

        System.out.println("=== Testing with <parameter> wrapper ===");
        var doc2 = parser.parseInput(pomXml, "");
        doc2.outputSettings().prettyPrint(false);

        var paramElems = doc2.select("parameter[name=content]");
        if (!paramElems.isEmpty()) {
            var paramElem = paramElems.first();
            System.out.println("Parameter innerHTML:");
            String innerHTML = paramElem.html();
            System.out.println(innerHTML.substring(0, Math.min(300, innerHTML.length())));
            System.out.println();

            // Check if <source> closing tag is present
            if (innerHTML.contains("<source>21</source>")) {
                System.out.println("✓ <source>21</source> is INTACT");
            } else if (innerHTML.contains("<source>21")) {
                System.out.println("✗ <source>21 found but closing tag MISSING!");
                System.out.println("Let's see what's there:");
                int idx = innerHTML.indexOf("<source>");
                if (idx != -1) {
                    System.out.println(innerHTML.substring(idx, Math.min(idx + 50, innerHTML.length())));
                }
            }
        }

        // NOW TEST WITH XML PARSER
        System.out.println("\n=== Testing with XML parser instead ===");
        var xmlParser = Parser.xmlParser();
        xmlParser.settings(org.jsoup.parser.ParseSettings.preserveCase);
        xmlParser.setTrackPosition(true);
        var doc3 = xmlParser.parseInput(xmlContent, "");
        doc3.outputSettings().prettyPrint(false);

        System.out.println("After XML parser:");
        System.out.println(doc3.html());
        System.out.println();

        var sourceElemsXml = doc3.select("source");
        if (!sourceElemsXml.isEmpty()) {
            var sourceElem = sourceElemsXml.first();
            System.out.println("<source> with XML parser:");
            System.out.println("outerHtml: " + sourceElem.outerHtml());
            System.out.println("html: " + sourceElem.html());
        }
    }
}

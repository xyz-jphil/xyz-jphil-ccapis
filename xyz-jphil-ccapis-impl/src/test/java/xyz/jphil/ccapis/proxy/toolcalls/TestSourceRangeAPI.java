package xyz.jphil.ccapis.proxy.toolcalls;

import org.jsoup.parser.Parser;

/**
 * Test to understand what sourceRange() actually returns
 */
public class TestSourceRangeAPI {

    public static void main(String[] args) {
        // Simple test case
        String html = "<div><p>Hello</p></div>";

        System.out.println("=== Testing sourceRange() API ===");
        System.out.println("Input: " + html);
        System.out.println();

        var parser = Parser.htmlParser();
        parser.setTrackPosition(true);
        var doc = parser.parseInput(html, "");

        var pElem = doc.select("p").first();
        if (pElem != null) {
            var range = pElem.sourceRange();
            if (range != null) {
                int start = range.start().pos();
                int end = range.end().pos();

                System.out.println("<p> element:");
                System.out.println("  sourceRange.start().pos() = " + start);
                System.out.println("  sourceRange.end().pos() = " + end);
                System.out.println("  Extracted: " + html.substring(start, end));
                System.out.println();

                // What's at the positions?
                System.out.println("  Character at start: '" + html.charAt(start) + "'");
                System.out.println("  Character at end: '" + html.charAt(end) + "'");
                System.out.println();
            }
        }

        // Now test with our actual problem: <source> tag inside <parameter>
        String problematic = "<parameter name=\"content\"><configuration><source>21</source><target>21</target></configuration></parameter>";

        System.out.println("=== Testing with <source> tag ===");
        System.out.println("Input: " + problematic);
        System.out.println();

        var doc2 = parser.parseInput(problematic, "");

        // Check <parameter>
        var paramElem = doc2.select("parameter").first();
        if (paramElem != null) {
            var paramRange = paramElem.sourceRange();
            if (paramRange != null) {
                int start = paramRange.start().pos();
                int end = paramRange.end().pos();

                System.out.println("<parameter> element:");
                System.out.println("  sourceRange.start().pos() = " + start);
                System.out.println("  sourceRange.end().pos() = " + end);
                System.out.println("  Extracted: " + problematic.substring(start, end));
                System.out.println();
            }
        }

        // Check <source>
        var sourceElem = doc2.select("source").first();
        if (sourceElem != null) {
            System.out.println("<source> element (parsed by HTML parser):");
            System.out.println("  outerHtml(): " + sourceElem.outerHtml());
            System.out.println("  html(): " + sourceElem.html());

            var sourceRange = sourceElem.sourceRange();
            if (sourceRange != null) {
                int start = sourceRange.start().pos();
                int end = sourceRange.end().pos();

                System.out.println("  sourceRange.start().pos() = " + start);
                System.out.println("  sourceRange.end().pos() = " + end);
                System.out.println("  Extracted: " + problematic.substring(start, end));
            }
        }
    }
}

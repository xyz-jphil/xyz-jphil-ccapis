package xyz.jphil.ccapis.proxy.toolcalls;

import xyz.jphil.ccapis.proxy.anthropic.ToolUse;

import java.util.Map;

/**
 * Simple manual test for PRP-22: XML tag case preservation and <?xml?> declaration handling
 * Also includes PRP-23: JSON array/object parameter parsing
 *
 * Run this directly from IDE or with: java TestToolCallParserManual
 */
public class TestToolCallParserManual {

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("Manual Tests: PRP-22 & PRP-23");
        System.out.println("========================================\n");

        // PRP-22 tests
        testPomXmlWithXmlDeclaration();
        testMixedCaseTags();
        testHtmlEntities();
        testSpecialCharacters();

        // PRP-23 test
        testJsonArrayParameter();

        System.out.println("\n========================================");
        System.out.println("All manual tests completed!");
        System.out.println("========================================");
    }

    private static void testPomXmlWithXmlDeclaration() {
        System.out.println("TEST 1: POM XML with <?xml?> Declaration");
        System.out.println("------------------------------------------");

        String toolCallXml = """
            I'll help you create a Maven pom.xml file.

            <tool_use name="Write">
            <parameter name="file_path">pom.xml</parameter>
            <parameter name="content"><?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0">
                <modelVersion>4.0.0</modelVersion>
                <groupId>com.example</groupId>
                <artifactId>test-project</artifactId>
            </project>
            </parameter>
            </tool_use>
            """;

        var result = ToolCallParser.parse(toolCallXml);

        if (!result.hasToolCalls()) {
            System.out.println("❌ FAIL: No tool calls detected!");
            return;
        }

        ToolUse toolUse = result.getToolUses().get(0);
        String content = (String) toolUse.getInput().get("content");

        System.out.println("Extracted content:");
        System.out.println(content);
        System.out.println();

        // Check XML declaration
        if (content.contains("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")) {
            System.out.println("✓ PASS: XML declaration preserved correctly");
        } else if (content.contains("<!--?xml")) {
            System.out.println("❌ FAIL: XML declaration converted to comment!");
        } else {
            System.out.println("❌ FAIL: XML declaration missing!");
        }

        // Check case preservation
        boolean hasModelVersion = content.contains("<modelVersion>");
        boolean hasGroupId = content.contains("<groupId>");
        boolean hasArtifactId = content.contains("<artifactId>");
        boolean hasLowercaseModel = content.contains("<modelversion>");
        boolean hasLowercaseGroup = content.contains("<groupid>");

        if (hasModelVersion && hasGroupId && hasArtifactId) {
            System.out.println("✓ PASS: Tag case preserved (camelCase maintained)");
        } else {
            System.out.println("❌ FAIL: Tag case not preserved!");
        }

        if (hasLowercaseModel || hasLowercaseGroup) {
            System.out.println("❌ FAIL: Found lowercase tags (should be camelCase)");
        }

        System.out.println();
    }

    private static void testMixedCaseTags() {
        System.out.println("TEST 2: Mixed Case Tags");
        System.out.println("------------------------------------------");

        String toolCallXml = """
            <tool_use name="Write">
            <parameter name="file_path">config.xml</parameter>
            <parameter name="content"><Config>
                <DatabaseConnection>
                    <HostName>localhost</HostName>
                    <PortNumber>5432</PortNumber>
                </DatabaseConnection>
            </Config>
            </parameter>
            </tool_use>
            """;

        var result = ToolCallParser.parse(toolCallXml);
        ToolUse toolUse = result.getToolUses().get(0);
        String content = (String) toolUse.getInput().get("content");

        System.out.println("Extracted content:");
        System.out.println(content);
        System.out.println();

        boolean hasConfig = content.contains("<Config>");
        boolean hasDBConn = content.contains("<DatabaseConnection>");
        boolean hasHostName = content.contains("<HostName>");
        boolean hasPortNumber = content.contains("<PortNumber>");

        if (hasConfig && hasDBConn && hasHostName && hasPortNumber) {
            System.out.println("✓ PASS: All mixed-case tags preserved");
        } else {
            System.out.println("❌ FAIL: Mixed-case tags not preserved");
        }

        System.out.println();
    }

    private static void testHtmlEntities() {
        System.out.println("TEST 3: HTML Entity Decoding");
        System.out.println("------------------------------------------");

        String toolCallXml = """
            <tool_use name="Write">
            <parameter name="file_path">Test.java</parameter>
            <parameter name="content">public class Test {
                String html = "&lt;div&gt;Hello &amp; Goodbye&lt;/div&gt;";
                boolean condition = x &gt; 5 &amp;&amp; y &lt; 10;
            }
            </parameter>
            </tool_use>
            """;

        var result = ToolCallParser.parse(toolCallXml);
        ToolUse toolUse = result.getToolUses().get(0);
        String content = (String) toolUse.getInput().get("content");

        System.out.println("Extracted content:");
        System.out.println(content);
        System.out.println();

        boolean hasLessThan = content.contains("<");
        boolean hasGreaterThan = content.contains(">");
        boolean hasAmpersand = content.contains("&");
        boolean hasDoubleAmpersand = content.contains("&&");
        boolean hasEncodedLt = content.contains("&lt;");
        boolean hasEncodedGt = content.contains("&gt;");
        boolean hasEncodedAmp = content.contains("&amp;");

        if (hasLessThan && hasGreaterThan && hasAmpersand && hasDoubleAmpersand) {
            System.out.println("✓ PASS: Entities decoded correctly");
        } else {
            System.out.println("❌ FAIL: Entities not decoded");
        }

        if (hasEncodedLt || hasEncodedGt || hasEncodedAmp) {
            System.out.println("❌ FAIL: Found encoded entities (should be decoded)");
        }

        System.out.println();
    }

    private static void testSpecialCharacters() {
        System.out.println("TEST 4: Special Characters in Command");
        System.out.println("------------------------------------------");

        String toolCallXml = """
            <tool_use name="Bash">
            <parameter name="command">cd /tmp &amp;&amp; ls -la</parameter>
            </tool_use>
            """;

        var result = ToolCallParser.parse(toolCallXml);
        ToolUse toolUse = result.getToolUses().get(0);
        String command = (String) toolUse.getInput().get("command");

        System.out.println("Extracted command: " + command);
        System.out.println();

        if ("cd /tmp && ls -la".equals(command)) {
            System.out.println("✓ PASS: Command with && decoded correctly");
        } else {
            System.out.println("❌ FAIL: Command not decoded correctly");
            System.out.println("Expected: cd /tmp && ls -la");
            System.out.println("Got: " + command);
        }

        System.out.println();
    }

    private static void testJsonArrayParameter() {
        System.out.println("TEST 5: JSON Array Parameter (PRP-23)");
        System.out.println("------------------------------------------");

        String toolCallXml = """
            I'll help you create a simple JavaFX "Hello World" project. Let me start by creating a todo list to track this task.

            <tool_uses>
            <tool_use name="TodoWrite">
            <parameter name="todos">[
              {
                "content": "Create project directory structure",
                "activeForm": "Creating project directory structure",
                "status": "pending"
              },
              {
                "content": "Create Maven pom.xml with JavaFX dependencies",
                "activeForm": "Creating Maven pom.xml with JavaFX dependencies",
                "status": "pending"
              },
              {
                "content": "Create HelloWorld JavaFX application class",
                "activeForm": "Creating HelloWorld JavaFX application class",
                "status": "pending"
              }
            ]</parameter>
            </tool_use>
            </tool_uses>
            """;

        var result = ToolCallParser.parse(toolCallXml);

        if (!result.hasToolCalls()) {
            System.out.println("❌ FAIL: No tool calls detected!");
            return;
        }

        ToolUse toolUse = result.getToolUses().get(0);
        Object todosParam = toolUse.getInput().get("todos");

        System.out.println("Parameter type: " + todosParam.getClass().getName());
        System.out.println();

        // Check if it's a List (not a String)
        if (todosParam instanceof java.util.List) {
            System.out.println("✓ PASS: todos parameter is a List (not String)");

            @SuppressWarnings("unchecked")
            var todosList = (java.util.List<Map<String, Object>>) todosParam;
            System.out.println("  List size: " + todosList.size());

            if (todosList.size() == 3) {
                System.out.println("✓ PASS: Correct number of todo items (3)");
            } else {
                System.out.println("❌ FAIL: Expected 3 todo items, got " + todosList.size());
            }

            // Check first todo item
            var firstTodo = todosList.get(0);
            String content = (String) firstTodo.get("content");
            String activeForm = (String) firstTodo.get("activeForm");
            String status = (String) firstTodo.get("status");

            System.out.println("  First todo:");
            System.out.println("    content: " + content);
            System.out.println("    activeForm: " + activeForm);
            System.out.println("    status: " + status);

            if ("Create project directory structure".equals(content) &&
                "Creating project directory structure".equals(activeForm) &&
                "pending".equals(status)) {
                System.out.println("✓ PASS: First todo item parsed correctly");
            } else {
                System.out.println("❌ FAIL: First todo item not parsed correctly");
            }

        } else if (todosParam instanceof String) {
            System.out.println("❌ FAIL: todos parameter is still a String (should be List)");
            System.out.println("String value: " + todosParam);
        } else {
            System.out.println("❌ FAIL: todos parameter is unexpected type: " + todosParam.getClass().getName());
        }

        System.out.println();
    }
}

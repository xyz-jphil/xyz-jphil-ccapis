# Pitfall: JSoup text() vs wholeText() - Preserving Line Breaks in XML Parsing

## Problem Description

When parsing XML responses containing tool calls with JSoup, using `Element.text()` or `TextNode.text()` collapses all whitespace (including newlines) into single spaces. This causes multi-line content like source code to appear as a single line, breaking code formatting and causing server termination issues.

## Symptoms

- Multi-line source code in tool parameters appears as a single line
- Code formatting is destroyed with all newlines removed
- Server processes may terminate due to malformed responses
- Example: `"line1\nline2\nline3"` becomes `"line1 line2 line3"`

## Root Cause

JSoup's `text()` method is designed for extracting human-readable text and normalizes whitespace:
- Collapses consecutive whitespace characters to single spaces
- Removes newline characters (`\n`)
- Suitable for display text, NOT for preserving formatting

## Solution

Use `wholeText()` instead of `text()` when extracting content that needs to preserve formatting:

```java
// ❌ WRONG - Collapses whitespace
String paramValue = paramElem.text();

// ✅ CORRECT - Preserves line breaks and formatting
String paramValue = paramElem.wholeText();
```

### Additional Requirements

1. **Disable pretty printing** to preserve whitespace during parsing:
   ```java
   var jsoupDoc = Jsoup.parseBodyFragment(responseText);
   jsoupDoc.outputSettings().prettyPrint(false); // Essential!
   ```

2. **Use wholeText() for all content extraction**:
   - Tool parameter values
   - Text before/after tool calls
   - Any content that may contain code or structured text

## When to Use Each Method

| Method | Use Case | Example |
|--------|----------|---------|
| `text()` | Display text, UI labels, summaries | Button labels, error messages |
| `wholeText()` | Source code, JSON, formatted text, any multi-line content | Tool parameters, code snippets, configuration |

## Fixed Files

- `ToolCallParser.java:117` - Parameter extraction now uses `wholeText()`
- `ToolCallParser.java:96` - Text before tool calls uses `wholeText()`
- `ToolCallParser.java:101` - Element text extraction uses `wholeText()`
- `ToolCallParser.java:62` - Added `prettyPrint(false)` setting

## Related Issues

- PRP-14: Server termination due to single-line source code
- See class javadoc in `ToolCallParser.java` for detailed explanation

## Testing

To verify the fix:
1. Send a tool call with multi-line source code as a parameter
2. Check server logs to ensure newlines are preserved
3. Verify the server processes the request without termination

## Date Identified

2025-11-09

## References

- JSoup documentation: https://jsoup.org/apidocs/org/jsoup/nodes/Element.html#wholeText()
- ToolCallParser.java class documentation

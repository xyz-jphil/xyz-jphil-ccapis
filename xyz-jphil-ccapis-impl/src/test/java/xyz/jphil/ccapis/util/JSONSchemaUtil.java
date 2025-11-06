package xyz.jphil.ccapis.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;

/**
 * Utility class for JSON schema generation and sample management
 */
public class JSONSchemaUtil {
    
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * Generate a JSON schema from a sample JSON string
     */
    public static String generateSchemaFromSample(String sampleJson) throws IOException {
        JsonNode sampleNode = objectMapper.readTree(sampleJson);
        return generateSchemaRecursive(sampleNode, "Root");
    }
    
    private static String generateSchemaRecursive(JsonNode node, String name) {
        StringBuilder schema = new StringBuilder();
        schema.append("{\n");
        schema.append("  \"$schema\": \"http://json-schema.org/draft-07/schema#\",\n");
        schema.append("  \"type\": \"").append(getJsonType(node)).append("\",\n");
        
        if (node.isObject()) {
            schema.append("  \"properties\": {\n");
            boolean first = true;
            
            // Add all field names as required
            var fieldNames = new ArrayList<String>();
            node.fieldNames().forEachRemaining(e->fieldNames.add(e));

            for(String fieldName:fieldNames) {
                if (!first) schema.append(",\n");
                first = false;
                schema.append("    \"").append(fieldName).append("\": ");
                JsonNode childNode = node.get(fieldName);
                String childSchema = generateSchemaRecursive(childNode, fieldName).replaceAll("(?m)^", "      ");
                schema.append(childSchema.substring(7)); // Remove the first 7 characters which is "{\n      "
            }
            schema.append("\n  },\n");
            schema.append("  \"required\": [");
            
            boolean firstField = true;
            for (String fieldName : fieldNames) {
                if (!firstField) schema.append(",");
                firstField = false;
                schema.append("\"").append(fieldName).append("\"");
            }
            schema.append("]\n");
        } else if (node.isArray()) {
            if (node.size() > 0) {
                JsonNode element = node.get(0);
                schema.append("  \"items\": {\n");
                String itemSchema = generateSchemaRecursive(element, "item").replaceAll("(?m)^", "    ");
                schema.append(itemSchema.substring(5)); // Remove the first 5 characters which is "{\n    "
                schema.append("\n  }\n");
            } else {
                schema.append("  \"items\": {}\n");
            }
        }
        
        schema.append("}");
        return schema.toString();
    }
    
    private static String getJsonType(JsonNode node) {
        if (node.isObject()) return "object";
        if (node.isArray()) return "array";
        if (node.isNumber()) return "number";
        if (node.isBoolean()) return "boolean";
        if (node.isTextual()) return "string";
        return "null";
    }
    
    /**
     * Save a JSON sample to a file in the test-scripts directory
     */
    public static void saveJSONSample(String filename, String jsonContent) throws IOException {
        Path path = Paths.get("test-scripts", filename);
        Files.createDirectories(path.getParent());
        Files.write(path, jsonContent.getBytes());
        System.out.println("JSON sample saved to: " + path.toAbsolutePath());
    }
    
    /**
     * Load a JSON sample from a file
     */
    public static String loadJSONSample(String filename) throws IOException {
        Path path = Paths.get("test-scripts", filename);
        return Files.readString(path);
    }
    
    public static void main2(String[] args) {
        // Example usage
        String sampleJson = "{\n" +
                "  \"five_hour\": {\n" +
                "    \"utilization\": 0,\n" +
                "    \"resets_at\": null\n" +
                "  },\n" +
                "  \"seven_day\": {\n" +
                "    \"utilization\": 60,\n" +
                "    \"resets_at\": \"2025-10-22T15:00:00.344232+00:00\"\n" +
                "  }\n" +
                "}";
        
        try {
            String schema = generateSchemaFromSample(sampleJson);
            System.out.println("Generated Schema:");
            System.out.println(schema);
            
            saveJSONSample("usage_sample.json", sampleJson);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
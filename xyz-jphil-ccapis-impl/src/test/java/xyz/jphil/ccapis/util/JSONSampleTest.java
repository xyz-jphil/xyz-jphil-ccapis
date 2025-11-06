package xyz.jphil.ccapis.util;

import org.junit.Test;
import xyz.jphil.ccapis.CCAPIExperimentTest;
import xyz.jphil.ccapis.util.JSONSchemaUtil;

import java.io.IOException;

import static org.junit.Assert.assertNotNull;

/**
 * Test class to ensure all JSON samples are properly saved
 */
public class JSONSampleTest {
    
    //@Test
    public void testAllJSONSamplesExist() throws IOException {
        // Verify that all expected JSON samples exist in the test-scripts directory
        
        // Usage API response sample
        String usageSample = JSONSchemaUtil.loadJSONSample("usage_api_response.json");
        assertNotNull("Usage API response sample should exist", usageSample);
        System.out.println("Loaded usage API sample, length: " + usageSample.length());
        
        // Conversations API response sample
        String conversationsSample = JSONSchemaUtil.loadJSONSample("conversations_api_response.json");
        assertNotNull("Conversations API response sample should exist", conversationsSample);
        System.out.println("Loaded conversations API sample, length: " + conversationsSample.length());
        
        // Create chat API response sample
        String createChatSample = JSONSchemaUtil.loadJSONSample("create_chat_api_response.json");
        assertNotNull("Create chat API response sample should exist", createChatSample);
        System.out.println("Loaded create chat API sample, length: " + createChatSample.length());
        
        // Attachment upload response sample
        String attachmentSample = JSONSchemaUtil.loadJSONSample("attachment_upload_response.json");
        // This might not exist if the attachment test wasn't run, so we'll make it optional for now
        if (attachmentSample != null) {
            System.out.println("Loaded attachment upload API sample, length: " + attachmentSample.length());
        } else {
            System.out.println("Attachment upload sample not found (this is OK if attachment test wasn't run)");
        }
        
        // Generate schemas for each sample
        String usageSchema = JSONSchemaUtil.generateSchemaFromSample(usageSample);
        JSONSchemaUtil.saveJSONSample("usage_api_schema.json", usageSchema);
        
        String conversationsSchema = JSONSchemaUtil.generateSchemaFromSample(conversationsSample);
        JSONSchemaUtil.saveJSONSample("conversations_api_schema.json", conversationsSchema);
        
        String createChatSchema = JSONSchemaUtil.generateSchemaFromSample(createChatSample);
        JSONSchemaUtil.saveJSONSample("create_chat_api_schema.json", createChatSchema);
        
        if (attachmentSample != null) {
            String attachmentSchema = JSONSchemaUtil.generateSchemaFromSample(attachmentSample);
            JSONSchemaUtil.saveJSONSample("attachment_upload_schema.json", attachmentSchema);
        }
        
        System.out.println("All JSON samples verified and schemas generated");
    }
}
package xyz.jphil.ccapis.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * Wrapper POJO for CCAPI conversations list response
 * This is an array of Conversation objects, so this class is mainly for documentation
 * The actual response is a List<Conversation> that gets deserialized directly
 */
@Data
@Accessors(fluent = true, chain = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ConversationsData {
    // This is just a wrapper class for documentation
    // The actual API returns an array of Conversation objects
}
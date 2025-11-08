package xyz.jphil.ccapis.conversation_downloader;

public class ConversationSemanticXHtml {
    public static final String
            PUBROOT = "https://xyz-jphil.github.io/ccapis-static/",
            CSS     = PUBROOT + "styles.css",
            JS      = PUBROOT + "scripts.js",
            DEFINITION = "Browser-renderable, CCAPI Conversation format optimized for AI comprehension. " +
                        "Structure: conversation > metadata + messages > (human|assistant) elements. " +
                        "Content is raw markdown text preserved as-is for AI parsing. " +
                        "Browser rendering uses external CSS/JS for markdown-to-HTML conversion.",
            TRIVIA = "This XHTML is literally as good as plain markdown - it can be simply read without special HTML parsing. " +
                    "We are merely packaging it as XHTML to additionally support browser rendering. " +
                    "The raw text content remains unescaped markdown for easy AI comprehension. " +
                    "Decoupled presentation layer allows rendering upgrades without document modification.",
            TYPE = "CCAPI Conversation Semantic XHTML5"
    ;
}

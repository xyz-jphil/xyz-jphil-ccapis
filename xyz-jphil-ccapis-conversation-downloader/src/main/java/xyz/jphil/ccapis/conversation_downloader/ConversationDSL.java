package xyz.jphil.ccapis.conversation_downloader;

import luvml.HtmlAttribute;
import luvml.element.SemanticBlockContainerElement;
import luvml.element.SemanticElementTagNameClassNameMapping.CamelCase_E;
import luvx.Frag_I;

/**
 * DSL factory methods for Conversation XHTML elements and attributes
 */
public class ConversationDSL {

    // Custom attribute factory methods

    public static HtmlAttribute serialNum(int value) {
        return new HtmlAttribute("serialNum", String.valueOf(value));
    }

    public static HtmlAttribute uuid(String value) {
        return new HtmlAttribute("uuid", value);
    }

    public static HtmlAttribute created_at(String value) {
        return new HtmlAttribute("created_at", value);
    }

    // Custom element factory methods

    public static Human_E human(Frag_I<?>... fragments) {
        return new Human_E(fragments);
    }

    public static Assistant_E assistant(Frag_I<?>... fragments) {
        return new Assistant_E(fragments);
    }

    public static Metadata_E metadata(Frag_I<?>... fragments) {
        return new Metadata_E(fragments);
    }

    public static Messages_E messages(Frag_I<?>... fragments) {
        return new Messages_E(fragments);
    }

    // Custom element classes

    public static class Human_E extends SemanticBlockContainerElement<Human_E> implements CamelCase_E {
        public Human_E(Frag_I<?>... fragments) {
            super(Human_E.class);
            ____(fragments);
        }
    }

    public static class Assistant_E extends SemanticBlockContainerElement<Assistant_E> implements CamelCase_E {
        public Assistant_E(Frag_I<?>... fragments) {
            super(Assistant_E.class);
            ____(fragments);
        }
    }

    public static class Metadata_E extends SemanticBlockContainerElement<Metadata_E> implements CamelCase_E {
        public Metadata_E(Frag_I<?>... fragments) {
            super(Metadata_E.class);
            ____(fragments);
        }
    }

    public static class Messages_E extends SemanticBlockContainerElement<Messages_E> implements CamelCase_E {
        public Messages_E(Frag_I<?>... fragments) {
            super(Messages_E.class);
            ____(fragments);
        }
    }
}

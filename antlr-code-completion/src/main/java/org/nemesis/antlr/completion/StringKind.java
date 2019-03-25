package org.nemesis.antlr.completion;

/**
 * Kinds of text renderings of completion items which are passed to the
 * {@link Stringifier}, if your {@link CompletionBuilder} uses one.
 *
 * @author Tim Boudreau
 */
public enum StringKind {
    /**
     * Text used to sort the item when in alphabetic sort mode.
     */
    SORT_TEXT,
    /**
     * The display name used in code completion - your stringifier must not
     * return null for this.
     */
    DISPLAY_NAME,
    /**
     * If present, will render a string next to the display name of the item,
     * with the text dimmed a bit, separated by a space.
     */
    DISPLAY_DIFFERENTIATOR,
    /**
     * Text used for finding the longest common prefix when completing a
     * partially typed word.
     */
    INSERT_PREFIX,
    /**
     * Get the actual text that should be inserted in the document. This must
     * not return null.
     */
    TEXT_TO_INSERT
}

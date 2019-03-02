package org.nemesis.antlr.spi.language.highlighting.semantic;

/**
 * Determines what is the trigger for a highlighter's highlights to be
 * recomputed .
 *
 * @author Tim Boudreau
 */
public enum HighlightRefreshTrigger {

    /**
     * Update when the document is edited, after a delay.
     */
    DOCUMENT_CHANGED(150),
    /**
     * Update when the caret position changes, for "mark occurences" style
     * highlighting.
     */
    CARET_MOVED(1200);

    private final int refreshDelay;

    HighlightRefreshTrigger(int refreshDelay) {
        this.refreshDelay = refreshDelay;
    }

    public int refreshDelay() {
        return refreshDelay;
    }
}

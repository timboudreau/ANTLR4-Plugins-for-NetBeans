package org.nemesis.antlrformatting.api;

import java.util.Objects;

/**
 * Contains the reformatted text and the offsets thereof - if requested to
 * reformat a range where the range starts in the middle of a token,
 * reformatting will actually begin with the first complete token after the
 * requested start index, and end at the last complete token within the range.
 * So the actual character ranges returned may differ from those requested.
 *
 * @author Tim Boudreau
 */
public final class FormattingResult {

    private final int startOffset;
    private final int endOffset;
    private final String text;

    FormattingResult(int startOffset, int endOffset, String text) {
        this.startOffset = startOffset;
        this.endOffset = endOffset;
        this.text = text;
    }

    /**
     * If true, no formatting was performed or the result was zero length.
     *
     * @return True if nothing here
     */
    public boolean isEmpty() {
        return startOffset == endOffset || startOffset < 0 || endOffset < 0;
    }

    /**
     * The start offset in <i>the original text</i> of the area that was
     * reformatted.
     *
     * @return A start offset
     */
    public int startOffset() {
        return startOffset;
    }

    /**
     * The end offset (exclusive) <i>in the original text</i> of the area that
     * was reformatted.
     *
     * @return An end offset
     */
    public int endOffset() {
        return endOffset;
    }

    /**
     * The reformatted text.
     *
     * @return Some text
     */
    public String text() {
        return text;
    }

    public String toString() {
        return "FormattingResult{" + startOffset + ":" + endOffset
                + " '" + text + "'";
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 37 * hash + this.startOffset;
        hash = 37 * hash + this.endOffset;
        hash = 37 * hash + Objects.hashCode(this.text);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final FormattingResult other = (FormattingResult) obj;
        if (this.startOffset != other.startOffset) {
            return false;
        }
        if (this.endOffset != other.endOffset) {
            return false;
        }
        return Objects.equals(this.text, other.text);
    }
}

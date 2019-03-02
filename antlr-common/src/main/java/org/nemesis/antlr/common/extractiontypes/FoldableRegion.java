package org.nemesis.antlr.common.extractiontypes;

import java.util.Objects;

/**
 *
 * @author Tim Boudreau
 */
public final class FoldableRegion {

    public final FoldableKind kind;
    public final String text;
    private static final int MAX_LENGTH = 15;

    private FoldableRegion(String text, FoldableKind kind) {
        this.text = text;
        this.kind = kind;
    }

    @Override
    public String toString() {
        return kind + "(" + text + ")";
    }

    public boolean shouldFold(String text) {
        return text != null && text.length() > MAX_LENGTH - 3;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 53 * hash + Objects.hashCode(this.kind);
        hash = 53 * hash + Objects.hashCode(this.text);
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
        final FoldableRegion other = (FoldableRegion) obj;
        if (!Objects.equals(this.text, other.text)) {
            return false;
        }
        if (this.kind != other.kind) {
            return false;
        }
        return true;
    }

    public static enum FoldableKind {
        COMMENT,
        DOC_COMMENT,
        RULE,
        ACTION;

        public FoldableRegion createFor(String text) {
            return new FoldableRegion(truncate(text), this);
        }

        private String truncate(String text) {
            if (text.length() > MAX_LENGTH - 3) {
                text = text.substring(MAX_LENGTH - 3) + "...";
            }
            return text;
        }
    }
}

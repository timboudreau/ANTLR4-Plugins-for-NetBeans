/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nemesis.antlr.v4.netbeans.v8.grammar.code.summary;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.text.AttributeSet;
import org.netbeans.api.editor.mimelookup.MimeLookup;
import org.netbeans.api.editor.mimelookup.MimePath;
import org.netbeans.api.editor.settings.FontColorSettings;
import org.netbeans.modules.csl.api.OffsetRange;

/**
 *
 * @author Tim Boudreau
 */
public final class EbnfElement implements RuleComponent {

    private final int startPosition;
    private final int endPosition;
    private final byte flags;

    public EbnfElement(int startPosition, int endPosition, boolean atLeastOne, boolean greedy, boolean isQuestionMark) {
        this.startPosition = startPosition;
        this.endPosition = endPosition;
        byte fl = 0;
        if (atLeastOne) {
            fl |= 1;
        }
        if (greedy) {
            fl |= 2;
        }
        if (isQuestionMark) {
            fl |= 4;
        }
        flags = fl;
    }

    public boolean isQuestionMark() {
        return (flags & 4) == 0;
    }

    public OffsetRange toOffsetRange() {
        return new OffsetRange(startPosition, endPosition);
    }

    public boolean isGreedy() {
        return (flags & 2) != 0;
    }

    public boolean isAtLeastOne() {
        return (flags & 1) != 0;
    }

    public int getStartOffset() {
        return startPosition;
    }

    @Override
    public String toString() {
        return ebnfToken() + "@" + startPosition + ":" + endPosition;
    }

    public int getEndOffset() {
        return endPosition;
    }

    public String ebnfToken() {
        if (isAtLeastOne()) {
            if (isGreedy()) {
                return "+";
            } else {
                return "+?";
            }
        } else {
            if (isGreedy()) {
                return "*";
            } else {
                return "*?";
            }
        }
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 97 * hash + this.startPosition;
        hash = 97 * hash + this.endPosition;
        hash = 97 * hash + this.flags;
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
        final EbnfElement other = (EbnfElement) obj;
        if (this.startPosition != other.startPosition) {
            return false;
        }
        if (this.endPosition != other.endPosition) {
            return false;
        }
        if (this.flags != other.flags) {
            return false;
        }
        return true;
    }

    private static final String[] COLORING_NAMES = {"plus_block", "wildcard_block"};
    public String coloringName() {
        return isAtLeastOne() ? COLORING_NAMES[0] : COLORING_NAMES[1];
    }

    public static Map<String, AttributeSet> colorings() {
        // Do not cache - user can edit these
        MimePath mimePath = MimePath.parse("text/x-g4");
        FontColorSettings fcs = MimeLookup.getLookup(mimePath).lookup(FontColorSettings.class);
        Map<String, AttributeSet> result = new HashMap<>(3);
        for (String kind : COLORING_NAMES) {
            AttributeSet attrs = fcs.getTokenFontColors(kind);

            assert attrs != null : kind + " missing from fonts and colors for text/x-g4";
            result.put(kind, attrs);
        }
        return result;
    }

    public boolean contains(EbnfElement other) {
        return startPosition >= other.startPosition && endPosition <= other.endPosition;
    }

    public boolean contains(int position) {
        return position >= startPosition && position < endPosition;
    }

    public boolean overlaps(EbnfElement other) {
        if (contains(other) || other.contains(this)) {
            return true;
        }
        if (contains(other.startPosition) || other.contains(startPosition)) {
            return true;
        }
        if (contains(other.endPosition) || other.contains(endPosition)) {
            return true;
        }
        return false;
    }

    public static List<EbnfElement> coalesce(List<EbnfElement> all) {
        List<EbnfElement> result = new ArrayList<>(all.size());
        try (EbnfListCoalescer coalescer = new EbnfListCoalescer(result)) {
            coalescer.addAll(all);
        }
        return result;
    }

    public int length() {
        return endPosition - startPosition;
    }

    private static class EbnfListCoalescer implements AutoCloseable {

        private final List<EbnfElement> all;
        private EbnfElement last;

        public EbnfListCoalescer(List<EbnfElement> all) {
            this.all = all;
        }

        void addAll(List<EbnfElement> items) {
            for (EbnfElement e : items) {
                add(e);
            }
        }

        private void replaceLast(EbnfElement el) {
            all.set(all.size() - 1, el);
        }

        void add(EbnfElement element) {
            if (last != null) {
                if (last.equals(element)) {
                    return;
                } else if (last.contains(element)) {
                    return;
                } else if (element.contains(last)) {
                    replaceLast(element);
                }
                all.add(last);
            }
            last = element;
        }

        @Override
        public void close() {
            if (last != null) {
                all.add(last);
            }
        }
    }
}

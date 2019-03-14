package org.nemesis.antlrformatting.api;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Manages the set of alterations to whitespace to be appended or prepended to
 * the next / previous token; in particular, allows for prepend rules to set up
 * one set of modifications, and append rules to set up another, and then to
 * coalesce these into a single modification which subsumes both, choosing the
 * larger number of indents and newlines from each.
 *
 * @author Tim Boudreau
 */
final class WhitespaceState {

    private final Modification SPACE;
    private final Modification SINGLE_INDENT;
    private final Modification DOUBLE_INDENT;
    private final Modification NEWLINE;
    private final Modification DOUBLE_NEWLINE;
    MetaModification a = new MetaModification();
    MetaModification b = new MetaModification();
    private boolean usingA = true;
    private final int indentDepth;
    private final WhitespaceStringCache cache;

    WhitespaceState(int indentDepth, WhitespaceStringCache cache) {
        this.indentDepth = indentDepth;
        this.cache = cache;
        SPACE = new IndentBy(1, false, cache);
        SINGLE_INDENT = new IndentBy(1, cache);
        DOUBLE_INDENT = new IndentBy(2, cache);
        NEWLINE = new Newlines(1, cache);
        DOUBLE_NEWLINE = new Newlines(2, cache);
    }

    boolean isEmpty() {
        return a.isEmpty() && b.isEmpty();
    }

    WhitespaceState clear() {
        a.clear();
        b.clear();
        return this;
    }

    private MetaModification curr() {
        return usingA ? a : b;
    }

    public int getLineOffset(int origOffset) {
        Modification result = a.copy().coalesce(b.copy(), indentDepth);
        int[] val = new int[]{origOffset};
        result.updateLineOffset(val, indentDepth);
        return val[0];
    }

    String description() {
        return a.copy().coalesce(b.copy(), indentDepth).toString();
    }

    Supplier<String> descriptionSupplier() {
        Modification copy = a.copy().coalesce(b.copy(), indentDepth);
        return new Supplier<String>() {
            @Override
            public String get() {
                return copy.toString();
            }

            @Override
            public String toString() {
                return get();
            }
        };
    }

    WhitespaceState space() {
        curr().add(SPACE, indentDepth);
        return this;
    }

    WhitespaceState indent() {
        curr().add(SINGLE_INDENT, indentDepth);
        return this;
    }

    WhitespaceState doubleIndent() {
        curr().add(DOUBLE_INDENT, indentDepth);
        return this;
    }

    WhitespaceState indentBy(int amt) {
        curr().add(new IndentBy(amt, cache), indentDepth);
        return this;
    }

    WhitespaceState spaces(int amt) {
        curr().add(new IndentBy(amt, false, cache), indentDepth);
        return this;
    }

    WhitespaceState newlines(int amt) {
        switch (amt) {
            case 0:
                return this;
            case 1:
                return newline();
            case 2:
                return doubleNewline();
            default:
                curr().add(new Newlines(amt, cache), indentDepth);
        }
        return this;
    }

    WhitespaceState newline() {
        curr().add(NEWLINE, indentDepth);
        return this;
    }

    WhitespaceState doubleNewline() {
        curr().add(DOUBLE_NEWLINE, indentDepth);
        return this;
    }

    WhitespaceState flip() {
        usingA = !usingA;
        curr().clear();
        return this;
    }

    public void apply(StringBuilder sb, int[] lineOffsetDest) {
        MetaModification toApply = get();
        toApply.apply(sb, indentDepth, lineOffsetDest);
    }

    public String getString() {
        StringBuilder sb = new StringBuilder();
        apply(sb, new int[0]);
        return sb.toString();
    }

    MetaModification get() {
        MetaModification result = a.coalesce(b, indentDepth);
        if (result == a || result == b) {
            result = result.copy();
        }
        a.clear();
        b.clear();
        usingA = true;
        return result;
    }

    int charCount() {
        return a.copy().coalesce(b.copy(), indentDepth).charCount(indentDepth);
    }

    @Override
    public String toString() {
        return a + " (" + b + ")";
    }

    interface Modification /* extends Comparable<Modification> */ {

        int order(int indentChars);

        void apply(StringBuilder sb, int indentChars, int[] lineOffsetDest);

        void updateLineOffset(int[] origOffset, int indentChars);

        int charCount(int indentChars);

        default Modification copy() {
            return this;
        }

//        @Override
//        public default int compareTo(Modification o) {
//            int mine = order(4);
//            int theirs = o.order(4);
//            return mine > theirs ? -1 : mine == theirs ? 0 : 1;
//        }
//
        default boolean isSameType(Modification other) {
            return getClass() == other.getClass();
        }

        static void sort(int indentChars, List<Modification> modifications) {
            Collections.sort(modifications, (a, b) -> {
                int oa = a.order(indentChars);
                int ob = b.order(indentChars);
                return oa > ob ? -1 : oa == ob ? 0 : 1;
            });
        }
    }

    private static class MetaModification implements Modification, Iterable<Modification> {

        private final List<Modification> modifications;

        MetaModification() {
            modifications = new LinkedList<>();
        }

        MetaModification(List<Modification> all, boolean hasNewlines) {
            modifications = new LinkedList<>();
            for (Modification m : all) {
                modifications.add(m.copy());
            }
            this.hasNewlines = hasNewlines;
        }

        public int charCount(int indentChars) {
            int result = 0;
            for (Modification m : modifications) {
                result += m.charCount(indentChars);
            }
            return result;
        }

        @Override
        public Iterator<Modification> iterator() {
            return modifications.iterator();
        }

        @Override
        public MetaModification copy() {
            MetaModification result = new MetaModification(modifications, hasNewlines);
            result.hasNewlines = hasNewlines;
            return result;
        }

        void clear() {
            modifications.clear();
            hasNewlines = false;
        }

        boolean hasNewlines;

        void add(Modification mod, int indentDepth) {
            boolean added = false;
            boolean shouldAdd = true;
            for (int i = 0; i < modifications.size(); i++) {
                Modification m = modifications.get(i);
                if (m.isSameType(mod)) {
                    Modification best = pickBest(m, mod, indentDepth);
                    if (best == mod) {
                        modifications.set(i, mod);
                        added = true;
                        shouldAdd = false;
                        break;
                    } else {
                        shouldAdd = false;
                    }
                }
            }
            if (shouldAdd) {
                modifications.add(mod);
                added = true;
            }
            if (added) {
                hasNewlines |= mod instanceof Newlines;
            }
            Modification.sort(indentDepth, modifications);
        }

        boolean isEmpty() {
            return modifications.isEmpty();
        }

        int size() {
            return modifications.size();
        }

        Modification get(int i) {
            return modifications.get(i);
        }

        private MetaModification pruneSpaces() {
            if (hasNewlines) {
                for (Iterator<Modification> it = modifications.iterator(); it.hasNext();) {
                    Modification m = it.next();
                    if (m instanceof IndentBy && ((IndentBy) m).isSpace()) {
                        it.remove();
                    }
                }
            }
            return this;
        }

        @SuppressWarnings("null")
        MetaModification coalesce(MetaModification other, int indentChars) {
            if (other == this || other.equals(this)) {
                return this.pruneSpaces();
            } else if (other.isEmpty() && !this.isEmpty()) {
                return this.pruneSpaces();
            } else if (this.isEmpty() && !other.isEmpty()) {
                return other.pruneSpaces();
            } else if (this.isEmpty() && other.isEmpty()) {
                return this.pruneSpaces();
            }
            boolean newlines = hasNewlines || other.hasNewlines;
            List<Modification> nue = new ArrayList<>(modifications);
            nue.addAll(other.modifications);
            Modification.sort(indentChars, nue);
            Set<Class<?>> types = new HashSet<>();
            for (Iterator<Modification> it = nue.iterator(); it.hasNext();) {
                Modification m = it.next();
                if (newlines && m instanceof IndentBy && ((IndentBy) m).isSpace()) {
                    continue;
                }
                if (types.contains(m.getClass())) {
                    it.remove();
                } else {
                    types.add(m.getClass());
                }
            }
            MetaModification result = new MetaModification(nue, newlines);
            return result;
        }

        private Modification pickBest(Modification a, Modification b, int indentChars) {
            List<Modification> l = new ArrayList<>(Arrays.asList(a, b));
            Modification.sort(indentChars, l);
            return l.get(0);
        }

        @Override
        public int order(int indentChars) {
            return 0;
        }

        @Override
        public void apply(StringBuilder sb, int indentChars, int[] lineOffsetDest) {
            modifications.forEach((mod) -> {
                mod.apply(sb, indentChars, lineOffsetDest);
            });
        }

        @Override
        public String toString() {
            if (modifications.isEmpty()) {
                return "empty";
            }
            StringBuilder sb = new StringBuilder();
            for (Iterator<Modification> it = modifications.iterator(); it.hasNext();) {
                Modification mod = it.next();
                sb.append(mod);
                if (it.hasNext()) {
                    sb.append(":");
                }
            }
            return sb.toString();
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 41 * hash + Objects.hashCode(this.modifications);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            } else if (obj == null) {
                return false;
            } else if (obj instanceof MetaModification) {
                return false;
            }
            final MetaModification other = (MetaModification) obj;
            return this.modifications.equals(other.modifications);
        }

        @Override
        public void updateLineOffset(int[] origOffset, int indentChars) {
            MetaModification mm = hasNewlines ? new MetaModification(modifications, true).pruneSpaces() : this;
            for (Modification m : mm) {
                m.updateLineOffset(origOffset, indentChars);
            }
        }
    }

    private static class IndentBy implements Modification {

        private final int depth;
        private final boolean isTabStops;
        private final WhitespaceStringCache cache;

        public IndentBy(int depth, WhitespaceStringCache cache) {
            this(depth, true, cache);
        }

        public IndentBy(int depth, boolean tabStops, WhitespaceStringCache cache) {
            this.depth = depth;
            this.isTabStops = tabStops;
            this.cache = cache;
        }

        boolean isSpace() {
            return !isTabStops && depth == 1;
        }

        @Override
        public int order(int indentChars) {
            return isTabStops ? indentChars * depth : depth;
        }

        @Override
        public String toString() {
            if (!isTabStops) {
                switch (depth) {
                    case 1:
                        return "space";
                    default:
                        return "spaces-" + depth;
                }
            }
            return "indent-" + depth;
        }

        public int charCount(int indentChars) {
            return depth * (isTabStops ? indentChars : 1);
        }

        @Override
        public void apply(StringBuilder sb, int indentChars, int[] lineOffsetDest) {
            if (!isTabStops && depth == 1) {
                sb.append(' ');
                return;
            }
            int count = depth * (isTabStops ? indentChars : 1);
            lineOffsetDest[0] += count;
            sb.append(cache.spaces(count));
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 79 * hash + this.depth;
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            } else if (obj == null) {
                return false;
            } else if (!(obj instanceof IndentBy)) {
                return false;
            }
            final IndentBy other = (IndentBy) obj;
            return this.depth == other.depth;
        }

        @Override
        public void updateLineOffset(int[] origOffset, int indentChars) {
            if (isTabStops) {
                origOffset[0] += indentChars * depth;
            } else {
                origOffset[0] += depth;
            }
        }
    }

    private static class Newlines implements Modification {

        private final int count;
        private final WhitespaceStringCache cache;

        public Newlines(int count, WhitespaceStringCache cache) {
            this.count = count;
            this.cache = cache;
        }

        public int charCount(int indentDepth) {
            return count;
        }

        @Override
        public int order(int indentDepth) {
            return count * 1000;
        }

        @Override
        public void apply(StringBuilder sb, int indentChars, int[] lineOffsetDest) {
            sb.append(cache.newlines(count));
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 67 * hash + this.count;
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            } else if (obj == null) {
                return false;
            } else if (!(obj instanceof Newlines)) {
                return false;
            }
            final Newlines other = (Newlines) obj;
            return this.count == other.count;
        }

        @Override
        public String toString() {
            switch (count) {
                case 0:
                    return "do-nothing";
                case 1:
                    return "newline";
                case 2:
                    return "double-newline";
                default:
                    return count + "-newline";
            }
        }

        @Override
        public void updateLineOffset(int[] origOffset, int indentChars) {
            origOffset[0] = 0;
        }
    }
}

package org.nemesis.antlr.v4.netbeans.v8.grammar.code.highlighting;

import java.awt.Color;
import java.util.Enumeration;
import java.util.Set;
import java.util.TreeSet;
import javax.swing.text.AttributeSet;
import javax.swing.text.Document;
import javax.swing.text.StyleConstants;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.NBANTLRv4Parser.ANTLRv4ParserResult;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.ANTLRv4SemanticParser;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.AntlrExtractor;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.extraction.Extraction;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.data.SemanticRegions;
import org.netbeans.api.editor.mimelookup.MimeLookup;
import org.netbeans.api.editor.mimelookup.MimePath;
import org.netbeans.api.editor.settings.FontColorSettings;
import org.openide.util.Enumerations;
import org.openide.util.Exceptions;

/**
 *
 * @author Tim Boudreau
 */
final class AntlrNestingDepthHighlighter extends AbstractAntlrHighlighter.DocumentOriented<Void, ANTLRv4ParserResult, ANTLRv4SemanticParser> {

    public AntlrNestingDepthHighlighter(Document doc) {
        super(doc, ANTLRv4ParserResult.class, GET_SEMANTICS);
    }

    Set<HighlightElement> lastElements;

    public void refresh(Document doc, Void argument, ANTLRv4SemanticParser semantics, ANTLRv4ParserResult result) {
        Extraction ext = semantics.extraction();
        if (ext != null) {
            SemanticRegions<Void> blocks = ext.regions(AntlrExtractor.BLOCKS);
            Set<HighlightElement> els = new TreeSet<>();
            for (SemanticRegions.SemanticRegion<Void> block : blocks) {
                int depth = block.nestingDepth();
                if (depth > 1) {
                    els.add(new HighlightElement(block.start(), block.end(), depth));
                }
            }
            synchronized (this) {
                // avoids flashing on re-parse if nothing has
                // changed
                if (lastElements != null && lastElements.equals(els)) {
                    return;
                }
            }
            bag.clear();
            if (!els.isEmpty()) {
                AttributeSet[] cache = new AttributeSet[20];
                rendering(() -> {
                    for (HighlightElement block : els) {
                        int depth = block.depth;
                        if (depth > 1) {
                            AttributeSet set = depth < cache.length
                                    ? cache[depth] : null;
                            if (set == null) {
                                set = new ColorAttributeSet(depth);
                            }
                            if (depth < cache.length) {
                                cache[depth] = set;
                            }
                            bag.addHighlight(block.start, block.end,
                                    set);
                        }
                    }
                });
            }
        }
    }

    private static class HighlightElement implements Comparable<HighlightElement> {

        public final int start;
        public final int end;
        public final int depth;

        public HighlightElement(int start, int end, int depth) {
            this.start = start;
            this.end = end;
            this.depth = depth;
        }

        @Override
        public int hashCode() {
            int hash = 5;
            hash = 13 * hash + this.start;
            hash = 13 * hash + this.end;
            hash = 13 * hash + this.depth;
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
            final HighlightElement other = (HighlightElement) obj;
            if (this.start != other.start) {
                return false;
            }
            if (this.end != other.end) {
                return false;
            }
            if (this.depth != other.depth) {
                return false;
            }
            return true;
        }

        @Override
        public int compareTo(HighlightElement o) {
            int result = start > o.start ? 1 : start == o.start ? 0 : -1;
            if (result == 0) {
                result = end > o.end ? -1 : o.end == end ? 0 : 1;
            }
            if (result == 0) {
                result = depth > o.depth ? 1 : depth == o.depth ? 0 : -1;
            }
            return result;
        }
    }

    public static AttributeSet coloring() {
        // Do not cache - user can edit these
        MimePath mimePath = MimePath.parse("text/x-g4");
        FontColorSettings fcs = MimeLookup.getLookup(mimePath).lookup(FontColorSettings.class);
        AttributeSet result = fcs.getTokenFontColors("nested_blocks");
        assert result != null : "nested_block missing from colors";
        return result;
    }

    private static void rendering(Runnable run) {
        try {
            renderWith(coloring(), run);
        } catch (Throwable e1) {
            Exceptions.printStackTrace(e1);
        }
    }

    private static final ThreadLocal<AttributeSet> COLORS = new ThreadLocal<>();
    private static final ThreadLocal<Color[]> CURR_COLOR_MAP = new ThreadLocal<>();

    private static void renderWith(AttributeSet colors, Runnable run) {
        AttributeSet old = COLORS.get();
        Color[] oldColors = CURR_COLOR_MAP.get();
        Color[] cache = new Color[20];
        COLORS.set(colors);
        CURR_COLOR_MAP.set(cache);
        try {
            run.run();
        } catch (Exception e) {
            Exceptions.printStackTrace(e);
        } finally {
            if (old != null) {
                COLORS.set(old);
            } else {
                COLORS.remove();
            }
            if (oldColors != null) {
                CURR_COLOR_MAP.set(oldColors);
            } else {
                CURR_COLOR_MAP.remove();
            }
        }
    }

    private static final Color FALLBACK_COLOR = new Color(245, 245, 200);
    private static final float ALPHA_FACTOR = 0.0325f;
    private static final float HUE_FACTOR = 0.1f;

    static Color color(int depth) {
        // we only highlight > 1 depth, so we actually
        // will never be passed 1
        depth -= 1;
        Color[] cache = CURR_COLOR_MAP.get();
        Color result = cache == null || depth >= cache.length ? null : cache[depth];
        if (result != null) {
            return result;
        }
        Color color;
        AttributeSet colors = COLORS.get();
        if (colors == null) {
            color = FALLBACK_COLOR;
        } else {
            Object val = colors.getAttribute(StyleConstants.ColorConstants.Background);
            if (!(val instanceof Color)) {
                color = FALLBACK_COLOR;
            } else {
                color = (Color) val;
            }
        }

        float[] hsbs = new float[3];
        Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), hsbs);
        float newHue = hsbs[0] + (depth * HUE_FACTOR);
        if (newHue > 1) {
            newHue = newHue - (float) Math.floor(newHue);
        }
        float newAlpha = ALPHA_FACTOR * depth;
        result = new Color(newHue, hsbs[1], hsbs[2], newAlpha);

        if (cache != null && depth < cache.length) {
            cache[depth] = result;
        }
        return result;
    }

    private static final class ColorAttributeSet implements AttributeSet {

        private final int depth;

        public ColorAttributeSet(int depth) {
            this.depth = depth;
        }

        public boolean equals(Object o) {
            return o instanceof ColorAttributeSet && ((ColorAttributeSet) o).depth == depth;
        }

        public String toString() {
            return "ColorAttributeSet_" + depth + " = " + color(depth);
        }

        public int hashCode() {
            return 71 * (1 + depth);
        }

        @Override
        public int getAttributeCount() {
            return 1;
        }

        @Override
        public boolean isDefined(Object attrName) {
            return StyleConstants.ColorConstants.Background.equals(attrName);
        }

        @Override
        public boolean isEqual(AttributeSet attr) {
            return attr instanceof ColorAttributeSet && ((ColorAttributeSet) attr).depth == depth;
        }

        @Override
        public AttributeSet copyAttributes() {
            return this;
        }

        @Override
        public Object getAttribute(Object key) {
            if (isDefined(key)) {
                return color(depth);
            }
            return null;
        }

        @Override
        public Enumeration<?> getAttributeNames() {
            return Enumerations.singleton(StyleConstants.ColorConstants.Background);
        }

        @Override
        public boolean containsAttribute(Object name, Object value) {
            return isDefined(name) && color(depth).equals(value);
        }

        @Override
        public boolean containsAttributes(AttributeSet attributes) {
            Enumeration<?> names = attributes.getAttributeNames();
            int count = 0;
            boolean found = false;
            while (names.hasMoreElements()) {
                Object name = names.nextElement();
                if (isDefined(name)) {
                    found = true;
                }
                count++;
            }
            return found && count == 1;
        }

        @Override
        public AttributeSet getResolveParent() {
            return null;
        }
    }

}

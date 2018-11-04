package org.nemesis.antlr.v4.netbeans.v8.grammar.code.summary;

import java.awt.Color;
import java.util.Enumeration;
import javax.swing.text.AttributeSet;
import javax.swing.text.StyleConstants;
import org.netbeans.api.editor.mimelookup.MimeLookup;
import org.netbeans.api.editor.mimelookup.MimePath;
import org.netbeans.api.editor.settings.FontColorSettings;
import org.openide.util.Enumerations;
import org.openide.util.Exceptions;

/**
 *
 * @author Tim Boudreau
 */
public class BlockElement implements RuleComponent {

    private final int start;
    private final int end;
    private final int nestingDepth;

    public BlockElement(int start, int end, int nestingDepth) {
        this.start = start;
        this.end = end;
        this.nestingDepth = nestingDepth;
    }

    @Override
    public int getStartOffset() {
        return start;
    }

    @Override
    public int getEndOffset() {
        return end;
    }

    public int nestingDepth() {
        return nestingDepth;
    }

    @Override
    public String toString() {
        return start + ":" + end + "-" + nestingDepth;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 17 * hash + this.start;
        hash = 17 * hash + this.end;
        hash = 17 * hash + this.nestingDepth;
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
        final BlockElement other = (BlockElement) obj;
        if (this.start != other.start) {
            return false;
        }
        if (this.end != other.end) {
            return false;
        }
        if (this.nestingDepth != other.nestingDepth) {
            return false;
        }
        return true;
    }

    @Override
    public int compareTo(RuleComponent o) {
        // We sort these differently, so nested blocks
        // sit after their containing ones
        int a = getStartOffset();
        int b = o.getStartOffset();
        if (a == b) {
            a = getEndOffset();
            b = o.getEndOffset();
            return a <= b ? -1 : a == b ? 0 : 1;
        }
        if (b > a && getEndOffset() > o.getEndOffset()) {
            return -1;
        }
        return a == b ? 0 : a > b ? 1 : -1;
    }

    public AttributeSet attrs() {
        return new ColorAttributeSet(nestingDepth);
    }

    public static AttributeSet coloring() {
        // Do not cache - user can edit these
        MimePath mimePath = MimePath.parse("text/x-g4");
        FontColorSettings fcs = MimeLookup.getLookup(mimePath).lookup(FontColorSettings.class);
        AttributeSet result = fcs.getTokenFontColors("nested_blocks");
        assert result != null : "nested_block missing from colors";
        return result;
    }

    public static void rendering(Runnable run) {
        try {
            renderWith(coloring(), run);
        } catch (Throwable e1) {
            Exceptions.printStackTrace(e1);
        }
    }

    public boolean contains(BlockElement el) {
        return getStartOffset() < el.getStartOffset() && getEndOffset() > el.getEndOffset();
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

    static final Color FALLBACK_COLOR = new Color(245, 245, 200);
    static final int ALPHA_BASE = 20;
    static final float ALPHA_FACTOR = 0.0325f;
    static final float HUE_FACTOR = 0.1f;
    static final float ALPHA_MAX = ALPHA_FACTOR * 7f;

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

    static final class ColorAttributeSet implements AttributeSet {

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

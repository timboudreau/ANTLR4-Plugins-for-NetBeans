package org.nemesis.antlr.live.language;

import com.mastfrog.util.collections.ArrayUtils;
import com.mastfrog.util.collections.CollectionUtils;
import java.awt.Color;
import java.io.Serializable;
import java.util.EnumSet;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import javax.swing.text.AttributeSet;
import javax.swing.text.StyleConstants;
import org.openide.util.Parameters;

/**
 * A very lightweight AttributeSet used for ad-hoc token colorings.
 */
public class AdhocColoring implements AttributeSet, Serializable {

    private static final long serialVersionUID = 1;
    static final byte MASK_ACTIVE = 1;
    static final byte MASK_BACKGROUND = 2;
    static final byte MASK_FOREGROUND = 4;
    static final byte MASK_BOLD = 8;
    static final byte MASK_ITALIC = 16;
    static final byte[] STYLE_FLAGS = {MASK_BACKGROUND, MASK_FOREGROUND, MASK_BOLD, MASK_ITALIC};
    static final Object[] STYLE_CONSTS = {StyleConstants.Background,
        StyleConstants.Foreground, StyleConstants.Bold, StyleConstants.Italic};
    byte flags;
    Color color;

    public AdhocColoring(Color color, boolean active, boolean background, boolean foreground, boolean bold, boolean italic) {
        this.color = color;
        boolean[] vals = {background, foreground, bold, italic};
        byte fl = active ? MASK_ACTIVE : 0;
        for (int i = 0; i < STYLE_FLAGS.length; i++) {
            if (vals[i]) {
                fl = (byte) (fl | STYLE_FLAGS[i]);
            }
        }
        this.flags = fl;
    }

    public AdhocColoring(int flags, Color color) {
        this((byte) flags, color);
    }

    public AdhocColoring(byte flags, Color color) {
        this.flags = flags;
        this.color = color;
    }

    private AdhocColoring(AdhocColoring orig) {
        this.flags = orig.flags;
        this.color = orig.color;
    }

    public Color color() {
        return color;
    }

    AttributeSet combine(AdhocColoring other) {
        if (isColor() && other.isColor()) {
            if (!isSameColorAttribute(other)) {
                return new AdhocColoringMerged(this, other);
            }
        }
        Color c = color == null ? other.color : color;
        return new AdhocColoring(flags | other.flags, c);
    }

    public static AttributeSet combine(AdhocColoring a, AdhocColoring b) {
        AttributeSet result = a.combine(b);
        if (result == null) {

//            return AttributesUtilities.createImmutable(b, a);
//            return new Combined(new AttributeSet[]{b, a});
        }
        return result;
    }

    boolean isColor() {
        return isForegroundColor() || isBackgroundColor();
    }

    boolean isSameColorAttribute(AdhocColoring other) {
        return (isForegroundColor() && other.isForegroundColor())
                || (isBackgroundColor() && other.isBackgroundColor());
    }

    public boolean addFlag(AttrTypes flag) {
        boolean result = !containsFlag(flag);
        this.flags |= (byte) flag.maskValue();
        return result;
    }

    public boolean removeFlag(AttrTypes flag) {
        boolean result = containsFlag(flag);
        this.flags ^= (byte) flag.maskValue();
        return result;
    }

    public boolean containsFlag(AttrTypes flag) {
        return (flags & flag.maskValue()) != 0;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(getClass().getSimpleName()).append('{');
        sb.append("r=").append(color.getRed()).append(" g=").append(color.getGreen()).append(" b=").append(color.getBlue()).append(", flags:");
        for (AttrTypes t : flags()) {
            sb.append(t.name()).append(" ");
        }
        return sb.append('}').toString();
    }

    public void setFlags(Set<AttrTypes> flags) {
        int newFlags = 0;
        for (AttrTypes t : flags) {
            newFlags |= t.maskValue();
        }
        this.flags = (byte) newFlags;
    }

    public Set<AttrTypes> flags() {
        Set<AttrTypes> result = EnumSet.noneOf(AttrTypes.class);
        AttrTypes[] types = AttrTypes.values();
        for (int i = 0; i < STYLE_FLAGS.length; i++) {
            if ((flags & STYLE_FLAGS[i]) != 0) {
                result.add(types[i]);
            }
        }
        return result;
    }

    static AdhocColoring parse(String toParse) {
        // Parses the format written by toLine()
        String[] s = toParse.trim().split(";");
        if (s.length != 2) {
            return new AdhocColoring(0, Color.BLACK);
        }
        byte flags = (byte) Integer.parseInt(s[0], 16);
        int color = Integer.parseInt(s[1], 16);
        return new AdhocColoring(flags, new Color(color));
    }

    void addToFlags(int maskValue) {
        flags = (byte) (flags | maskValue);
    }

    void removeFromFlags(int maskValue) {
        flags = (byte) (flags ^ maskValue);
    }

    public String toLine() {
        return new StringBuilder(Integer.toString(flags, 16))
                .append(';')
                .append(Integer.toString(color.getRGB(), 16))
                .append('\n').toString();
    }

    public void toggleBackgroundForeground() {
        if (isBackgroundColor()) {
            flags = (byte) ((flags ^ MASK_BACKGROUND) | MASK_FOREGROUND);
        } else {
            flags = (byte) ((flags ^ MASK_FOREGROUND) | MASK_BACKGROUND);
        }
    }

    public void setBold(boolean bold) {
        if (bold) {
            flags = (byte) (flags | MASK_BOLD);
        } else {
            flags = (byte) (flags ^ MASK_BOLD);
        }
    }

    public void setItalic(boolean italic) {
        if (italic) {
            flags = (byte) (flags | MASK_ITALIC);
        } else {
            flags = (byte) (flags ^ MASK_ITALIC);
        }
    }

    public boolean setColor(Color color) {
        Parameters.notNull("color", color);
        boolean result = !this.color.equals(color);
        this.color = color;
        return result;
    }

    public boolean isActive() {
        return (flags & MASK_ACTIVE) != 0;
    }

    public boolean isBackgroundColor() {
        return (flags & MASK_BACKGROUND) != 0;
    }

    public boolean isForegroundColor() {
        return (flags & MASK_FOREGROUND) != 0;
    }

    public boolean isBold() {
        return (flags & MASK_BOLD) != 0;
    }

    public boolean isItalic() {
        return (flags & MASK_ITALIC) != 0;
    }

    @Override
    public int getAttributeCount() {
        if (!isActive()) {
            return 0;
        }
        int result = 0;
        for (byte b : STYLE_FLAGS) {
            if ((flags & b) != 0) {
                result++;
            }
        }
        return result;
    }

    @Override
    public boolean isDefined(Object attrName) {
        if (StyleConstants.Bold == attrName) {
            return isBold();
        } else if (StyleConstants.Italic == attrName) {
            return isItalic();
        } else if (StyleConstants.Foreground == attrName && (flags & MASK_FOREGROUND) != 0) {
            return true;
        } else if (StyleConstants.Background == attrName && (flags & MASK_BACKGROUND) != 0) {
            return true;
        }
        return false;
    }

    @Override
    public boolean isEqual(AttributeSet attr) {
        if (attr instanceof AdhocColoring) {
            AdhocColoring c = (AdhocColoring) attr;
            return c.flags == flags && c.color.equals(color);
        }
        int ct = attr.getAttributeCount();
        if (ct != getAttributeCount()) {
            return false;
        }
        for (Object name : CollectionUtils.toIterable(getAttributeNames())) {
            Object val = attr.getAttribute(name);
            if (!Objects.equals(val, getAttribute(name))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public AdhocColoring copyAttributes() {
        return new AdhocColoring(this);
    }

    @Override
    public Object getAttribute(Object key) {
        if (!isActive()) {
            return null;
        }
        if (key == StyleConstants.Background && (flags & MASK_BACKGROUND) != 0) {
            return color;
        } else if (key == StyleConstants.Foreground && (flags & MASK_FOREGROUND) != 0) {
            return color;
        } else if (key == StyleConstants.Italic && (flags & MASK_ITALIC) != 0) {
            return true;
        } else if (key == StyleConstants.Bold && (flags & MASK_BOLD) != 0) {
            return true;
        }
        return null;
    }

    @Override
    public Enumeration<?> getAttributeNames() {
        return new AttrNameEnum(flags);
    }

    static class AttrNameEnum implements Enumeration<Object> {

        private final byte flags;
        private int ix = -1;
        private Object next;

        AttrNameEnum(byte flags) {
            this.flags = flags;
            next = findNext();
        }

        private Object findNext() {
            while (++ix < STYLE_CONSTS.length) {
                if ((flags & STYLE_FLAGS[ix]) != 0) {
                    return STYLE_CONSTS[ix];
                }
            }
            return null;
        }

        @Override
        public boolean hasMoreElements() {
            return next != null;
        }

        @Override
        public Object nextElement() {
            Object result = next;
            if (result == null) {
                throw new NoSuchElementException();
            }
            next = findNext();
            return result;
        }

    }

    @Override
    public boolean containsAttribute(Object name, Object value) {
        /*
    static final byte[] STYLE_FLAGS = {MASK_BACKGROUND, MASK_FOREGROUND, MASK_BOLD, MASK_ITALIC};
    static final Object[] STYLE_CONSTS = {StyleConstants.Background,
        StyleConstants.Foreground, StyleConstants.Bold, StyleConstants.Italic};

         */
        if (!isActive()) {
            return false;
        }
        if (name == StyleConstants.Background) {
            return (flags & MASK_BACKGROUND) != 0 && Objects.equals(color, value);
        } else if (name == StyleConstants.Foreground) {
            return (flags & MASK_FOREGROUND) != 0 && Objects.equals(color, value);
        } else if (name == StyleConstants.Bold) {
            return (flags & MASK_BOLD) != 0 ? Boolean.TRUE.equals(value) : Boolean.FALSE.equals(value) ? true : false;
        } else if (name == StyleConstants.Italic) {
            return (flags & MASK_ITALIC) != 0 ? Boolean.TRUE.equals(value) : Boolean.FALSE.equals(value) ? true : false;
        }
        return false;
//        Object val = getAttribute(name);
//        return val != null && val.equals(value);
    }

    @Override
    public boolean containsAttributes(AttributeSet attributes) {
        if (attributes instanceof AdhocColoring) {
            AdhocColoring c = (AdhocColoring) attributes;
            return c.flags == flags && Objects.equals(color, c.color);
        }
        Enumeration<?> en = attributes.getAttributeNames();
        while (en.hasMoreElements()) {
            Object name = en.nextElement();
            if (!containsAttribute(name, attributes.getAttribute(name))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public AttributeSet getResolveParent() {
        return null;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 59 * hash + this.flags;
        hash = 59 * hash + Objects.hashCode(this.color);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (obj == null) {
            return false;
        } else if (obj instanceof AdhocColoring) {
            final AdhocColoring other = (AdhocColoring) obj;
            if (this.flags != other.flags) {
                return false;
            }
            return Objects.equals(this.color, other.color);
        } else if (obj instanceof AttributeSet) {
            return isEqual((AttributeSet) obj);
        }
        return false;
    }

    static AttributeSet concatenate(AttributeSet a, AttributeSet b) {
        AttributeSet result;
        if (a instanceof AdhocColoring && b instanceof AdhocColoring) {
            result = ((AdhocColoring) a).combine((AdhocColoring) b);
        } else if (a instanceof AdhocColoringMerged && b instanceof AdhocColoring) {
            result = ((AdhocColoringMerged) a).add((AdhocColoring) b);
        } else if (a instanceof AdhocColoring && b instanceof AdhocColoringMerged) {
            result = new AdhocColoringMerged((AdhocColoring) a, ((AdhocColoringMerged) b));
        } else if (a instanceof AdhocColoringMerged && b instanceof AdhocColoring) {
            result = new AdhocColoringMerged((AdhocColoringMerged) a, ((AdhocColoring) b));
        } else if (a instanceof AdhocColoringMerged && b instanceof AdhocColoringMerged) {
            result = new AdhocColoringMerged((AdhocColoringMerged) a, (AdhocColoringMerged) b);
        } else {
            result = new Combined(new AttributeSet[]{a, b});
        }
        System.out.println("Combine to " + result);
        return result;
    }

    static class Combined implements AttributeSet {

        private final AttributeSet[] colorings;

        public Combined(AttributeSet[] colorings) {
            this.colorings = colorings;
        }

        public Combined combine(AttributeSet s) {
            if (s == this) {
                return this;
            }
            if (s instanceof Combined) {
                Combined c = (Combined) s;
                AttributeSet[] all = ArrayUtils.concatenate(colorings, c.colorings);
                return new Combined(all);
            } else {
                AttributeSet[] nue = new AttributeSet[colorings.length + 1];
                System.arraycopy(colorings, 0, nue, 0, colorings.length);
                nue[nue.length - 1] = s;
                return new Combined(nue);
            }
        }

        private Set<Object> allAttributeNames() {
            Set<Object> all = new HashSet<>();
            for (AttributeSet c : colorings) {
                Enumeration<?> e = c.getAttributeNames();
                while (e.hasMoreElements()) {
                    all.add(e.nextElement());
                }
            }
            return all;
        }

        @Override
        public int getAttributeCount() {
            return allAttributeNames().size();
        }

        @Override
        public boolean isDefined(Object attrName) {
            return allAttributeNames().contains(attrName);
        }

        @Override
        public boolean isEqual(AttributeSet attr) {
            Set<Object> s = allAttributeNames();
            for (Object o : s) {
                if (!Objects.equals(getAttribute(o), attr.getAttribute(o))) {
                    return false;
                }
            }
            return attr.getAttributeCount() == s.size();
        }

        @Override
        public AttributeSet copyAttributes() {
            return new Combined(this.colorings);
        }

        @Override
        public Object getAttribute(Object key) {
            for (AttributeSet c : colorings) {
                Object result = c.getAttribute(key);
                if (result != null) {
                    return result;
                }
            }
            return null;
        }

        @Override
        public Enumeration<?> getAttributeNames() {
            return CollectionUtils.toEnumeration(allAttributeNames());
        }

        @Override
        public boolean containsAttribute(Object name, Object value) {
            return Objects.equals(getAttribute(name), value);
        }

        @Override
        public boolean containsAttributes(AttributeSet attributes) {
            for (Object o : CollectionUtils.toIterable(attributes.getAttributeNames())) {
                if (!Objects.equals(getAttribute(o), attributes.getAttribute(o))) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public AttributeSet getResolveParent() {
            return null;
        }
    }
}

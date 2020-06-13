/*
 * Copyright 2016-2019 Tim Boudreau, Frédéric Yvon Vinet
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.nemesis.antlr.live.language.coloring;

import com.mastfrog.util.collections.CollectionUtils;
import java.awt.Color;
import java.util.Enumeration;
import java.util.Objects;
import java.util.Set;
import javax.swing.text.AttributeSet;
import javax.swing.text.StyleConstants;
import org.openide.util.Parameters;

/**
 * A very lightweight AttributeSet used for ad-hoc token colorings, optimized to
 * minimize memory footprint.
 */
public class AdhocColoring implements AdhocAttributeSet {

    private static final long serialVersionUID = 1;
    static final byte MASK_ACTIVE = 1;
    static final byte MASK_BACKGROUND = 2;
    static final byte MASK_FOREGROUND = 4;
    static final byte MASK_BOLD = 8;
    static final byte MASK_ITALIC = 16;
    static final byte[] STYLE_FLAGS = {MASK_BACKGROUND, MASK_FOREGROUND, MASK_BOLD, MASK_ITALIC};
    static final Object[] STYLE_CONSTS = {StyleConstants.Background,
        StyleConstants.Foreground, StyleConstants.Bold, StyleConstants.Italic};
    static AttrTypes[] TYPES = {AttrTypes.BACKGROUND, AttrTypes.FOREGROUND, AttrTypes.BOLD, AttrTypes.ITALIC};
    byte flags;
    Color color;

    public AdhocColoring(Color color, boolean active, boolean background, boolean foreground, boolean bold, boolean italic) {
        this.color = color;
        boolean[] vals = {background, foreground, bold, italic};
        byte fl = (byte) ((active ? MASK_ACTIVE : 0)
                | (background ? MASK_BACKGROUND : 0)
                | (foreground ? MASK_FOREGROUND : 0)
                | (bold ? MASK_BOLD : 0)
                | (italic ? MASK_ITALIC : 0));
        if (background && foreground) {
            throw new IllegalArgumentException("Background and foreground cannot both be set");
        }
        this.flags = fl;
    }

    public AdhocColoring(AdhocColoring orig, boolean active) {
        this(orig);
        setActive(active);
    }

    public AdhocColoring(int flags, Color color) {
        this((byte) flags, color);
    }

    public AdhocColoring(byte flags, Color color) {
        this.flags = flags;
        this.color = color;
        sanityCheck();
    }

    private AdhocColoring(AdhocColoring orig) {
        this.flags = orig.flags;
        this.color = orig.color;
    }

    private void sanityCheck() {
        if (isForegroundColor() && isBackgroundColor()) {
            throw new IllegalArgumentException("Cannot be both foreground and background: " + this);
        }
    }

    public Color color() {
        return color;
    }

    AdhocAttributeSet combine(AdhocColoring other) {
        if (!other.isActive()) {
            return this;
        } else if (!isActive()) {
            return other;
        }
        if (isColor() && other.isColor()) {
            if (!isSameColorAttribute(other)) {
                return new AdhocColoringMerged(this, other);
            }
        }
        if (true) {
            return new AdhocColoringMerged(this, other);
        }
        Color c = color == null ? other.color : color;
        return new AdhocColoring(flags | other.flags, c);
    }

    public static AttributeSet combine(AdhocColoring a, AdhocColoring b) {
        AttributeSet result = a.combine(b);
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
        if (result) {
            this.flags |= (byte) flag.maskValue();
            switch (flag) {
                case BACKGROUND:
                    if ((this.flags & MASK_FOREGROUND) != 0) {
                        this.flags ^= MASK_FOREGROUND;
                    }
                    break;
                case FOREGROUND:
                    if ((this.flags & MASK_BACKGROUND) != 0) {
                        this.flags ^= MASK_BACKGROUND;
                    }
                    break;
            }
        }
        return result;
    }

    public boolean removeFlag(AttrTypes flag) {
        boolean result = containsFlag(flag);
        if (result) {
            this.flags ^= (byte) flag.maskValue();
        }
        return result;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(getClass().getSimpleName()).append('(');
        sb.append(isActive() ? "active" : "inactive");
        if (color != null) {
        sb.append(" r=").append(color.getRed()).append(" g=").append(color.getGreen()).append(" b=").append(color.getBlue()).append(", flags:");
        } else {
            sb.append(" flags=");
        }
        for (AttrTypes t : flags()) {
            sb.append(t.name()).append(" ");
        }
        return sb.append(')').toString();
    }

    public void setFlags(Set<AttrTypes> flags) {
        int newFlags = 0;
        if (flags.contains(AttrTypes.BACKGROUND) && flags.contains(AttrTypes.FOREGROUND)) {
            throw new IllegalArgumentException("Cannot set background *and* foreground: "
                    + flags);
        }
        for (AttrTypes t : flags) {
            newFlags |= t.maskValue();
        }
        this.flags = (byte) newFlags;
    }

    public static AdhocColoring parse(String toParse) {
        // Parses the format written by toLine()
        String[] s = toParse.trim().split(";");
        if (s.length != 2) {
            return new AdhocColoring(0, Color.BLACK);
        }
        int flags = Integer.parseInt(s[0], 16);
        if ((flags & MASK_FOREGROUND) != 0 && (flags & MASK_BACKGROUND) != 0) {
            flags ^= MASK_BACKGROUND;
//            System.err.println("Removed simultaneous background and foreground flags from " + toParse + " - removed background flag");
        }
        int color = Integer.parseInt(s[1], 16);
        return new AdhocColoring((byte) flags, new Color(color));
    }

    void addToFlags(int maskValue) {
        flags = (byte) (flags | maskValue);
    }

    void removeFromFlags(int maskValue) {
        if ((flags & maskValue) != 0) {
            flags = (byte) (flags ^ maskValue);
        }
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

    public void setActive(boolean active) {
        if (active) {
            flags = (byte) (flags | MASK_ACTIVE);
        } else {
            if ((flags & MASK_ACTIVE) != 0) {
                flags = (byte) (flags ^ MASK_ACTIVE);
            }
        }
    }

    public void setBold(boolean bold) {
        if (bold) {
            flags = (byte) (flags | MASK_BOLD);
        } else {
            if ((flags & MASK_BOLD) != 0) {
                flags = (byte) (flags ^ MASK_BOLD);
            }
        }
    }

    public void setItalic(boolean italic) {
        if (italic) {
            flags = (byte) (flags | MASK_ITALIC);
        } else {
            if ((flags & MASK_ITALIC) != 0) {
                flags = (byte) (flags ^ MASK_ITALIC);
            }
        }
    }

    public boolean setColor(Color color) {
        Parameters.notNull("color", color);
        boolean result = !this.color.equals(color);
        this.color = color;
        return result;
    }

    @Override
    public int colorCount() {
        return color != null && (isBackgroundColor() || isForegroundColor()) ? 1 : 0;
    }

    @Override
    public boolean isEqual(AttributeSet attr) {
        if (attr == null) {
            return false;
        }
        if (attr instanceof AdhocColoring) {
            AdhocColoring c = (AdhocColoring) attr;
            return c.flags == flags && Objects.equals(color, c.color);
        }
        if (attr instanceof AdhocColoringMerged) {
            AdhocColoringMerged c = (AdhocColoringMerged) attr;
            if (c.intFlags() == intFlags() && c.colorCount() == colorCount()) {
                return Objects.equals(c.colorAttribute(), this.colorAttribute());
            }
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
    public Enumeration<?> getAttributeNames() {
        int workingFlags = this.flags;
        if (color == null && (workingFlags & MASK_BACKGROUND) != 0) {
            workingFlags ^= MASK_BACKGROUND;
        }
        if (color == null && (workingFlags & MASK_FOREGROUND) != 0) {
            workingFlags ^= MASK_FOREGROUND;
        }
        return new AttrNameEnum((byte) workingFlags);
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
        if (key == StyleConstants.Background && color != null && (flags & MASK_BACKGROUND) != 0) {
            return color;
        } else if (key == StyleConstants.Foreground && color != null && (flags & MASK_FOREGROUND) != 0) {
            return color;
        } else if (key == StyleConstants.Italic && (flags & MASK_ITALIC) != 0) {
            return true;
        } else if (key == StyleConstants.Bold && (flags & MASK_BOLD) != 0) {
            return true;
        }
        return null;
    }

    @Override
    public boolean containsAttribute(Object name, Object value) {
        if (!isActive()) {
            return false;
        }
        if (name == StyleConstants.Background) {
            return (flags & MASK_BACKGROUND) != 0 && Objects.equals(color, value);
        } else if (name == StyleConstants.Foreground) {
            return (flags & MASK_FOREGROUND) != 0 && Objects.equals(color, value);
        } else if (name == StyleConstants.Bold) {
            return (flags & MASK_BOLD) != 0 ? Boolean.TRUE.equals(value) : value == null || Boolean.FALSE.equals(value);
        } else if (name == StyleConstants.Italic) {
            return (flags & MASK_ITALIC) != 0 ? Boolean.TRUE.equals(value) : value == null || Boolean.FALSE.equals(value);
        }
        return false;
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
        return hashCode(flags, isBackgroundColor() ? color : null, isForegroundColor() ? color : null);
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
        } else if (obj instanceof AdhocColoringMerged) {
            AdhocColoringMerged other = (AdhocColoringMerged) obj;
            int occ = other.colorCount();
            if (occ > 1 || other.intFlags() != intFlags()) {
                return false;
            }
            int cc = colorCount();
            if (cc != occ) {
                return false;
            } else if (isBackgroundColor()) {
                return Objects.equals(other.background, color);
            } else if (isForegroundColor()) {
                return Objects.equals(other.foreground, color);
            } else {
                return true;
            }
        } else if (obj instanceof DepthAttributeSet) {
            return equals(((DepthAttributeSet) obj).delegate());
        } else if (obj instanceof AttributeSet) {
            return isEqual((AttributeSet) obj);
        }
        return false;
    }

    @Override
    public int intFlags() {
        return flags;
    }

    static AdhocAttributeSet merge(AdhocAttributeSet a, AdhocAttributeSet b) {
        if (!a.isActive()) {
            return b;
        } else if (!b.isActive()) {
            return a;
        }
        int depth = a instanceof DepthAttributeSet || b instanceof DepthAttributeSet
                ? 0 : Integer.MAX_VALUE;
        if (a instanceof DepthAttributeSet) {
            depth = a.depth();
            a = ((DepthAttributeSet) a).delegate();
        }
        if (b instanceof DepthAttributeSet) {
            depth = Math.max(depth, b.depth());
            b = ((DepthAttributeSet) b).delegate();
        }
        AdhocAttributeSet result;
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
            result = new Combined(new AdhocAttributeSet[]{a, b});
        }
        if (depth != Integer.MAX_VALUE) {
            result = new DepthAttributeSet(result, depth);
        }
        return result;
    }

    static int hashCode(byte flags, Color bg, Color fg) {
        if (flags == 0) {
            return 0;
        }
        int result = 53 * flags;
        if (bg != null && (flags & MASK_BACKGROUND) != 0) {
            result += 71 * bg.getRGB();
        }
        if (fg != null && (flags & MASK_FOREGROUND) != 0) {
            result += 39 * fg.getRGB();
        }
        return result;
    }
}

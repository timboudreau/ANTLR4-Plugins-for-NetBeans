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
package org.nemesis.antlr.v4.netbeans.v8.grammar.file.preview;

import java.awt.Color;
import java.io.Serializable;
import java.util.EnumSet;
import java.util.Enumeration;
import java.util.Objects;
import java.util.Set;
import java.util.Vector;
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
    private byte flags;
    private Color color;

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

    public Color color() {
        return color;
    }

    AdhocColoring combine(AdhocColoring other) {
        if (isColor() && other.isColor()) {
            if (!isSameColorAttribute(other)) {
                return null;
            }
        }
        Color c = color == null ? other.color : color;
        return new AdhocColoring(flags | other.flags, c);
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
        for (int i = 0; i < STYLE_FLAGS.length; i++) {
            byte b = STYLE_FLAGS[i];
            if ((flags & b) != 0) {
                if (STYLE_CONSTS[i].equals(attrName)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean isEqual(AttributeSet attr) {
        if (attr instanceof AdhocColoring) {
            AdhocColoring c = (AdhocColoring) attr;
            return c.flags == flags && c.color.equals(color);
        }
        return false;
    }

    @Override
    public AdhocColoring copyAttributes() {
        return new AdhocColoring(flags, color);
    }

    @Override
    public Object getAttribute(Object key) {
        if (!isActive()) {
            return null;
        }
        for (int i = 0; i < STYLE_FLAGS.length; i++) {
            byte b = STYLE_FLAGS[i];
            if ((flags & b) != 0) {
                if (STYLE_CONSTS[i].equals(key)) {
                    switch (b) {
                        case MASK_BACKGROUND:
                        case MASK_FOREGROUND:
                            return color;
                        case MASK_BOLD:
                            return true;
                        case MASK_ITALIC:
                            return true;
                    }
                }
            }
        }
        return null;
    }

    @Override
    @SuppressWarnings(value = "deprecation")
    public Enumeration<?> getAttributeNames() {
        Vector<Object> v = new Vector<>();
        if (isActive()) {
            for (int i = 0; i < STYLE_FLAGS.length; i++) {
                byte b = STYLE_FLAGS[i];
                if ((flags & b) != 0) {
                    v.add(STYLE_CONSTS[i]);
                }
            }
        }
        return v.elements();
    }

    @Override
    public boolean containsAttribute(Object name, Object value) {
        Object val = getAttribute(name);
        return val != null && val.equals(value);
    }

    @Override
    public boolean containsAttributes(AttributeSet attributes) {
        if (attributes instanceof AdhocColoring) {
            AdhocColoring c = (AdhocColoring) attributes;
            return c.flags == flags;
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
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final AdhocColoring other = (AdhocColoring) obj;
        if (this.flags != other.flags) {
            return false;
        }
        return Objects.equals(this.color, other.color);
    }
}

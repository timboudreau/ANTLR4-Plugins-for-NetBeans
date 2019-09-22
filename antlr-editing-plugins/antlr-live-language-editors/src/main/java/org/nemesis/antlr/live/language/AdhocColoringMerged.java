/*
BSD License

Copyright (c) 2016-2018, Frédéric Yvon Vinet, Tim Boudreau
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

* Redistributions of source code must retain the above copyright
  notice, this list of conditions and the following disclaimer.
* Redistributions in binary form must reproduce the above copyright
  notice, this list of conditions and the following disclaimer in the
  documentation and/or other materials provided with the distribution.
* The name of its author may not be used to endorse or promote products
  derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR IMPLIED 
WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF 
MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT
SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, 
EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT
OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS 
INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN 
CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING
IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY 
OF SUCH DAMAGE.
 */
package org.nemesis.antlr.live.language;

import com.mastfrog.util.collections.CollectionUtils;
import java.awt.Color;
import java.util.Enumeration;
import java.util.Objects;
import javax.swing.text.AttributeSet;
import javax.swing.text.StyleConstants;
import static org.nemesis.antlr.live.language.AdhocColoring.*;

/**
 * Attribute sets are created in sufficient volume and need a small enough
 * footprint that it is worth the optimization.
 *
 * @author Tim Boudreau
 */
final class AdhocColoringMerged implements AttributeSet {

    private byte flags;
    private Color background;
    private Color foreground;

    public AdhocColoringMerged(int flags, Color bg, Color fg) {
        this.flags = (byte) flags;
        this.background = bg;
        this.foreground = fg;
    }

    public AdhocColoringMerged(AdhocColoring orig) {
        this.flags = orig.flags;
        this.background = orig.isBackgroundColor() ? orig.color : null;
        this.foreground = orig.isForegroundColor() ? orig.color : null;
    }

    public AdhocColoringMerged(AdhocColoring a, AdhocColoring b) {
        this.flags = !a.isActive() && !b.isActive() ? 0
                : a.isActive() && !b.isActive() ? a.flags
                : b.isActive() && !a.isActive() ? b.flags
                : (byte) (a.flags | b.flags);
        background = b.isBackgroundColor() && b.isActive() ? b.color : a.isBackgroundColor() && a.isActive() ? a.color : null;
        foreground = b.isForegroundColor() && b.isActive() ? b.color : a.isForegroundColor() && a.isActive() ? a.color : null;
    }

    public AdhocColoringMerged(AdhocColoringMerged a, AdhocColoringMerged b) {
        this.flags = !a.isActive() && !b.isActive() ? 0
                : a.isActive() && !b.isActive() ? a.flags
                : b.isActive() && !a.isActive() ? b.flags
                : (byte) (a.flags | b.flags);
        background = b.isBackgroundColor() && b.isActive() ? b.background : a.isBackgroundColor() && a.isActive() ? a.background : null;
        foreground = b.isForegroundColor() && b.isActive() ? b.foreground : a.isForegroundColor() && a.isActive() ? a.foreground : null;
    }

    public AdhocColoringMerged(AdhocColoring a, AdhocColoringMerged b) {
        this.flags = !a.isActive() && !b.isActive() ? 0
                : a.isActive() && !b.isActive() ? a.flags
                : b.isActive() && !a.isActive() ? b.flags
                : (byte) (a.flags | b.flags);
        background = b.isBackgroundColor() && b.isActive() ? b.background : a.isBackgroundColor() && a.isActive() ? a.color : null;
        foreground = b.isForegroundColor() && b.isActive() ? b.foreground : a.isForegroundColor() && a.isActive() ? a.color : null;
    }

    public AdhocColoringMerged(AdhocColoringMerged a, AdhocColoring b) {
        this.flags = !a.isActive() && !b.isActive() ? 0
                : a.isActive() && !b.isActive() ? a.flags
                : b.isActive() && !a.isActive() ? b.flags
                : (byte) (a.flags | b.flags);
        background = b.isBackgroundColor() && b.isActive() ? b.color : a.isBackgroundColor() && a.isActive() ? a.background : null;
        foreground = b.isForegroundColor() && b.isActive() ? b.color : a.isForegroundColor() && a.isActive() ? a.foreground : null;
    }

    public AdhocColoringMerged(AdhocColoringMerged m) {
        this.foreground = m.foreground;
        this.background = m.background;
        this.flags = m.flags;
    }

    public AdhocColoringMerged() {

    }

    public AdhocColoringMerged add(AdhocColoringMerged other) {
        if (!other.isActive()) {
            return this;
        }
        if (!isActive()) {
            return other;
        }
        int newFlags = flags | other.flags;
        Color bg = background;
        Color fg = foreground;
        if (other.isBackgroundColor()) {
            bg = other.background;
        }
        if (other.isForegroundColor()) {
            fg = other.foreground;
        }
        return new AdhocColoringMerged(newFlags, bg, fg);
    }

    public AdhocColoringMerged add(AdhocColoring coloring) {
        if (!coloring.isActive()) {
            return this;
        }
        int newFlags = (flags | coloring.flags);
        Color bg = background;
        Color fg = foreground;
        if (coloring.isBackgroundColor()) {
            bg = coloring.color;
        }
        if (coloring.isForegroundColor()) {
            fg = coloring.color;
        }
        return new AdhocColoringMerged(newFlags, bg, fg);
    }

    boolean isActive() {
        return (flags & MASK_ACTIVE) != 0;
    }

    boolean isBackgroundColor() {
        return (flags & MASK_BACKGROUND) != 0;
    }

    boolean isForegroundColor() {
        return (flags & MASK_FOREGROUND) != 0;
    }

    boolean isBold() {
        return (flags & MASK_BOLD) != 0;
    }

    boolean isItalic() {
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
            return (flags & MASK_BACKGROUND) != 0;
        } else if (StyleConstants.Italic == attrName) {
            return (flags & MASK_FOREGROUND) != 0;
        } else if (StyleConstants.Foreground == attrName && (flags & MASK_FOREGROUND) != 0) {
            return true;
        } else if (StyleConstants.Background == attrName && (flags & MASK_BACKGROUND) != 0) {
            return true;
        }
        return false;
    }

    @Override
    public boolean isEqual(AttributeSet attr) {
        if (attr instanceof AdhocColoringMerged) {
            AdhocColoringMerged c = (AdhocColoringMerged) attr;
            return c.flags == flags && Objects.equals(c.foreground, foreground)
                    & Objects.equals(c.background, background);
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
    public AdhocColoringMerged copyAttributes() {
        return new AdhocColoringMerged(this);
    }

    @Override
    public Object getAttribute(Object key) {
        if (!isActive()) {
            return null;
        }
        if (key == StyleConstants.Background && (flags & MASK_BACKGROUND) != 0) {
            return background;
        } else if (key == StyleConstants.Foreground && (flags & MASK_FOREGROUND) != 0) {
            return foreground;
        } else if (key == StyleConstants.Italic && (flags & MASK_ITALIC) != 0) {
            return true;
        } else if (key == StyleConstants.Bold && (flags & MASK_BOLD) != 0) {
            return true;
        }
        return null;
    }

    @Override
    public Enumeration<?> getAttributeNames() {
        return new AdhocColoring.AttrNameEnum(flags);
    }

    @Override
    public boolean containsAttribute(Object name, Object value) {
        if (!isActive()) {
            return false;
        }
        if (name == StyleConstants.Background) {
            return (flags & MASK_BACKGROUND) != 0 && Objects.equals(background, value);
        } else if (name == StyleConstants.Foreground) {
            return (flags & MASK_FOREGROUND) != 0 && Objects.equals(foreground, value);
        } else if (name == StyleConstants.Bold) {
            return (flags & MASK_BOLD) != 0 ? Boolean.TRUE.equals(value) : Boolean.FALSE.equals(value) ? true : false;
        } else if (name == StyleConstants.Italic) {
            return (flags & MASK_ITALIC) != 0 ? Boolean.TRUE.equals(value) : Boolean.FALSE.equals(value) ? true : false;
        }
        return false;
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
        hash = 59 * hash + Objects.hashCode(this.background);
        hash = 59 * hash + Objects.hashCode(this.foreground);
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
        if (obj instanceof AdhocColoringMerged) {
            final AdhocColoringMerged other = (AdhocColoringMerged) obj;
            if (this.flags != other.flags) {
                return false;
            }
            return Objects.equals(this.background, other.background)
                    && Objects.equals(this.foreground, other.foreground);
        }
        if (obj instanceof AttributeSet) {
            return isEqual((AttributeSet) obj);
        }
        return false;
    }

    @Override
    public String toString() {
        return "AdhocColoringMerged(bold=" + isBold()
                + "  italic=" + isItalic()
                + (isForegroundColor() ? " fg=" + foreground : "")
                + (isBackgroundColor() ? " bg=" + background : "")
                + ")";
    }

}

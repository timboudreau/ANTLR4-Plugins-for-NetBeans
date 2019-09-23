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
package org.nemesis.antlr.live.language.coloring;

import com.mastfrog.util.collections.CollectionUtils;
import java.awt.Color;
import java.util.Enumeration;
import java.util.Objects;
import javax.swing.text.AttributeSet;
import javax.swing.text.StyleConstants;
import static org.nemesis.antlr.live.language.coloring.AdhocColoring.*;

/**
 * Attribute sets are created in sufficient volume and need a small enough
 * footprint that it is worth the optimization.
 *
 * @author Tim Boudreau
 */
final class AdhocColoringMerged implements AdhocAttributeSet {

    private byte flags;
    Color background;
    Color foreground;

    public AdhocColoringMerged(int flags, Color bg, Color fg) {
        if (bg == null) {
            flags = flags ^ MASK_BACKGROUND;
        }
        if (fg == null) {
            flags = flags ^ MASK_FOREGROUND;
        }
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

    @Override
    public int intFlags() {
        return flags;
    }

    public AdhocColoringMerged add(AdhocColoringMerged other) {
        if (!other.isActive()) {
            return this;
        } else if (!isActive()) {
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

    @Override
    public boolean isBackgroundColor() {
        return AdhocAttributeSet.super.isBackgroundColor() && background != null;
    }

    @Override
    public boolean isForegroundColor() {
        return AdhocAttributeSet.super.isForegroundColor() && foreground != null;
    }

    @Override
    public boolean isEqual(AttributeSet attr) {
        if (attr == null) {
            return false;
        }
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
    public boolean containsAttribute(Object name, Object value) {
        if (!isActive()) {
            return false;
        }
        if (name == StyleConstants.Background) {
            return (flags & MASK_BACKGROUND) != 0 && Objects.equals(background, value);
        } else if (name == StyleConstants.Foreground) {
            return (flags & MASK_FOREGROUND) != 0 && Objects.equals(foreground, value);
        } else if (name == StyleConstants.Bold) {
            return (flags & MASK_BOLD) != 0 ? Boolean.TRUE.equals(value) : Boolean.FALSE.equals(value);
        } else if (name == StyleConstants.Italic) {
            return (flags & MASK_ITALIC) != 0 ? Boolean.TRUE.equals(value) : Boolean.FALSE.equals(value);
        }
        return false;
    }

    @Override
    public AttributeSet getResolveParent() {
        return null;
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
        } else if (obj instanceof AdhocColoring) {
            AdhocColoring other = (AdhocColoring) obj;
            int cc = colorCount();
            if (cc > 1) {
                return false;
            }
            int occ = other.colorCount();
            if (cc != occ || intFlags() != other.intFlags()) {
                return false;
            } else if (cc == 0) {
                return true;
            } else if (isBackgroundColor() && other.isBackgroundColor()) {
                return Objects.equals(background, other.color);
            } else if (isForegroundColor() && other.isForegroundColor()) {
                return Objects.equals(foreground, other.color);
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
    public Enumeration<?> getAttributeNames() {
        int flags = this.flags;
        if (background == null && (flags & MASK_BACKGROUND) != 0) {
            flags ^= MASK_BACKGROUND;
        }
        if (foreground == null && (flags & MASK_FOREGROUND) != 0) {
            flags ^= MASK_FOREGROUND;
        }
        return new AttrNameEnum((byte) flags);
    }

    @Override
    public String toString() {
        return "AdhocColoringMerged(active=" + isActive() + " bold=" + isBold()
                + "  italic=" + isItalic()
                + (isForegroundColor() ? " fg=" + foreground : "")
                + (isBackgroundColor() ? " bg=" + background : "")
                + ")";
    }

    @Override
    public int hashCode() {
        return AdhocColoring.hashCode(flags, isBackgroundColor()
                ? background : null, isForegroundColor() ? foreground : null);
    }
}

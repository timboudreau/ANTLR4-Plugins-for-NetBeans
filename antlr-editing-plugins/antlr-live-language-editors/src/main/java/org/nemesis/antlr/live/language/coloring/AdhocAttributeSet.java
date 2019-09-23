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

import java.awt.Color;
import java.io.Serializable;
import java.util.EnumSet;
import java.util.Enumeration;
import java.util.Set;
import javax.swing.text.AttributeSet;
import javax.swing.text.StyleConstants;
import static org.nemesis.antlr.live.language.coloring.AdhocColoring.MASK_ACTIVE;
import static org.nemesis.antlr.live.language.coloring.AdhocColoring.MASK_BACKGROUND;
import static org.nemesis.antlr.live.language.coloring.AdhocColoring.MASK_BOLD;
import static org.nemesis.antlr.live.language.coloring.AdhocColoring.MASK_FOREGROUND;
import static org.nemesis.antlr.live.language.coloring.AdhocColoring.MASK_ITALIC;
import static org.nemesis.antlr.live.language.coloring.AdhocColoring.STYLE_FLAGS;
import static org.nemesis.antlr.live.language.coloring.AdhocColoring.TYPES;

/**
 *
 * @author Tim Boudreau
 */
public interface AdhocAttributeSet extends AttributeSet, Serializable {

    default boolean isActive() {
        return (intFlags() & MASK_ACTIVE) != 0;
    }

    default boolean isBackgroundColor() {
        return (intFlags() & MASK_BACKGROUND) != 0;
    }

    default boolean isBold() {
        return (intFlags() & MASK_BOLD) != 0;
    }

    default boolean isForegroundColor() {
        return (intFlags() & MASK_FOREGROUND) != 0;
    }

    default boolean isItalic() {
        return (intFlags() & MASK_ITALIC) != 0;
    }

    default int depth() {
        return Integer.MAX_VALUE;
    }

    @Override
    default boolean isDefined(Object attrName) {
        if (StyleConstants.Bold == attrName) {
            return isBold();
        } else if (StyleConstants.Italic == attrName) {
            return isItalic();
        } else if (StyleConstants.Foreground == attrName) {
            return isForegroundColor();
        } else if (StyleConstants.Background == attrName) {
            return isBackgroundColor();
        }
        return false;
    }

    @Override
    default int getAttributeCount() {
        int flags = intFlags();
        if ((flags & MASK_ACTIVE) == 0) {
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

    /**
     * Create a new Attribute set that represents the passed one with
     * all flags that would conflict with this one turned off.
     *
     * @param other Another attribute set
     * @return A new attribute set
     */
    default AdhocAttributeSet mute(AdhocAttributeSet other) {
        if (other == this) {
            return this;
        }
        int flags = other.intFlags();
        for (int check : new int[]{MASK_BACKGROUND, MASK_FOREGROUND, MASK_ITALIC, MASK_BOLD}) {
            if ((this.intFlags() & check) != 0) {
                flags ^= check;
            }
        }
        if (other.isActive()) {
            flags |= MASK_ACTIVE;
        }
        AdhocAttributeSet result;
        if ((flags & (MASK_FOREGROUND | MASK_BACKGROUND)) != 0) {
            Color fg = (Color) other.getAttribute(StyleConstants.Foreground);
            Color bg = (Color) other.getAttribute(StyleConstants.Background);
            if (fg != null && bg != null) {
                result = new AdhocColoringMerged(flags, bg, fg);
            } else if (fg != null && (flags & MASK_FOREGROUND) != 0) {
                result = new AdhocColoring(flags ^ MASK_BACKGROUND, fg);
            } else if (bg != null && (flags & MASK_BACKGROUND) != 0) {
                result = new AdhocColoring(flags ^ MASK_FOREGROUND, bg);
            } else {
                result = new AdhocColoring(flags ^ (MASK_FOREGROUND | MASK_BACKGROUND), Color.BLACK);
            }
        } else if ((flags & MASK_FOREGROUND) != 0) {
            result = new AdhocColoring(flags, other.colorAttribute());
        } else if ((flags & MASK_BACKGROUND) != 0) {
            result = new AdhocColoring(flags, other.colorAttribute());
        } else {
            result = new AdhocColoring(flags, Color.BLACK);
        }
        return result;
    }

    int intFlags();

    default Color colorAttribute() {
        Color result = (Color) getAttribute(StyleConstants.Foreground);
        if (result == null) {
            return (Color) getAttribute(StyleConstants.Background);
        }
        return result;
    }

    /**
     * Determine if this attribute will have no effect - no flags that
     * affect appearance are active (but the ACTIVE flag may be).
     *
     * @return True if it is empty
     */
    default boolean isEmpty() {
        return (intFlags() & (MASK_FOREGROUND | MASK_BACKGROUND | MASK_ITALIC | MASK_BOLD)) == 0;
    }

    /**
     * Get the set of flags as a friendly enum.
     *
     * @return The set of flags
     */
    default Set<AttrTypes> flags() {
        int flags = intFlags();
        Set<AttrTypes> result = EnumSet.noneOf(AttrTypes.class);
        for (int i = 0; i < STYLE_FLAGS.length; i++) {
            if ((flags & STYLE_FLAGS[i]) != 0) {
                result.add(TYPES[i]);
            }
        }
        if (isActive()) {
            result.add(AttrTypes.ACTIVE);
        }
        return result;
    }

    @Override
    default Enumeration<?> getAttributeNames() {
        return new AttrNameEnum((byte) intFlags());
    }

    default boolean containsFlag(AttrTypes flag) {
        return (intFlags() & flag.maskValue()) != 0;
    }

    @Override
    default boolean containsAttributes(AttributeSet attributes) {
        Enumeration<?> en = attributes.getAttributeNames();
        while (en.hasMoreElements()) {
            Object name = en.nextElement();
            if (!containsAttribute(name, attributes.getAttribute(name))) {
                return false;
            }
        }
        return true;
    }

    default int colorCount() {
        int result = isBackgroundColor() ? 1 : 0;
        result += isForegroundColor() ? 1 : 0;
        return result;
    }
}

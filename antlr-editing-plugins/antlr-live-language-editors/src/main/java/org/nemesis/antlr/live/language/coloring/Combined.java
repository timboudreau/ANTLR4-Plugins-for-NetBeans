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

import com.mastfrog.util.collections.ArrayUtils;
import com.mastfrog.util.collections.CollectionUtils;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import javax.swing.text.AttributeSet;

/**
 * Should be safe to get rid of this - it's a fallback in case we are
 * handed an alien AttributeSet.
 *
 * @author Tim Boudreau
 */
class Combined implements AdhocAttributeSet {

    private final AdhocAttributeSet[] colorings;

    public Combined(AdhocAttributeSet[] colorings) {
        this.colorings = colorings;
    }

    public int depth() {
        int result = 0;
        for (AdhocAttributeSet a : colorings) {
            result = Math.max(a.depth(), result);
        }
        return result;
    }

    public Combined combine(AdhocAttributeSet s) {
        if (s == this) {
            return this;
        }
        if (s instanceof Combined) {
            Combined c = (Combined) s;
            AdhocAttributeSet[] all = ArrayUtils.concatenate(colorings, c.colorings);
            return new Combined(all);
        } else {
            AdhocAttributeSet[] nue = new AdhocAttributeSet[colorings.length + 1];
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

    @Override
    public boolean isActive() {
        for (AdhocAttributeSet a : colorings) {
            if (a.isActive()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isBackgroundColor() {
        for (AdhocAttributeSet a : colorings) {
            if (a.isActive() && a.isBackgroundColor()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isBold() {
        for (AdhocAttributeSet a : colorings) {
            if (a.isActive() && a.isBold()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isForegroundColor() {
        for (AdhocAttributeSet a : colorings) {
            if (a.isActive() && a.isForegroundColor()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isItalic() {
        for (AdhocAttributeSet a : colorings) {
            if (a.isActive() && a.isItalic()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int intFlags() {
        int result = 0;
        for (AdhocAttributeSet a : colorings) {
            result |= a.intFlags();
        }
        return result;
    }
}

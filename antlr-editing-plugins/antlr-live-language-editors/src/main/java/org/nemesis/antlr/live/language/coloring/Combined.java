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

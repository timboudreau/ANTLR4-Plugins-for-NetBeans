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

import java.util.Enumeration;
import java.util.Objects;
import javax.swing.text.AttributeSet;

/**
 * Wraps an AttributeSet and adds a depth value to it.
 *
 * @author Tim Boudreau
 */
class DepthAttributeSet implements AdhocAttributeSet {

    private AdhocAttributeSet delegate;
    private final int depth;
    private static long seq = Long.MIN_VALUE;
    long creationOrder = seq++;

    public DepthAttributeSet(AdhocAttributeSet attrSet, int depth) {
        this.delegate = attrSet;
        this.depth = depth;
    }

    AdhocAttributeSet delegate() {
        return delegate;
    }

    @Override
    public int depth() {
        return depth;
    }

    @Override
    public boolean isActive() {
        return delegate.isActive();
    }

    @Override
    public boolean isBackgroundColor() {
        return delegate.isBackgroundColor();
    }

    @Override
    public boolean isBold() {
        return delegate.isBold();
    }

    @Override
    public boolean isForegroundColor() {
        return delegate.isForegroundColor();
    }

    @Override
    public boolean isItalic() {
        return delegate.isItalic();
    }

    @Override
    public int getAttributeCount() {
        return delegate.getAttributeCount();
    }

    @Override
    public boolean isDefined(Object attrName) {
        return delegate.isDefined(attrName);
    }

    @Override
    public boolean isEqual(AttributeSet attr) {
        return delegate.isEqual(attr);
    }

    @Override
    public AttributeSet copyAttributes() {
        return delegate.copyAttributes();
    }

    @Override
    public Object getAttribute(Object key) {
        return delegate.getAttribute(key);
    }

    @Override
    public Enumeration<?> getAttributeNames() {
        return delegate.getAttributeNames();
    }

    @Override
    public boolean containsAttribute(Object name, Object value) {
        return delegate.containsAttribute(name, value);
    }

    @Override
    public boolean containsAttributes(AttributeSet attributes) {
        return delegate.containsAttributes(attributes);
    }

    @Override
    public AttributeSet getResolveParent() {
        return delegate.getResolveParent();
    }

    @Override
    public String toString() {
        return "DepthAttributeSet(" + depth + " - " + delegate + ")";
    }

    @Override
    public int intFlags() {
        return delegate.intFlags();
    }

    @Override
    public AdhocAttributeSet mute(AdhocAttributeSet other) {
        delegate = delegate.mute(other);
        return this;
    }

    @Override
    public int hashCode() {
        return delegate.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (obj instanceof DepthAttributeSet) {
            DepthAttributeSet other = (DepthAttributeSet) obj;
            return Objects.equals(this.delegate, other.delegate);
        } else if (obj instanceof AdhocAttributeSet) {
            return ((AdhocAttributeSet) obj).isEqual(delegate);
        } else if (obj instanceof AttributeSet) {
            return isEqual((AttributeSet) obj);
        }
        return false;
    }
}

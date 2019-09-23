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

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
package org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.extraction.src.spi;

import java.io.IOException;
import java.io.Serializable;
import java.util.Objects;
import org.antlr.v4.runtime.CharStream;

/**
 * Abstraction for files, documents or even text strings which can provide
 * input, and can resolve sibling documents/files/whatever.
 *
 * @author Tim Boudreau
 */
public abstract class GrammarSourceImplementation<T> implements Serializable {

    protected final Class<T> type;

    protected GrammarSourceImplementation(Class<T> type) {
        this.type = type;
    }

    public final Class<T> type() {
        return type;
    }

    /**
     * Get the identifying name or path of this object. This is primarily for
     * logging purposes and is not required to uniquely identify it.
     *
     * @return A name
     */
    public abstract String name();

    /**
     * Get an antlr character stream from this object.
     *
     * @return A character stream
     * @throws IOException If something goes wrong
     */
    public abstract CharStream stream() throws IOException;

    /**
     * Resolve an "imported" grammar source, relative to this one. The exact
     * meaning of this is language-dependent.
     *
     * @param name The name of the thing to import
     * @return A grammar source over that
     */
    public abstract GrammarSourceImplementation<?> resolveImport(String name);

    /**
     * Get the object that is the source document, string, file or whatever that
     * this grammar source creates character streams over.
     *
     * @return The source object
     */
    public abstract T source();

    /**
     * Convenience method for looking up capabilities. First calls lookupImpl(),
     * and if that returns non-null, returns that result; then tests if the this
     * is an instance of the type and if so, returns this cast to the type; if
     * not, tests if the return value of source() is an instance of the type,
     * and casts and returns that if it is one.
     *
     * @param <R> The type
     * @param type The type
     * @return An instance of R or null
     */
    public final <R> R lookup(Class<R> type) {
        R result = lookupImpl(type);
        if (result != null) {
            return result;
        }
        if (type.isInstance(this)) {
            return type.cast(this);
        }
        T src = source();
        if (type.isInstance(src)) {
            return type.cast(src);
        }
        return null;
    }

    /**
     * Optional method to implement additional lookup functionality. The default
     * implementation simply returns null.
     *
     * @param <R> The type requested
     * @param type The type requested
     * @return An instance of R or null
     */
    protected <R> R lookupImpl(Class<R> type) {
        return null;
    }

    /**
     * Get the last modified date of the underlying source document / file /
     * whatever, if known. The default implementation returns 0.
     *
     * @return A unix timestamp in millis-since-epoch
     * @throws IOException If something goes wrong
     */
    public long lastModified() throws IOException {
        return 0;
    }

    /**
     * Tests exclusively on equality of the return value of source().
     *
     * @param o Another object
     * @return True if they are equal
     */
    @Override
    public final boolean equals(Object o) {
        if (o == null) {
            return false;
        } else if (o == this) {
            return true;
        } else if (o instanceof GrammarSourceImplementation<?>) {
            GrammarSourceImplementation<?> impl = (GrammarSourceImplementation<?>) o;
            return Objects.equals(source(), impl.source());
        }
        return false;
    }

    /**
     * Returns the hash code of the return value of source().
     *
     * @return A hash code
     */
    public final int hashCode() {
        return Objects.hashCode(source());
    }

    /**
     * Default implementation returns name() + source(). If source() may result
     * in an object whose toString() method returns voluminous output, overide
     * this method.
     *
     * @return A string
     */
    @Override
    public String toString() {
        return name() + "{source=" + source() + "}";
    }
}

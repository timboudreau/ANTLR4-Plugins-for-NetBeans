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
package org.nemesis.source.spi;

import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Path;
import java.util.Objects;
import org.antlr.v4.runtime.CharStream;
import org.nemesis.source.impl.GSAccessor;

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
        return DocumentAdapterRegistry.getDefault()
                .converters().convert(src, type);
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
            Object a = source();
            GrammarSourceImplementation<?> impl = (GrammarSourceImplementation<?>) o;
            Object b = impl.source();
            if (a == this) { // GrammarSource.NONE
                return b == this;
            }
            if (Objects.equals(source(), impl.source())) {
                return true;
            }
            String aid = computeId();
            String bid = impl.computeId();
            if (Objects.equals(aid, bid)) {
                return true;
            }
            return false;
        }
        return false;
    }

    /**
     * Returns the hash code of the return value of source().
     *
     * @return A hash code
     */
    @Override
    public final int hashCode() {
        T o = source();
        if (o == this) { // GrammarSource.NONE
            return 1;
        }
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

    /**
     * Override this method to compute an id which is consistent when two
     * grammar sources represent the same file/thing even when the source object
     * varies, such that an equality test of the ID of a File and that of a
     * Document over that file are <code>equals()</code>.
     *
     * @return Tries to lookup a Path instance on itself; if one is present,
     * calls <code>hashString(path.toUri().toString());
     * fails over to a base-36 string of the identity hash code of the source
     * object.
     */
    private String id;
    public String computeId() {
        if (id != null) {
            return id;
        }
        Path op = lookup(Path.class);
        if (op != null) {
            return id = hashString(op.toUri().toString());
        }
        Object source = source();
        if (source == null) {
            return id = "0";
        }
        return id = Integer.toString(System.identityHashCode(source), 36);
    }

    protected static final String hashString(String string) {
        return GSAccessor.getDefault().hashString(string);
    }
}

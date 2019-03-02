/*
BSD License

Copyright (c) 2016, Frédéric Yvon Vinet
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
package org.nemesis.source.spi;

import java.io.Serializable;
import java.util.Optional;
import org.nemesis.source.api.RelativeResolver;
import org.nemesis.source.impl.RRAccessor;

/**
 * SPI for RelativeResolver.  These are registered by MIME type and looked up
 * using Lookups.forPath.
 *
 * @author Tim Boudreau
 */
public abstract class RelativeResolverImplementation<T> implements Serializable {

    protected final Class<T> type;

    protected RelativeResolverImplementation(Class<T> type) {
        this.type = type;
    }

    public final Class<T> type() {
        return type;
    }

    /**
     * Resolve an "imported" file - depending on the MIME type this may
     * have very different behavior, such as looking up a Java file on
     * the project classpath, or looking up an imported ANTLR grammar
     * in a parent directory named "imports".
     *
     * @param relativeTo The object to look up relative to
     * @param name The name of the file to look up, as it is described in the
     * original document
     * @return An optional which may contain a reference to the object in question
     */
    public abstract Optional<T> resolve(T relativeTo, String name);

    public static <T> RelativeResolverImplementation<T> noop(Class<T> type) {
        return new Noop<>(type);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" + type.getName() + "}";
    }

    RelativeResolver<T> toAPI() {
        return RRAccessor.getDefault().newResolver(this);
    }

    private static final class Noop<T> extends RelativeResolverImplementation<T> {

        Noop(Class<T> type) {
            super(type);
        }

        @Override
        public Optional<T> resolve(T relativeTo, String name) {
            return Optional.empty();
        }
    }
}

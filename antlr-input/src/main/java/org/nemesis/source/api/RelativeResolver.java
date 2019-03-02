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
package org.nemesis.source.api;

import java.io.Serializable;
import java.util.Objects;
import java.util.Optional;
import org.nemesis.source.impl.RRAccessor;
import org.nemesis.source.spi.RelativeResolverImplementation;

/**
 *
 * @author Tim Boudreau
 */
public final class RelativeResolver<T> implements Serializable {

    final RelativeResolverImplementation<T> spi;

    private RelativeResolver(RelativeResolverImplementation<T> type) {
        this.spi = type;
    }

    public final Class<T> type() {
        return RRAccessor.getDefault().typeOf(spi);
    }

    public final Optional<T> resolve(T relativeTo, String name) {
        return RRAccessor.getDefault().resolve(spi, relativeTo, name);
    }

    public static <T> RelativeResolver<T> noop(Class<T> type) {
        return new RelativeResolver<>(new Noop<>(type));
    }

    RelativeResolverImplementation<T> implementation() {
        return spi;
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

    @Override
    public final int hashCode() {
        int hash = 5;
        hash = 37 * hash + Objects.hashCode(this.spi);
        return hash;
    }

    @Override
    public final boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final RelativeResolver<?> other = (RelativeResolver<?>) obj;
        return Objects.equals(this.spi, other.spi);
    }

    static final class RRAccessorImpl extends RRAccessor {

        @Override
        public <T> RelativeResolver<T> newResolver(RelativeResolverImplementation<T> impl) {
            return new RelativeResolver<>(impl);
        }

        @Override
        public <T> RelativeResolverImplementation<T> implementation(RelativeResolver<T> resolver) {
            return resolver.implementation();
        }
    }

    static {
        RRAccessor.DEFAULT = new RRAccessorImpl();
    }
}

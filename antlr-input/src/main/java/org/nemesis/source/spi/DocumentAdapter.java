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

import static com.mastfrog.util.preconditions.Checks.notNull;
import java.util.Objects;
import java.util.function.Function;

/**
 * 
 *
 * @author Tim Boudreau
 */
public class DocumentAdapter<F, T> {

    final Class<F> fromType;
    final Class<T> toType;
    final Function<F, T> convertTo;
    final Function<T, F> convertFrom;

    protected DocumentAdapter(Class<F> fromType, Class<T> toType, Function<F, T> convertTo, Function<T, F> convertFrom) {
        this.fromType = notNull("fromType", fromType);
        this.toType = notNull("toType", toType);
        this.convertTo = notNull("convertTo", convertTo);
        this.convertFrom = notNull("convertFrom", convertFrom);
    }

    Class<F> fromType() {
        return fromType;
    }

    Class<T> toType() {
        return toType;
    }

    Function<T, F> convertFrom() {
        return convertFrom;
    }

    Function<F, T> convertTo() {
        return convertTo;
    }

    @Override
    public String toString() {
        return fromType.getName() + "->" + toType.getName();
    }

    @Override
    public final int hashCode() {
        int hash = 3;
        hash = 89 * hash + Objects.hashCode(this.fromType);
        hash = 89 * hash + Objects.hashCode(this.toType);
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
        final DocumentAdapter<?, ?> other = (DocumentAdapter<?, ?>) obj;
        if (!Objects.equals(this.fromType, other.fromType)) {
            return false;
        }
        return Objects.equals(this.toType, other.toType);
    }

    static <T, N> RelativeResolverImplementation<N> adapt(RelativeResolverImplementation<T> orig,
            Class<N> to, Function<? super T, ? extends N> toTo, Function<? super N, ? extends T> toFrom) {
        return new AdaptedRelativeResolverImplementation<>(orig, to, toTo, toFrom);
    }
}

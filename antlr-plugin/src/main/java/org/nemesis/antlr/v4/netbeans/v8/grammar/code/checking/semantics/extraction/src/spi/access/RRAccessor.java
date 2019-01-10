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


package org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.extraction.src.spi.access;

import java.util.Optional;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.extraction.src.RelativeResolver;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.extraction.src.spi.RelativeResolverImplementation;
import org.openide.util.Exceptions;

/**
 *
 * @author Tim Boudreau
 */
public abstract class RRAccessor {
    public static RRAccessor DEFAULT;

    public static RRAccessor getDefault() {
        if (DEFAULT != null) {
            return DEFAULT;
        }
        Class<?> type = RelativeResolver.class;
        try {
            Class.forName(type.getName(), true, type.getClassLoader());
        } catch (ClassNotFoundException ex) {
            Exceptions.printStackTrace(ex);
        }
        assert DEFAULT != null : "The DEFAULT field must be initialized";
        return DEFAULT;
    }

    public <T> Class<T> typeOf(RelativeResolverImplementation<T> resolver) {
        return resolver.type();
    }

    public <T> Optional<T> resolve(RelativeResolverImplementation<T> resolver, T relativeTo, String name) {
        return resolver.resolve(relativeTo, name);
    }

    public abstract <T> RelativeResolver<T> newResolver(RelativeResolverImplementation<T> impl);
}

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
package org.nemesis.extraction.nb;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.nemesis.source.spi.RelativeResolverAdapter;
import org.nemesis.source.spi.RelativeResolverImplementation;
import org.nemesis.source.spi.RelativeResolverRegistry;
import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 * @author Tim Boudreau
 */
@ServiceProvider(service = RelativeResolverRegistry.class)
public class RelativeResolverRegistryImpl extends RelativeResolverRegistry {

    private static final String RESOLVERS_PATH = "antlr-languages/relative-resolvers/";

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    protected List<? extends RelativeResolverImplementation<?>> allResolvers(String mimeType) {
        String path = RESOLVERS_PATH + mimeType;
        Lookup lkp = Lookups.forPath(path);
        Collection<? extends RelativeResolverImplementation> forMimeType = lkp.lookupAll(RelativeResolverImplementation.class);
        List<RelativeResolverImplementation<?>> result = new ArrayList<>();
        for (RelativeResolverImplementation<?> r : forMimeType) {
            result.add(r);
        }
        return result;
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    protected List<? extends RelativeResolverAdapter<?, ?>> allAdapters() {
        List<RelativeResolverAdapter<?, ?>> result = new ArrayList<>();
        Collection<? extends RelativeResolverAdapter> forMimeType = Lookup.getDefault().lookupAll(RelativeResolverAdapter.class);
        for (RelativeResolverAdapter r : forMimeType) {
            result.add(r);
        }
        return result;
    }
}

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
package org.nemesis.antlr.nbinput;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.nemesis.source.spi.DocumentAdapter;
import org.nemesis.source.spi.RelativeResolverImplementation;
import org.nemesis.source.spi.DocumentAdapterRegistry;
import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 * @author Tim Boudreau
 */
@ServiceProvider(service = DocumentAdapterRegistry.class)
public class RelativeResolverRegistryImpl extends DocumentAdapterRegistry {

    private static final String RESOLVERS_PATH = "antlr-languages/relative-resolvers/";

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    protected List<? extends RelativeResolverImplementation<?>>
            allResolvers(String mimeType) {
        String path = RESOLVERS_PATH + mimeType;
        Lookup lkp = Lookups.forPath(path);
        Collection<? extends RelativeResolverImplementation> forMimeType
                = lkp.lookupAll(RelativeResolverImplementation.class);
        List<RelativeResolverImplementation<?>> result = new ArrayList<>(
                forMimeType.size());
        for (RelativeResolverImplementation<?> r : forMimeType) {
            result.add(r);
        }
        return result;
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    protected List<? extends DocumentAdapter<?, ?>> allAdapters() {
        List<DocumentAdapter<?, ?>> result = new ArrayList<>();
        Collection<? extends DocumentAdapter> forMimeType
                = Lookup.getDefault().lookupAll(DocumentAdapter.class);
        for (DocumentAdapter r : forMimeType) {
            result.add(r);
        }
        return result;
    }
}

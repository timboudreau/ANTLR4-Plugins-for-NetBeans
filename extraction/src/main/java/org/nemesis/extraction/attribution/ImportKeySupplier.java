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
package org.nemesis.extraction.attribution;

import java.util.Set;
import java.util.function.Supplier;
import org.nemesis.data.named.NamedSemanticRegion;
import org.nemesis.extraction.Extraction;
import org.nemesis.extraction.key.NamedRegionKey;
import org.nemesis.source.api.GrammarSource;

/**
 * Interface which can be implemented by an ImportFinder, allowing the
 * infrastructure to hyperlink or otherwise highlight semantic regions which
 * indicate imports.
 *
 * @author Tim Boudreau
 */
public interface ImportKeySupplier extends Supplier<NamedRegionKey<?>[]> {

    default <T extends Enum<T>> void importsForKey(
            Set<? super GrammarSource<?>> result,
            NamedRegionKey<T> k,
            Extraction importer,
            Set<? super NamedSemanticRegion<? extends Enum<?>>> notFound) {
        if (this instanceof ImportFinder) {
            Set<GrammarSource<?>> all = ((ImportFinder) this).allImports(importer, notFound);
            result.addAll(all);
        }
    }
}

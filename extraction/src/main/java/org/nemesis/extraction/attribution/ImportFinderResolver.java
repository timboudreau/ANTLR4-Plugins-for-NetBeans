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
import org.antlr.v4.runtime.ParserRuleContext;
import org.nemesis.extraction.Extraction;
import org.nemesis.extraction.Extractors;
import org.nemesis.extraction.UnknownNameReference;
import org.nemesis.extraction.key.NamedRegionKey;
import org.nemesis.source.api.GrammarSource;

/**
 *
 * @author Tim Boudreau
 */
final class ImportFinderResolver<K extends Enum<K>> implements SimpleRegisterableResolver<K, GrammarSource<?>> {

    private final NamedRegionKey<K> key;
    private final ImportFinder finder;

    public ImportFinderResolver(NamedRegionKey<K> key, ImportFinder finder) {
        this.key = key;
        this.finder = finder;
    }

    @Override
    public Set<GrammarSource<?>> importsThatCouldContain(Extraction extraction, UnknownNameReference<K> ref) {
        return finder.possibleImportersOf(ref, extraction);
    }

    @Override
    public NamedRegionKey<K> key() {
        return key;
    }

    @Override
    public Extraction resolveImport(GrammarSource<?> g, Extraction in) {
        if (g != null) {
            Class<? extends ParserRuleContext> ruleType = in.documentRootType();
            return Extractors.getDefault().extract(in.mimeType(), g, ruleType);
        }
        return null;
    }

    @Override
    public Class<K> type() {
        return key.type();
    }
}

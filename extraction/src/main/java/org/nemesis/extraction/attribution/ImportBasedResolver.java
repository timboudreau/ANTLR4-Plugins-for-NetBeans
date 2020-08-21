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

import java.lang.ref.WeakReference;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.text.Document;
import org.antlr.v4.runtime.ParserRuleContext;
import org.nemesis.extraction.Extraction;
import org.nemesis.extraction.Extractors;
import org.nemesis.extraction.key.NamedRegionKey;
import org.nemesis.source.api.GrammarSource;

/**
 * Specialization of SimpleRegisterableResolver which simply extracts named
 * region and tries to look them up as source files using
 * GrammarSource.resolveRelative().
 *
 * @author Tim Boudreau
 */
public interface ImportBasedResolver<K extends Enum<K>> extends SimpleRegisterableResolver<K, String> {

    default Extraction resolveImport(String name, Extraction in) {
        // XXX use ParserManager?
        GrammarSource<?> g = in.resolveRelative(name);
        if (g != null) {
            // Try doing this the cheaper way
            Optional<Document> docOpt = g.lookup(Document.class);
            if (docOpt != null) {
                AtomicReference<WeakReference<Extraction>> ref
                        = (AtomicReference<WeakReference<Extraction>>) docOpt.get()
                                .getProperty("_ext");
                if (ref != null) {
                    WeakReference<Extraction> wr = ref.get();
                    if (wr != null) {
                        Extraction result = wr.get();
                        if (result != null && !result.isSourceProbablyModifiedSinceCreation()) {
                            return result;
                        }
                    }
                }
            }
            Class<? extends ParserRuleContext> ruleType = in.documentRootType();
            return Extractors.getDefault().extract(in.mimeType(), g, ruleType);
        }
        return null;
    }

    static <K extends Enum<K>> ImportBasedResolver<K> create(NamedRegionKey<K> key, NamedRegionKey<?>... importKeys) {
        return new DefaultImportBasedResolver(key, importKeys);
    }
}

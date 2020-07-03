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
package com.mastfrog.antlr.utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.BiConsumer;
import javax.swing.text.Document;
import org.openide.util.Lookup;

/**
 * Generic code completion, registerable in the default lookup.
 *
 * @author Tim Boudreau
 */
public abstract class CompletionsSupplier {

    public static CompletionsSupplier forMimeType(String mimeType) {
        Collection<? extends CompletionsSupplier> all = Lookup.getDefault().lookupAll(CompletionsSupplier.class);
        if (all.isEmpty()) {
            return Dummy.INSTANCE;
        } else if (all.size() == 1) {
            return all.iterator().next();
        } else {
            return new MetaSupplier(all);
        }
    }

    public abstract Completer forDocument(Document document);

    protected Completer noop() {
        return Dummy.INSTANCE;
    }

    public interface Completer {

        public void namesForRule(int parserRuleId, String optionalPrefix,
                int maxResultsPerKey, String optionalSuffix, BiConsumer<String, Enum<?>> names);
    }

    private static final class Dummy extends CompletionsSupplier implements Completer {

        private static final Dummy INSTANCE = new Dummy();

        @Override
        public Completer forDocument(Document document) {
            return this;
        }

        @Override
        public void namesForRule(int parserRuleId, String optionalPrefix, 
                int maxResultsPerKey, String optionalSuffix,
                BiConsumer<String, Enum<?>> names) {
            // do nothing
        }
    }

    private static final class MetaSupplier extends CompletionsSupplier {

        private final Collection<? extends CompletionsSupplier> all;

        public MetaSupplier(Collection<? extends CompletionsSupplier> all) {
            this.all = all;
        }

        @Override
        public Completer forDocument(Document document) {
            List<Completer> completers = new ArrayList<>(all.size());
            for (CompletionsSupplier c : all) {
                completers.add(c.forDocument(document));
            }
            return new MetaCompleter(completers);
        }

    }

    private static final class MetaCompleter implements Completer {

        private final List<Completer> completers;

        private MetaCompleter(List<Completer> completers) {
            this.completers = completers;
        }

        @Override
        public void namesForRule(int parserRuleId, String optionalPrefix, int maxResultsPerKey, String optionalSuffix, BiConsumer<String, Enum<?>> names) {
            for (Completer c : completers) {
                c.namesForRule(parserRuleId, optionalPrefix, maxResultsPerKey, optionalSuffix, names);
            }
        }
    }
}

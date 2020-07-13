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
package com.mastfrog.antlr.code.completion.spi;

import java.util.function.BiConsumer;
import javax.swing.text.Document;

/**
 * Generic code completion, registerable in the default lookup.
 *
 * @author Tim Boudreau
 */
public abstract class CompletionsSupplier {

    public static boolean isNoOp(Completer completer) {
        return completer == Dummy.INSTANCE;
    }

    public abstract Completer forDocument(Document document);

    protected Completer noop() {
        return Dummy.INSTANCE;
    }

    private static final class Dummy extends CompletionsSupplier implements Completer {

        static final Dummy INSTANCE = new Dummy();

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
}

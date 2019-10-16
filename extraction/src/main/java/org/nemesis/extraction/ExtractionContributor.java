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
package org.nemesis.extraction;

import java.util.function.Consumer;
import java.util.function.Function;
import org.antlr.v4.runtime.ParserRuleContext;
import org.nemesis.extraction.key.RegionsKey;

/**
 *
 * @author Tim Boudreau
 */
public interface ExtractionContributor<T extends ParserRuleContext> extends Consumer<ExtractorBuilder<? super T>> {

    Class<T> type();

    @SuppressWarnings("unchecked")
    default <R extends ParserRuleContext> Consumer<ExtractorBuilder<? super R>> castIfCompatible(Class<R> type) {
        if (type.isAssignableFrom(type())) {
            return (Consumer<ExtractorBuilder<? super R>>) this;
        }
        return null;
    }

    static ExtractionContributor<ParserRuleContext> createGenericRuleRegionExtractor(RegionsKey<String> key, Function<ParserRuleContext, String> convert, int... ruleIds) {
        return new GenericRuleIdExtractionContributor<>(convert, ParserRuleContext.class, key, ruleIds);
    }
}

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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import org.antlr.v4.runtime.ParserRuleContext;
import org.nemesis.extraction.key.RegionsKey;
import org.nemesis.extraction.key.SingletonKey;

/**
 *
 * @author Tim Boudreau
 */
public class ExtractorBuilder<T extends ParserRuleContext> {

    private final Class<T> documentRootType;

    private final Set<RegionExtractionStrategies<?>> regionsInfo2 = new HashSet<>();
    private final Set<NamesAndReferencesExtractionStrategy<?>> nameExtractors = new HashSet<>();
    private final Map<SingletonKey<?>, SingletonExtractionStrategies<?>> singles = new HashMap<>();
    private final String mimeType;
    private Function<Supplier<Extraction>, Extraction> wrapExecution;

    ExtractorBuilder(Class<T> entryPoint, String mimeType) {
        this.documentRootType = entryPoint;
        this.mimeType = mimeType;
    }

    public Extractor<T> build() {
        return new Extractor<>(documentRootType, nameExtractors, regionsInfo2, singles, mimeType, wrapExecution);
    }

    /**
     * Wrap every invocation of extraction for this mime type with some code
     * which, for example, can set some state in a ThreadLocal for the
     * extraction code to cache data, then clear it on completion. The function
     * <i>MUST</i> call the passed supplier and return its result, regardless of
     * its state.  Example usage:
     * <pre>
     *
     * bldr.wrappingExecutionWith(runner -> {
     *  someThreadLocal.set(new HeteroMap()); // a cache
     *  try {
     *     return runner.get();
     *  } finally {
     *     someThreadLocal.remove();
     *  }
     * }).extractionRegions(SOME_KEY).whenRuleType(SomeParserContext.class)
     * .extractingRegionsWith(rule -> {
     *    HeteroMap cache = someThreadLocal.get();
     *    if (cache.contains(someKey)) {
     *        ... don't recompute something
     *    } else {
     *         ... compute it
     *    }
     * }).finishRegionExtractor();
     * </pre>
     * Basically there are a few tasks in extraction, such as finding the indices
     * of a node's siblings, where you will do a lot of extra work if each node
     * walks up to its ancestor and traverses its siblings to compute a slightly
     * different number than the last one did, and you're better off writing a
     * visitor that does it once on first invocation, and then looks up the
     * cached data for each matching node that's visited.
     *
     * @param wrapper A function which will call the supplier passed to it,
     * synchronously, possibly aftter some pre- and post-work.
     *
     * @return this
     */
    public ExtractorBuilder<T> wrappingExtractionWith(Function<Supplier<Extraction>, Extraction> wrapper) {
        if (wrapExecution != null) {
            final Function<Supplier<Extraction>, Extraction> old = wrapExecution;
            wrapExecution = supp -> {
                return old.apply(() -> {
                    return wrapper.apply(supp);
                });
            };
        } else {
            wrapExecution = wrapper;
        }
        return this;
    }

    /**
     * Extract named regions, which may each be assigned an enum value of the
     * passed enum type.
     *
     * @param <K> The enum type
     * @param type The enum type
     * @return A builder
     */
    public <K extends Enum<K>> NamedRegionExtractorBuilder<K, ExtractorBuilder<T>> extractNamedRegionsKeyedTo(Class<K> type) {
        return new NamedRegionExtractorBuilder<>(type, ne -> {
            nameExtractors.add(ne);
            return this;
        });
    }

    void addRegionEx(RegionExtractionStrategies<?> info) {
        regionsInfo2.add(info);
    }

    /**
     * Extract <i>singleton data</i> from a source, retrievable using the passed
     * key. This is data which should occur exactly <i>once</i> in a source
     * file, if present. You can retrieve a {@link SingletonEncounters} instance
     * from the resulting {@link Extraction} which handles the case that the
     * data in question is actually found more than once.
     *
     * @param <KeyType> The key type
     * @param key The key
     * @return A builder
     */
    public <KeyType> SingletonExtractionBuilder<T, KeyType> extractingSingletonUnder(SingletonKey<KeyType> key) {
        return new SingletonExtractionBuilder<>(this, key);
    }

    /**
     * Extract unnamed, but possibly nested, semantic regions which use the
     * passed key type for data associated with the region.
     *
     * @param <KeyType> The key type
     * @param key The key to use for retrieval
     * @return A builder
     */
    public <KeyType> RegionExtractionBuilder<T, KeyType> extractingRegionsUnder(RegionsKey<KeyType> key) {
        return new RegionExtractionBuilder<>(this, key);
    }

    @SuppressWarnings("unchecked")
    <KeyType> ExtractorBuilder<T> addSingletons(SingletonKey<KeyType> key, Set<SingletonExtractionStrategy<KeyType, ?>> single) {
        if (singles.containsKey(key)) {
            SingletonExtractionStrategies<KeyType> infos = (SingletonExtractionStrategies<KeyType>) singles.get(key);
            infos.addAll(single);
            return this;
        }
        SingletonExtractionStrategies<KeyType> infos = new SingletonExtractionStrategies<>(key, single);
        singles.put(key, infos);
        return this;
    }

}

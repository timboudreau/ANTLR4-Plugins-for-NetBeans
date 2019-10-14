package org.nemesis.extraction;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
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

    ExtractorBuilder(Class<T> entryPoint, String mimeType) {
        this.documentRootType = entryPoint;
        this.mimeType = mimeType;
    }

    public Extractor<T> build() {
        return new Extractor<>(documentRootType, nameExtractors, regionsInfo2, singles, mimeType);
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

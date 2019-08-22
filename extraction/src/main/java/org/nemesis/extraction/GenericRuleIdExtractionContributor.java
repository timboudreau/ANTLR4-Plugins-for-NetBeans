package org.nemesis.extraction;

import java.util.function.Function;
import org.antlr.v4.runtime.ParserRuleContext;
import org.nemesis.extraction.key.RegionsKey;

/**
 *
 * @author Tim Boudreau
 */
final class GenericRuleIdExtractionContributor<T extends ParserRuleContext> implements ExtractionContributor<T> {

    private final RegionsKey<String> key;
    private final int[] ids;
    private final Function<ParserRuleContext, String> convert;
    private final Class<T> type;

    GenericRuleIdExtractionContributor(Function<ParserRuleContext, String> convert, Class<T> type, RegionsKey<String> key, int... ruleIds) {
        this.key = key;
        this.ids = ruleIds;
        this.convert = convert;
        this.type = type;
    }

    @Override
    public Class<T> type() {
        return type;
    }

    @Override
    public void accept(ExtractorBuilder<? super T> b) {
        b.extractingRegionsUnder(key).whenRuleIdIn(ids).extractingKeyWith(convert);
    }

}

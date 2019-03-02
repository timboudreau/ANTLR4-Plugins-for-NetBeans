package org.nemesis.extraction;

import java.util.function.Consumer;
import org.antlr.v4.runtime.ParserRuleContext;

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

}

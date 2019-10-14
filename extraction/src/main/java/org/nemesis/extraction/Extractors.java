package org.nemesis.extraction;

import org.antlr.v4.runtime.ParserRuleContext;
import org.nemesis.source.api.GrammarSource;
import org.openide.util.Lookup;

/**
 *
 * @author Tim Boudreau
 */
public abstract class Extractors {

    public static Extractors getDefault() {
        Extractors result = Lookup.getDefault().lookup(Extractors.class);
        if (result == null) {
            result = DummyExtractors.INSTANCE;
        }
        return result;
    }

    public abstract <P extends ParserRuleContext> Extraction extract(String mimeType,
            GrammarSource<?> src, Class<P> type);

    private static final class DummyExtractors extends Extractors {

        private static final DummyExtractors INSTANCE = new DummyExtractors();

        @Override
        public <P extends ParserRuleContext> Extraction extract(String mimeType, GrammarSource<?> src, Class<P> type) {
            // XXX we *could* do something crazy here like derive the name of the parser and
            // lexer classes from the passed type and try to load them reflectively and
            // parse the grammar source.  Would be useful for tests, not so much
            // elsewhere
            return null;
        }
    }
}

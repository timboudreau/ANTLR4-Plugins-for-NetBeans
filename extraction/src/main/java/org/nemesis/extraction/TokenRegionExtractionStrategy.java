package org.nemesis.extraction;

import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.IntPredicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.antlr.v4.runtime.Token;
import org.nemesis.data.Hashable;
import org.nemesis.data.SemanticRegions;

/**
 *
 * @author Tim Boudreau
 */
final class TokenRegionExtractionStrategy<R> implements Hashable {

    private final Class<R> type;
    private final IntPredicate tokenTypes;
    private final Function<Token, R> typeForToken;
    private static final Logger LOG = Logger.getLogger(TokenRegionExtractionStrategy.class.getName());

    TokenRegionExtractionStrategy(Class<R> type, IntPredicate tokenTypes, Function<Token, R> typeForToken) {
        this.type = type;
        this.tokenTypes = tokenTypes;
        this.typeForToken = typeForToken;
    }

    @Override
    public String toString() {
        return super.toString() + "{type=" + type + ", matching: " + tokenTypes + "}";
    }

    SemanticRegions<R> scan(Iterable<? extends Token> tokens, BooleanSupplier cancelled) {
        // XXX move the inner part of the loop, and scan all in a single pass over the tokens
        assert tokens != null : "Null tokens iterable";
        SemanticRegions.SemanticRegionsBuilder<R> bldr = SemanticRegions.builder(type);
        boolean log = LOG.isLoggable(Level.FINEST);
        for (Token tok : tokens) {
            int type = tok.getType();
            if (cancelled.getAsBoolean()) {
                return null;
            }
            boolean matched = tokenTypes.test(type);
            if (log) {
                LOG.log(Level.FINEST, "Tok {0} at {1} matched {2}", new Object[] {type, tok.getTokenIndex(), matched});
            }
            if (matched) {
                R key = typeForToken.apply(tok);
                if (log) {
                    LOG.log(Level.FINEST, "Match {0} at {1} with {2} text {3} - use {4}",
                            new Object[] {type, tok.getTokenIndex(), tok, tok.getText(), key});
                }
                if (key != null) {
                    bldr.add(key, tok.getStartIndex(), tok.getStopIndex());
                }
            }
        }
        return bldr.build();
    }

    @Override
    public void hashInto(Hasher hasher) {
        hasher.writeInt(230917);
        hasher.writeString(type.getName());
        hasher.hashObject(tokenTypes);
        hasher.hashObject(typeForToken);
    }
}

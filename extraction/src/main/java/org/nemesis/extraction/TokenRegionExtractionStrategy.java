package org.nemesis.extraction;

import java.util.function.Function;
import java.util.function.IntPredicate;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;
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

    TokenRegionExtractionStrategy(Class<R> type, IntPredicate tokenTypes, Function<Token, R> typeForToken) {
        this.type = type;
        this.tokenTypes = tokenTypes;
        this.typeForToken = typeForToken;
    }

    SemanticRegions<R> scan(TokenStream tokens) {
        assert tokens != null : "Null token stream";
        SemanticRegions.SemanticRegionsBuilder<R> bldr = SemanticRegions.builder(type);
        for (int i = 1, type = tokens.LA(i); type != Lexer.EOF; i++, type = tokens.LA(i)) {
            if (tokenTypes.test(type)) {
                Token tok = tokens.LT(i);
                R key = typeForToken.apply(tok);
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

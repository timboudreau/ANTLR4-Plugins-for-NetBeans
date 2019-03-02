package org.nemesis.extraction;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.RuleNode;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.nemesis.data.Hashable;
import org.nemesis.data.Hashable.Hasher;

/**
 *
 * @author Tim Boudreau
 */
final class NameExtractionStrategy<R extends ParserRuleContext, T extends Enum<T>> implements Hashable {

    final Class<R> type;
    final Function<R, NamedRegionData<T>> extractor;
    final Predicate<RuleNode> ancestorQualifier;
    final T argType;
    final Function<R, List<? extends TerminalNode>> terminalFinder;

    NameExtractionStrategy(Class<R> type, Function<R, NamedRegionData<T>> extractor, Predicate<RuleNode> ancestorQualifier) {
        this.type = type;
        this.extractor = extractor;
        this.ancestorQualifier = ancestorQualifier;
        this.terminalFinder = null;
        this.argType = null;
    }

    NameExtractionStrategy(Class<R> type, Predicate<RuleNode> ancestorQualifier, T argType, Function<R, List<? extends TerminalNode>> terminalFinder) {
        this.type = type;
        this.extractor = null;
        this.ancestorQualifier = ancestorQualifier;
        this.argType = argType;
        this.terminalFinder = terminalFinder;
    }

    @Override
    public void hashInto(Hasher hasher) {
        hasher.writeString(type.getName());
        hasher.hashObject(extractor);
        if (ancestorQualifier != null) {
            hasher.hashObject(ancestorQualifier);
        }
        if (argType != null) {
            hasher.writeInt(argType.ordinal());
        }
        if (terminalFinder != null) {
            hasher.hashObject(terminalFinder);
        }
    }

    public void find(R node, BiConsumer<NamedRegionData<T>, TerminalNode> cons) {
        if (extractor != null) {
            cons.accept(extractor.apply(node), null);
        } else {
            List<? extends TerminalNode> nds = terminalFinder.apply(node);
            if (nds != null) {
                for (TerminalNode tn : nds) {
                    Token tok = tn.getSymbol();
                    if (tok != null) {
                        cons.accept(new NamedRegionData<>(tn.getText(), argType, tok.getStartIndex(), tok.getStopIndex() + 1), tn);
                    }
                }
            }
        }
    }
}

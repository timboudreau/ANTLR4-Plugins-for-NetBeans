package org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.extraction;

import java.lang.reflect.Array;
import java.util.HashSet;
import java.util.Set;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.AbstractParseTreeVisitor;
import org.antlr.v4.runtime.tree.RuleNode;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.extraction.key.SingletonKey;
import org.nemesis.data.Hashable;
import org.nemesis.data.Hashable.Hasher;

/**
 * Represents a group of strategies for extracting singletons using various rule
 * nodes.
 *
 * @author Tim Boudreau
 */
final class SingletonExtractionStrategies<KeyType> implements Hashable {

    final SingletonKey<KeyType> key;
    final Set<SingletonExtractionStrategy<KeyType, ?>> infos;

    SingletonExtractionStrategies(SingletonKey<KeyType> key, Set<SingletonExtractionStrategy<KeyType, ?>> infos) {
        this.key = key;
        this.infos = new HashSet<>(infos);
    }

    void addAll(Set<SingletonExtractionStrategy<KeyType, ?>> all) {
        for (SingletonExtractionStrategy<KeyType, ?> so : all) {
            infos.add(so);
        }
    }

    @Override
    public void hashInto(Hasher hasher) {
        hasher.hashObject(key);
        for (SingletonExtractionStrategy i : infos) {
            hasher.hashObject(i);
        }
    }

    public SingletonEncounters<KeyType> extract(ParserRuleContext node) {
        SingleVisitor<KeyType> v = new SingleVisitor<>(infos);
        node.accept(v);
        return v.encounters;
    }

    static final class SingleVisitor<KeyType> extends AbstractParseTreeVisitor<Void> {

        private final SingletonExtractionStrategy<KeyType, ?>[] infos;
        private int[] activations;
        private final SingletonEncounters<KeyType> encounters = new SingletonEncounters<>();

        @SuppressWarnings("unchecked")
        SingleVisitor(Set<SingletonExtractionStrategy<KeyType, ?>> infos) {
            this.infos = infos.toArray((SingletonExtractionStrategy<KeyType, ?>[]) Array.newInstance(SingletonExtractionStrategy.class, infos.size()));
            activations = new int[this.infos.length];
            for (int i = 0; i < this.infos.length; i++) {
                SingletonExtractionStrategy<?, ?> info = this.infos[i];
                if (info.ancestorQualifier == null) {
                    activations[i] = 1;
                }
            }
        }

        @Override
        public Void visitChildren(RuleNode node) {
            if (node instanceof ParserRuleContext) {
                visitRule((ParserRuleContext) node);
            } else {
                super.visitChildren(node);
            }
            return null;
        }

        private <R extends ParserRuleContext> void runOne(SingletonExtractionStrategy<KeyType, R> extractor, ParserRuleContext ctx) {
            if (extractor.ruleType.isInstance(ctx)) {
                doRunOne(extractor, extractor.ruleType.cast(ctx));
            }
        }

        private <R extends ParserRuleContext> void doRunOne(SingletonExtractionStrategy<KeyType, R> extractor, R ctx) {
            KeyType found = extractor.extractor.apply(ctx);
            if (found != null) {
                encounters.add(found, ctx.start.getStartIndex(), ctx.stop.getStopIndex() + 1, extractor.ruleType);
            }
        }

        private void visitRule(ParserRuleContext rule) {
            boolean[] scratch = new boolean[infos.length];
            boolean anyActivated = false;
            for (int i = 0; i < infos.length; i++) {
                if (infos[i].ancestorQualifier != null) {
                    if (infos[i].ancestorQualifier.test(rule)) {
                        activations[i]++;
                        scratch[i] = true;
                        anyActivated = true;
                    }
                }
                if (activations[i] > 0) {
                    runOne(infos[i], rule);
                }
            }
            super.visitChildren(rule);
            if (anyActivated) {
                for (int i = 0; i < infos.length; i++) {
                    if (scratch[i]) {
                        activations[i]--;
                    }
                }
            }
        }
    }

}

package org.nemesis.data.graph;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4BaseVisitor;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Parser;

/**
 * Wraps an instance of BitSetGraph and converts its BitSets to
 sets of string rule names.
 *
 * @author Tim Boudreau
 */
class BitSetTreeBuilder extends ANTLRv4BaseVisitor<Void> {

    private final BitSet[] ruleReferences;
    private final BitSet[] referencedBy;
    private final int[] ruleIndicesForSorted;
    private final String[] namesSorted;
    String currRule;
    private static final Logger LOG =
            Logger.getLogger(BitSetTreeBuilder.class.getName());

    BitSetTreeBuilder(String[] ruleNames) {
        // A file with a partially typed rule may contain duplicate rule names;
        // we weed them out here, as they would wreak havoc with binary search
        List<String> orig = new ArrayList<>(new LinkedHashSet<>(Arrays.asList(ruleNames)));
        if (orig.size() != ruleNames.length) {
            LOG.log(Level.FINE, "Received rule list that contained duplicates: {1}",
                    Arrays.asList(ruleNames));
        }
        ruleReferences = new BitSet[orig.size()];
        referencedBy = new BitSet[orig.size()];
        ruleIndicesForSorted = new int[orig.size()];
        namesSorted = orig.toArray(new String[orig.size()]);
        Arrays.sort(namesSorted);
        for (int i = 0; i < namesSorted.length; i++) {
            ruleIndicesForSorted[i] = orig.indexOf(namesSorted[i]);
            ruleReferences[i] = new BitSet(ruleNames.length);
            referencedBy[i] = new BitSet(ruleNames.length);
        }
    }

    void addEdge(String referencer, String referenced) {
        int referencerIndex = Arrays.binarySearch(namesSorted, referencer);
        if (referencerIndex < 0) {
            LOG.log(Level.FINE, "Attempt to add an edge between rules {0} and "
                    + "{1} but {0} is not in the grammar",
                    new Object[]{referencer, referenced});
            return;
        }
        int referencedIndex = Arrays.binarySearch(namesSorted, referenced);
        if (referencedIndex < 0) {
            LOG.log(Level.FINE, "Attempt to add an edge between rules {0} and "
                    + "{1} but {1} is not in the grammar",
                    new Object[]{referencer, referenced});
            return;
        }
        ruleReferences[referencerIndex].set(referencedIndex);
        referencedBy[referencedIndex].set(referencerIndex);
    }

    void enterItem(String item, Runnable run) {
        String old = currRule;
        currRule = item;
        try {
            run.run();
        } finally {
            currRule = old;
        }
    }

    @Override
    public Void visitParserRuleReference(ANTLRv4Parser.ParserRuleReferenceContext ctx) {
        //            System.out.println("VISIT RULE REF " + ctx.getText() + " in " + currRule);
        addEdge(currRule, ctx.getText());
        super.visitParserRuleReference(ctx);
        return null;
    }

    @Override
    public Void visitParserRuleSpec(ANTLRv4Parser.ParserRuleSpecContext ctx) {
        ctx.accept(idFinder);
        enterItem(idFinder.rule, () -> {
            super.visitParserRuleSpec(ctx);
        });
        return null;
    }

    public IntGraph toRuleTree() {
        return IntGraph.create(ruleReferences, referencedBy);
    }
    static final IdFinder idFinder = new IdFinder();

    static final class IdFinder extends ANTLRv4BaseVisitor<Void> {

        String rule;

        @Override
        public Void visitParserRuleDeclaration(ANTLRv4Parser.ParserRuleDeclarationContext ctx) {
            rule = ctx.getText();
            return null;
        }
    }
}

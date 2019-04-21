package org.nemesis.antlr.completion;

import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.IntPredicate;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.Interval;
import org.nemesis.antlr.completion.annos.DeletionPolicy;
import org.nemesis.antlr.completion.annos.InsertAction;
import org.nemesis.antlr.completion.annos.InsertPolicy;
import org.nemesis.antlr.completion.annos.TokenModification;

/**
 *
 * @author Tim Boudreau
 */
final class TokenPatternMatcher implements BiFunction<List<Token>, Token, TokenMatch> {

    private final IntPredicate ignoring;
    private final int[][] befores;
    private final int[][] afters;
    private final String[] names;
    private final IntPredicate[] tokenMatches;
    private final InsertAction[] actions;
    private final List<Set<InsertPolicy>> insertPolicies;
    private final List<Set<DeletionPolicy>> deletionPolicies;

    public TokenPatternMatcher(IntPredicate ignoring, int[][] befores,
            int[][] afters, String[] names, IntPredicate[] tokenMatches,
            InsertAction[] actions, List<Set<InsertPolicy>> insertPolicies,
            List<Set<DeletionPolicy>> deletionPolicies) {
        this.ignoring = ignoring;
        this.befores = befores;
        this.afters = afters;
        this.names = names;
        this.tokenMatches = tokenMatches;
        this.actions = actions;
        this.insertPolicies = insertPolicies;
        this.deletionPolicies = deletionPolicies;
    }

    @Override
    public TokenMatch apply(List<Token> t, Token u) {
        int index = TokenUtils.findTokenPatternMatch(befores, afters,
                ignoring, tokenMatches, t, u);
        if (index >= 0) {
            Interval startStop = TokenModification.getReplacementRange(actions[index], 
                    deletionPolicies.get(index), t, u);
            return new TokenMatch(names[index], insertPolicies.get(index),
                    actions[index], deletionPolicies.get(index), startStop.a, startStop.b + 1);
        }
        return null;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(getClass().getSimpleName())
                .append("{\n");
        for (int i = 0; i < names.length; i++) {
            sb.append("  ");
            sb.append(names[i]).append(": ");
            if (befores[i].length > 0) {
                for (int j = 0; j < befores[i].length; j++) {
                    sb.append(befores[i][j]);
                    if (j != befores[i].length) {
                        sb.append(',');
                    }
                }
                sb.append("-->");
            }
            sb.append(tokenMatches[i]);
            if (afters[i].length > 0) {
                sb.append("<--");
                for (int j = 0; j < afters[i].length; j++) {
                    sb.append(afters[i][j]);
                    if (j != afters[i].length) {
                        sb.append(',');
                    }
                }
            }
            sb.append('\n');
        }
        return sb.append('}').toString();
    }
}

package org.nemesis.antlr.v4.netbeans.v8.grammar.code.formatting;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import org.antlr.v4.runtime.Vocabulary;
import static org.nemesis.antlr.v4.netbeans.v8.grammar.code.formatting.Criterion.anyOf;
import static org.nemesis.antlr.v4.netbeans.v8.grammar.code.formatting.Criterion.matching;

/**
 * A set of formatting rules which can be appled to reformat a file that uses an
 * ANTLR grammar.
 *
 * @author Tim Boudreau
 */
public final class FormattingRules {

    private final List<FormattingRule> rules = new LinkedList<>();
    private volatile boolean sorted;
    private final Vocabulary vocabulary;

    /**
     * Create a new formatting rule set.
     *
     * @param vocabulary The vocabulary for the lexer being used, which is
     * used to construct human-readable log messages
     */
    public FormattingRules(Vocabulary vocabulary) {
        this.vocabulary = vocabulary;
    }

    Vocabulary vocabulary() {
        return vocabulary;
    }

    void addRule(FormattingRule rule) {
        rules.add(rule);
    }

    /**
     * Create a new rule which matches tokens based on the passed criterion.
     *
     * @param criterion A criterion for matching tokens.
     * @return A new rule which can be further configured
     */
    public FormattingRule onTokenType(Criterion criterion) {
        FormattingRule result = new FormattingRule(criterion, this);
        rules.add(result);
        sorted = false;
        return result;
    }

    /**
     * Create a new rule which matches tokens which have the token type passed
     * here.
     *
     * @param type The token type
     * @return A new rule which can be further configured
     */
    public FormattingRule onTokenType(int type) {
        FormattingRule result = new FormattingRule(matching(vocabulary, type), this);
        rules.add(result);
        sorted = false;
        return result;
    }

    /**
     * Create a new rule which matches tokens which have any of the token types
     * passed here.
     *
     * @param item
     * @param more
     * @return
     */
    public FormattingRule onTokenType(int item, int... more) {
        if (more.length == 0) {
            return onTokenType(item);
        }
        FormattingRule result = new FormattingRule(anyOf(vocabulary,
                FormattingRule.combine(item, more)), this);
        rules.add(result);
        sorted = false;
        return result;
    }

    /**
     * Apply all rules to one token.
     *
     * @param token The token
     * @param prevToken The previous token type
     * @param nextToken The next token type
     * @param precededByNewline Whether or not a newline (possibly with
     * trailing whitespace) came immediately before this token
     * @param ctx The formatting context which can be used to manipulate
     * formatting
     * @param debug If true, matches and reasons for non-matching will be
     * logged
     */
    public void apply(ModalToken token, int prevToken, int nextToken,
            boolean precededByNewline, FormattingContext ctx, boolean debug) {
        if (!sorted) {
            Collections.sort(rules);
            sorted = true;
//            System.out.println("RULES:");
//            for (FormattingRule r : rules) {
//                System.out.println(" - " + r);
//            }
        }
        for (FormattingRule rule : rules) {
            if (rule.matches(token.getType(), prevToken, nextToken, precededByNewline, token.mode(), debug)) {
                if (rule.hasAction()) {
                    if (debug) {
                        System.out.println("'" + token.getText() + "' " + vocabulary
                                .getSymbolicName(token.getType()) + " matched by " + rule);
                    }
                    rule.perform(token, ctx);
                } else {
                    if (debug) {
                        System.out.println("NULL ACTION: " + rule);
                    }
                }
                break;
            }
        }
    }
}

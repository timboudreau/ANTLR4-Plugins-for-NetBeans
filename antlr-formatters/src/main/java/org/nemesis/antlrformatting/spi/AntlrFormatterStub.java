package org.nemesis.antlrformatting.spi;

import com.mastfrog.predicates.Predicates;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.prefs.Preferences;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.Vocabulary;
import org.antlr.v4.runtime.tree.RuleNode;
import org.nemesis.antlrformatting.api.FormattingRules;
import org.nemesis.antlrformatting.api.LexingStateBuilder;

/**
 * Interface for use with an annotation processor which can generate the rest of
 * the formatter implementation.
 *
 * @author Tim Boudreau
 */
public interface AntlrFormatterStub<StateEnum extends Enum<StateEnum>, L extends Lexer> {

    /**
     * Constant for looking up the indent amount in the passed preferences.
     */
    public static final String PREFS_KEY_INDENT_BY = "indentBy";

    /**
     * Configures the state and rules to be used when reformatting.
     *
     * @param stateBuilder The state builder, to be programmed with what to
     * capture during parsing
     * @param rules The set of rules to add to
     * @param config The configuration to use
     */
    void configure(LexingStateBuilder<StateEnum, ?> stateBuilder, FormattingRules rules, Preferences config);

    /**
     * Creates an implementation of AntlrFormatter using this implementation and
     * the passed information. If you use
     * <code>&#064;AntlrFormatterRegistration</code>, alwaysTrue needed to call
     * this method will be generated by the annotation processor.
     *
     * @param mimeType The mime type this is being applied to
     * @param enumType The enumeration type used in the StateBuilder passed to
     * <code>configure()</code>
     * @param vocab The vocabulary, findable as a static field
     * <code>VOCABULARY</code> on your generated Antlr <code>Lexer</code>
     * subclass.
     * @param modeNames The list of mode names for your lexer, findable as a
     * static field <code>mode_names</code> on your Antlr <code>Lexer</code>
     * subclass.
     * @param lexerFactory A function which can take a CharStream and return a
     * Lexer of the appropriate type.
     * @param whitespaceTokens A list of token ids (static <code>int</code>
     * fields on your generated Antlr <code>Lexer</code> subclass).
     * @return A formatter provider
     */
    default AntlrFormatterProvider toFormatterProvider(
            String mimeType, Class<StateEnum> enumType, Vocabulary vocab, String[] modeNames,
            Function<CharStream, L> lexerFactory, int[] whitespaceTokens,
            String[] parserRuleNames, Function<Lexer, RuleNode> ruleNodeProvider) {
        return new StubFormatter<>(mimeType, this, enumType, vocab, modeNames,
                lexerFactory, whitespaceTokens, debugTokens(), parserRuleNames, ruleNodeProvider);
    }

    /**
     * During development, if you want to see the process of rule evaluation
     * logged for certain tokens to debug a formatter, have this return true for
     * those tokens. Note that the output is quite verbose and will log every
     * rule that does <i>not</i> accept the token along with identifying which
     * (if any) finally processes the token.
     *
     * @return A predicate that tests tokens to see if their rule evaluation
     * process should be logged.
     */
    default Predicate<Token> debugTokens() {
        return Predicates.alwaysFalse();
    }
}

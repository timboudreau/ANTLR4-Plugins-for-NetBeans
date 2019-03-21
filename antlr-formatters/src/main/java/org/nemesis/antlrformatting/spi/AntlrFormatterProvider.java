/*
BSD License

Copyright (c) 2018-2019, Tim Boudreau
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

* Redistributions of source code must retain the above copyright
  notice, this list of conditions and the following disclaimer.
* Redistributions in binary form must reproduce the above copyright
  notice, this list of conditions and the following disclaimer in the
  documentation and/or other materials provided with the distribution.
* The name of its author may not be used to endorse or promote products
  derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR IMPLIED 
WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF 
MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT
SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, 
EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT
OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS 
INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN 
CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING
IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY 
OF SUCH DAMAGE.
 */
package org.nemesis.antlrformatting.spi;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.Vocabulary;
import org.antlr.v4.runtime.tree.RuleNode;
import org.nemesis.antlrformatting.api.Criteria;
import org.nemesis.antlrformatting.api.Criterion;
import org.nemesis.antlrformatting.api.FormattingResult;
import org.nemesis.antlrformatting.api.FormattingRules;
import org.nemesis.antlrformatting.api.LexingState;
import org.nemesis.antlrformatting.api.LexingStateBuilder;
import org.nemesis.antlrformatting.impl.CaretInfo;
import org.nemesis.antlrformatting.impl.FormattingAccessor;
import org.netbeans.modules.editor.indent.spi.Context;
import org.netbeans.modules.editor.indent.spi.ReformatTask;

/**
 * Service Provider Interface for formatters which use this library to format
 * sources using Antlr lexers. The critial methods are configure() and
 * whitespace() - these allow you to configure the operation of formatting.
 * Implementations should be entirely stateless.
 *
 * @author Tim Boudreau
 */
public abstract class AntlrFormatterProvider<C, StateEnum extends Enum<StateEnum>> {

    private final Class<StateEnum> enumType;
    private Criterion whitespaceCriterion;

    static final Logger LOGGER = Logger.getLogger(
            AntlrFormatterProvider.class.getName());

    /**
     * Create a new formatter provider. The Enum class passed is what you use
     * when configuring your LexingStateBuilder with instructions for statistics
     * you want to collect and then look up when performing formatting.
     *
     * @param enumType An enum type
     */
    protected AntlrFormatterProvider(Class<StateEnum> enumType) {
        this.enumType = enumType;
    }

    /**
     * Compute the indent size, potentially consulting the configuration object.
     * The default implementation simply returns 4.
     *
     * @param config
     * @return
     */
    protected int indentSize(C config) {
        return 4;
    }

    /**
     * Create a lexer over the passed document.
     *
     * @param document A document
     * @return A lexer
     * @throws BadLocationException if something goes wrong
     */
    protected Lexer createLexer(Document document) throws BadLocationException {
//        DocumentUtilities.getText(document);
        return createLexer(CharStreams.fromString(document.getText(0, document.getLength())));
    }

    /**
     * Get the configuration object to use for the file represented by the
     * passed context (may be project-dependent or global).
     *
     * @param ctx The context which has been passed to the formatter created
     * from this object.
     * @return A configuration object which other methods on this object know
     * how to interpret
     */
    protected abstract C configuration(Context ctx);

    /**
     * Create an ANTLR lexer for a character stream.
     *
     * @param stream The character stream.
     * @return A lexer
     */
    protected abstract Lexer createLexer(CharStream stream);

    /**
     * Get the ANTLR Vocabulary instance (usually it is a static field named
     * Vocabulary on your lexer implementation called <code>VOCABULARY</code>)
     * which is associated with the language you are parsing.
     *
     * @return A vocabulary
     */
    protected abstract Vocabulary vocabulary();

    /**
     * Get the set of lexical mode names (usually a static field on your
     * generated lexer implementation called <code>modeNames</code>). Formatting
     * rules may be defined to be active only when a particular mode is in
     * effect; this array is used to map modes to the mode numbers Antlr tokens
     * return.
     *
     * @return An array of unique mode names used by this lexer
     */
    protected abstract String[] modeNames();

    /**
     * Set up <i>lexing state</i> collection during parsing - this allows you to
     * define flexible rules that are useful in conditioning different
     * formatting on surrounding tokens, distance forward or backward to a token
     * of some other type (for example, handling a set of braces containing a
     * single statement differently than a multi-line set of statements),
     * special handling for list members or the last token within a list of
     * similar tokens, etc. The LexingStateBuilder lets you use enum constants
     * you define as keys for fetching integers and booleans that can be used
     * directly by your formatting code, or to selectively enable formatting
     * rules only when the value associated with some key(s) is greater than,
     * less than or equal to a value. Then configure the set of formatting rules
     * that will be applied to reformat the document.
     *
     * @param stateBuilder A lexingStateBuilder that you can configure
     * @param config The configuration object (user preferences, project
     * settings or similar) which may affect how you configure the passed
     * builder.
     */
    protected abstract void configure(LexingStateBuilder<StateEnum, ?> stateBuilder, FormattingRules rules, C config);

    /**
     * Creates the criterion once and caches it.
     *
     * @return A criterion for matching whitespace
     */
    Criterion _whitespace() {
        if (whitespaceCriterion != null) {
            return whitespaceCriterion;
        }
        return whitespaceCriterion = whitespace();
    }

    /**
     * Create a criterion which will match all tokens which should be considered
     * to be whitespace. By default this attempts heuristic name-matching and
     * literal name content matching to figure out what is a whitespce token.
     * This will most likely be wrong.
     *
     * @return A criterion
     */
    protected Criterion whitespace() {
        Vocabulary vocab = vocabulary();
        List<Integer> all = new ArrayList<>();
        // Do our best here, which is likely to be pretty limited
        for (int i = 1; i <= vocab.getMaxTokenType(); i++) {
            String litName = vocab.getLiteralName(i);
            boolean allWhitespace = litName != null;
            if (allWhitespace) {
                if (litName.length() > 2 && litName.charAt(0) == '\'' && litName.charAt(litName.length() - 1) == '\'') {
                    litName = litName.substring(1, litName.length() - 1);
                }
                for (int j = 0; j < litName.length(); j++) {
                    if (!Character.isWhitespace(litName.charAt(j))) {
                        allWhitespace = false;
                        break;
                    }
                }
            }
            if (allWhitespace) {
                all.add(i);
            } else {
                String progName = vocab.getSymbolicName(i);
                if (progName != null) {
                    if (progName.toLowerCase().contains("whitespace")) {
                        all.add(i);
                    }
                }
            }
        }
        int[] ints = new int[all.size()];
        for (int i = 0; i < ints.length; i++) {
            ints[i] = all.get(i);
        }
        Arrays.sort(ints);
//        int[] ints = (int[]) Utilities.toPrimitiveArray(all.toArray());
        Criterion result = Criteria.forVocabulary(vocab).anyOf(ints);
        LOGGER
                .log(Level.WARNING, "whitespace() is not implemented in {0}. "
                        + "Using heuristically determined whitespace tokens "
                        + "(or perhaps none).  Override it or specify the "
                        + "whitespace tokens on your registration annotation to "
                        + "get a formatter that works correctly - this probably "
                        + "will not: {1}", new Object[]{getClass().getName(),
                    result});
        return result;
    }

    /**
     * For debugging purposes, override this method to return a predicate which
     * will match tokens where you would like the decision process of rule
     * selection for that token to be logged to the console.
     *
     * @return A predicate - by default, one which always returns false
     */
    protected Predicate<Token> debugLogPredicate() {
        return AlwaysFalse.INSTANCE;
    }

    /**
     * Create a NetBeans ReformatTask for this Antlr formatter.
     *
     * @param context The reformatting context containing the document.
     *
     * @return A task
     */
    public final ReformatTask createFormatter(Context context) {
        return new AntlrReformatTask(new DocumentReformatRunner<>(this), context);
    }

    /**
     * Reformat the passed string, returning only that portion which is
     * reformatted. Note that if a range of the text is requested to be
     * reformatted, and the boundaries are in the middle of tokens, only those
     * tokens that are completely within the bounds requested will be
     * reformatted, and the returned FormattingResult will have the position of
     * the actual start and end of what was reformatted.
     *
     * @param text Some text
     * @param from The starting position (greater than equal to zero)
     * @param to The ending position
     * @param config The configuration object to use for configuring the
     * formatter
     * @return A formatting result
     */
    public FormattingResult reformat(String text, int from, int to, C config) {
        return reformat(CharStreams.fromString(text), from, to, config);
    }

    /**
     * Reformat the passed string, returning only that portion which is
     * reformatted.
     *
     * @param text Some text
     * @param from The starting position (greater than equal to zero)
     * @param to The ending position
     * @param config The configuration object to use for configuring the
     * formatter
     * @return A reformatted string
     */
    public String reformattedString(String text, int from, int to, C config) {
        return reformat(text, from, to, config).text();
    }

    /**
     * Optionally, allow for formatting where rules activate when in a
     * particular parser rule, override this to create a parser, parse and
     * return whatever rule node is the entry point to your file type - the rule
     * which represents an entire source file.
     *
     * @param lexer A lexer
     * @return A rule node, or null
     */
    protected RuleNode parseAndExtractRootRuleNode(Lexer lexer) {
        return null;
    }

    /**
     * If you use parser by overrideing parseAndExtractRootRuleNode, override
     * this to provide the parser rule names for logging purposes.
     *
     * @return An array of parser rule names, findable as a static field on your
     * generated Antlr parser.
     */
    protected String[] parserRuleNames() {
        return null;
    }

    /**
     * Reformat the passed string, returning only that portion which is
     * reformatted.
     *
     * @param text An ANTLR character stream
     * @param from The starting position (greater than equal to zero)
     * @param to The ending position
     * @param config The configuration object to use for configuring the
     * formatter
     * @return A reformatted string
     */
    public FormattingResult reformat(CharStream text, int from, int to, C config) {
        RulesAndState rs = populate(config);
        FormattingRules rules = rs.rules;
        LexingState state = rs.state;
        Criterion whitespace = _whitespace();
        Predicate<Token> debug = debugLogPredicate();
        Lexer lexer = createLexer(text);

        RuleNode node = parseAndExtractRootRuleNode(lexer);
        if (node != null) {
            lexer = createLexer(text);
        }

        return FormattingAccessor.getDefault().reformat(from, to, indentSize(config), rules,
                state, whitespace, debug, lexer, modeNames(), CaretInfo.NONE, null, node);
    }

    RulesAndState populate(C config) {
        FormattingRules rules = FormattingAccessor.getDefault().createFormattingRules(vocabulary(),
                modeNames(), parserRuleNames());
        LexingStateBuilder<StateEnum, LexingState> stateBuilder = LexingState.builder(enumType);
        configure(stateBuilder, rules, config);
        return new RulesAndState(rules, stateBuilder.build());
    }

    /**
     * Just a holder class aggregating the rules and state configured by
     * builders passed to configure().
     */
    static class RulesAndState {

        final FormattingRules rules;
        final LexingState state;

        RulesAndState(FormattingRules rules, LexingState state) {
            this.rules = rules;
            this.state = state;
        }
    }

    private static final class AlwaysFalse implements Predicate<Token> {

        private static final AlwaysFalse INSTANCE = new AlwaysFalse();

        private AlwaysFalse() {
        }

        @Override
        public boolean test(Token t) {
            return false;
        }

        public String toString() {
            return "false";
        }
    }
}

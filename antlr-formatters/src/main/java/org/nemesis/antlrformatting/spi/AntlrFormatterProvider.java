/*
BSD License

Copyright (c) 2016-2018, Frédéric Yvon Vinet, Tim Boudreau
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

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.function.BiConsumer;
import java.util.function.Function;
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
import org.nemesis.antlrformatting.api.Criteria;
import org.nemesis.antlrformatting.api.Criterion;
import org.nemesis.antlrformatting.api.FormattingRules;
import org.nemesis.antlrformatting.api.LexingState;
import org.nemesis.antlrformatting.api.LexingStateBuilder;
import org.nemesis.antlrformatting.impl.FormattingAccessor;
import org.netbeans.editor.BaseDocument;
import org.netbeans.modules.csl.api.Formatter;
import org.netbeans.modules.csl.spi.ParserResult;
import org.netbeans.modules.editor.indent.spi.Context;
import org.netbeans.spi.lexer.MutableTextInput;

/**
 * Service Provider Interface for formatters which use this library to format
 * sources using Antlr lexers. The critial methods are populateLexingState() and
 * populateFormattingRules() - these allow you to configure the operation of
 * formatting.
 *
 * @author Tim Boudreau
 */
public abstract class AntlrFormatterProvider<C, StateEnum extends Enum<StateEnum>> {

    private final Class<StateEnum> enumType;

    protected AntlrFormatterProvider(Class<StateEnum> enumType) {
        this.enumType = enumType;
    }

    protected int indentSize() {
        return 4;
    }

    protected int hangingIndentSize() {
        return indentSize() * 2;
    }

    protected Lexer createLexer(Document document) throws BadLocationException {
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
     * less than or equal to a value.
     *
     * @param stateBuilder A lexingStateBuilder that you can configure
     * @param config The configuration object (user preferences, project
     * settings or similar) which may affect how you configure the passed
     * builder.
     */
    protected abstract void populateLexingState(LexingStateBuilder<StateEnum, ?> stateBuilder, C config);

    /**
     * Configure the set of formatting rules that will be applied to reformat
     * the document.
     *
     * @param rules The formatting rules, which can be added to - rules can be
     * conditional on a wide variety of things.
     * @param config The configuration object (user preferences, project
     * settings or similar) which may affect how you configure the passed
     * builder.
     */
    protected abstract void populateFormattingRules(FormattingRules rules, C config);

    /**
     * Create a criterion which will match all tokens which should be considered
     * to be whitespace.
     *
     * @return A criterion
     */
    protected abstract Criterion whitespace();

    static final class ReflectiveFormatterProvider<L extends Lexer, C, StateEnum extends Enum<StateEnum>> extends AntlrFormatterProvider<C, StateEnum> {

        private final Class<L> lexer;
        private final Vocabulary vocabulary;
        private final Criterion whitespace;
        private final BiConsumer<LexingStateBuilder<StateEnum, ?>, C> statePopulator;
        private final Function<Context, C> configFinder;
        private final BiConsumer<FormattingRules, C> rulesPopulator;
        private final String[] modeNames;

        ReflectiveFormatterProvider(Class<L> lexer,
                BiConsumer<LexingStateBuilder<StateEnum, ?>, C> statePopulator,
                BiConsumer<FormattingRules, C> rulesPopulator,
                Function<Context, C> configFinder,
                int indentSize,
                Predicate<Token> debug,
                Class<StateEnum> stateType,
                String... whitespaceRuleNames) throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
            super(stateType);
            this.lexer = lexer;
            Field vocabField = lexer.getField("VOCABULARY");
            vocabulary = (Vocabulary) vocabField.get(null);
            int[] fieldIds = new int[whitespaceRuleNames.length];
            for (int i = 0; i < whitespaceRuleNames.length; i++) {
                try {
                    Field rule = lexer.getField(whitespaceRuleNames[i]);
                    assert rule.getType() == Integer.TYPE : "Field " + whitespaceRuleNames[i]
                            + " on " + lexer + " is not of type int";
                    int val = rule.getInt(null);
                    fieldIds[i] = val;
                } catch (NoSuchFieldException ex) {
                    throw new IllegalStateException("No field " + whitespaceRuleNames[i] + " on " + lexer.getName());
                }
            }
            Field modeNamesField = lexer.getField("modeNames");
            assert modeNamesField.getType() == String[].class : "mode names field is not a string on " + lexer;
            modeNames = (String[]) modeNamesField.get(null);
            whitespace = Criteria.forVocabulary(vocabulary).anyOf(fieldIds);
            this.configFinder = configFinder;
            this.statePopulator = statePopulator;
            this.rulesPopulator = rulesPopulator;
        }

        @Override
        protected C configuration(Context ctx) {
            return configFinder.apply(ctx);
        }

        @Override
        protected Lexer createLexer(CharStream stream) {
            try {
                Constructor<L> con = lexer.getConstructor(CharStream.class);
                return con.newInstance(stream);
            } catch (NoSuchMethodException | SecurityException | InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
                throw new IllegalArgumentException("Could not invoke a"
                        + " constructor that takes a single argument of "
                        + CharStream.class.getName() + " on "
                        + lexer.getName(), ex);
            }
        }

        @Override
        protected Vocabulary vocabulary() {
            return vocabulary;
        }

        @Override
        protected String[] modeNames() {
            return modeNames;
        }

        @Override
        protected void populateLexingState(LexingStateBuilder<StateEnum, ?> e, C config) {
            statePopulator.accept(e, config);
        }

        @Override
        protected void populateFormattingRules(FormattingRules rules, C config) {
            rulesPopulator.accept(rules, config);
        }

        @Override
        protected Criterion whitespace() {
            return whitespace;
        }
    }

    private static void withMutableTextInputDisabled(Document document, Runnable run) {
        MutableTextInput<?> mti = (MutableTextInput<?>) document.getProperty(MutableTextInput.class);
        if (mti != null) {
            mti.tokenHierarchyControl().setActive(false);
        }
        try {
            run.run();
        } finally {
            if (mti != null) {
                mti.tokenHierarchyControl().setActive(true);
            }
        }
    }

    private static void withDocumentLock(Document doc, Runnable run) {
        if (doc instanceof BaseDocument) {
            ((BaseDocument) doc).runAtomic(run);
        } else {
            doc.render(run);
        }
    }

    private static void replaceInDocument(Document doc, int start, int end, String replacement) throws BadLocationException {
        if (doc instanceof BaseDocument) {
            BaseDocument document = (BaseDocument) doc;
            document.replace(start, end, replacement, null);
        } else {
            doc.remove(start, end - start);
            doc.insertString(start, replacement, null);
        }
    }

    FormattingRules rules(C config) {
        FormattingRules rules = new FormattingRules(vocabulary(), modeNames());
        populateFormattingRules(rules, config);
        return rules;
    }

    LexingState state(C config) {
        LexingStateBuilder<StateEnum, LexingState> bldr = LexingState.builder(enumType);
        populateLexingState(bldr, config);
        return bldr.build();
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

    Formatter createFormatter() {
        return new Fmt();
    }

    String arrToString(String[] arr) {
        if (arr == null) {
            return "null";
        }
        return Arrays.toString(arr);
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
    public String reformat(String text, int from, int to, C config) {
        return reformat(CharStreams.fromString(text), from, to, config);
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
    public String reformat(CharStream text, int from, int to, C config) {
        FormattingRules rules = rules(config);
        LexingState state = state(config);
        Criterion whitespace = whitespace();
        Predicate<Token> debug = debugLogPredicate();
        Lexer lexer = createLexer(text);
        String[] modeNames = modeNames();
        return FormattingAccessor.getDefault().reformat(from, to, indentSize(), rules,
                state, whitespace, debug, lexer, modeNames);
    }

    class Fmt implements Formatter {

        String reformat(Lexer lexer, int start, int end, C config) {
            FormattingRules rules = rules(config);
            LexingState state = state(config);
            Criterion whitespace = whitespace();
            Predicate<Token> debug = debugLogPredicate();
            String[] modeNames = modeNames();
            if (modeNames == null || modeNames.length == 0) {
                throw new IllegalStateException(AntlrFormatterProvider.this
                        + " does not correctly implement modeNames() - got " + arrToString(modeNames));
            }
            return FormattingAccessor.getDefault().reformat(start, end, indentSize(), rules,
                    state, whitespace, debug, lexer, modeNames);
        }

        @Override
        public void reformat(Context cntxt, ParserResult pr) {
            C config = configuration(cntxt);
            Document document = (Document) cntxt.document();
            int start = cntxt.startOffset();
            int end = cntxt.endOffset();
            withDocumentLock(cntxt.document(), () -> {
                withMutableTextInputDisabled(document, () -> {
                    try {
                        Lexer lexer = createLexer(document);
                        lexer.removeErrorListeners();
                        String reformatted = reformat(lexer, start, end, config);
                        replaceInDocument(document, start, end, reformatted);
                    } catch (BadLocationException ex) {
                        Logger.getLogger(Fmt.class.getName()).log(Level.SEVERE, null, ex);
                    }
                });
            });
        }

        @Override
        public void reindent(Context context) {
            // XXX implement with a lexing state enum constant
        }

        @Override
        public boolean needsParserResult() {
            return false;
        }

        @Override
        public int indentSize() {
            return AntlrFormatterProvider.this.indentSize();
        }

        @Override
        public int hangingIndentSize() {
            return AntlrFormatterProvider.this.hangingIndentSize();
        }
    }

    static final class AlwaysFalse implements Predicate<Token> {

        private static final AlwaysFalse INSTANCE = new AlwaysFalse();

        private AlwaysFalse() {
        }

        @Override
        public boolean test(Token t) {
            return false;
        }
    }

    static {
        FormattingAccessor.FMT_CONVERT = afp -> {
            return afp.createFormatter();
        };
    }
}

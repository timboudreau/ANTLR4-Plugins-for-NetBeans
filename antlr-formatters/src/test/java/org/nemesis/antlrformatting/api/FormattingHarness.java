/*
 * Copyright 2016-2019 Tim Boudreau, Frédéric Yvon Vinet
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.nemesis.antlrformatting.api;

import com.mastfrog.function.throwing.io.IOSupplier;
import com.mastfrog.util.collections.IntList;
import com.mastfrog.util.strings.Escaper;
import com.mastfrog.util.strings.Strings;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.Vocabulary;
import org.antlr.v4.runtime.misc.Interval;
import org.antlr.v4.runtime.tree.RuleNode;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.nemesis.antlrformatting.impl.CaretFixer;
import org.nemesis.antlrformatting.impl.CaretInfo;
import org.nemesis.antlrformatting.impl.FormattingAccessor;
import org.nemesis.simple.SampleFile;
import org.nemesis.simple.language.SimpleLanguageLexer;
import static org.nemesis.simple.language.SimpleLanguageLexer.K_BOOLEAN;
import static org.nemesis.simple.language.SimpleLanguageLexer.K_DEFAULT;
import static org.nemesis.simple.language.SimpleLanguageLexer.K_FLOAT;
import static org.nemesis.simple.language.SimpleLanguageLexer.K_OBJECT;
import static org.nemesis.simple.language.SimpleLanguageLexer.K_REFERENCE;
import static org.nemesis.simple.language.SimpleLanguageLexer.K_STRING;
import static org.nemesis.simple.language.SimpleLanguageLexer.L_BOOLEAN;
import static org.nemesis.simple.language.SimpleLanguageLexer.L_STRING;

/**
 * A generic test harness that exposes some of the internal state of the
 * reformatting process so that tests can incrementally check if they are doing
 * the right thing.
 *
 * @author Tim Boudreau
 */
public class FormattingHarness<E extends Enum<E>> {

    final SampleFile file;
    final Class<E> stateType;
    private BiConsumer<LexingStateBuilder<E, ?>, FormattingRules> ruleConfigurer;
    final FA fa = new FA(this);
    private Consumer<ModalToken> beforeEachToken;
    private Consumer<ModalToken> afterEachToken;
    private Predicate<Token> debugEnabled;
    private boolean debug;
    private Function<FormattingAction, FormattingAction> wrapRules;
    public static final Criteria criteria = Criteria.forVocabulary(SimpleLanguageLexer.VOCABULARY);
    public static final Criterion keywords = criteria.anyOf(K_BOOLEAN, K_DEFAULT, L_BOOLEAN, K_OBJECT, K_STRING, K_FLOAT,
            K_REFERENCE, L_STRING);

    FormattingHarness(SampleFile file, Class<E> stateType, BiConsumer<LexingStateBuilder<E, ?>, FormattingRules> ruleConfigurer) {
        this.file = file;
        this.stateType = stateType;
        this.ruleConfigurer = ruleConfigurer;
    }

    public static <E extends Enum<E> & SampleFile> BiFunction<E, BiConsumer<LexingStateBuilder<E, ?>, FormattingRules>, FormattingHarness<E>> factory(Class<E> type) {
        return (file, configurer) -> {
            return new FormattingHarness<>(file, type, configurer);
        };
    }

    /**
     * Add an additional rule configurer to the one being used.
     *
     * @param ruleConfigurer A rule configurer
     * @return this
     */
    FormattingHarness<E> withRuleConfigurer(BiConsumer<LexingStateBuilder<E, ?>, FormattingRules> ruleConfigurer) {
        this.ruleConfigurer = this.ruleConfigurer.andThen(ruleConfigurer);
        return this;
    }

    /**
     * The file this harness will reformat.
     *
     * @return A sample file
     */
    public SampleFile file() {
        return file;
    }

    /**
     * Run a consumer that performs some tests before each token is processed.
     *
     * @param before a consumer
     * @return this
     */
    public FormattingHarness<E> onBeforeEachToken(Consumer<ModalToken> before) {
        if (beforeEachToken != null) {
            beforeEachToken = beforeEachToken.andThen(before);
        } else {
            beforeEachToken = before;
        }
        return this;
    }

    /**
     * Run a consumer that performs some tests after each token is processed.
     *
     * @param after a consumer
     * @return this
     */
    public FormattingHarness<E> onAfterEachToken(Consumer<ModalToken> after) {
        if (afterEachToken != null) {
            afterEachToken = afterEachToken.andThen(after);
        } else {
            afterEachToken = after;
        }
        return this;
    }

    /**
     * Turn on verbose FormattingRule processing debugging for tokens where this
     * predicate returns true.
     *
     * @param debug
     * @return
     */
    public FormattingHarness<E> withDebugPredicate(Predicate<Token> debug) {
        if (this.debugEnabled != null) {
            this.debugEnabled = debugEnabled.or(debug);
        } else {
            this.debugEnabled = debug;
        }
        return this;
    }

    /**
     * Wrap every formatting action in the rules using the passed function
     * (usually to log that the rule is being applied).
     *
     * @param wrapRule A function which takes a formatting action and returns
     * another which likely wraps the original and performs some testing logic
     * @return this
     */
    public FormattingHarness<E> wrapFormattingActions(Function<FormattingAction, FormattingAction> wrapRule) {
        if (this.wrapRules != null) {
            this.wrapRules = this.wrapRules.andThen(wrapRule);
        } else {
            this.wrapRules = wrapRule;
        }
        return this;
    }

    /**
     * Log all formatting actions as they are applied.
     *
     * @return this
     */
    public FormattingHarness<E> logFormattingActions() {
        return wrapFormattingActions(new RuleLoggingFactory());
    }

    /**
     * Turn on formatting rule debug logging.
     *
     * @param val On or off
     * @return this
     */
    public FormattingHarness<E> setDebug(boolean val) {
        this.debug = val;
        return this;
    }

    Predicate<Token> debugFieldPredicate = tok -> {
        return debug;
    };

    Predicate<Token> debugPredicate() {
        if (debugEnabled != null) {
            return debugFieldPredicate.or(debugEnabled);
        }
        return debugFieldPredicate;
    }

    /**
     * Turn verbose rule debugging on for any tokens that pass this predicate's
     * test. If this has called previously, it will be or'd with that one.
     *
     * @param pred A predicate
     * @return this
     */
    public FormattingHarness<E> debugLogOn(Predicate<Token> pred) {
        if (this.debugEnabled != null) {
            this.debugEnabled = this.debugEnabled.or(pred);
        } else {
            this.debugEnabled = pred;
        }
        return this;
    }

    /**
     * Turn on debug logging only for a specific list of token indices.
     *
     * @param toks a list of token indices in the stream
     * @return this
     */
    public FormattingHarness<E> debugLogOn(int... toks) {
        Criterion c = criteria.anyOf(toks);
        return debugLogOn(c.toTokenPredicate());
    }

    /**
     * This is the main entry point, which will perform reformatting.
     *
     * @return The reformatted text
     * @throws IOException if something goes wrong
     */
    String reformat() throws IOException {
        return withHarnessSet(fa, () -> {
            return new GenericConfig<E>(stateType, this.ruleConfigurer, tok -> {
                // use another lambda here so the debug predicate can
                // be added to while running and take effect
                return debugPredicate().test(tok);
            }).reformattedString(file.text(), 0, file.length(), null);
        });
    }

    static <T> T withHarnessSet(FA fa, IOSupplier<T> r) throws IOException {
        // use accessor method to ensure we aren't clobbered by
        // late initialization
        FormattingAccessor old = FormattingAccessor.getDefault();
        try {
            FormattingAccessor.DEFAULT = fa;
            return r.get();
        } finally {
            FormattingAccessor.DEFAULT = old;
        }
    }

    /**
     * Get the current stream rewriter. This uses ThreadLocals to allow
     * concurrent tests, but when not in the closure of reformat(), returns the
     * last one created.
     *
     * @return A rewriter
     */
    public static FastStreamRewriter rewriter() {
        LoggableStreamRewriter rew = rewriterContext.get();
        if (rew == null) {
            rew = lastRewriter;
        }
        if (rew != null) {
            return rew.orig;
        }
        return null;
    }

    /**
     * Get the distance to the nearest newline to the passed token index, taking
     * into account any insertions or modifications during reformatting thus
     * far.
     *
     * @param tokenIndex The token index
     * @return The number of characters from the start of this token back to the
     * nearest newline; 0 means the current token starts with a newline.
     */
    public static int lastNewlineDistance(int tokenIndex) {
        FastStreamRewriter rew = rewriter();
        assertNotNull(rew, "No rewriter currently in use");
        return rew.lastNewlineDistance(tokenIndex);
    }

    /**
     * Get a snapshot of the character positions in the document of the start
     * (in characters) of each character in the current document at the time
     * this method is called during reformatting. Useful to verify that things
     * are where they are supposed to be.
     *
     * @return A list of integers
     */
    public static IntList rewrittenTokenStartPositionsSnapshot() {
        FastStreamRewriter rew = rewriter();
        assertNotNull(rew, "No rewriter currently in use");
        return rew.startPositions.copy();
    }

    /**
     * Get a snapshot of the newline positions in the document of each newline
     * in the current document at the time this method is called during
     * reformatting. Useful to verify that things are where they are supposed to
     * be.
     *
     * @return A list of integers
     */
    public static IntList rewrittenNewlinePositionsInDocumentSnapshot() {
        FastStreamRewriter rew = rewriter();
        assertNotNull(rew, "No rewriter currently in use");
        return rew.newlinePositions.copy();
    }

    /**
     * Get the rewritten document text (can be called on a format in-progress).
     *
     * @return The rewritten text
     */
    public static String rewrittenText() {
        FastStreamRewriter rew = rewriter();
        assertNotNull(rew, "No rewriter currently in use");
        return rew.getText();
    }

    static FCImpl lastContext;
    static ThreadLocal<FCImpl> context = new ThreadLocal<>();
    static EverythingTokenStream lastStream;
    static ThreadLocal<EverythingTokenStream> streamContext = new ThreadLocal<>();
    static LoggableStreamRewriter lastRewriter;
    static ThreadLocal<LoggableStreamRewriter> rewriterContext = new ThreadLocal<>();

    /**
     * Print the list of token modification operations in the current format to
     * the console, most recent first.
     *
     * @param limit The maximum number of operations to list
     */
    public void logRecentOps(int limit) {
        LoggableStreamRewriter rew = rewriterContext.get();
        if (rew == null) {
            rew = lastRewriter;
        }
        if (rew != null) {
            rew.listRecentOps(limit);
        }
    }

    /**
     * Get the current formatting context. This uses ThreadLocals to allow
     * concurrent tests, but when not in the closure of reformat(), returns the
     * last one created.
     *
     * @return A rewriter
     */
    public static FormattingContext context() {
        return _context();
    }

    static FCImpl _context() {
        FCImpl result = context.get();
        if (result == null) {
            result = lastContext;
        }
        assertNotNull(result, "No current context");
        return result;
    }

    /**
     * Get the LexingState for the in-progress reformat.
     *
     * @return The lexing state
     */
    public static LexingState currentLexingState() {
        FCImpl result = _context();
        assertNotNull(result, "No current context");
        return result.state;
    }

    /**
     * Get the stream currently being processed.
     *
     * @return The stream
     */
    public static EnhancedTokenStream stream() {
        EverythingTokenStream result = streamContext.get();
        if (result == null) {
            result = lastStream;
        }
        return result;
    }

    static final class FA<E extends Enum<E>> extends FormattingAccessor {

        private final FormattingHarness<E> harn;

        public FA(FormattingHarness<E> harn) {
            this.harn = harn;
        }

        @Override
        public FormattingResult reformat(int start, int end, int indentSize,
                FormattingRules rules, LexingState state, Criterion whitespace,
                Predicate<Token> debug, Lexer lexer, String[] modeNames,
                CaretInfo caretPos, CaretFixer updateWithCaretPosition,
                RuleNode parseTreeRoot) {
            if (harn.wrapRules != null) {
                rules = rules.wrapAllRules(harn.wrapRules);
            }
            lexer.reset();
            EverythingTokenStream tokens = lastStream = new EverythingTokenStream(lexer, modeNames);
            IntFunction<Set<Integer>> ruleFetcher = null;
            if (parseTreeRoot != null) {
                ParserRuleCollector collector = new ParserRuleCollector(tokens);
                ruleFetcher = collector.visit(parseTreeRoot);
                lexer.reset();
            }

            LoggableStreamRewriter rew = new LoggableStreamRewriter(new FastStreamRewriter(tokens));
//            StreamRewriterFacade rew = new LinePositionComputingRewriter(tokens);
            FCImpl result = new FCImpl(rew, start, end, indentSize,
                    rules, state, whitespace, debug, ruleFetcher);
            result.onBefore = tok -> {
                if (harn.beforeEachToken != null) {
                    harn.beforeEachToken.accept(tok);
                }
            };
            result.onAfter = tok -> {
                if (harn.afterEachToken != null) {
                    harn.afterEachToken.accept(tok);
                }
            };
            FCImpl oldCtx = context.get();
            EverythingTokenStream oldStream = streamContext.get();
            LoggableStreamRewriter oldRew = rewriterContext.get();
            context.set(result);
            lastContext = result;
            streamContext.set(tokens);
            lastStream = tokens;
            rewriterContext.set(rew);
            lastRewriter = rew;
            try {
                return result.go(tokens, caretPos, updateWithCaretPosition);
            } finally {
                streamContext.set(oldStream);
                context.set(oldCtx);
                rewriterContext.set(oldRew);
            }
        }

        @Override
        public FormattingRules createFormattingRules(Vocabulary vocabulary, String[] modeNames, String[] parserRuleNames) {
            return new FormattingRules(vocabulary, modeNames, parserRuleNames);
        }
    }

    static final class FCImpl extends FormattingContextImpl {

        Consumer<ModalToken> onBefore;
        Consumer<ModalToken> onAfter;

        public FCImpl(StreamRewriterFacade rew, int start, int end, int indentSize, FormattingRules rules, LexingState state, Criterion whitespace, Predicate<Token> debugLogging, IntFunction<Set<Integer>> ruleFinder) {
            super(rew, start, end, indentSize, rules, state, whitespace, debugLogging, ruleFinder);
        }

        @Override
        boolean onOneToken(ModalToken tok, int prevType, int prevMode, int nextType, EverythingTokenStream tokens, boolean hasFollowingNewline, int tokensFormatted) {
            if (onBefore != null) {
                onBefore.accept(tok);
            }
            boolean result = super.onOneToken(tok, prevType, prevMode, nextType, tokens, hasFollowingNewline, tokensFormatted);
            if (onAfter != null) {
                onAfter.accept(tok);
            }
            return result;
        }

        @Override
        protected void close(EverythingTokenStream str) {
            // do nothing
        }
    }

    static class RuleLoggingFactory implements Function<FormattingAction, FormattingAction> {

        @Override
        public FormattingAction apply(FormattingAction t) {
            return new LoggingAction(t);
        }
    }

    static class LoggingAction implements FormattingAction {

        private final FormattingAction delegate;

        public LoggingAction(FormattingAction delegate) {
            this.delegate = delegate;
        }

        @Override
        public void accept(Token token, FormattingContext ctx, LexingState state) {
            System.out.println("Apply " + delegate + " to " + token.getTokenIndex() + ": "
                    + Strings.escape(token.getText(), Escaper.NEWLINES_AND_OTHER_WHITESPACE));
            delegate.accept(token, ctx, state);
        }

        public String toString() {
            return "log:" + delegate.toString();
        }
    }

    static class LoggableStreamRewriter implements StreamRewriterFacade {

        private final FastStreamRewriter orig;
        private LinkedList<String> ops = new LinkedList<>();

        public LoggableStreamRewriter(FastStreamRewriter orig) {
            this.orig = orig;
        }

        static String elide(String text) {
            StringBuilder sb = new StringBuilder();
            FastStreamRewriter.elideSpaces(text, sb);
            return sb.toString();
        }

        List<String> ops() {
            return ops();
        }

        void listRecentOps(int limit) {
            for (int i = 0; i < Math.min(limit, ops.size()); i++) {
                String s = ops.get(i);
                System.out.println(" - " + s);
            }
        }

        void op(String action, int tok) {
            op(action, orig.stream.get(tok));
        }

        void op(String action, Token tok) {
            ops.push(action + " " + tok.getTokenIndex() + " " + elide(Strings.escape(tok.getText(), Escaper.NEWLINES_AND_OTHER_WHITESPACE)));
        }

        void op(String action, String what, int tok) {
            op(action, what, orig.stream.get(tok));
        }

        void op(String action, String what, Token tok) {
            what = elide(what);
            ops.push(action + " " + what + " on " + tok.getTokenIndex() + " " + elide(Strings.escape(tok.getText(), Escaper.NEWLINES_AND_OTHER_WHITESPACE)));
        }

        @Override
        public void delete(Token tok) {
            op("Delete", tok);
            orig.delete(tok);
        }

        @Override
        public void delete(int tokenIndex) {
            op("Delete", tokenIndex);
            orig.delete(tokenIndex);
        }

        @Override
        public String getText() {
            return orig.getText();
        }

        @Override
        public String getText(Interval interval) {
            return orig.getText(interval);
        }

        @Override
        public void insertAfter(Token tok, String text) {
            op("insertAfter", text, tok);
            orig.insertAfter(tok, text);
        }

        @Override
        public void insertAfter(int index, String text) {
            op("insertAfter", text, index);
            orig.insertAfter(index, text);
        }

        @Override
        public void insertBefore(Token tok, String text) {
            op("insertBefore", text, tok);
            orig.insertBefore(tok, text);
        }

        @Override
        public void insertBefore(int index, String text) {
            op("insertBefore", text, index);
            orig.insertBefore(index, text);
        }

        @Override
        public int lastNewlineDistance(int tokenIndex) {
            return orig.lastNewlineDistance(tokenIndex);
        }

        @Override
        public void replace(Token tok, String text) {
            op("replace", text, tok);
            orig.replace(tok, text);
        }

        @Override
        public void replace(int index, String text) {
            op("replace", text, index);
            orig.replace(index, text);
        }

        @Override
        public void close() {
            orig.close();
        }

        @Override
        public String rewrittenText(int index) {
            return orig.rewrittenText(index);
        }
    }
}

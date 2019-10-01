/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nemesis.antlrformatting.api;

import com.mastfrog.function.throwing.io.IOSupplier;
import com.mastfrog.util.strings.Escaper;
import com.mastfrog.util.strings.Strings;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.Vocabulary;
import org.antlr.v4.runtime.misc.Interval;
import org.antlr.v4.runtime.tree.RuleNode;
import org.nemesis.antlrformatting.impl.CaretFixer;
import org.nemesis.antlrformatting.impl.CaretInfo;
import org.nemesis.antlrformatting.impl.FormattingAccessor;
import org.nemesis.simple.SampleFiles;
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
 *
 * @author Tim Boudreau
 */
public class FormattingHarness {

    private final SampleFiles file;
    private BiConsumer<LexingStateBuilder<SLState, ?>, FormattingRules> ruleConfigurer;
    private final FA fa = new FA(this);
    private Consumer<ModalToken> beforeEachToken;
    private Consumer<ModalToken> afterEachToken;
    private Predicate<Token> debugEnabled;
    private boolean debug;
    private Function<FormattingAction, FormattingAction> wrapRules;
    public static final Criteria criteria = Criteria.forVocabulary(SimpleLanguageLexer.VOCABULARY);
    public static final Criterion keywords = criteria.anyOf(K_BOOLEAN, K_DEFAULT, L_BOOLEAN, K_OBJECT, K_STRING, K_FLOAT,
            K_REFERENCE, L_STRING);

    FormattingHarness(SampleFiles file, BiConsumer<LexingStateBuilder<SLState, ?>, FormattingRules> ruleConfigurer) {
        this.file = file;
        this.ruleConfigurer = ruleConfigurer;
    }

    FormattingHarness withRuleConfigurer(BiConsumer<LexingStateBuilder<SLState, ?>, FormattingRules> ruleConfigurer) {
        this.ruleConfigurer = this.ruleConfigurer.andThen(ruleConfigurer);
        return this;
    }

    public FormattingHarness onBeforeEachToken(Consumer<ModalToken> before) {
        if (beforeEachToken != null) {
            beforeEachToken = beforeEachToken.andThen(before);
        } else {
            beforeEachToken = before;
        }
        return this;
    }

    public FormattingHarness onAfterEachToken(Consumer<ModalToken> after) {
        if (afterEachToken != null) {
            afterEachToken = afterEachToken.andThen(after);
        } else {
            afterEachToken = after;
        }
        return this;
    }

    public FormattingHarness withDebugPredicate(Predicate<Token> debug) {
        if (this.debugEnabled != null) {
            this.debugEnabled = debugEnabled.or(debug);
        } else {
            this.debugEnabled = debug;
        }
        return this;
    }

    public FormattingHarness wrapFormattingActions(Function<FormattingAction, FormattingAction> wrapRule) {
        if (this.wrapRules != null) {
            this.wrapRules = this.wrapRules.andThen(wrapRule);
        } else {
            this.wrapRules = wrapRule;
        }
        return this;
    }

    public FormattingHarness logFormattingActions() {
        return wrapFormattingActions(new RuleLoggingFactory());
    }

    public FormattingHarness setDebug(boolean val) {
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

    public FormattingHarness debugLogOn(Predicate<Token> pred) {
        if (this.debugEnabled != null) {
            this.debugEnabled = this.debugEnabled.or(pred);
        } else {
            this.debugEnabled = pred;
        }
        return this;
    }

    public FormattingHarness debugLogOn(int... toks) {
        Criterion c = criteria.anyOf(toks);
        return debugLogOn(c.toTokenPredicate());
    }

    public String reformat() throws IOException {
        return withHarnessSet(fa, () -> {
            return new GenericConfig(this.ruleConfigurer, tok -> {
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

    static FastStreamRewriter rewriter() {
        LoggableStreamRewriter rew = rewriterContext.get();
        if (rew == null) {
            rew = lastRewriter;
        }
        if (rew != null) {
            return rew.orig;
        }
        return null;
    }

    static FCImpl lastContext;
    static ThreadLocal<FCImpl> context = new ThreadLocal<>();
    static EverythingTokenStream lastStream;
    static ThreadLocal<EverythingTokenStream> streamContext = new ThreadLocal<>();
    static LoggableStreamRewriter lastRewriter;
    static ThreadLocal<LoggableStreamRewriter> rewriterContext = new ThreadLocal<>();

    public void logRecentOps(int limit) {
        LoggableStreamRewriter rew = rewriterContext.get();
        if (rew == null) {
            rew = lastRewriter;
        }
        if (rew != null) {
            rew.listRecentOps(limit);
        }
    }

    public FCImpl context() {
        FCImpl result = context.get();
        if (result == null) {
            result = lastContext;
        }
        return result;
    }

    public EverythingTokenStream stream() {
        EverythingTokenStream result = streamContext.get();
        if (result == null) {
            result = lastStream;
        }
        return result;
    }

    public LexingState state() {
        return context().state;
    }

    static final class FA extends FormattingAccessor {

        private final FormattingHarness harn;

        public FA(FormattingHarness harn) {
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

        boolean onOneToken(ModalToken tok, int prevType, int prevMode, int nextType, EverythingTokenStream tokens, boolean hasFollowingNewline) {
            if (onBefore != null) {
                onBefore.accept(tok);
            }
            boolean result = super.onOneToken(tok, prevType, prevMode, nextType, tokens, hasFollowingNewline);
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

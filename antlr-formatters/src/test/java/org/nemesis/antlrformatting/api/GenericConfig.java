package org.nemesis.antlrformatting.api;

import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.prefs.Preferences;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.Vocabulary;
import org.nemesis.antlrformatting.spi.AntlrFormatterProvider;
import org.nemesis.simple.language.SimpleLanguageLexer;
import org.netbeans.modules.editor.indent.spi.Context;

/**
 *
 * @author Tim Boudreau
 */
final class GenericConfig extends AntlrFormatterProvider<Preferences, SLState> {

    private final BiConsumer<LexingStateBuilder<SLState, ?>, FormattingRules> configurer;
    private Predicate<Token> debugLog;

    GenericConfig(BiConsumer<LexingStateBuilder<SLState, ?>, FormattingRules> configurer, Predicate<Token> debugLog) {
        super(SLState.class);
        this.configurer = configurer;
        this.debugLog = debugLog;
    }

    GenericConfig(BiConsumer<LexingStateBuilder<SLState, ?>, FormattingRules> configurer) {
        super(SLState.class);
        this.configurer = configurer;
    }

    @Override
    protected Preferences configuration(Context ctx) {
        return null;
    }

    @Override
    protected Lexer createLexer(CharStream stream) {
        return new SimpleLanguageLexer(stream);
    }

    @Override
    protected Vocabulary vocabulary() {
        return SimpleLanguageLexer.VOCABULARY;
    }

    @Override
    protected String[] modeNames() {
        return SimpleLanguageLexer.modeNames;
    }

    @Override
    protected Criterion whitespace() {
        return Criterion.matching(vocabulary(), SimpleLanguageLexer.S_WHITESPACE);
    }

    @Override
    protected void configure(LexingStateBuilder<SLState, ?> stateBuilder, FormattingRules rules, Preferences config) {
        configurer.accept(stateBuilder, rules);
    }

    @Override
    protected Predicate<Token> debugLogPredicate() {
        return debugLog == null ? Criterion.NEVER.toTokenPredicate() : debugLog;
    }
}

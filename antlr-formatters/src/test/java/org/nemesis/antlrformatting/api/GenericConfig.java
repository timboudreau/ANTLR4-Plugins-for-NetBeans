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
final class GenericConfig<E extends Enum<E>> extends AntlrFormatterProvider<Preferences, E> {

    private final BiConsumer<LexingStateBuilder<E, ?>, FormattingRules> configurer;
    private Predicate<Token> debugLog;

    GenericConfig(Class<E> stateType, BiConsumer<LexingStateBuilder<E, ?>, FormattingRules> configurer, Predicate<Token> debugLog) {
        super(stateType);
        this.configurer = configurer;
        this.debugLog = debugLog;
    }

    GenericConfig(Class<E> stateType, BiConsumer<LexingStateBuilder<E, ?>, FormattingRules> configurer) {
        super(stateType);
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
    protected void configure(LexingStateBuilder<E, ?> stateBuilder, FormattingRules rules, Preferences config) {
        configurer.accept(stateBuilder, rules);
    }

    @Override
    protected Predicate<Token> debugLogPredicate() {
        return debugLog == null ? Criterion.NEVER.toTokenPredicate() : debugLog;
    }
}

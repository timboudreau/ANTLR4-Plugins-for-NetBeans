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

import com.mastfrog.antlr.utils.Criterion;
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

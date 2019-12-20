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
package org.nemesis.antlrformatting.spi;

import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.Vocabulary;
import org.antlr.v4.runtime.tree.RuleNode;
import com.mastfrog.antlr.utils.Criterion;
import org.nemesis.antlrformatting.api.FormattingRules;
import org.nemesis.antlrformatting.api.LexingStateBuilder;
import org.netbeans.modules.editor.indent.spi.Context;

/**
 *
 * @author Tim Boudreau
 */
public class WrapperFormatterProvider<C, E extends Enum<E>> extends AntlrFormatterProvider<C, E> {

    private final AntlrFormatterProvider<C, E> prov;
    private final Supplier<C> config;
    private final Supplier<Predicate<Token>> debugSupplier;

    public WrapperFormatterProvider(Class<E> type, AntlrFormatterProvider<C, E> prov, Supplier<C> config, Supplier<Predicate<Token>> debugSupplier) {
        super(type);
        this.prov = prov;
        this.config = config;
        this.debugSupplier = debugSupplier;
    }

    @Override
    protected C configuration(Context ctx) {
        return config.get();
    }

    @Override
    protected Lexer createLexer(CharStream stream) {
        return prov.createLexer(stream);
    }

    @Override
    protected Vocabulary vocabulary() {
        return prov.vocabulary();
    }

    @Override
    protected String[] modeNames() {
        return prov.modeNames();
    }

    @Override
    public void configure(LexingStateBuilder<E, ?> stateBuilder, FormattingRules rules, C config) {
        prov.configure(stateBuilder, rules, this.config.get());
    }

    @Override
    protected String[] parserRuleNames() {
        return prov.parserRuleNames();
    }

    @Override
    protected RuleNode parseAndExtractRootRuleNode(Lexer lexer) {
        return prov.parseAndExtractRootRuleNode(lexer);
    }

    @Override
    protected Predicate<Token> debugLogPredicate() {
        return debugSupplier.get();
    }

    @Override
    public Criterion whitespace() {
        return prov.whitespace();
    }

    @Override
    public Lexer createLexer(Document document) throws BadLocationException {
        return prov.createLexer(document);
    }

    @Override
    public int indentSize(C config) {
        return prov.indentSize(this.config.get());
    }

//    @Override
//    public FormattingResult reformat(CharStream text, int from, int to, C config) {
//        return prov.reformat(text, from, to, this.config.get());
//    }
//
//    @Override
//    RulesAndState populate(C config) {
//        return prov.populate(this.config.get());
//    }
//
//    @Override
//    public String reformattedString(String text, int from, int to, C config) {
//        return prov.reformattedString(text, from, to, this.config.get());
//    }
//
//    @Override
//    public FormattingResult reformat(String text, int from, int to, C config) {
//        return prov.reformat(text, from, to, this.config.get());
//    }
}

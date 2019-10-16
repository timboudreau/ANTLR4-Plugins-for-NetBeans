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

import static com.mastfrog.util.preconditions.Checks.notNull;
import com.mastfrog.util.preconditions.Exceptions;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.Document;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.Vocabulary;
import org.antlr.v4.runtime.misc.Pair;
import org.antlr.v4.runtime.tree.RuleNode;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.nemesis.antlrformatting.spi.AntlrFormatterProvider;
import org.nemesis.antlrformatting.spi.AntlrFormatterStub;
import org.nemesis.antlrformatting.spi.WrapperFormatterProvider;
import org.nemesis.simple.SampleFile;

/**
 *
 * @author Tim Boudreau
 */
public class AntlrFormattingHarness<C, E extends Enum<E>> extends FormattingHarness<E> {

    private final BCE<C, E> bce;

    /**
     * Create an AntlrFormattingHarness, looking up as much information as
     * possible via reflection for convenience. Relies on being able to look up
     * and load classes based on the generic interfaces of the passed type, so
     * may not work for everything, but sufficient for a lot of tests.
     *
     * @param <T> The AntlrFormatterStub type
     * @param <L> The lexer type
     * @param file The file to create a harness for
     * @param stubType The subclass of AntlrFormatterStub being tested
     * @param whitespaceTokens An array of token rule ids which are whitespace
     * and should be ignored when reformatting
     * @throws ClassNotFoundException If something goes wrong
     * @throws NoSuchFieldException If something goes wrong
     * @throws IllegalArgumentException If something goes wrong
     * @throws IllegalAccessException If something goes wrong
     * @throws InstantiationException If something goes wrong
     * @throws NoSuchMethodException If something goes wrong
     */
    public <T extends AntlrFormatterStub<E, L>, L extends Lexer> AntlrFormattingHarness(SampleFile file, Class<T> stubType, int... whitespaceTokens) throws ClassNotFoundException, NoSuchFieldException, IllegalArgumentException, IllegalAccessException, InstantiationException, NoSuchMethodException {
        this(file, reflective(stubType, whitespaceTokens));
    }

    private AntlrFormattingHarness(SampleFile file, Pair<Class<E>, AntlrFormatterProvider<C, E>> pair) {
        this(file, pair.a, pair.b);
    }

    public AntlrFormattingHarness(SampleFile file, Class<E> stateType, AntlrFormatterProvider<C, E> provider) {
        this(new BCE<>(provider, stateType), file, stateType, provider);
    }

    AntlrFormattingHarness(BCE<C, E> bce, SampleFile file, Class<E> stateType, AntlrFormatterProvider<C, E> provider) {
        super(file, stateType, bce);
        this.bce = bce;
        bce.debugPredicate = this::debugPredicate;
    }

    /**
     * Main entry point - reformat the file - callbacks for checking things will
     * be called within the closure of this method.
     *
     * @param config The config
     * @return The reformatted text
     * @throws IOException If something goes wrong
     */
    public String reformat(C config) throws IOException {
        assertNotNull(config, "config is null");
        C old = bce.currentConfig.get();
        bce.currentConfig.set(notNull("config", config));
        try {
            return withHarnessSet(fa, () -> {
                return bce.provider.reformattedString(
                        file.text(), 0, file.length(), config);
            });
        } finally {
            bce.currentConfig.set(old);
        }
    }

    public Lexer lex(String text) {
        Document doc = new DefaultStyledDocument();
        try {
            doc.insertString(0, text, null);
            return bce.provider.createLexer(doc);
        } catch (BadLocationException ex) {
            return Exceptions.chuck(ex);
        }
    }

    static final class BCE<C, E extends Enum<E>> implements BiConsumer<LexingStateBuilder<E, ?>, FormattingRules> {

        ThreadLocal<C> currentConfig = new ThreadLocal<>();
        final WrapperFormatterProvider<C, E> provider;
        Supplier<Predicate<Token>> debugPredicate;

        public BCE(AntlrFormatterProvider<C, E> provider, Class<E> type) {
            this.provider = new WrapperFormatterProvider<>(type, provider, currentConfig::get, this::debugPredicate);
        }

        Predicate<Token> debugPredicate() {
            return debugPredicate == null ? Criterion.NEVER.toTokenPredicate() : debugPredicate.get();
        }

        @Override
        public void accept(LexingStateBuilder<E, ?> t, FormattingRules u) {
            provider.configure(t, u, currentConfig.get());
        }
    }

    static <C, E extends Enum<E>, L extends Lexer, T extends AntlrFormatterStub<E, L>> Pair<Class<E>, AntlrFormatterProvider<C, E>> reflective(Class<T> type, int... whitespaceTokens) throws ClassNotFoundException, NoSuchFieldException, IllegalArgumentException, IllegalAccessException, InstantiationException, NoSuchMethodException {
        Type[] gis = type.getGenericInterfaces();
        if (gis.length > 0) {
            for (int i = 0; i < gis.length; i++) {
                Type t = gis[i];
                String tn = t.getTypeName();
                if (tn.startsWith(AntlrFormatterStub.class.getName())) {
                    int ix = tn.indexOf('<');
                    if (ix > 0) {
                        String genericSig = tn.substring(ix + 1, tn.length() - 1);
                        String[] counterAndLexer = genericSig.split(",");
                        if (counterAndLexer.length == 2) {
                            Class<E> enumType = (Class<E>) Class.forName(counterAndLexer[0].trim());
                            Class<L> lexerType = (Class<L>) Class.forName(counterAndLexer[1].trim());
                            Constructor<L> lexerConstructor = lexerType.getConstructor(CharStream.class);
                            Vocabulary vocabulary = (Vocabulary) lexerType.getField("VOCABULARY").get(null);
                            String[] modeNames = (String[]) lexerType.getField("modeNames").get(null);
                            Function<CharStream, L> lexerFactory = cs -> {
                                try {
                                    L result = lexerConstructor.newInstance(cs);
                                    result.removeErrorListeners();
                                    return result;
                                } catch (Exception ex) {
                                    return Exceptions.chuck(ex);
                                }
                            };

                            Function<Lexer, RuleNode> parserFactory = lex -> null;
                            try {
                                Class<? extends Parser> parser
                                        = (Class<? extends Parser>) Class.forName(lexerType.getName().replace("Lexer", "Parser"));

                                Constructor<? extends Parser> pcon
                                        = parser.getConstructor(TokenStream.class);

                                String[] parserRuleNames = (String[]) parser.getField("ruleNames").get(null);

                                Method entryPointRule = parser.getMethod(parserRuleNames[0]);

                                parserFactory = lexer -> {
                                    lexer.reset();
                                    CommonTokenStream str = new CommonTokenStream(lexer);
                                    try {
                                        Parser p = pcon.newInstance(str);
                                        p.removeErrorListeners();
                                        return (RuleNode) entryPointRule.invoke(p);
                                    } catch (Exception ex) {
                                        return Exceptions.chuck(ex);
                                    }
                                };
                            } catch (Exception e) {
                                System.err.println("Could not find a corresponding parser for " + type);
                                e.printStackTrace();
                            }

                            T stub = type.newInstance();
                            AntlrFormatterProvider prov = stub.toFormatterProvider("text/dummy", enumType, vocabulary, modeNames, lexerFactory,
                                    whitespaceTokens, modeNames, parserFactory);
                            return new Pair<>(enumType, prov);
                        }
                    }
                }
            }
        }
        return null;
    }
}

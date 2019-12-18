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
package org.nemesis.antlr.completion;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import org.antlr.v4.runtime.CommonToken;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.Token;
import com.mastfrog.function.throwing.io.IOFunction;
import com.mastfrog.function.throwing.ThrowingFunction;
import org.netbeans.spi.editor.completion.CompletionItem;
import org.netbeans.spi.editor.completion.CompletionProvider;
import org.netbeans.spi.editor.completion.CompletionResultSet;
import org.netbeans.spi.editor.completion.CompletionTask;
import org.netbeans.spi.editor.completion.support.AsyncCompletionQuery;
import org.netbeans.spi.editor.completion.support.AsyncCompletionTask;
import org.openide.util.Exceptions;
import org.openide.util.Parameters;

/**
 *
 * @author Tim Boudreau
 */
public final class AntlrCompletionProvider implements CompletionProvider {

    private final IOFunction<? super Document, ? extends Lexer> lexerFactory;

    private final Iterable<? extends CompletionStub<?>> stubs;

    AntlrCompletionProvider(IOFunction<? super Document, ? extends Lexer> lexerFactory, Iterable<? extends CompletionStub<?>> stubs) {
        this.lexerFactory = lexerFactory;
        this.stubs = stubs;
    }

    public static CompletionsBuilder builder(IOFunction<? super Document, ? extends Lexer> lexerFactory) {
        return new CompletionsBuilder(lexerFactory);
    }

    @Override
    public CompletionTask createTask(int queryType, JTextComponent component) {
        Parameters.notNull("component", component);
        if (queryType == COMPLETION_QUERY_TYPE) {
            return new AsyncCompletionTask(new CP(), component);
        }
        return new DummyTask();
    }

    static final class DummyTask implements CompletionTask {

        @Override
        public void query(CompletionResultSet resultSet) {
            // do nothing
        }

        @Override
        public void refresh(CompletionResultSet resultSet) {
            // do nothing
        }

        @Override
        public void cancel() {
            // do nothing
        }

    }

    @Override
    public int getAutoQueryTypes(JTextComponent component, String typedText) {
        return 0;
    }

    public String toString() {
        return stubs.toString();
    }

    interface PrepIt {

        void withTokenInfo(List<Token> tokens, int[] tokenFrequencies, Token caretToken) throws IOException;
    }

    void prep(Document doc, int caretPosition, PrepIt prep) throws IOException {
        Lexer lexer = lexerFactory.apply(doc);
//        lexer.reset();
        List<Token> tokens = new ArrayList<>();
        // Prioritize token suggestions based on their frequency in the
        // document (the items will then de-prioritize those that are
        // punctuation by multiplying this number)
        int[] frequencies = new int[lexer.getVocabulary().getMaxTokenType() + 1];
        int ix = 0;
        CommonToken caretToken = null;
        for (Token t = lexer.nextToken(); t.getType() != Token.EOF; t = lexer.nextToken()) {
            CommonToken ct = t instanceof CommonToken ? (CommonToken) t
                    : new CommonToken(t);
            if (ct.getTokenIndex() == -1) {
                ct.setTokenIndex(ix++);
            }
            tokens.add(ct);
            frequencies[t.getType()]++;
            if (caretToken == null && TokenUtils.contains(ct, caretPosition)) {
                System.out.println("TARGET TOKEN " + lexer.getVocabulary().getSymbolicName(t.getType())
                        + " " + lexer.getVocabulary().getDisplayName(t.getType()) + " "
                        + lexer.getVocabulary().getLiteralName(t.getType()));

                try {
                    System.out.println("CARET WORD: " + CompletionItemProvider.wordAtCaretPosition(doc, caretPosition));
                } catch (BadLocationException ex) {
                    Exceptions.printStackTrace(ex);
                }
                caretToken = ct;
            }
        }
        prep.withTokenInfo(tokens, frequencies, caretToken);
    }

    private class CP extends AsyncCompletionQuery {

        @Override
        protected void query(CompletionResultSet resultSet, Document doc, int caretOffset) {
            Parameters.notNull("doc", doc);
            try {
                prep(doc, caretOffset, (List<Token> tokens, int[] frequencies, Token caretToken) -> {
                    // It will not be uncommon for multiple providers to use the same set
                    // of data, so don't recompute it every time
                    Map<CompletionItemProvider<?>, Collection<?>> cache = new HashMap<>();
                    for (CompletionStub<?> stub : stubs) {
                        TokenMatch matchName = stub.matches(tokens, caretToken);
                        if (matchName == null) {
                            continue;
                        }
                        try {
                            runOne(matchName, resultSet, stub, doc, caretOffset,
                                    tokens, frequencies, caretToken, cache);
                        } catch (Exception ex) {
                            Exceptions.printStackTrace(ex);
                        }
                    }
                });
            } catch (Exception ex) {
                Exceptions.printStackTrace(ex);
            } finally {
                resultSet.finish();
            }
        }

        // XXX need caret offset - and shared way to find the caret token
        // and maybe pass in a list of tokens
        private <I> void runOne(TokenMatch matchName, CompletionResultSet resultSet, CompletionStub<I> stub, Document doc, int caretOffset, List<Token> tokens, int[] frequencies, Token caretToken, Map<CompletionItemProvider<?>, Collection<?>> cache) throws Exception {
            ThrowingFunction<CompletionItemProvider<? extends I>, Collection<? extends I>> cachedFetcher
                    = (CompletionItemProvider<? extends I> io) -> {
                        Collection<?> c = cache.get(io);
                        if (c != null) {
                            return (Collection<? extends I>) c;
                        }
                        Collection<? extends I> coll = io.fetch(doc, caretOffset, tokens, frequencies, caretToken, matchName);
                        cache.put(io, coll);
                        return coll;
                    };
            List<CompletionItem> items = stub.run(matchName, cachedFetcher);
            resultSet.addAllItems(items);
        }
    }

}

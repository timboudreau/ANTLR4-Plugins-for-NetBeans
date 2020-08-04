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
package org.nemesis.antlr.completion.grammar;

import com.mastfrog.antlr.code.completion.spi.CompletionsSupplier;
import com.mastfrog.antlr.utils.RulesMapping;
import com.mastfrog.util.collections.IntList;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.IntPredicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import org.antlr.v4.runtime.CommonToken;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.Token;
import com.mastfrog.util.collections.IntMap;
import com.mastfrog.util.collections.IntSet;
import com.mastfrog.util.search.Bias;
import com.mastfrog.util.strings.Strings;
import java.util.PrimitiveIterator;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Vocabulary;
import com.mastfrog.antlr.code.completion.spi.CaretToken;
import static com.mastfrog.antlr.code.completion.spi.CaretTokenRelation.AT_TOKEN_START;
import static com.mastfrog.antlr.code.completion.spi.CaretTokenRelation.UNRELATED;
import static com.mastfrog.antlr.code.completion.spi.CaretTokenRelation.WITHIN_TOKEN;
import com.mastfrog.antlr.code.completion.spi.Completer;
import com.mastfrog.antlr.code.completion.spi.CompletionApplier;
import com.mastfrog.antlr.code.completion.spi.CompletionItemBuilder;
import com.mastfrog.antlr.code.completion.spi.CompletionItems;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.swing.text.BadLocationException;
import javax.swing.text.StyledDocument;
import org.nemesis.antlr.completion.TokenUtils;
import org.nemesis.localizers.api.Localizers;
import org.nemesis.swing.cell.TextCell;
import org.netbeans.spi.editor.completion.CompletionResultSet;
import org.netbeans.spi.editor.completion.support.AsyncCompletionQuery;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;

/**
 *
 * @author Tim Boudreau
 */
public class GrammarCompletionQuery extends AsyncCompletionQuery {

    private static final Logger LOG = Logger.getLogger(GrammarCompletionQuery.class.getName());
    private final String mimeType;
    private final ParserAndRuleContextProvider<?, ?> parserProvider;
    private final IntPredicate preferredRules;
    private final IntPredicate ignoredRules;
    private final Map<String, IntMap<CodeCompletionCore.FollowSetsHolder>> cache;
    private final IntMap<String> supplemental;
    private final com.mastfrog.util.collections.IntIntMap ruleSubstitutions;

    GrammarCompletionQuery(String mimeType,
            ParserAndRuleContextProvider<?, ?> parserProvider,
            IntPredicate preferredRules, IntPredicate ignoredRules,
            Map<String, IntMap<CodeCompletionCore.FollowSetsHolder>> cache,
            IntMap<String> supplemental, com.mastfrog.util.collections.IntIntMap ruleSubstitutions) {
        this.mimeType = mimeType;
        this.parserProvider = parserProvider;
        this.preferredRules = preferredRules;
        this.ignoredRules = ignoredRules;
        this.cache = cache;
        this.supplemental = supplemental;
        this.ruleSubstitutions = ruleSubstitutions;
    }

    @Override
    protected void query(CompletionResultSet resultSet, Document doc, int caretOffset) {
        if (caretOffset < 0 || !(doc instanceof StyledDocument)) {
            resultSet.finish();
            return;
        }
        try {
            doQuery(resultSet, (StyledDocument) doc, caretOffset, parserProvider);
        } catch (IOException | BadLocationException ex) {
            LOG.log(Level.SEVERE, "Exception computing completion query", ex);
        } finally {
            resultSet.finish();
        }
    }

    private <P extends Parser, R extends ParserRuleContext> void doQuery(CompletionResultSet resultSet, StyledDocument doc, int caret, ParserAndRuleContextProvider<P, R> provider) throws IOException, BadLocationException {
        P p = provider.createParser(doc);
        p.removeErrorListeners();
        Lexer lexer = (Lexer) p.getInputStream().getTokenSource();
        lexer.removeErrorListeners();
        lexer.reset();

        List<Token> allTokens = new ArrayList<>(1024);
        // Prioritize token suggestions based on their frequency in the
        // document (the items will then de-prioritize those that are
        // punctuation by multiplying this number)
        int[] frequencies = new int[p.getVocabulary().getMaxTokenType() + 1];
        int ix = 0;
        for (Token t = lexer.nextToken(); t.getType() != -1; t = lexer.nextToken(), ix++) {
            if (!(t instanceof CommonToken) || t.getTokenIndex() != ix) {
                CommonToken ct = new CommonToken(t);
                ct.setTokenIndex(ix);
                t = ct;
            }
            allTokens.add(t);
            frequencies[t.getType()]++;
        }
        lexer.reset();
        // Use a Position-based wrapper which is automagically updated on changes
//        CaretToken tokenInfo = new PositionCaretToken(doc, TokenUtils.caretTokenInfo(caret, allTokens, Bias.NONE));
        CaretToken tokenInfo = TokenUtils.caretTokenInfo(caret, allTokens, Bias.NONE);

        if (!tokenInfo.isUserToken()) {
            return;
        }
        if (tokenInfo.isWhitespace()) {
            switch (tokenInfo.caretRelation()) {
                case AT_TOKEN_START:
                    tokenInfo = tokenInfo.biasedBy(Bias.BACKWARD);
                    break;
                case WITHIN_TOKEN:
//                    tokenInfo = tokenInfo.after();
                    tokenInfo = tokenInfo.after();
                    break;
                case UNRELATED:
                    return;
            }
        }
        processCodeCompletion(doc, tokenInfo, allTokens, p, resultSet, frequencies, provider);
    }

    private Map<? extends Completer, ? extends CompletionItemsImpl> completers(StyledDocument doc, CaretToken token) {
        Collection<? extends CompletionsSupplier> all = Lookup.getDefault().lookupAll(CompletionsSupplier.class);
        Map<Completer, CompletionItemsImpl> result = new IdentityHashMap<>(all.size());
        for (CompletionsSupplier c : all) {
            Completer completer = c.forDocument(doc);
            if (!CompletionsSupplier.isNoOp(completer)) {
                result.put(completer, new CompletionItemsImpl(token, doc));
            }
        }
        return result;
    }

    private <P extends Parser, R extends ParserRuleContext> void processCodeCompletion(
            StyledDocument doc,
            CaretToken caretToken, List<Token> tokens, P p, CompletionResultSet resultSet,
            int[] frequencies, ParserAndRuleContextProvider<P, R> provider) throws IOException {
        CodeCompletionCore.CandidatesCollection result = processCodeCompletion(p, caretToken, provider, tokens);

        if (result.isEmpty()) {
            return;
        }

        CompletionItemsImpl completionItems = new CompletionItemsImpl(caretToken, doc);
        // We keep a mapping of completer to its items, so that the scores from
        // each set can be normalized to a mutually comparable 0-1
        Map<? extends Completer, ? extends CompletionItemsImpl> itemsForCompleters
                = completers(doc, caretToken);

        IntSet blacklist = IntSet.create(-1, Math.max(2, itemsForCompleters.size()));

        result.rules.forEach((int ruleId, IntList callStack) -> {
            RulesMapping<?> mapping = RulesMapping.forMimeType(mimeType);
            itemsForCompleters.forEach((completer, items) -> {
                if (blacklist.contains(System.identityHashCode(completer))) {
                    return;
                }
                try {
                    completer.apply(ruleId, caretToken, 30, callStack, items);
                } catch (Exception ex) {
                    blacklist.add(System.identityHashCode(completer));
                    Exceptions.printStackTrace(ex);
                }
            });
        });

        result.tokens.forEach((int tokenId, IntSet list) -> {
            if (tokenId < Token.MIN_USER_TOKEN_TYPE) {
                return;
            }
            IntSet added = IntSet.create(3);
            int effectiveTokenId = ruleSubstitutions.getOrDefault(tokenId, tokenId);

            String literalName = p.getVocabulary().getLiteralName(effectiveTokenId);
            String dispName = p.getVocabulary().getDisplayName(effectiveTokenId);
            if (literalName != null) {
                added.add(effectiveTokenId);
                completionItems.add(new GCI(
                        Strings.deSingleQuote(literalName), caretToken, doc, dispName), 0);
            } else {
                String suppliedToken = supplemental.get(effectiveTokenId);
                if (suppliedToken != null) {
                    added.add(effectiveTokenId);
                    completionItems.add(new GCI(suppliedToken, caretToken, doc, null), 0.01F);
//                    completionItems.add(suppliedToken).withScore(0.1F)
//                            .withRenderer(tc -> {
//                                tc.withText(suppliedToken)
//                                        .italic()
//                                        .append("Woo hoo", tcd -> {
//                                            tcd.withBackground(Color.BLUE, new Ellipse2D.Double())
//                                                    .withForeground(Color.WHITE);
//                                        });
//                            }).build((jtc, d) -> {
//                        d.insertString(caretToken.caretPositionInDocument(), suppliedToken, null);
//                    });
//                    completionItems.add(new GCI(suppliedToken, caretToken, doc, null), 0);
                }
            }

            list.forEachInt(i -> {
                if (i < Token.MIN_USER_TOKEN_TYPE) {
                    return;
                }
                // XXX this looks wrong
                Token tok = tokens.get(i);
                if (added.contains(tok.getType())) {
                    return;
                }
                int eff = ruleSubstitutions.getOrDefault(tok.getType(), tok.getType());
                String id = p.getVocabulary().getDisplayName(eff);
                completionItems.add(new GCI(tok.getText(),
                        caretToken, doc, null), 0);
            });
        });
        List<SortableCompletionItem> allItems = completionItems.items(0.5F);
        itemsForCompleters.forEach((completer, items) -> {
            allItems.addAll(items.items(completer.scoreMultiplier()));
        });
        if (allItems.size() == 1) {
            allItems.get(0).setInstant();;
        }
        resultSet.addAllItems(allItems);
    }

    private String tokensToString(Vocabulary vocab, int tokenId, IntSet kids) {
        StringBuilder sb = new StringBuilder();
        sb.append(vocab.getDisplayName(tokenId));
        if (!kids.isEmpty()) {
            PrimitiveIterator.OfInt it = kids.iterator();
            sb.append(" (");
            while (it.hasNext()) {
                String nm = vocab.getDisplayName(it.nextInt());
                sb.append(nm);
                if (it.hasNext()) {
                    sb.append("->");
                }
            }
            sb.append(')');
        }
        return sb.toString();

    }

    private String rulesToString(RulesMapping<?> mapping, int ruleId, IntList callStack) {
        StringBuilder sb = new StringBuilder();
        sb.append(mapping.nameForRuleId(ruleId));
        if (!callStack.isEmpty()) {
            PrimitiveIterator.OfInt it = callStack.iterator();
            sb.append(" (");
            while (it.hasNext()) {
                String nm = mapping.nameForRuleId(it.nextInt());
                sb.append(nm);
                if (it.hasNext()) {
                    sb.append("->");
                }
            }
            sb.append(')');
        }
        return sb.toString();
    }

    <P extends Parser, R extends ParserRuleContext> CodeCompletionCore.CandidatesCollection processCodeCompletion(P p,
            CaretToken tokenInfo, ParserAndRuleContextProvider<P, R> provider, List<Token> tokens) throws IOException {
        CodeCompletionCore core = new CodeCompletionCore(p, preferredRules, ignoredRules, cache);
        CodeCompletionCore.CandidatesCollection result = core.collectCandidates(tokenInfo.tokenIndex(),
                null /*provider.rootElement(p)*/, tokens);
        return result;
    }

    @Override
    protected void filter(CompletionResultSet resultSet) {
        super.filter(resultSet);
    }

    @Override
    protected boolean canFilter(JTextComponent component) {
        return super.canFilter(component);
    }

    @Override
    protected void preQueryUpdate(JTextComponent component) {
        super.preQueryUpdate(component);
    }

    @Override
    protected void prepareQuery(JTextComponent component) {
        super.prepareQuery(component);
    }

    private static class CompletionItemsImpl implements CompletionItems {

        private final CaretToken tokenInfo;
        private final StyledDocument doc;
        private final Set<SortableCompletionItem> items = new LinkedHashSet<>();
        private float minScore;
        private float maxScore;
        private final Map<Enum<?>, String> descriptors = new HashMap<>();

        public CompletionItemsImpl(CaretToken tokenInfo, StyledDocument doc) {
            this.tokenInfo = tokenInfo;
            this.doc = doc;
        }

        List<SortableCompletionItem> items(float multiplier) {
            float range = maxScore - minScore;
            List<SortableCompletionItem> result = new ArrayList<>(items);
            if (range > 0) {
                for (SortableCompletionItem ci : result) {
                    float sc = ci.relativeScore() - minScore;
                    float normScore = (sc / range) * multiplier;
                    ci.score(normScore);
                }
            }
            Collections.sort(result);
            return result;
        }

        int size() {
            return items.size();
        }

        @Override
        public CompletionItems add(String itemText, Enum<?> kind, float score) {
            String desc = descriptors.get(kind);
            if (desc == null) {
                desc = Localizers.displayName(kind);
                descriptors.put(kind, desc);
            }
            return add(itemText, desc, score);
        }

        private void add(SortableCompletionItem item, float score) {
            minScore = Math.min(score, minScore);
            maxScore = Math.min(score, maxScore);
            items.add(item);
        }

        @Override
        public CompletionItems add(String itemText, String description, float score) {
            add(new GCI(itemText, tokenInfo, doc, score, description), score);
            return this;
        }

        @Override
        public CompletionItems add(String itemText, String description, float score, CompletionApplier applier) {
            add(new GCI(itemText, tokenInfo, doc, description, false, score, applier), score);
            return this;
        }

        CompletionApplier defaultApplier(String toInsert) {
            return new DefaultCompletionApplier(tokenInfo, toInsert);
        }

        @Override
        public CompletionItemBuilder<? extends TextCell, ? extends CompletionItems> add(String displayText) {
            return new CompletionItemBuilderImpl<CompletionItems>(displayText, item -> {
                if (item instanceof CompletionItemImpl) {
                    ((CompletionItemImpl) item).ensureApplier(this::defaultApplier);
                }
                add(item, item.relativeScore());
                return this;
            });
        }
    }

}

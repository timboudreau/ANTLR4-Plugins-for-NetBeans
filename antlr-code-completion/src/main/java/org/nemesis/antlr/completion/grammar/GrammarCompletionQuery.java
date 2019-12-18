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

import com.mastfrog.antlr.utils.CompletionsSupplier;
import com.mastfrog.antlr.utils.RulesMapping;
import com.mastfrog.util.collections.IntList;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;
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
import java.util.HashMap;
import java.util.PrimitiveIterator;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Vocabulary;
import org.nemesis.antlr.completion.CaretTokenInfo;
import org.nemesis.antlr.completion.TokenUtils;
import org.nemesis.localizers.api.Localizers;
import org.netbeans.spi.editor.completion.CompletionResultSet;
import org.netbeans.spi.editor.completion.support.AsyncCompletionQuery;

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
    private final IntIntMap ruleSubstitutions;

    GrammarCompletionQuery(String mimeType,
            ParserAndRuleContextProvider<?, ?> parserProvider,
            IntPredicate preferredRules, IntPredicate ignoredRules,
            Map<String, IntMap<CodeCompletionCore.FollowSetsHolder>> cache,
            IntMap<String> supplemental, IntIntMap ruleSubstitutions) {
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
        if (caretOffset < 0) {
            resultSet.finish();
            return;
        }
        try {
            doQuery(resultSet, doc, caretOffset, parserProvider);
        } catch (IOException ex) {
            LOG.log(Level.SEVERE, "Exception computing completion query", ex);
        } finally {
            resultSet.finish();
        }
    }

    private <P extends Parser, R extends ParserRuleContext> void doQuery(CompletionResultSet resultSet, Document doc, int caret, ParserAndRuleContextProvider<P, R> provider) throws IOException {
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
        CaretTokenInfo tokenInfo = TokenUtils.caretTokenInfo(caret, allTokens, Bias.NONE);

        System.out.println("caret " + caret + " token " + tokenInfo);
        if (!tokenInfo.isUserToken()) {
            System.out.println("not a user token");
            return;
        }
        if (tokenInfo.isWhitespace()) {
            switch (tokenInfo.caretRelation()) {
                case AT_TOKEN_START:
                    tokenInfo = tokenInfo.biasedBy(Bias.BACKWARD);
                    break;
                case WITHIN_TOKEN:
                    tokenInfo = tokenInfo.after();
                    break;
                case UNRELATED:
                    return;
            }
        }

        processCodeCompletion(doc, tokenInfo, allTokens, p, resultSet, frequencies, provider);
    }

    private <P extends Parser, R extends ParserRuleContext> void processCodeCompletion(
            Document doc,
            CaretTokenInfo tokenInfo, List<Token> tokens, P p, CompletionResultSet resultSet,
            int[] frequencies, ParserAndRuleContextProvider<P, R> provider) throws IOException {
        CodeCompletionCore.CandidatesCollection result = processCodeCompletion(p, tokenInfo, provider, tokens);

        System.out.println("TARGET " + tokenInfo + " " + p.getVocabulary()
                .getDisplayName(tokenInfo.tokenType()));

        System.out.println("candidates: " + result);
        Token caretTok = tokenInfo.token();
        CompletionsSupplier supp = CompletionsSupplier.forMimeType(mimeType);

        String prefix = tokenInfo.leadingTokenText();
//        System.out.println("PREFIX: '" + prefix + "' suffix '" + tokenInfo.trailingTokenText() + "'");

        Map<Enum<?>, String> descriptors = new HashMap<>();

        int[] completionItemCount = new int[1];
        GCI[] last = new GCI[1];

        result.rules.forEach((int ruleId, IntList callStack) -> {
            RulesMapping<?> mapping = RulesMapping.forMimeType(mimeType);
            System.out.println("CANDIDATE-RULE: " + ruleId + " " + rulesToString(mapping, ruleId, callStack));

            supp.forDocument(doc).namesForRule(ruleId, prefix, 5, tokenInfo.trailingTokenText(), (name, kind) -> {
                String desc = descriptors.get(kind);
                if (desc == null) {
                    desc = Localizers.displayName(kind);
                    descriptors.put(kind, desc);
                }
//                System.out.println("ADD COMP ITEM FOR " + name + " (" + desc + ") for " + mapping.nameForRuleId(ruleId));
//                resultSet.addItem(new GrammarCompletionItem(name, caretTok, 100, desc));
                completionItemCount[0]++;
                resultSet.addItem(last[0] = new GCI(name, tokenInfo, doc, desc));
            });
        });

        result.tokens.forEach((int tokenId, IntSet list) -> {
            System.out.println("CANDIDATE-TOKEN: " + tokensToString(p.getVocabulary(), tokenId, list));
            if (tokenId < Token.MIN_USER_TOKEN_TYPE) {
                return;
            }
            int effectiveTokenId = ruleSubstitutions.getOrDefault(tokenId, tokenId);

            String symName = p.getVocabulary().getLiteralName(effectiveTokenId);
            String dispName = p.getVocabulary().getDisplayName(effectiveTokenId);
            if (symName != null) {
                System.out.println("SYMBOL: " + symName);
//                resultSet.addItem(new GrammarCompletionItem(
//                        strip(symName), caretTok, frequencies[tokenId]));
                completionItemCount[0]++;
                resultSet.addItem(last[0] = new GCI(
                        strip(symName), tokenInfo, doc, dispName));
            } else {
                String suppliedToken = supplemental.get(effectiveTokenId);
                if (suppliedToken != null) {
                    System.out.println("Have supplied token " + effectiveTokenId + " " + suppliedToken);
                    completionItemCount[0]++;
                    resultSet.addItem(last[0] = new GCI(suppliedToken,
                            tokenInfo, doc, null));
                } else {
                    System.out.println("Not completing token "
                            + p.getVocabulary().getDisplayName(effectiveTokenId) + " - not a predefined symbol");
                }
            }

            list.forEach((IntConsumer) i -> {
                if (i < Token.MIN_USER_TOKEN_TYPE) {
                    return;
                }
                Token tok = tokens.get(i);
                String id = p.getVocabulary().getDisplayName(effectiveTokenId);
                System.out.println("ADDTL: " + id);
//                resultSet.addItem(new GrammarCompletionItem(
//                        tok, caretTok, frequencies[tokenId]));
                resultSet.addItem(last[0] = new GCI(tok.getText(),
                        tokenInfo, doc, null));
            });
            if (completionItemCount[0] == 1) {
                last[0].setOnly(true);
            }
        });
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

    <P extends Parser, R extends ParserRuleContext> CodeCompletionCore.CandidatesCollection processCodeCompletion(P p, CaretTokenInfo tokenInfo, ParserAndRuleContextProvider<P, R> provider, List<Token> tokens) throws IOException {
        CodeCompletionCore core = new CodeCompletionCore(p, preferredRules, ignoredRules, cache);
        CodeCompletionCore.CandidatesCollection result = core.collectCandidates(tokenInfo.token().getTokenIndex(), null /*provider.rootElement(p)*/, tokens);
        return result;
    }

    private static String strip(String s) {
        if (s.length() > 1 && s.charAt(0) == '\'' && s.charAt(s.length() - 1) == '\'') {
            s = s.substring(1, s.length() - 1);
        }
        return s;
    }

    @Override
    protected void filter(CompletionResultSet resultSet) {
        super.filter(resultSet); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    protected boolean canFilter(JTextComponent component) {
        return super.canFilter(component); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    protected void preQueryUpdate(JTextComponent component) {
        super.preQueryUpdate(component); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    protected void prepareQuery(JTextComponent component) {
        super.prepareQuery(component); //To change body of generated methods, choose Tools | Templates.
    }

}

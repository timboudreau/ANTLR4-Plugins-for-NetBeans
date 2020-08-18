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
import com.mastfrog.antlr.code.completion.spi.CaretTokenRelation;
import static com.mastfrog.antlr.code.completion.spi.CaretTokenRelation.AT_TOKEN_START;
import static com.mastfrog.antlr.code.completion.spi.CaretTokenRelation.UNRELATED;
import static com.mastfrog.antlr.code.completion.spi.CaretTokenRelation.WITHIN_TOKEN;
import com.mastfrog.antlr.code.completion.spi.Completer;
import com.mastfrog.function.state.Bool;
import static com.mastfrog.util.collections.CollectionUtils.setOf;
import com.mastfrog.util.collections.IntIntMap;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.function.IntToDoubleFunction;
import java.util.function.Supplier;
import javax.swing.text.BadLocationException;
import javax.swing.text.Position;
import javax.swing.text.StyledDocument;
import org.nemesis.antlr.completion.TokenUtils;
import org.nemesis.editor.position.PositionFactory;
import org.nemesis.editor.position.PositionRange;
import org.netbeans.spi.editor.completion.CompletionResultSet;
import org.netbeans.spi.editor.completion.support.AsyncCompletionQuery;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;

/**
 *
 * @author Tim Boudreau
 */
public class GrammarCompletionQuery extends AsyncCompletionQuery implements Supplier<PositionRange> {

    private static final Logger LOG = Logger.getLogger(GrammarCompletionQuery.class.getName());
    private final String mimeType;
    private final ParserAndRuleContextProvider<?, ?> parserProvider;
    private final IntPredicate preferredRules;
    private final IntPredicate ignoredTokens;
    private final Map<String, IntMap<CodeCompletionCore.FollowSetsHolder>> cache;
    private final IntMap<String> supplemental;
    private final com.mastfrog.util.collections.IntIntMap ruleSubstitutions;

    GrammarCompletionQuery(String mimeType,
            ParserAndRuleContextProvider<?, ?> parserProvider,
            IntPredicate preferredRules, IntPredicate ignoredRules,
            Map<String, IntMap<CodeCompletionCore.FollowSetsHolder>> cache,
            IntMap<String> supplemental, IntIntMap ruleSubstitutions) {
        this.mimeType = mimeType;
        this.parserProvider = parserProvider;
        this.preferredRules = preferredRules;
        this.ignoredTokens = ignoredRules;
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

    private Position origCaretPosition;

    private <P extends Parser, R extends ParserRuleContext> void doQuery(CompletionResultSet resultSet, StyledDocument doc, int caret, ParserAndRuleContextProvider<P, R> provider) throws IOException, BadLocationException {
        if (origCaretPosition == null) {
            origCaretPosition = PositionFactory.forDocument(doc).createPosition(caret, Position.Bias.Backward);
        }
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
        int maxFrequency = Integer.MIN_VALUE;
        int ix = 0;
        for (Token t = lexer.nextToken(); t.getType() != -1; t = lexer.nextToken(), ix++) {
            if (!(t instanceof CommonToken) || t.getTokenIndex() != ix) {
                CommonToken ct = new CommonToken(t);
                ct.setTokenIndex(ix);
                t = ct;
            }
            allTokens.add(t);
            int type = t.getType();
            if (type >= 0) {
                int freq = ++frequencies[type];
                maxFrequency = Math.max(maxFrequency, freq);

            }
        }
        lexer.reset();
        // Use a Position-based wrapper which is automagically updated on changes
//        CaretToken tokenInfo = new PositionCaretToken(doc, TokenUtils.caretTokenInfo(caret, allTokens, Bias.NONE));
        CaretToken tokenInfo = TokenUtils.caretTokenInfo(caret, allTokens, Bias.NONE);
        tokenInfo = adjustCaretTokenToBestCompletionLocation(tokenInfo);
        if (tokenInfo == null) {
            return;
        }
        processCodeCompletion(doc, tokenInfo, allTokens, p, resultSet, scorer(frequencies, maxFrequency), provider);
    }

    CaretToken adjustCaretTokenToBestCompletionLocation(CaretToken tokenInfo) {
        if (tokenInfo == null || !tokenInfo.isUserToken()) {
            return null;
        }
        if (tokenInfo.isUserToken() && tokenInfo.isPunctuation()) {
            CaretToken old = tokenInfo;
            tokenInfo = tokenInfo.before();
            if (old == tokenInfo || old.equals(tokenInfo)) {
//                break;
            }
        }
        if (tokenInfo == null || !tokenInfo.isUserToken()) {
            return null;
        }
        if (tokenInfo.isWhitespace()) {
            switch (tokenInfo.caretRelation()) {
                case AT_TOKEN_START:
                    tokenInfo = tokenInfo.before();
                    break;
                case WITHIN_TOKEN:
                    if (!tokenInfo.before().isPunctuation() && !tokenInfo.containsNewline()) {
                        tokenInfo = tokenInfo.before().biasedBy(Bias.FORWARD);
                        if (tokenInfo.isWhitespace()) {
                            tokenInfo = tokenInfo.before();
                        }
                    } else if (tokenInfo.containsNewline()) {
                        tokenInfo = tokenInfo.after().biasedBy(Bias.BACKWARD);
                    }
                    break;
                case AT_TOKEN_END:
                    if (!tokenInfo.containsNewline() && !tokenInfo.before().isPunctuation()) {
                        tokenInfo = tokenInfo.before();
                    }
                    break;
                case UNRELATED:
                    return null;
            }
        }
        return tokenInfo;
    }

    private IntToDoubleFunction scorer(int[] frequencies, int max) {
        return tokenType -> {
            if (tokenType < 0 || tokenType >= frequencies.length) {
                return 0;
            }
            int val = frequencies[tokenType];
            if (val == 0) {
                return 0;
            }
            return (double) val / (double) max;
        };
    }

    private Map<? extends Completer, ? extends CompletionItemsImpl> completers(StyledDocument doc, CaretToken token) {
        Collection<? extends CompletionsSupplier> all = Lookup.getDefault().lookupAll(CompletionsSupplier.class);
        Map<Completer, CompletionItemsImpl> result = new IdentityHashMap<>(all.size());
        for (CompletionsSupplier c : all) {
            Completer completer = c.forDocument(doc);
            if (!CompletionsSupplier.isNoOp(completer)) {
                result.put(completer, new CompletionItemsImpl(token, doc, this));
            }
        }
        return result;
    }

    private IntArrayMapping filter(IntArrayMapping orig) {
        // CodeCompletionCore will give us ALL of the rule paths that
        // match a preferred rule, but we actually only want the
        // deepest one
        if (orig.size() <= 1) {
            return orig;
        }
        return orig.filter((old, nue) -> {
            List<Map.Entry<Integer, IntList>> l = new ArrayList<>(old.entrySet());
            Collections.sort(l, (a, b) -> {
                IntList la = a.getValue();
                IntList lb = b.getValue();
                if (la.startsWith(lb)) {
                    return 1;
                } else if (lb.startsWith(la)) {
                    return -1;
                } else {
                    int result = Integer.compare(la.size(), lb.size());
                    if (result == 0) {
                        for (int i = 0; i < Math.min(la.size(), lb.size()); i++) {
                            result = Integer.compare(la.get(i), lb.get(i));
                            if (result != 0) {
                                break;
                            }
                        }
                    }
                    return result;
                }
            });

            Map.Entry<Integer, IntList> last = l.get(l.size() - 1);
            nue.put(last.getKey(), last.getValue());
            for (int i = l.size() - 2; i >= 0; i--) {
                Map.Entry<Integer, IntList> prev = l.get(i);
//                RulesMapping<?> rm1 = RulesMapping.forMimeType(mimeType);
                if (!last.getValue().startsWith(prev.getValue())) {
                    nue.put(prev.getKey(), prev.getValue());
                    last = prev;
//                } else {
//                    RulesMapping<?> rm = RulesMapping.forMimeType(mimeType);
//                    System.out.println("SKIP " + rulesToString(rm, prev.getKey(), prev.getValue())
//                            + " same start seq as " + rulesToString(rm, last.getKey(), last.getValue())
//                    );
                }
            }
        });
    }

    private <P extends Parser, R extends ParserRuleContext> void processCodeCompletion(
            StyledDocument doc,
            CaretToken ct, List<? extends Token> toks, P p, CompletionResultSet resultSet,
            IntToDoubleFunction tokenScorer, ParserAndRuleContextProvider<P, R> provider) throws IOException {
//        System.out.println("\n------------------------ process cc -----------------------");
        List<? extends Token> realTokens = toks;
        if (previousResults != null) {
            ct = previousResults.tok;
            toks = TokenUtils.tokensOf(previousResults.tok);
            // We have already filtered the matches in the pre-update
            if (!previousResults.items.isEmpty()) {
                resultSet.addAllItems(previousResults.items);
                return;
            }
        }
        CaretToken caretToken = ct;
        List<? extends Token> tokens = toks;

        CodeCompletionCore.CandidatesCollection result = runCodeCompletionCore(p, caretToken, provider, tokens);

        if (result.isEmpty()) {
            // Got nothing - bail
            return;
        }

        CompletionItemsImpl completionItems = new CompletionItemsImpl(caretToken, doc, this);

        // We keep a mapping of completer to its items, so that the scores from
        // each set can be normalized to a mutually comparable 0-1
        Map<? extends Completer, ? extends CompletionItemsImpl> itemsForCompleters
                = completers(doc, caretToken);

        IntSet blacklist = IntSet.arrayBased(Math.max(2, itemsForCompleters.size()));

        Set<String> dontMatchOn = setOf(caretToken.isWhitespace() ? caretToken.before().tokenText() : caretToken.tokenText(),
                caretToken.leadingTokenText(), caretToken.trailingTokenText());

        // First, walk the rule completers and collect those
        filter(result.rules).forEach((int ruleId, IntList callStack) -> {
//            RulesMapping<?> rm = RulesMapping.forMimeType(mimeType);
//            System.out.println("MATCHED RULES " + rulesToString(rm, ruleId, callStack));
            LOG.finer(() -> {
                RulesMapping<?> rm = RulesMapping.forMimeType(mimeType);
                String rulePath = rulesToString(rm, ruleId, callStack);
                return "Complete " + caretToken + " on " + rulePath + " with " + itemsForCompleters.keySet();
            });

            // See @RuleSubstitutions - in some cases there are a wide variety of
            // tokens that may be expected, but we want to do our lookups based on
            // a smaller subset
            int effectiveRule = ruleSubstitutions.getOrDefault(ruleId, ruleId);
//            System.out.println("effective rule " + p.getRuleNames()[ruleId]);
            itemsForCompleters.forEach((completer, items) -> {
                // If a completer threw an exception, don't bang on it repeatedly
                if (blacklist.contains(System.identityHashCode(completer))) {
                    return;
                }
                try {
                    int oldSize = items.size();
//                    System.out.println("TRY " + completer + " with " + caretToken);
                    completer.apply(effectiveRule, caretToken, 30, callStack, items);
                    int newSize = items.size();
                    // XXX this still doesn't catch the case where we're after a
                    // name and there are two completions, one of which is the preceding
                    // prefix and the other of which is that + another string
                    if (newSize == oldSize + 1) {
                        // If we added a single item, and it's an exact match for the
                        // preceding text, throw it away, since there is nothing useful to
                        // be done with it, and try again pretending we're at the start
                        // of a new token
                        SortableCompletionItem lastItem = items.last();
                        if (lastItem.insertionText().equals(caretToken.leadingTokenText())) {
                            items.removeLast();
                            items.withPrefixText(caretToken.leadingTokenText().trim(), () -> {
                                CaretToken sansLeadingText = TokenUtils.strippingLeadingText(caretToken);
                                completer.apply(effectiveRule, sansLeadingText, 30, callStack, items);
                            });
                            newSize = items.size();
                        }
                    }
                    int added = newSize - oldSize;
                    if (added > 0) {
                        LOG.finest(() -> {
                            RulesMapping<?> rm = RulesMapping.forMimeType(mimeType);
                            String rulePath = rulesToString(rm, ruleId, callStack);
                            return "Completer " + completer + " added " + added + " items "
                                    + "for " + caretToken + " at " + rulePath;
                        });
                    }
                } catch (Exception ex) {
                    blacklist.add(System.identityHashCode(completer));
                    Exceptions.printStackTrace(ex);
                }
            });
        });

        // Generic code completion may supply us some tokens to complete on as well
        result.tokens.forEach((int tokenId, IntSet list) -> {
            if (tokenId < Token.MIN_USER_TOKEN_TYPE) {
                return;
            }
            IntSet added = IntSet.create(3);
            // Don't match on the immediately preceding or after token
            String literalName = p.getVocabulary().getLiteralName(tokenId);
            String dispName = p.getVocabulary().getDisplayName(tokenId);
            if (literalName != null && !dontMatchOn.contains(literalName)) {
                added.add(tokenId);
                float tokenScore = (float) tokenScorer.applyAsDouble(tokenId);
                completionItems.add(new GCI(
                        Strings.deSingleQuote(literalName), caretToken, doc, dispName), tokenScore);
            } else {
                // In some cases, we swap in following tokens that are always going
                // to be needed, but not strictly part of a single token - if something
                // is always followed by a comma or bracket or parentheses, we want to
                // fill them in here, not be sticklers for what is and isn't defined in
                // a single token
                String suppliedToken = supplemental.get(tokenId);
                if (suppliedToken != null && !dontMatchOn.contains(suppliedToken)) {
                    float tokenScore = (float) tokenScorer.applyAsDouble(tokenId);
                    added.add(tokenId);
                    completionItems.add(new GCI(suppliedToken, caretToken, doc, null), Math.max(tokenScore, 0.01F));
                }
            }

            Set<String> addedTokens = new HashSet<>(16);
            list.forEachInt(i -> {
                if (i < Token.MIN_USER_TOKEN_TYPE) {
                    return;
                }
                // Ensure we're using the real token list, not the munged one that belongs
                // to a synthesized token after the user has typed some characters
                Token tok = realTokens.get(i);
                String txt = tok.getText();
                // If it has a literal name, the token text will never vary, and seeing if we
                // should add it is a faster bitset test in IntSet
                boolean isFixed = p.getVocabulary().getLiteralName(tok.getType()) != null;
                // We don't want duplicate completions, but if we're completing on a token with
                // variable text that completion found somewhere else in the document,
                // we want to capture each unique one
                if ((added.contains(tok.getType()) && (isFixed || addedTokens.contains(txt))) || Strings.isBlank(txt)) {
                    return;
                }
                int type = tok.getType();
                // Token code completion is not that smart, and will suggest, for example ; as a completion of ;
                // so in general, we assume suggesting the exact token we've just seen is a bad suggestion
                if (dontMatchOn.contains(txt)
                        || caretToken.tokenText().equals(txt) || caretToken.isWhitespace() && caretToken.before().tokenText().equals(txt)) {
                    return;
                }
                addedTokens.add(txt);
                float tokenScore = (float) tokenScorer.applyAsDouble(type);
                String id = p.getVocabulary().getDisplayName(type);
                completionItems.add(new GCI(tok.getText(),
                        caretToken, doc, tokenScore, null), type);
            });
        });
        // Use this set to deduplicate - multiple providers may provide the same hint
        Set<String> seenNames = new HashSet<>(completionItems.size());
        List<SortableCompletionItem> allItems = completionItems.items(0.5F, seenNames);
        itemsForCompleters.forEach((completer, items) -> {
            List<SortableCompletionItem> resItems = items.items(completer.scoreMultiplier(), seenNames);
            allItems.addAll(resItems);
        });
        // Turns out this is dangerous for name completion
        // If it's a GCI, it is a token completion - i.e. a parenthesis or something,
        // which is always really going to be the only option if it's the only entry
        if (allItems.size() == 1 && allItems.get(0) instanceof GCI) {
            allItems.get(0).setInstant();
        }
        Collections.sort(allItems);
        resultSet.addAllItems(allItems);
        if (previousResults == null) {
            previousResults = new CompletionsInfoForFiltering(allItems, caretToken);
        }
    }

    @Override
    public PositionRange get() {
        // Get and reset the character range the user has typed
        PositionRange rng = previousResults == null ? null : previousResults.inserted;
        previousResults = null;
        origCaretPosition = null;
        return rng;
    }

    private CompletionsInfoForFiltering previousResults;

    /**
     * Cached results so we can just trim them down as the user types, layering
     * the more refined ones on the less, so it can be peeled off on backspace.
     */
    final class CompletionsInfoForFiltering {

        final List<SortableCompletionItem> items;
        final CaretToken tok;
        private CompletionsInfoForFiltering parent;

        public CompletionsInfoForFiltering(List<SortableCompletionItem> items, CaretToken tok, CompletionsInfoForFiltering parent) {
            this.items = items;
            this.tok = tok;
            this.parent = parent;
        }

        public CompletionsInfoForFiltering(List<SortableCompletionItem> items, CaretToken tok) {
            this.items = items;
            this.tok = tok;
            this.parent = null;
        }

        @Override
        public String toString() {
            return "CIFF(" + items.size() + ", " + tok + ")" + (parent == null ? "" : " <-- " + parent);
        }

        CompletionsInfoForFiltering top() {
            if (parent != null) {
                return parent.top();
            }
            return this;
        }

        private PositionRange inserted;

        private boolean preQueryUpdate(JTextComponent component) {
            // Compute the subset of previous result that are valid against the user's typing,
            // or figure out that nothing works
            int caretPosition = component.getCaretPosition();
            int origPosition = origCaretPosition.getOffset();
            Bool canUpdate = Bool.create(true);
            try {
                inserted = PositionFactory.forDocument(component.getDocument()).range(origPosition, Position.Bias.Backward, caretPosition, Position.Bias.Forward);
            } catch (BadLocationException ex) {
                Exceptions.printStackTrace(ex);
            }
            if (caretPosition < origPosition) {
                if (parent != null) {
                    previousResults = parent;
                } else {
                    canUpdate.set(false);
                }
            } else if (caretPosition > origPosition) {
                try {
                    CaretToken topToken = top().tok;
                    Bool updated = Bool.create();

                    boolean hasExactMatch = false;
                    for (SortableCompletionItem item : top().items) {
                        if (item.insertionText().equals(tok.leadingTokenText())) {
                            hasExactMatch = true;
                            break;
                        }
                    }
                    hasExactMatch |= tok.caretRelation() == CaretTokenRelation.AT_TOKEN_END
                            && tok.leadingTokenText().length() > 3;
                    TokenUtils.withInsertedCharacters(hasExactMatch, origPosition, topToken, component, (newCaretToken, insertedText) -> {
                        if (insertedText.length() > 0 && Character.isWhitespace(insertedText.charAt(insertedText.length() - 1))) {
                            canUpdate.set(false);
                            return;
                        }
                        String txt = newCaretToken.tokenText();
                        List<SortableCompletionItem> newItems = new ArrayList<>();
                        for (SortableCompletionItem item : items) {
                            if (item.matchesPrefix(txt)) {
                                newItems.add(item);
                            }
                        }
                        if (newItems.isEmpty()) {
//                            System.out.println("cant up 1");
//                            canUpdate.set(false);
                        } else {
                            previousResults = new CompletionsInfoForFiltering(newItems, newCaretToken, this);
                            updated.set(true);
                        }
                    });
                    if (updated.getAsBoolean()) {
                        previousResults.inserted = PositionFactory.forDocument(component.getDocument()).range(origPosition, Position.Bias.Backward, caretPosition, Position.Bias.Forward);
                    }
                } catch (BadLocationException ex) {
                    previousResults = null;
                    canUpdate.set(false);
                    Exceptions.printStackTrace(ex);
                }
            } else {
                canUpdate.set(false);
            }
            return canUpdate.get();
        }

        private boolean canFilter(JTextComponent component) {
            return !items.isEmpty();
        }

        private void filter(CompletionResultSet resultSet) {
            resultSet.addAllItems(items);
            resultSet.finish();
        }
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

    private <P extends Parser, R extends ParserRuleContext> CodeCompletionCore.CandidatesCollection runCodeCompletionCore(P p,
            CaretToken tokenInfo, ParserAndRuleContextProvider<P, R> provider, List<? extends Token> tokens) throws IOException {
        CodeCompletionCore core = new CodeCompletionCore(p, preferredRules, ignoredTokens, cache);
        int ix = tokenInfo.tokenIndex();
        if ((tokenInfo.isWhitespace() || ignoredTokens.test(tokenInfo.tokenType())) && ix > 0) {
            ix -= 1;
            LOG.finest(() -> {
                return "Current token is whitespace or ignored token - passing completion engine the preceding token "
                        + p.getVocabulary().getDisplayName(tokenInfo.before().tokenType());
            });
        }
        CodeCompletionCore.CandidatesCollection result = core.collectCandidates(ix,
                null /*provider.rootElement(p)*/, tokens);
        return result;
    }

    @Override
    protected void filter(CompletionResultSet resultSet) {
        if (previousResults != null) {
            previousResults.filter(resultSet);
        } else {
            resultSet.finish();
        }
    }

    @Override
    protected boolean canFilter(JTextComponent component) {
        if (previousResults != null) {
            return previousResults.preQueryUpdate(component);
        }
        return false;
    }

    @Override
    protected void prepareQuery(JTextComponent component) {
        super.prepareQuery(component);
    }
}

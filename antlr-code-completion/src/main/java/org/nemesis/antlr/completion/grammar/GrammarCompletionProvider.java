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

import com.mastfrog.antlr.cc.CodeCompletionCore;
import com.mastfrog.antlr.cc.FollowSetsHolder;
import java.util.HashMap;
import java.util.Map;
import java.util.function.IntPredicate;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import org.antlr.v4.runtime.Parser;
import com.mastfrog.util.collections.IntMap;
import com.mastfrog.function.throwing.io.IOFunction;
import com.mastfrog.util.collections.IntIntMap;
import java.util.function.Function;
import org.antlr.v4.runtime.ParserRuleContext;
import org.netbeans.spi.editor.completion.CompletionProvider;
import org.netbeans.spi.editor.completion.CompletionTask;
import org.netbeans.spi.editor.completion.support.AsyncCompletionTask;

/**
 *
 * @author Tim Boudreau
 */
public class GrammarCompletionProvider implements CompletionProvider {

    private final String mimeType;

    private final ParserAndRuleContextProvider<?, ?> parserForDoc;
    private final IntPredicate preferredRules;
    private final IntPredicate ignoredRules;
    private final Map<String, IntMap<FollowSetsHolder>> cache = new HashMap<>(3);
    private final IntMap<String> supplemental;
    private final com.mastfrog.util.collections.IntIntMap ruleSubstitutions;

    protected <P extends Parser, R extends ParserRuleContext> GrammarCompletionProvider(
            String mimeType,
            IOFunction<Document, P> parserForDoc, IntPredicate preferredRules,
            IntPredicate ignoredRules, int[] supplementalTokenKeys,
            String[] supplementalTokenTexts,
            int[] ruleSubstitutionKeys, int[] ruleSubstitutionValues) {
        this(mimeType, parserForDoc, preferredRules, ignoredRules, null,
                supplementalTokenKeys, supplementalTokenTexts,
                ruleSubstitutionKeys, ruleSubstitutionValues);
    }

    protected <P extends Parser, R extends ParserRuleContext> GrammarCompletionProvider(
            String mimeType,
            IOFunction<Document, P> parserForDoc, IntPredicate preferredRules,
            IntPredicate ignoredRules, Function<P, R> rootRuleFinder,
            int[] supplementalTokenKeys, String[] supplementalTokenTexts,
            int[] ruleSubstitutionKeys, int[] ruleSubstitutionValues) {
        this.mimeType = mimeType;
        this.parserForDoc = new ParserAndRuleContextProvider<>(parserForDoc, rootRuleFinder);
        this.preferredRules = preferredRules;
        this.ignoredRules = ignoredRules;
        supplemental = IntMap.of(supplementalTokenKeys, supplementalTokenTexts);
        ruleSubstitutions = IntIntMap.createUnsafe(ruleSubstitutionKeys, ruleSubstitutionValues);
    }

    @Override
    public CompletionTask createTask(int queryType, JTextComponent component) {
        if (queryType != COMPLETION_QUERY_TYPE) {
            return null;
        }
        return new AsyncCompletionTask(new GrammarCompletionQuery(mimeType, parserForDoc, preferredRules,
                ignoredRules, cache, supplemental, ruleSubstitutions), component);
    }

    @Override
    public int getAutoQueryTypes(JTextComponent component, String typedText) {
        return 0;
    }
}

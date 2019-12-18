/*
 * Copyright 2019 Mastfrog Technologies.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.mastfrog.antlr.utils;

import java.util.function.ToIntFunction;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.openide.util.lookup.Lookups;

/**
 * Allows parsers to be run by code that does not know the particular entry
 * point method desired, but does know the rule's name or id.  Modules should
 * implement and install in named lookups on the path antlr/$MIME_TYPE/rules
 *
 * @author Tim Boudreau
 */
public abstract class RulesMapping<P extends Parser> implements ToIntFunction<ParserRuleContext> {

    private final String mimeType;

    private final Class<P> parserType;

    protected RulesMapping(String mimeType, Class<P> parserType) {
        this.mimeType = mimeType;
        this.parserType = parserType;
    }

    public static RulesMapping<?> forMimeType(String mimeType) {
        return Lookups.forPath("antlr/" + mimeType + "/rules").lookup(RulesMapping.class);
    }

    public static ToIntFunction<? super ParserRuleContext> ruleIdMapper(String mimeType) {
        RulesMapping<?> result = forMimeType(mimeType);
        return result == null ? ignored -> -1 : result;
    }

    public final String mimeType() {
        return mimeType;
    }

    public final P castIfMatches(Parser parser) {
        if (parserType.isInstance(parser)) {
            return parserType.cast(parser);
        }
        return null;
    }

    public final Class<P> parserType() {
        return parserType;
    }

    @Override
    @SuppressWarnings("unchecked")
    public int applyAsInt(ParserRuleContext value) {
        return ruleIdForType((Class<? extends ParserRuleContext>) value.getClass());
    }

    /**
     * Get the nested class of the parser type which is returned when the rule
     * with the passed name is parsed.
     *
     * @param name The rule name
     *
     * @return The parse tree type
     */
    public abstract Class<? extends ParserRuleContext> ruleTypeForName(String name);

    /**
     * Get the rule name in the originating grammar for a given parse tree
     * object.
     *
     * @param type The parse tree class
     *
     * @return A string
     */
    public abstract String nameForRuleType(Class<? extends ParserRuleContext> type);

    /**
     * Get the rule id of for a parser tree subtype
     *
     * @param type The type
     *
     * @return the rule id (will be a static filed named RULE_* on the parser
     * class) or -1
     */
    public abstract int ruleIdForType(Class<? extends ParserRuleContext> type);

    /**
     * Get the nested class of the parser type which is returned when the rule
     * with the passed id is parsed.
     *
     * @param name The rule name
     *
     * @return The parse tree type
     */
    public abstract Class<? extends ParserRuleContext> typeForRuleId(int ruleId);

    /**
     * Get the rule name from the originating grammar of a rule Id, so code that
     * may not have access to the parser class can map rule ids (encountered
     * when, for example, performing generic code completion) back to the name
     * in the grammar file that the parser class was created from.
     *
     * @param ruleId A rule id
     *
     * @return The name in the .g4 file that the parser was generated from
     */
    public abstract String nameForRuleId(int ruleId);

    /**
     * Get the rule id in the parser of a rule name in that parser's originating
     * grammar file.
     *
     * @param name A name
     *
     * @return The id, or -1 if none
     */
    public abstract int ruleIdForName(String name);

    /**
     * Invoke a rule on a parser.
     *
     * @param ruleId A rule id
     * @param on A parser instance
     *
     * @return The result of calling the method for that rule on the parser.
     */
    public abstract ParserRuleContext invokeRule(int ruleId, P on);

}

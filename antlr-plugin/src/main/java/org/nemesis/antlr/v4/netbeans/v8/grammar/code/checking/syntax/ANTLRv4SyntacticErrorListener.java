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
package org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.syntax;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.nemesis.antlr.v4.netbeans.v8.generic.parsing.ParsingError;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Parser;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CommonToken;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.misc.IntervalSet;
import org.nemesis.source.api.GrammarSource;

import org.netbeans.modules.csl.api.Severity;


/**
 *
 * @author Frédéric Yvon Vinet
 */
public class ANTLRv4SyntacticErrorListener extends BaseErrorListener {
    private final List<ParsingError> parsingErrors;
    private final GrammarSource<?>         objectToBeParsed;
    
    public boolean encounteredError() {
        return getErrorNumber() != 0;
    }
    public int getErrorNumber() {
        return parsingErrors.size();
    }
    public List<ParsingError> getParsingError() {
        return parsingErrors;
    }
    
    public ANTLRv4SyntacticErrorListener(GrammarSource<?> objectToBeParsed) {
        this.parsingErrors = new ArrayList<>();
        this.objectToBeParsed = objectToBeParsed;
    }
    
    @Override
    public void syntaxError
        (Recognizer<?, ?>     recognizer        ,
         Object               offendingSymbol   ,
         int                  line              ,
         int                  charPositionInLine,
         String               msg               ,
         RecognitionException e                 ) {
     // We build the error key from the rule stack
        ANTLRv4Parser parser = (ANTLRv4Parser) recognizer;
        List<String> stack = parser.getRuleInvocationStack();
        Collections.reverse(stack);
        StringBuilder ruleStack = new StringBuilder("antlr.grammar.error.syntax");
        for (String rule : stack) {
            ruleStack.append(".");
            ruleStack.append(rule);
        }
        
     // ... and we add the offending token
        CommonToken offendingToken = (CommonToken) offendingSymbol;
        
     // We need to recover the position of the offending symbol rather than
     // the error in the char stream
        int startOffset = offendingToken.getStartIndex();
        int endOffset = offendingToken.getStopIndex() + 1;
        
        String TokenValue = offendingToken.getText();
        ruleStack.append(".unexpected.");
        ruleStack.append(TokenValue);
        String key = ruleStack.toString();

        StringBuilder displayName = new StringBuilder("at position ");
        displayName.append(line);
        displayName.append(":");
        displayName.append(charPositionInLine + 1);
        displayName.append(" unexpected symbol '");
        displayName.append(TokenValue);
        displayName.append("'");
        
        StringBuilder description = new StringBuilder(displayName);
/*
     // We recover the list of expected tokens
        IntervalSet tis = parser.getExpectedTokens();
        List<Integer> tokenIds = tis.toList();
        for (Integer tokenId : tokenIds) {
            DISPLAYED_TOKEN_NAME.get(tokenId);
        }
*/
        if (e != null) {
            IntervalSet expectedTokens = e.getExpectedTokens();
            String hint = expectedTokens.toString(parser.getVocabulary());
            description.append('\n');
            description.append(hint);
        }
        
        ParsingError parsingError = new ParsingError
            (objectToBeParsed      ,
             Severity.ERROR        ,
             key                   ,
             startOffset           ,
             endOffset             ,
             displayName.toString(),
             description.toString());
        parsingErrors.add(parsingError);
    }
}
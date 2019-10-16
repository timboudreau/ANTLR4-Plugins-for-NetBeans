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
package org.nemesis.antlr.v4.netbeans.v8.grammar.code.completion;

import java.util.Iterator;
import java.util.List;

import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.StyledDocument;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.TokenStream;

import org.nemesis.antlr.v4.netbeans.v8.grammar.code.completion.Position.PositionType;

import org.nemesis.antlr.v4.netbeans.v8.grammar.code.completion.items.GrammarCompletionItem;

import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Parser;

import org.netbeans.api.lexer.TokenHierarchy;

import org.netbeans.editor.BaseDocument;


import org.netbeans.spi.editor.completion.CompletionResultSet;
import org.netbeans.spi.editor.completion.support.AsyncCompletionQuery;

import org.openide.util.Exceptions;

/**
 *
 * @author Frédéric Yvon Vinet
 */
public class GrammarCompletionQuery extends AsyncCompletionQuery {
    
    public GrammarCompletionQuery() {
//        System.out.println("GrammarCompletionQuery:GrammarCompletionQuery() : begin");
//        System.out.println("GrammarCompletionQuery:GrammarCompletionQuery() : end");
    }

 /**
  * Called when user press Ctrl-Space
  * 
  * @param crs contains propositions for code completion
  * @param doc document where we ask for a code completion
  * @param caretOffset position in doc where we ask for a code completion
  */
    @Override
    protected void query
            (CompletionResultSet crs, Document doc, int caretOffset) {
/*
        System.out.println("GrammarCompletionQuery:query(CompletionResultSet," +
                           " Document, int) : begin");
*/
        assert crs != null;
        assert doc != null;
        try {
         // We need to recover all tokens (even comment tokens) in order to be
         // able to find the caret position in token flow
         // We need to lock document for ensuring no modification is done during
         // our processing (unlock is done in finally block)
            ((BaseDocument) doc).readLock();
            TokenHierarchy<Document> th = TokenHierarchy.get(doc);
            
         // If caret is place on a token then it is useless to parse anything
         // and we may conclude that there is no sugestion
            Position caretPosition = Position.getPositionInTokenStream
                                                              (caretOffset, th);
//            System.out.println("- caret position: " + caretPosition);
            if (caretPosition.getPositionType() != PositionType.ON_A_TOKEN) {
             // First, we set up a filter 
                final StyledDocument sDoc = (StyledDocument) doc;
                final int insertionOffset = getPreviousWordOffset(sDoc, caretOffset);
                final String enteredText = sDoc.getText
                                                (insertionOffset              ,
                                                 caretOffset - insertionOffset);
                final char[] enteredChars = enteredText.toCharArray();
                String filter = new String(enteredChars       ,
                                           0                  ,
                                           enteredChars.length);
//                System.out.println("- filter=" + filter);
//                System.out.println("- insertion offset=" + insertionOffset);
//                System.out.println("- caret offset=" + caretOffset);
            
             // If we stop the text to be lexed and parsed at caret offset then we
             // will have a syntax error if caret is placed in a parser rule but if
             // caret is placed between two valid rules, there will be no syntax
             // error.
             // But in previous case, even if parser is able to propose solutions 
             // for next token, these prposition are not nessarily compatible with 
             // the token already placed after caret. So that's why we choose to 
             // stop the text at the begining of the next token emitted to parser.
//                int cutOffset = getEndOffsetOfNextEmittedTokenAfter(caretOffset, th);
                int cutOffset = insertionOffset;
                
             // We determine if we have to add an extra whitespace after 
             // propositions or not
                boolean extraWhitespaceRequiredAfter;
                if (caretOffset != doc.getLength()) {
                    char nextChar = doc.getText(caretOffset, 1).charAt(0);
                    switch(nextChar) {
                        case ' ' :
                        case '\t':
                        case ';' :
                        case ',' :
                        case '.' :
                        case '+' :
                        case '*' :
                        case '?' :
                        case '>' :
                        case '=' :
                            extraWhitespaceRequiredAfter = false;
                            break;
                        default:
                            extraWhitespaceRequiredAfter = true;
                    }
                } else {
                    extraWhitespaceRequiredAfter = true;
                }
//                System.out.println("- extra whitespace required after? " + extraWhitespaceRequiredAfter);
//                System.out.println("- cut offset=" + cutOffset);
                String contentToBeParsed = doc.getText(0, cutOffset);
//                System.out.println("- content to be parsed:");
//                System.out.println("'" + contentToBeParsed+ "'");
//                System.out.println("  length of content to be parsed=" + contentToBeParsed.length());
                CharStream input = CharStreams.fromString(contentToBeParsed);
                org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Lexer
                    lexer = new
                        org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Lexer
                           (input);
                lexer.removeErrorListeners();
            
             // We use that token list as a token source in order to not lex our
             // document twice
                TokenStream tokenStream = new CommonTokenStream(lexer);
                ANTLRv4Parser parser = new ANTLRv4Parser(tokenStream);
                parser.removeErrorListeners();
                ExpectedTokenFinder etf = new ExpectedTokenFinder
                                                 (doc                         ,
                                                  insertionOffset             ,
                                                  caretOffset                 ,
                                                  extraWhitespaceRequiredAfter);
                parser.addErrorListener(etf);
            
                parser.grammarFile();
                if (etf.areThereAnyPropositions()) {
                 // If caret is located in a rule in error then etf will find it
                 // and will build correspondent prpositions
                    List<GrammarCompletionItem> propositions = etf.getPropositions();
                 // We need to remove from propositions the words that are already
                 // just after caret ()
                    String nextWord = getNextWordAfter(doc, caretOffset);
//                    System.out.println("next word=" + nextWord);
//                    System.out.println("filter=" + filter);
                    if (nextWord.equals("")) {
                        crs.addAllItems(propositions);
                    } else {
                        Iterator<GrammarCompletionItem> gciIt = propositions.iterator();
                        GrammarCompletionItem gci;
                        while (gciIt.hasNext()) {
                            gci = gciIt.next();
                            if (!gci.equals(nextWord) &&
                                gci.startsWith(filter)      )
                                crs.addItem(gci);
                        }
                    }
                }
            }
        } catch(BadLocationException ex) {
            Exceptions.printStackTrace(ex);
        } finally {
            crs.finish();
            ((BaseDocument) doc).readUnlock();
        }
/*
        System.out.println("GrammarCompletionQuery:query(CompletionResultSet," +
                           " Document, int) : end");
*/
    }
    
    
    private static int getPreviousWordOffset(StyledDocument doc, int offset)
            throws BadLocationException {
        int endOffset;
        if (offset == 0)
            endOffset = 0;
        else {
            endOffset = offset - 1;
            boolean end = false;
            while (endOffset >= 0 && !end) {
                try {
                    String currentCharString = doc.getText(endOffset, 1);
                    char currentChar = currentCharString.charAt(0);
                    switch (currentChar) {
                        case ' ':
                        case '\t':
                        case '\f':
                        case '\n':
                        case '\r':
                        case ';':
                        case ',':
                        case ':':
                        case '=':
                        case '(':
                        case ')':
                        case '[':
                        case ']':
                        case '{':
                        case '}':
                        case '<':
                        case '>':
                        case '#':
                        case '|':
                        case '~':
                        case '?':
                        case '+':
                        case '*':
                        case '@':
                        case '/':
                            end = true;
                            endOffset++;
                            break;
                        default:
                            endOffset--;
                    }
                } catch (BadLocationException ex) {
                    throw (BadLocationException)new BadLocationException
                     ("calling getText(" + endOffset + 
                      ", 1) on doc of length: " + doc.getLength(), endOffset).
                     initCause(ex);
                }
            }
        }
        
        return endOffset;
    }
    
            
    private String getNextWordAfter(Document doc, int caretOffset) {
        StringBuilder answer = new StringBuilder();
     // We look for the first non whitespace character
        int offset = caretOffset;
        int docLength = doc.getLength();
        char currentChar;
        try {
            boolean wordStartFound = false;
            while (offset < docLength && !wordStartFound) {
                currentChar = doc.getText(offset, 1).charAt(0);
                switch (currentChar) {
                    case ' ':
                    case '\t':
                    case '\f':
                    case '\r':
                    case '\n':
                        offset++;
                        break;
                    default:
                        wordStartFound = true;
                }
            }
            if (wordStartFound) {
                boolean wordEndFound = false;
                while (offset < docLength && !wordEndFound) {
                    currentChar = doc.getText(offset++, 1).charAt(0);
                    switch (currentChar) {
                        case ' ':
                        case '\t':
                        case '\f':
                        case '\r':
                        case '\n':
                            wordEndFound = true;
                            break;
                        case ';' :
                        case ':' :
                        case ',' :
                        case '.' :
                        case '?' :
                        case '+' :
                        case '*' :
                        case '<' :
                        case '>' :
                        case '(' :
                        case ')' :
                        case '[' :
                        case ']' :
                        case '{' :
                        case '}' :
                        case '#' :
                        case '~' :
                        case '|' :
                        case '@' :
                            if (answer.toString().equals(""))
                                answer.append(currentChar);
                            wordEndFound = true;
                            break;
                        default:
                            answer.append(currentChar);
                    }
                }
            }
        } catch (BadLocationException ex) {
            ex.printStackTrace();
        }
        return answer.toString();
    }
}
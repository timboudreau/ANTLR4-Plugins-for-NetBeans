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
package org.nemesis.antlr.file.completion;

import com.mastfrog.antlr.code.completion.spi.CaretToken;
import com.mastfrog.antlr.code.completion.spi.Completer;
import com.mastfrog.antlr.code.completion.spi.CompletionItems;
import com.mastfrog.antlr.code.completion.spi.CompletionsSupplier;
import com.mastfrog.function.state.Obj;
import com.mastfrog.util.collections.IntList;
import java.awt.Color;
import java.awt.event.KeyEvent;
import java.util.function.Supplier;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import javax.swing.text.Position;
import javax.swing.text.StyledDocument;
import org.nemesis.antlr.ANTLRv4Lexer;
import org.nemesis.antlr.ANTLRv4Parser;
import static org.nemesis.antlr.common.AntlrConstants.ANTLR_MIME_TYPE;
import org.netbeans.modules.editor.NbEditorUtilities;
import org.openide.text.NbDocument;
import org.openide.util.NbBundle.Messages;
import org.openide.util.lookup.ServiceProvider;
import org.nemesis.editor.function.DocumentSupplier;

/**
 *
 * @author Tim Boudreau
 */
@ServiceProvider(service = CompletionsSupplier.class, position = 1)
public class DupTokenCompletionsSupplier extends CompletionsSupplier implements Completer {

    @Override
    public Completer forDocument(Document document) {
        if (ANTLR_MIME_TYPE.equals(NbEditorUtilities.getMimeType(document))) {
            return DTCompleter.find(document);
        }
        return noop();
    }

    static class DTCompleter implements Completer {

        private final Document doc;

        static DTCompleter find(Document doc) {
            DTCompleter result = (DTCompleter) doc.getProperty(DTCompleter.class);
            if (result == null) {
                System.out.println("CREATE AN ANTLR COMPLETER");
                result = new DTCompleter(doc);
                doc.putProperty(DTCompleter.class, result);
            }
            return result;
        }

        DTCompleter(Document doc) {
            this.doc = doc;
        }

        @Override
        @Messages({"makeRepeating=Make Repeating",
            "tipRepeating=Press a non-character key to make that the delimiter"
        })
        public void apply(int parserRuleId, CaretToken token, int maxResultsPerKey, IntList rulePath, CompletionItems addTo) throws Exception {
            boolean isLexer;
            switch (parserRuleId) {
                case ANTLRv4Parser.RULE_parserRuleAtom:
                case ANTLRv4Parser.RULE_labeledParserRuleElement:
                    isLexer = false;
                    break;
                case ANTLRv4Parser.RULE_lexerRuleAtom:
                case ANTLRv4Parser.RULE_lexerRuleElement:
                    isLexer = true;
                    break;
                default:
                    System.out.println("NO GO " + ANTLRv4Parser.ruleNames[parserRuleId]);
                    return;
            }
            System.out.println("try to do the thing " + token);
            if (token.tokenType() == ANTLRv4Lexer.SEMI) {
                token = token.before();
                System.out.println(" on semi, back up to " + token);
                if (token == null) {
                    return;
                }
            }
            // XXX this will try to insert lexer literals in a parser grammer
            // Should search for a matching literal constant for the name we have.
            // We have all the string literal ruls in the extraction, but the key is
            // elsewhere I think
            Obj<CaretToken> preceding = Obj.create();
            if (!isAfterFirstAtom(token, preceding)) {
                System.out.println("  after first atom, bail");
                return;
            }
            Position pos = NbDocument.createPosition(doc, token.tokenEnd(), Position.Bias.Backward);
            char[] delimChar = new char[]{','};
            Supplier<String> insertText = () -> {
                CaretToken tk = preceding.get();
                String txt;
                if (delimChar[0] != 0) {
                    txt = " ('" + delimChar[0] + "' " + tk.tokenText() + ")*";
                } else {
                    txt = " " + tk.tokenText() + "*";
                }
                return txt;
            };
            assert preceding.isSet();
            addTo.add(Bundle.makeRepeating())
                    .withPriority(12000)
                    .withTooltip(Bundle.tipRepeating())
                    .withKeyHandler(keyEvent -> {
                        boolean matched = false;
                        switch (keyEvent.getKeyCode()) {
                            case KeyEvent.VK_COMMA:
                                delimChar[0] = ',';
                                matched = true;
                                break;
                            case KeyEvent.VK_SLASH:
                                matched = true;
                                delimChar[0] = '/';
                                break;
                            case KeyEvent.VK_PLUS:
                                matched = true;
                                delimChar[0] = '+';
                                break;
                            case KeyEvent.VK_SEMICOLON:
                                matched = true;
                                delimChar[0] = ';';
                                break;
                            case KeyEvent.VK_AMPERSAND:
                                matched = true;
                                delimChar[0] = '&';
                                break;
                            case KeyEvent.VK_BACK_SLASH:
                                matched = true;
                                delimChar[0] = keyEvent.isShiftDown() ? '|'
                                        : '\\';
                                break;
                            case KeyEvent.VK_COLON:
                                matched = true;
                                delimChar[0] = ':';
                                break;
                            case KeyEvent.VK_GREATER:
                                matched = true;
                                delimChar[0] = '>';
                                break;
                            case KeyEvent.VK_SPACE:
                                matched = true;
                                delimChar[0] = 0;
                                break;
                        }
                        if (matched) {
                            keyEvent.consume();
                        }
                    }).withRenderer(cell -> {
                cell.withText(Bundle.makeRepeating())
                        .append(insertText.get(), kid -> {
                            kid.withForeground(new Color(180, 240, 180))
                                    .monospaced()
                                    .leftMargin(12).italic();
                        });
            }).build((JTextComponent comp, StyledDocument doc1) -> {
                DocumentSupplier<Void, RuntimeException> sp = () -> {
                    doc1.insertString(pos.getOffset(), insertText.get(), null);
                    return null;
                };
                sp.run(run -> {
                    NbDocument.runAtomic(doc1, run);
                });
            });
        }

        private static boolean isAfterFirstAtom(CaretToken tok, Obj<CaretToken> prev) {
            while (tok != null && tok.isWhitespace()) {
                tok = tok.before();
            }
            if (tok == null) {
                return false;
            }
            switch (tok.tokenType()) {
                case ANTLRv4Lexer.PARSER_RULE_ID:
                case ANTLRv4Lexer.TOKEN_OR_PARSER_RULE_ID:
                case ANTLRv4Lexer.TOKEN_ID:
                case ANTLRv4Lexer.STRING_LITERAL:
                    prev.set(tok);
                    break;
                default:
                    return false;
            }
            tok = tok.before();
            while (tok != null && tok.isWhitespace()) {
                tok = tok.before();
            }
            return tok != null && tok.tokenType() == ANTLRv4Lexer.COLON;
        }

    }
}

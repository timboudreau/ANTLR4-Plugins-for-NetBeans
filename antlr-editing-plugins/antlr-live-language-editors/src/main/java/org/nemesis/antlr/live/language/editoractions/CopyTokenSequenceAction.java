/*
 * Copyright 2016-2020 Tim Boudreau, Frédéric Yvon Vinet
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

package org.nemesis.antlr.live.language.editoractions;

import com.mastfrog.util.strings.Strings;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.util.Objects;
import javax.swing.AbstractAction;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import javax.swing.text.Segment;
import javax.swing.text.StyledDocument;
import org.nemesis.antlr.live.language.AdhocLanguageHierarchy;
import org.nemesis.antlr.live.language.AdhocTokenId;
import org.nemesis.antlr.live.language.DynamicLanguages;
import org.nemesis.antlr.live.parsing.EmbeddedAntlrParser;
import org.nemesis.antlr.live.parsing.EmbeddedAntlrParserResult;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies;
import org.netbeans.api.lexer.Language;
import org.netbeans.api.lexer.Token;
import org.netbeans.api.lexer.TokenHierarchy;
import org.netbeans.api.lexer.TokenSequence;
import org.netbeans.modules.editor.NbEditorUtilities;
import org.openide.awt.StatusDisplayer;
import org.openide.text.NbDocument;
import org.openide.util.Exceptions;
import org.openide.util.NbBundle;

/**
 *
 * @author Tim Boudreau
 */
@NbBundle.Messages(value = {"copyTokenSequence=Copy Token Sequence", "copyTokenSequenceDesc=Copies the token names in the selection or to the end of the current line, creating a rough approximation of a rule definition", "noTokens=No token sequence found", "# {0} - theTokenSequence", "addedToClipboard=Token sequence added to clipboard: {0}"})
public final class CopyTokenSequenceAction extends AbstractAction {

    private final JTextComponent comp;

    public CopyTokenSequenceAction(JTextComponent comp) {
        this.comp = comp;
        putValue(NAME, Bundle.copyTokenSequence());
        putValue(SHORT_DESCRIPTION, Bundle.copyTokenSequenceDesc());
    }

    @Override
    @SuppressWarnings(value = "unchecked")
    public void actionPerformed(ActionEvent e) {
        StyledDocument doc = (StyledDocument) comp.getDocument();
        StringBuilder tokens = new StringBuilder();
        String mime = NbEditorUtilities.getMimeType(doc);
        doc.render(() -> {
            int start = comp.getSelectionStart();
            int end = comp.getSelectionEnd();
            if (start == end) {
                int lineCount = NbDocument.findLineNumber(doc, doc.getLength() - 1);
                int line = NbDocument.findLineNumber(doc, start);
                start = NbDocument.findLineOffset(doc, line);
                if (line >= lineCount) {
                    end = doc.getLength() - 1;
                } else {
                    end = NbDocument.findLineOffset(doc, line + 1);
                }
            }
            TokenHierarchy<Document> hier = TokenHierarchy.get(doc);
            if (hier == null) {
                return;
            }
            Language<AdhocTokenId> lang = (Language<AdhocTokenId>) Language.find(mime);
            if (lang == null) {
                DynamicLanguages.ensureRegistered(mime);
                lang = (Language<AdhocTokenId>) Language.find(mime);
                if (lang == null) {
                    // our lock bypassing makes this possible
                    return;
                }
            }
            TokenSequence<AdhocTokenId> seq = hier.tokenSequence(lang);
            if (seq == null || seq.isEmpty() || !seq.isValid()) {
                // do it the hard way - the token sequence held by the lexer infrastructure
                // was garbage collected
                EmbeddedAntlrParser par = AdhocLanguageHierarchy.parserFor(mime);
                if (par != null) {
                    Segment seg = new Segment();
                    try {
                        doc.getText(0, doc.getLength(), seg);
                        EmbeddedAntlrParserResult res = par.parse(seg);
                        if (res != null && res.isUsable() && res.proxy() != null && !res.proxy().isUnparsed()) {
                            AntlrProxies.ParseTreeProxy prx = res.proxy();
                            AntlrProxies.ProxyToken tok = prx.tokenAtPosition(start);
                            AntlrProxies.ProxyTokenType last = null;
                            while (tok != null && tok.getStartIndex() < end) {
                                AntlrProxies.ProxyTokenType type = prx.tokenTypeForInt(tok.getType());
                                CharSequence tokTxt = prx.textOf(tok);
                                if (!Strings.isBlank(tokTxt) && !tok.isEOF()) {
                                    if (Objects.equals(last, type)) {
                                        if (tokens.length() > 0 && tokens.charAt(tokens.length() - 1) != '+') {
                                            tokens.append('+');
                                        }
                                    } else {
                                        if (tokens.length() > 0) {
                                            tokens.append(' ');
                                        }
                                        if (type.literalName != null) {
                                            tokens.append('\'').append(type.literalName).append('\'');
                                        } else {
                                            tokens.append(type.symbolicName);
                                        }
                                    }
                                }
                                if (tok.getTokenIndex() + 1 >= prx.tokenCount()) {
                                    break;
                                }
                                tok = prx.tokens().get(tok.getTokenIndex() + 1);
                            }
                        }
                    } catch (Exception ex) {
                        Exceptions.printStackTrace(ex);
                    }
                }
                return;
            }
            seq.move(start);
            if (!seq.moveNext()) {
                return;
            }
            int count = seq.tokenCount();
            AdhocTokenId last = null;
            do {
                Token<AdhocTokenId> tok = seq.offsetToken();
                if (tok == null) {
                    break;
                }
                // Assume whitespace is on another channel and noise
                // if the user is attempting to figure out a token sequence
                // to form a rule from
                if (Strings.isBlank(tok.text())) {
                    seq.moveNext();
                    continue;
                }
                AdhocTokenId id = tok.id();
                if (Objects.equals(id, last)) {
                    tokens.append('+');
                } else {
                    if (tokens.length() > 0) {
                        tokens.append(' ');
                    }
                    tokens.append(id.toTokenString());
                }
                if (!seq.moveNext()) {
                    break;
                }
            } while (seq.index() < count && seq.offset() < end);
        });
        if (tokens.length() == 0) {
            StatusDisplayer.getDefault().setStatusText(Bundle.noTokens());
            Toolkit.getDefaultToolkit().beep();
        } else {
            StatusDisplayer.getDefault().setStatusText(Bundle.addedToClipboard(tokens));
            StringSelection sel = new StringSelection(tokens.toString());
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(sel, sel);
        }
    }

}

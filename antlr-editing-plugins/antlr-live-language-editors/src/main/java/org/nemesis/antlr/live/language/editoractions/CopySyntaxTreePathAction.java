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

import com.mastfrog.util.strings.Escaper;
import java.awt.Cursor;
import java.awt.EventQueue;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.AbstractAction;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import javax.swing.text.Segment;
import org.nemesis.antlr.live.language.AdhocEditorKit;
import org.nemesis.antlr.live.language.AdhocLanguageHierarchy;
import org.nemesis.antlr.live.parsing.EmbeddedAntlrParser;
import org.nemesis.antlr.live.parsing.EmbeddedAntlrParserResult;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies;
import org.nemesis.editor.ops.DocumentOperator;
import org.netbeans.modules.editor.NbEditorUtilities;
import org.openide.awt.StatusDisplayer;
import org.openide.util.Exceptions;
import org.openide.util.NbBundle;

/**
 *
 * @author Tim Boudreau
 */
@NbBundle.Messages(value = {"copySyntaxTree=Copy Syntax Tree Path", "copyTokenDetails=Copy Token Sequence Details", "parsing=Reparsing for syntax tree path", "copiedToClipboard=Copied to clipboard"})
public final class CopySyntaxTreePathAction extends AbstractAction {

    private final JTextComponent component;
    private StatusDisplayer.Message message;

    public CopySyntaxTreePathAction(JTextComponent component) {
        // set by PreviewPanel
        boolean isLexer = Boolean.TRUE.equals(component.getDocument().getProperty("isLexer"));
        putValue(NAME, isLexer ? Bundle.copyTokenDetails() : Bundle.copySyntaxTree());
        this.component = component;
    } // set by PreviewPanel

    private void failureMessage(String msg) {
        this.message = StatusDisplayer.getDefault().setStatusText(msg, StatusDisplayer.IMPORTANCE_FIND_OR_REPLACE);
    }

    @NbBundle.Messages(value = {"errDocTooShort=Document too short", "# {0} - caretPosition", "errNoToken=No token at caret position {0}", "errNothingToCopy=Nothing to copy (at start?)", "# {0} - caretPosition", "noTreeElement=No parse tree at caret position {0} (routed to another channel?)"})
    @Override
    public void actionPerformed(ActionEvent e) {
        int caret = component.getCaret().getDot();
        Document doc = component.getDocument();
        String mime = NbEditorUtilities.getMimeType(doc);
        Cursor oldCursor = component.getCursor();
        component.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        AdhocEditorKit.popupPool().submit(() -> {
            try {
                EmbeddedAntlrParser parser = AdhocLanguageHierarchy.parserFor(mime);
                Segment seg = DocumentOperator.render(doc, () -> {
                    Segment s = new Segment();
                    doc.getText(0, doc.getLength(), s);
                    return s;
                });
                if (seg.length() < 3) {
                    failureMessage(Bundle.errDocTooShort());
                    return;
                }
                EmbeddedAntlrParserResult res = parser.parse(seg);
                AntlrProxies.ParseTreeProxy proxy = res.proxy();
                AntlrProxies.ProxyToken tok = proxy.tokenAtPosition(caret);
                if (tok == null) {
                    failureMessage(Bundle.errNoToken(caret));
                    return;
                }
                if (proxy != null && !proxy.isUnparsed()) {
                    StringBuilder content = new StringBuilder();
                    if (proxy.isLexerGrammar()) {
                        List<String> infos = new ArrayList<>();
                        List<CharSequence> texts = new ArrayList<>();
                        List<AntlrProxies.ProxyToken> tokens = proxy.tokens();
                        for (AntlrProxies.ProxyToken pt : tokens) {
                            CharSequence text = Escaper.CONTROL_CHARACTERS.escape(proxy.textOf(tok));
                            if (tok.isWhitespace()) {
                                text = '\'' + text.toString() + '\'';
                            }
                            StringBuilder info = new StringBuilder();
                            AntlrProxies.ProxyTokenType type = proxy.tokenTypeForInt(pt.getType());
                            info.append(type.name()).append('(').append(pt.getType()).append(") ").append(pt.getStartIndex()).append('-').append(pt.getEndIndex()).append('=').append(pt.length());
                            infos.add(info.toString());
                            texts.add(text.toString());
                            if (pt.equals(tok)) {
                                break;
                            }
                        }
                        if (!infos.isEmpty()) {
                            StringBuilder textLine = new StringBuilder();
                            StringBuilder infoLine = new StringBuilder();
                            int maxLine = 120;
                            for (int i = 0; i < infos.size(); i++) {
                                CharSequence text = texts.get(i);
                                String info = infos.get(i);
                                textLine.append(text);
                                infoLine.append(info);
                                int maxLength = Math.max(text.length(), info.length());
                                if (textLine.length() + maxLength > maxLine) {
                                    content.append('\n').append(textLine);
                                    content.append('\n').append(infoLine);
                                    textLine.setLength(0);
                                    infoLine.setLength(0);
                                }
                                if (text.length() > info.length()) {
                                    char[] pad = new char[text.length() - info.length()];
                                    Arrays.fill(pad, ' ');
                                    infoLine.append(pad);
                                } else if (text.length() < info.length()) {
                                    char[] pad = new char[info.length() - text.length()];
                                    Arrays.fill(pad, ' ');
                                    textLine.append(pad);
                                }
                                if (textLine.length() + 3 > maxLine) {
                                    content.append('\n').append(textLine);
                                    content.append('\n').append(infoLine);
                                    textLine.setLength(0);
                                    infoLine.setLength(0);
                                } else if (i != infos.size() - 1) {
                                    textLine.append(" / ");
                                    infoLine.append(" / ");
                                }
                            }
                            if (textLine.length() > 0) {
                                content.append('\n').append(textLine);
                                content.append('\n').append(infoLine);
                            }
                        } else {
                            return;
                        }
                    } else {
                        int tix = tok.getTokenIndex();
                        int bestDepth = Integer.MIN_VALUE;
                        AntlrProxies.ParseTreeElement bestElement = null;
                        for (AntlrProxies.ParseTreeElement el : proxy.allTreeElements()) {
                            if (el instanceof AntlrProxies.TokenAssociated) {
                                AntlrProxies.TokenAssociated ta = (AntlrProxies.TokenAssociated) el;
                                if (ta.startTokenIndex() >= tix && ta.stopTokenIndex() <= tix) {
                                    if (el.depth() > bestDepth) {
                                        bestDepth = el.depth();
                                        bestElement = el;
                                    }
                                }
                            }
                        }
                        if (bestElement != null) {
                            AntlrProxies.ParseTreeElement el = bestElement;
                            while (el.kind() != AntlrProxies.ParseTreeElementKind.ROOT) {
                                char[] pad = new char[el.depth() * 4];
                                Arrays.fill(pad, ' ');
                                String txt = new String(pad) + el.stringify(proxy) + "\n";
                                content.insert(0, txt);
                                el = el.parent();
                            }
                        } else {
                            failureMessage(Bundle.noTreeElement(caret));
                            return;
                        }
                    }
                    if (content.length() > 0) {
                        Clipboard clip = Toolkit.getDefaultToolkit().getSystemClipboard();
                        StringSelection string = new StringSelection(content.toString());
                        clip.setContents(string, string);
                        StatusDisplayer.getDefault().setStatusText(Bundle.copiedToClipboard());
                    } else {
                        failureMessage(Bundle.errNothingToCopy());
                    }
                }
            } catch (Exception ex) {
                Exceptions.printStackTrace(ex);
            } finally {
                EventQueue.invokeLater(() -> {
                    component.setCursor(oldCursor);
                });
            }
        });
    }

}

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
package org.nemesis.antlr.live.language;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.Element;
import javax.swing.text.Position;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import org.nemesis.antlr.live.language.AdhocHighlighterManager.HighlightingInfo;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies;
import org.netbeans.api.editor.document.LineDocument;
import org.netbeans.api.editor.document.LineDocumentUtils;
import org.netbeans.editor.BaseDocument;
import org.netbeans.modules.editor.NbEditorUtilities;
import org.netbeans.spi.editor.highlighting.HighlightsContainer;
import org.netbeans.spi.editor.highlighting.ZOrder;
import org.netbeans.spi.editor.highlighting.support.OffsetsBag;
import org.netbeans.spi.editor.hints.ErrorDescription;
import org.netbeans.spi.editor.hints.ErrorDescriptionFactory;
import org.netbeans.spi.editor.hints.HintsController;
import org.netbeans.spi.editor.hints.Severity;
import org.openide.filesystems.FileObject;
import org.openide.util.NbBundle.Messages;
import org.openide.util.RequestProcessor;

/**
 *
 * @author Tim Boudreau
 */
public class AdhocErrorHighlighter extends AbstractAntlrHighlighter implements Runnable {

    protected final OffsetsBag bag;
    private static AttributeSet errorColoring;
    private final RequestProcessor.Task refreshErrorsTask;

    public AdhocErrorHighlighter(AdhocHighlighterManager mgr) {
        super(mgr, ZOrder.BOTTOM_RACK.forPosition(2001));
        bag = new OffsetsBag(mgr.document());
        refreshErrorsTask = mgr.threadPool().create(this);
    }

    @Override
    public HighlightsContainer getHighlightsBag() {
        return bag;
    }

    public static AttributeSet coloring() {
        // Do not cache - user can edit these
//        MimePath mimePath = MimePath.parse(ANTLR_MIME_TYPE);
//        FontColorSettings fcs = MimeLookup.getLookup(mimePath).lookup(FontColorSettings.class);
//        AttributeSet result = fcs.getFontColors("nested_blocks");
//        assert result != null : "nested_block missing from colors";
//        return result;
        if (errorColoring != null) {
            return errorColoring;
        }
        SimpleAttributeSet set = new SimpleAttributeSet();
        StyleConstants.setUnderline(set, true);
        StyleConstants.setForeground(set, Color.red);
        return errorColoring = set;
    }

    @Override
    protected void refresh(HighlightingInfo info) {
        Document doc = info.doc;
        AntlrProxies.ParseTreeProxy semantics = info.semantics;
        if (semantics.text() == null || semantics.text().length() < 2) {
            bag.clear();
            return;
        }
        LOG.log(Level.FINER, "Refresh errors with {0} errors", semantics.syntaxErrors().size());
        OffsetsBag bag = new OffsetsBag(doc);
        if (!semantics.syntaxErrors().isEmpty()) {
            AttributeSet attrs = coloring();
            for (AntlrProxies.ProxySyntaxError e : semantics.syntaxErrors()) {
                if (e instanceof AntlrProxies.ProxyDetailedSyntaxError) {
                    AntlrProxies.ProxyDetailedSyntaxError d = (AntlrProxies.ProxyDetailedSyntaxError) e;
                    int start = Math.max(0, d.startIndex());
                    int end = Math.min(doc.getLength() - 1, d.stopIndex());
                    bag.addHighlight(Math.min(start, end), Math.max(start, end) + 1, attrs);
                } else {
                    if (doc instanceof LineDocument) {
                        LineDocument ld = (LineDocument) doc;
                        try {
                            int start = LineDocumentUtils.getLineStart(ld, e.line())
                                    + e.charPositionInLine();
                            int end = LineDocumentUtils.getLineEnd(ld, e.line());
                            bag.addHighlight(Math.min(start, end), Math.max(start, end), attrs);
                        } catch (BadLocationException ble) {
                            Logger.getLogger(AdhocErrorHighlighter.class.getName()).log(Level.INFO, "BLE parsing " + e, ble);
                        }
                    }
                }
            }
        }
        this.bag.setHighlights(bag);
        this.refreshErrorsTask.schedule(100);
    }

    public void run() {
        HighlightingInfo info = mgr.lastInfo();
        if (info != null) {
            Document doc = info.doc;
            Runnable doIt = () -> {
                refreshSourceErrors(info);
            };
            // createErrorDescription will lock the document;
            // so will the call to set the errors - so rather than
            // risk deadlock, we do this in a background thread and
            // pre-lock the document so we don't lock/unlock once per
            // error
            if (doc instanceof BaseDocument) {
                BaseDocument bd = (BaseDocument) doc;
                bd.runAtomicAsUser(doIt);
            } else {
                doc.render(doIt);
            }
        }
    }

    @Messages({"buildFailed=Build Failed"})
    private void refreshSourceErrors(HighlightingInfo info) {
        Document d = info.doc;
        FileObject fo = NbEditorUtilities.getFileObject(d);

        AntlrProxies.ParseTreeProxy proxy = info.semantics;

        LOG.log(Level.FINEST, "Refresh src errs {0} from {1}",
                new Object[]{this, info});

//        System.out.println("refresh source errors " + info.semantics.loggingInfo());

        if (proxy.isUnparsed()) {
            String msg = Bundle.buildFailed();
            ErrorDescription ed = ErrorDescriptionFactory
                    .createErrorDescription(org.netbeans.spi.editor.hints.Severity.ERROR,
                            msg, d, 0);
            setErrors(d, Collections.singleton(ed));
        } else if (!proxy.syntaxErrors().isEmpty()) {
            List<ErrorDescription> ed = new ArrayList<>();
            for (AntlrProxies.ProxySyntaxError err : proxy.syntaxErrors()) {
                try {
                    int start, end;
                    if (err.hasFileOffsetsAndTokenIndex()) {
                        start = err.startIndex();
                        end = err.stopIndex() + 1;
                    } else {
                        Element lineRoot = ((BaseDocument) d).getParagraphElement(0).getParentElement();
                        Element line = lineRoot.getElement(err.line() - 1); // antlr lines are 1-indexed
                        if (line == null) {
                            start = 0;
                            end = Math.min(80, d.getLength());
                        } else {
                            start = line.getStartOffset() + err.charPositionInLine();
                            AntlrProxies.ProxyToken tok = proxy.tokenAtLinePosition(err.line(), err.charPositionInLine());
//                            System.out.println("COMPUTE CHAR OFFSET line "
//                                    + err.line() + " element start offset "
//                                    + line.getStartOffset() + " token "
//                                    + tok + " " + " tok line " + (tok == null ? -1 :tok.getLine())
//                                    + " length " + (tok == null ? -1 : tok.length()) + " end will be "
//                                    + (start + (tok == null ? 0 : tok.length())
//                                            + " in " + NbEditorUtilities.getFileObject(d))
//                            );
                            if (tok != null) {
                                end = Math.min(line.getEndOffset(), start + tok.length());
                            } else {
                                start = line.getStartOffset();
                                end = Math.min(line.getEndOffset(), start + 1);
                            }
                        }
                    }
                    Position pos1 = d.createPosition(start);
                    Position pos2 = d.createPosition(end);
                    ErrorDescription error = ErrorDescriptionFactory
                            .createErrorDescription(Severity.ERROR, err.message(), Collections.emptyList(), d, pos1, pos2);
                    ed.add(error);
                    // The following errors simply *are* possible, in the case
                    // that the document has changed, as long as we are using
                    // createPosition
                } catch (IndexOutOfBoundsException ex) {
                    IllegalStateException ise = new IllegalStateException("IOOBE highlighting error on "
                            + err.line() + ":" + err.charPositionInLine() + " in "
                            + d.getProperty(Document.StreamDescriptionProperty)
                            + " parse is of " + info.semantics.grammarPath()
                            + " err " + err.getClass().getSimpleName() + ": " + err,
                            ex);
                    LOG.log(Level.FINE, "Error highlighting error '" + ex
                            + "' in " + fo, ise);
                } catch (BadLocationException ex) {
                    IllegalStateException e2 = new IllegalStateException("IOOBE highlighting error on "
                            + err.line() + ":" + err.charPositionInLine() + " in "
                            + d.getProperty(Document.StreamDescriptionProperty)
                            + " parse is of " + info.semantics.grammarPath()
                            + " err " + err.getClass().getSimpleName() + ": " + err,
                            ex);
                    LOG.log(Level.FINE, "Error highlighting error '" + e2
                            + "' in " + fo, e2);
                }
            }
            setErrors(d, ed);
        } else {
            setErrors(d, Collections.emptySet());
        }
    }

    protected void setErrors(Document document, Collection<ErrorDescription> errors) {
        HintsController.setErrors(document, "adhoc-errors", errors);
    }

}

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
package org.nemesis.antlr.v4.netbeans.v8.grammar.file.preview;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.Element;
import javax.swing.text.Position;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.extract.AntlrProxies;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.extract.AntlrProxies.ParseTreeProxy;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.extract.AntlrProxies.ProxyToken;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.extract.GenerateBuildAndRunGrammarResult;
import org.netbeans.editor.BaseDocument;
import org.netbeans.modules.parsing.api.Snapshot;
import org.netbeans.modules.parsing.spi.ParserResultTask;
import org.netbeans.modules.parsing.spi.Scheduler;
import org.netbeans.modules.parsing.spi.SchedulerEvent;
import org.netbeans.modules.parsing.spi.SchedulerTask;
import org.netbeans.modules.parsing.spi.TaskFactory;
import org.netbeans.spi.editor.hints.ErrorDescription;
import org.netbeans.spi.editor.hints.ErrorDescriptionFactory;
import org.netbeans.spi.editor.hints.HintsController;
import org.netbeans.spi.editor.hints.Severity;
import org.openide.filesystems.FileObject;
import org.openide.util.NbBundle.Messages;

/**
 *
 * @author Tim Boudreau
 */
class AdhocErrorsHighlighter extends ParserResultTask<AdhocParserResult> {

    private static final String LAYER = "adhoc-parse";
    private final Snapshot snapshot;
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private static final Logger LOG = Logger.getLogger(AdhocErrorsHighlighter.class.getName());

    AdhocErrorsHighlighter(Snapshot sn) {
        this.snapshot = sn;
    }

    public static TaskFactory create() {
        return new TaskFactory() {
            @Override
            public Collection<? extends SchedulerTask> create(Snapshot snpsht) {
                return Collections.singleton(new AdhocErrorsHighlighter(snpsht));
            }
        };
    }

    @Messages({
        "compileFailed=Compile failed",
        "generationFailed=Source generation failed",
        "buildFailed=Source generation, compilation or extraction failed"
    })
    @Override
    public void run(AdhocParserResult t, SchedulerEvent se) {
        if (cancelled.getAndSet(false)) {
            return;
        }
        Document d = snapshot.getSource().getDocument(false);
        FileObject fo = snapshot.getSource().getFileObject();

        ParseTreeProxy proxy = t.parseTree();
        if (proxy.isUnparsed()) {
            String msg = Bundle.buildFailed();
            if (t.buildResult() != null) {
                GenerateBuildAndRunGrammarResult r = t.buildResult();
                if (r.thrown().isPresent()) {
                    msg = r.thrown().get().toString();
                } else {
                    if (r.compileResult().isPresent() && !r.compileResult().get().isUsable()) {
                        msg = Bundle.compileFailed();
                    } else if (!r.generationResult().isSuccess()) {
                        msg = Bundle.generationFailed();
                    }
                }
            }
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
                        Element line = lineRoot.getElement(err.line()-1); // antlr lines are 1-indexed
                        if (line == null) {
                            start = 0;
                            end = Math.min(80, d.getLength());
                        } else {
                            start = line.getStartOffset() + err.charPositionInLine();
                            ProxyToken tok = proxy.tokenAtLinePosition(err.line(), err.charPositionInLine());
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
                    IllegalStateException ise =  new IllegalStateException("IOOBE highlighting error on "
                            + err.line() + ":" + err.charPositionInLine() + " in "
                            + d.getProperty(Document.StreamDescriptionProperty)
                            + " parse is of " + t.buildResult().generationResult().sourceFile()
                            + " err " + err.getClass().getSimpleName() + ": " + err,
                            ex);
                    LOG.log(Level.FINE, "Error highlighting error '" + ex
                            + "' in " + fo, ise);
                } catch (BadLocationException ex) {
                    IllegalStateException e2 = new IllegalStateException("IOOBE highlighting error on "
                            + err.line() + ":" + err.charPositionInLine() + " in "
                            + d.getProperty(Document.StreamDescriptionProperty)
                            + " parse is of " + t.buildResult().generationResult().sourceFile()
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
        HintsController.setErrors(document, LAYER, errors);
    }

    @Override
    public int getPriority() {
        return 1000;
    }

    @Override
    public Class<? extends Scheduler> getSchedulerClass() {
        return Scheduler.CURSOR_SENSITIVE_TASK_SCHEDULER;
    }

    @Override
    public void cancel() {
        cancelled.set(true);
    }
}

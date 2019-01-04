package org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.syntax;

import com.google.common.annotations.VisibleForTesting;
import java.awt.EventQueue;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.text.Document;
import org.nemesis.antlr.v4.netbeans.v8.AntlrFolders;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.NBANTLRv4Parser;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.ANTLRv4SemanticParser;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.AntlrLibrary;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.AntlrSourceGenerationResult;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.InMemoryAntlrSourceGenerationBuilder;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.ParsedAntlrError;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.extract.ParseProxyBuilder;
import org.nemesis.antlr.v4.netbeans.v8.project.helper.ProjectHelper;
import org.nemesis.antlr.v4.netbeans.v8.util.TimedCache;
import org.netbeans.api.editor.mimelookup.MimeRegistration;
import org.netbeans.api.project.Project;
import org.netbeans.modules.parsing.api.Snapshot;
import org.netbeans.modules.parsing.spi.ParserResultTask;
import org.netbeans.modules.parsing.spi.Scheduler;
import org.netbeans.modules.parsing.spi.SchedulerEvent;
import org.netbeans.modules.parsing.spi.SchedulerTask;
import org.netbeans.modules.parsing.spi.TaskFactory;
import org.netbeans.spi.editor.hints.ErrorDescription;
import org.netbeans.spi.editor.hints.ErrorDescriptionFactory;
import org.netbeans.spi.editor.hints.HintsController;
import org.openide.cookies.EditorCookie;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;
import org.openide.loaders.DataObjectNotFoundException;
import org.openide.util.Exceptions;
import org.openide.util.RequestProcessor;

/**
 *
 * @author Tim Boudreau
 */
public class AntlrInvocationErrorHighlighter extends ParserResultTask<NBANTLRv4Parser.ANTLRv4ParserResult> {

    private Future<?> future;

    @MimeRegistration(mimeType = "text/x-g4", service = TaskFactory.class)
    public static class Factory extends TaskFactory {

        @Override
        public Collection<? extends SchedulerTask> create(Snapshot snapshot) {
            return Collections.singleton(new AntlrInvocationErrorHighlighter());
        }
    }

    private void checkOpenedPanes(Snapshot snapshot, Runnable ifAny) {

        EventQueue.invokeLater(() -> {
            // We do NOT want to fire off huge copy/compile/parse jobs
            // for every .g4 file the IDE happens to encounter in passing,
            // just ones that have an open editor
            FileObject fo = snapshot.getSource().getFileObject();
            if (fo == null) {
                return;
            }
            try {
                DataObject dob = DataObject.find(fo);
                EditorCookie ck = dob.getLookup().lookup(EditorCookie.class);
                if (ck == null || ck.getOpenedPanes() == null || ck.getOpenedPanes().length == 0) {
                    return;
                }
            } catch (DataObjectNotFoundException ex) {
                Exceptions.printStackTrace(ex);
            }
            PROC.post(ifAny);
        });

    }

    private final AtomicBoolean cancelled = new AtomicBoolean();
    private static final RequestProcessor PROC = new RequestProcessor("antlr-error-highlighting", 2, true);

    private static final TimedCache<Path, ParseProxyBuilder, IOException> builderForPath
            = TimedCache.createThrowing(30000L,
                    (Path request) -> InMemoryAntlrSourceGenerationBuilder.forAntlrSource(request)
                            .withImportDir(findImportDir(request))
                            .toParseAndRunBuilder());

    private static Optional<Path> findImportDir(Path sourceFile) {
        return AntlrFolders.IMPORT.getPath(ProjectHelper.getProject(sourceFile), Optional.of(sourceFile));
    }

    @Override
    public void run(NBANTLRv4Parser.ANTLRv4ParserResult t, SchedulerEvent se) {
        if (cancelled.getAndSet(false)) {
            return;
        }
        checkOpenedPanes(t.getSnapshot(), () -> {
            Future<?> fut = PROC.submit((Runnable) () -> {
                if (cancelled.get()) {
                    return;
                }
                FileObject fo = t.getSnapshot().getSource().getFileObject();
                if (fo != null) {
                    ANTLRv4SemanticParser sem = t.semanticParser();
                    Path sourceFile = FileUtil.toFile(fo).toPath();
                    AntlrLibrary lib = AntlrLibrary
                            .forOwnerOf(sourceFile);

                    Optional<Project> prj = ProjectHelper.getProject(sourceFile);
                    if (prj.isPresent()) {
                        File file = ProjectHelper.getJavaBuildDirectory(prj.get());
                        if (file != null && file.exists()) {
                            lib = lib.with(file.toPath());
                        }
                    }
                    try {
                        AntlrSourceGenerationResult bldr
                                = builderForPath.get(sourceFile)
                                        .parse(null).generationResult();
//                        AntlrSourceGenerationResult bldr
//                                = GrammarJavaSourceGeneratorBuilder
//                                        .forAntlrSource(sourceFile)
//                                        .withAntlrLibrary(lib)
//                                        .withImportDir(sem.importDir())
//                                        .build();
//                        bldr.delete();
                        if (cancelled.get()) {
                            return;
                        }
                        List<ParsedAntlrError> diags = bldr.diagnostics();
                        if (!diags.isEmpty()) {
                            List<ErrorDescription> errors = new ArrayList<>();
                            for (ParsedAntlrError pae : diags) {
                                FileObject errFile = FileUtil.toFileObject(FileUtil.normalizeFile(pae.path().toFile()));
                                if (errFile != null) {
                                    org.netbeans.spi.editor.hints.Severity sev
                                            = pae.isError() ? org.netbeans.spi.editor.hints.Severity.ERROR
                                            : org.netbeans.spi.editor.hints.Severity.WARNING;
                                    ErrorDescription ed = ErrorDescriptionFactory
                                            .createErrorDescription(sev,
                                                    "antlr: " + pae.message(), errFile, pae.fileOffset(), pae.fileOffset() + pae.length());
                                    errors.add(ed);
                                }
                            }
                            setErrors(t.getSnapshot().getSource().getDocument(false), "antlr-parse", errors);
                        }
                    } catch (IOException ex) {
                        Logger.getLogger(AntlrInvocationErrorHighlighter.class.getName()).log(Level.WARNING, "Exception building", ex);
                    }
                }
            });
            setFuture(fut);
        });
    }

    @VisibleForTesting
    protected void setErrors(Document document, String layerName, List<ErrorDescription> errors) {
        HintsController.setErrors(document, layerName, errors);
    }

    @Override
    public int getPriority() {
        return 1;
    }

    @Override
    public Class<? extends Scheduler> getSchedulerClass() {
        return Scheduler.EDITOR_SENSITIVE_TASK_SCHEDULER;
    }

    @Override
    public void cancel() {
        cancelled.set(true);
        Future<?> fut;
        synchronized (this) {
            fut = this.future;
            this.future = null;
        }
        if (fut != null) {
            fut.cancel(true);
        }
    }

    private void setFuture(Future<?> fut) {
        this.future = fut;
    }
}

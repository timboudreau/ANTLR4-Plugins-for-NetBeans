package org.nemesis.antlr.spi.language;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.text.Document;
import org.netbeans.modules.parsing.api.Snapshot;
import org.netbeans.modules.parsing.spi.ParserResultTask;
import org.netbeans.modules.parsing.spi.Scheduler;
import org.netbeans.modules.parsing.spi.SchedulerEvent;
import org.netbeans.modules.parsing.spi.SchedulerTask;
import org.netbeans.modules.parsing.spi.TaskFactory;
import org.netbeans.spi.editor.hints.ErrorDescription;
import org.netbeans.spi.editor.hints.HintsController;
import org.openide.filesystems.FileObject;

/**
 *
 * @author Tim Boudreau
 */
final class AntlrInvocationErrorHighlighter extends ParserResultTask<AntlrParseResult> {

    private static final Logger LOG = Logger.getLogger(AntlrInvocationErrorHighlighter.class.getName());

    static {
        LOG.setLevel(Level.ALL);
    }

    private final AtomicBoolean cancelled = new AtomicBoolean();

    private final String id;

    AntlrInvocationErrorHighlighter(String mimeType) {
        id = "errors-" + mimeType.replace('/', '-');
    }

    @Override
    public String toString() {
        return id;
    }

    static TaskFactory factory(String mimeType) {
        LOG.log(Level.FINE, "Create error task factory for {0} for "
                + " with {2}", new Object[]{mimeType});
        return new Factory(mimeType);
    }

    private static class Factory extends TaskFactory {

        private final String mimeType;

        Factory(String mimeType) {
            this.mimeType = mimeType;
        }

        @Override
        public Collection<? extends SchedulerTask> create(Snapshot snapshot) {
            return Collections.singleton(new AntlrInvocationErrorHighlighter(mimeType));
        }
    }

    @Override
    public void run(AntlrParseResult t, SchedulerEvent se) {
        if (cancelled.getAndSet(false)) {
            LOG.log(Level.FINER, "Skip error highlighting for cancellation for {0} with {1}",
                    new Object[]{id, t});
            return;
        }
        List<? extends ErrorDescription> errors = t.getErrorDescriptions();
        LOG.log(Level.FINEST, "Syntax errors in {0}: {1}", new Object[] {t, errors});
        setForSnapshot(t.getSnapshot(), errors);
    }

    private void setForSnapshot(Snapshot snapshot, List<? extends ErrorDescription> errors) {
        FileObject fo = snapshot.getSource().getFileObject();
        if (fo != null) {
            HintsController.setErrors(fo, id, errors);
        } else {
            Document doc = snapshot.getSource().getDocument(false);
            if (doc != null) {
                HintsController.setErrors(doc, id, errors);
            }
        }
    }

    protected void setErrors(Document document, String layerName, List<? extends ErrorDescription> errors) {
        if (document != null) {
            HintsController.setErrors(document, layerName, errors);
        }
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
        LOG.log(Level.FINEST, "Cancel {0}", this);
    }
}

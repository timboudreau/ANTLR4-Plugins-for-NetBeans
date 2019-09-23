package org.nemesis.antlr.live.language;

import com.mastfrog.function.TriConsumer;
import java.lang.ref.WeakReference;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import org.nemesis.antlr.compilation.GrammarRunResult;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies;
import org.nemesis.debug.api.Debug;
import org.netbeans.modules.editor.NbEditorUtilities;
import org.netbeans.spi.editor.highlighting.HighlightsContainer;
import org.openide.util.Exceptions;
import org.openide.util.RequestProcessor;

/**
 * Base class for highlighters that takes care of most of the boilerplate of
 * listening and re-running as needed.
 *
 * @author Tim Boudreau
 */
public abstract class AbstractAntlrHighlighter implements TriConsumer<Document, GrammarRunResult<?>, AntlrProxies.ParseTreeProxy> {

    protected final WeakReference<Document> weakDoc;
    protected static final RequestProcessor threadPool = new RequestProcessor("antlr-highlighting", 5, true);
    protected final static int REFRESH_DELAY = 100;
    private RequestProcessor.Task refreshTask;
    protected JTextComponent comp;
    protected final Logger LOG = Logger.getLogger(getClass().getName());
    private final AtomicReference<HighlightingInfo> lastParseInfo = new AtomicReference<>();

    public AbstractAntlrHighlighter(Document doc) {
        weakDoc = new WeakReference<>(doc);
        AdhocReparseListeners.listen(NbEditorUtilities.getMimeType(doc), doc, this);
        refreshTask = threadPool.create(this::doRefresh);
    }

    protected HighlightingInfo lastInfo() {
        return lastParseInfo.get();
    }

    protected final void scheduleRefresh() {
        refreshTask.schedule(REFRESH_DELAY);
    }

    private void doRefresh() {
        HighlightingInfo info = lastParseInfo.get();
        if (info != null) {
            refresh(info);
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName();
    }

    protected final Optional<Document> document() {
        return Optional.ofNullable(weakDoc.get());
    }

    void clearLastParseInfo() {
        lastParseInfo.set(null);
    }

    public abstract HighlightsContainer getHighlightsBag();

    @Override
    public final void apply(Document a, GrammarRunResult<?> b, AntlrProxies.ParseTreeProxy s) {
        if (s.text() == null) {
            return;
        }
        Debug.run(this, getClass().getSimpleName() + "-notified-reparse", s::toString, () -> {
            Debug.message(NbEditorUtilities.getFileObject(a) + " (fileobject)", () -> {
                try {
                    return "PROXY TEXT:\n" + s.toString() + "\n\nDOC TEXT:\n"
                            + a.getText(0, a.getLength());
                } catch (BadLocationException ex) {
                    Exceptions.printStackTrace(ex);
                    return ex.toString();
                }
            });
            HighlightingInfo info = new HighlightingInfo(a, b, s);
            lastParseInfo.set(info);
            scheduleRefresh();
        });
    }

    protected abstract void refresh(HighlightingInfo info);

    protected static final class HighlightingInfo {

        final Document doc;
        final GrammarRunResult<?> runResult;
        final AntlrProxies.ParseTreeProxy semantics;

        public HighlightingInfo(Document doc, GrammarRunResult<?> runResult, AntlrProxies.ParseTreeProxy semantics) {
            this.doc = doc;
            this.runResult = runResult;
            this.semantics = semantics;
        }
    }
}

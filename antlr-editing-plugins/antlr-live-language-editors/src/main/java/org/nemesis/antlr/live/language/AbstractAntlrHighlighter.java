package org.nemesis.antlr.live.language;

import java.awt.EventQueue;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.text.JTextComponent;
import org.nemesis.antlr.live.language.AdhocHighlighterManager.HighlightingInfo;
import org.netbeans.spi.editor.highlighting.HighlightsContainer;
import org.netbeans.spi.editor.highlighting.ZOrder;

/**
 * Base class for highlighters that takes care of most of the boilerplate of
 * listening and re-running as needed.
 *
 * @author Tim Boudreau
 */
public abstract class AbstractAntlrHighlighter {

    protected final Logger LOG = Logger.getLogger(getClass().getName());
    protected final AdhocHighlighterManager mgr;
    private final ZOrder zorder;

    public AbstractAntlrHighlighter(AdhocHighlighterManager mgr, ZOrder zorder) {
        this.mgr = mgr;
        this.zorder = zorder;
        LOG.setLevel(Level.ALL);
    }

    final ZOrder zorder() {
        return zorder;
    }

    protected void addNotify(JTextComponent comp) {

    }

    protected void removeNotify(JTextComponent comp) {

    }

    protected void onColoringsChanged() {

    }

    protected void onNewHighlightingInfo() {

    }

    protected void onEq(Runnable run) {
        if (EventQueue.isDispatchThread()) {
            run.run();
        } else {
            EventQueue.invokeLater(run);
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName();
    }

    public abstract HighlightsContainer getHighlightsBag();

    protected abstract void refresh(HighlightingInfo info);

}

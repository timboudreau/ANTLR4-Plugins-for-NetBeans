package org.nemesis.antlr.live.language;

import java.awt.EventQueue;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.lang.ref.WeakReference;
import java.util.Optional;
import java.util.logging.Level;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import org.nemesis.adhoc.mime.types.AdhocMimeTypes;
import static org.nemesis.adhoc.mime.types.AdhocMimeTypes.loggableMimeType;
import org.nemesis.antlr.live.ParsingUtils;
import org.nemesis.antlr.live.language.coloring.AdhocHighlightsContainer;
import org.nemesis.antlr.live.parsing.EmbeddedAntlrParser;
import org.nemesis.antlr.live.parsing.EmbeddedAntlrParsers;
import org.nemesis.debug.api.Debug;
import org.netbeans.modules.editor.NbEditorUtilities;
import org.netbeans.modules.parsing.api.ResultIterator;
import org.netbeans.modules.parsing.api.UserTask;
import org.netbeans.modules.parsing.spi.Parser;
import org.netbeans.spi.editor.highlighting.HighlightsContainer;
import org.netbeans.spi.editor.highlighting.HighlightsLayerFactory.Context;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.RequestProcessor.Task;
import org.openide.util.WeakListeners;

/**
 *
 * @author Tim Boudreau
 */
public final class AdhocRuleHighlighter extends AbstractAntlrHighlighter implements ChangeListener, DocumentListener {

    private final String mimeType;
    private final AdhocColorings colorings;
    private final EmbeddedAntlrParser parser;
    private final WeakReference<JTextComponent> comp;
    private final CompL compl = new CompL();
    private volatile boolean showing;
    private ComponentListener cl;
    private final Task updateTask;
    private final AdhocHighlightsContainer bag;

    @SuppressWarnings("LeakingThisInConstructor")
    public AdhocRuleHighlighter(Context ctx, String mimeType) {
        super(ctx.getDocument());
        bag = new AdhocHighlightsContainer();
        this.mimeType = mimeType;
        colorings = AdhocColoringsRegistry.getDefault().get(mimeType);
        parser = EmbeddedAntlrParsers.forGrammar("rule-higlighter "
                + logNameOf(ctx, mimeType), FileUtil.toFileObject(
                AdhocMimeTypes.grammarFilePathForMimeType(mimeType)
                        .toFile()));
        JTextComponent c = ctx.getComponent();
        comp = new WeakReference<>(c);
        c.addComponentListener(cl = WeakListeners.create(
                ComponentListener.class, compl, c));
        c.addPropertyChangeListener("ancestor",
                WeakListeners.propertyChange(compl, c));
        if (c.isShowing()) {
            compl.setShowing(c, true);
        }
        Runnable r = this::scheduleRefresh;
        c.putClientProperty("trigger", r);
        updateTask = threadPool.create(this::doChange);
    }

    static final String logNameOf(Context ctx, String mimeType) {
        Document doc = ctx.getDocument();
        if (doc != null) {
            FileObject fo = NbEditorUtilities.getFileObject(doc);
            if (fo != null) {
                return fo.getNameExt() + ":" + AdhocMimeTypes.loggableMimeType(mimeType);
            } else {
                return doc.toString() + ":" + AdhocMimeTypes.loggableMimeType(mimeType);
            }
        }
        return "unknown";
    }

    class CompL extends ComponentAdapter implements PropertyChangeListener {

        private void setShowing(JTextComponent comp, boolean nowShowing) {
            System.out.println("\n\nsetShowing " + nowShowing);
            if (showing != nowShowing) {
                showing = nowShowing;
                Document dd = comp.getDocument();
                if (!nowShowing) {
                    AdhocReparseListeners.unlisten(mimeType, dd, AdhocRuleHighlighter.this);
                    dd.removeDocumentListener(AdhocRuleHighlighter.this);
                    colorings.removeChangeListener(AdhocRuleHighlighter.this);
                    clearLastParseInfo();
                    Debug.message("component-hidden", dd::toString);
                } else {
                    AdhocReparseListeners.listen(mimeType, dd, AdhocRuleHighlighter.this);
                    dd.addDocumentListener(AdhocRuleHighlighter.this);
                    colorings.addChangeListener(AdhocRuleHighlighter.this);
//                    scheduleRefresh();
                    Debug.message("component-shown", dd::toString);
                    change();
                }
            }
        }

        @Override
        public void componentHidden(ComponentEvent e) {
            setShowing((JTextComponent) e.getComponent(), false);
        }

        @Override
        public void componentShown(ComponentEvent e) {
            setShowing((JTextComponent) e.getComponent(), true);
        }

        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            if ("ancestor".equals(evt.getPropertyName())) {
                setShowing((JTextComponent) evt.getSource(), evt.getNewValue() != null);
            }
        }
    }

    private boolean componentIsShowing() {
        return showing;
    }

    @Override
    public void stateChanged(ChangeEvent e) {
        super.scheduleRefresh();
    }

    @Override
    public String toString() {
        return AdhocRuleHighlighter.class.getSimpleName() + " for " + loggableMimeType(mimeType);
    }

    @Override
    protected void refresh(HighlightingInfo info) {
        if (info.semantics.isUnparsed() || info.semantics.text() == null || info.semantics.text().length() == 0) {
            return;
        }
//        AdhocHighlightsSequence seq = new AdhocHighlightsSequence(colorings,
//                info.semantics, info.doc.getLength());
        onEq(() -> {
//            bag.setHighlights(seq);
            bag.update(colorings, info.semantics, info.semantics.text().length());
        });
    }

    public final HighlightsContainer getHighlightsBag() {
        return bag;
    }

    private void onEq(Runnable run) {
        if (EventQueue.isDispatchThread()) {
            run.run();
        } else {
            EventQueue.invokeLater(run);
        }
    }

    @Override
    public void insertUpdate(DocumentEvent e) {
        change();
    }

    @Override
    public void removeUpdate(DocumentEvent e) {
        change();
    }

    @Override
    public void changedUpdate(DocumentEvent e) {
        change();
    }

    void doChange() {
        Debug.run(this, "rule-highlighter-onchange " + mimeType, () -> {
            Optional<Document> doc = super.document();
            Document d = null;
            if (doc.isPresent()) {
                d = doc.get();
            } else {
                HighlightingInfo info = lastInfo();
                if (info != null) {
                    d = info.doc;
                }
            }
            if (d == null) {
                return;
            }

            try {
                ParsingUtils.parse(d);
                String txt = d.getText(0, d.getLength());
                parser.parse(txt);
                scheduleRefresh();
            } catch (Exception ex) {
                LOG.log(Level.SEVERE, null, ex);
            }
        });
    }

    private void change() {
        if (componentIsShowing()) {
            updateTask.schedule(100);
        } else {
            System.out.println("component not showing, but doc changed");
        }
    }

    final UserTask ut = new UserTask() {
        @Override
        public void run(ResultIterator resultIterator) throws Exception {
            Debug.runObjectThrowing(this, "get-parser-result " + mimeType, () -> {
                Parser.Result res = resultIterator.getParserResult();
                if (res == null) {
                    Debug.failure("null-parse-result", () -> "");
                } else {
                    Debug.success("parse-result", () -> {
                        return res.toString();
                    });
                }
                return res;
            });
        }
    };
}

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
package org.nemesis.antlr.fold;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.LongSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JEditorPane;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import org.netbeans.api.editor.fold.FoldHierarchy;
import org.netbeans.api.editor.fold.FoldType;
import org.netbeans.spi.editor.fold.FoldInfo;
import org.netbeans.spi.editor.fold.FoldOperation;
import org.openide.cookies.EditorCookie;
import org.openide.loaders.DataObject;
import org.openide.util.Exceptions;

/**
 *
 * @author Tim Boudreau
 */
final class FoldCommitter implements Runnable {

    private boolean rendering;
    private final Document doc;
    private List<FoldInfo> infos;
    private final List<Integer> anchors;
    private long startTime;
    private final LongSupplier version;
    private final long stamp;
    private static final Logger LOG = Logger.getLogger(FoldCommitter.class.getName());

    private final boolean first;
    private final FoldOperation op;

    FoldCommitter(Document doc, List<FoldInfo> infos, List<Integer> anchors, LongSupplier version, long stamp, FoldOperation op, boolean first) {
        this.doc = doc;
        this.infos = infos;
        this.version = version;
        this.stamp = stamp;
        this.anchors = anchors;
        this.op = op;
        this.first = first;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("FoldCommitter{");
        sb.append(stamp)
                .append(", currVersion=").append(version.getAsLong())
                .append(", componentId=").append(componentIdHash())
                .append(", doc=").append(doc);
        return sb.append('}').toString();
    }

    private int componentIdHash() {
        FoldHierarchy hier = op.getHierarchy();
        if (hier != null) {
            JTextComponent comp = hier.getComponent();
            if (comp != null) {
                return System.identityHashCode(comp);
            }
        }
        return 0;
    }

    private FoldInfo expanded(FoldInfo info) {
        FoldInfo ex = FoldInfo.range(info.getStart(), info.getEnd(), info.getType());
        if (info.getTemplate() != info.getType().getTemplate()) {
            ex = ex.withTemplate(info.getTemplate());
        }
        if (info.getDescriptionOverride() != null) {
            ex = ex.withDescription(info.getDescriptionOverride());
        }
        ex.attach(info.getExtraInfo());
        return ex.collapsed(false);
    }

    @Override
    public void run() {
        FoldOperation operation = op;
        int caretPos = -1;
        if (!rendering) {
            startTime = System.currentTimeMillis();
            rendering = true;
            doc.render(this);
            return;
        }
        if (first) {
            LOG.log(Level.FINER, "Run first folds on {0}", operation);
            JTextComponent c = operation.getHierarchy().getComponent();
            Object od = doc.getProperty(Document.StreamDescriptionProperty);
            if (od instanceof DataObject) {
                DataObject d = (DataObject) od;
                EditorCookie cake = d.getLookup().lookup(EditorCookie.class);
                JEditorPane[] panes = cake.getOpenedPanes();
                int idx = panes == null ? -1 : Arrays.asList(panes).indexOf(c);
                if (idx != -1) {
                    caretPos = c.getCaret().getDot();
                }
            }
        }
        JTextComponent c = operation.getHierarchy().getComponent();
        final int currentCaretPos = caretPos;
        maintainingScrollPosition(c, () -> {
            operation.getHierarchy().lock();
            try {
                if (!first) {
                    // the first call must always complete - it initializes the folds
                    // folds will be absent if this one is cancelled
                    if (version.getAsLong() != stamp || operation.getHierarchy().getComponent().getDocument() != doc) {
                        return;
                    }
                }
                int expandIndex = -1;
                if (currentCaretPos >= 0) {
                    for (int i = 0; i < anchors.size(); i++) {
                        int a = anchors.get(i);
                        if (a > currentCaretPos) {
                            continue;
                        }
                        FoldInfo fi = infos.get(i);
                        if (a == currentCaretPos) {
                            // do not expand comments if the pos is at the start, not within
                            FoldType ft = fi.getType();
                            if (ft.isKindOf(FoldType.INITIAL_COMMENT) || ft.isKindOf(FoldType.COMMENT) || ft.isKindOf(FoldType.DOCUMENTATION)) {
                                continue;
                            }
                        }
                        if (fi.getEnd() > currentCaretPos) {
                            expandIndex = i;
                            break;
                        }
                    }
                }
                if (expandIndex != -1) {
                    infos = new ArrayList<>(infos);
                    FoldInfo newInfo = expanded(infos.get(expandIndex));
                    infos.set(expandIndex, newInfo);
                }
                operation.update(infos, null, null);
            } catch (BadLocationException e) {
                Exceptions.printStackTrace(e);
            } finally {
                operation.getHierarchy().unlock();
                LOG.log(Level.FINE, "Finish commiting folds with {0} folds on {1}", new Object[]{infos.size(), doc});
            }
        });
        long endTime = System.currentTimeMillis();
        Logger.getLogger("TIMER").log(Level.FINE, "AFolds - 2", new Object[]{doc, endTime - startTime});
    }

    static void maintainingScrollPosition(JTextComponent comp, Runnable run) {
        JScrollPane pane = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, comp);
        if (pane == null) {
            run.run();
            return;
        }
        Point viewPosition = pane.getViewport().getViewPosition();
        boolean oldCompIgnoreRepaint = comp.getIgnoreRepaint();
        boolean oldPaneIgnoreRepaint = pane.getIgnoreRepaint();
        comp.setIgnoreRepaint(true);
        pane.setIgnoreRepaint(true);
        try {
            run.run();
        } finally {
            pane.getViewport().setViewPosition(viewPosition);
            comp.setIgnoreRepaint(oldCompIgnoreRepaint);
            pane.setIgnoreRepaint(oldPaneIgnoreRepaint);
            pane.repaint();
            comp.repaint();
        }
    }
}

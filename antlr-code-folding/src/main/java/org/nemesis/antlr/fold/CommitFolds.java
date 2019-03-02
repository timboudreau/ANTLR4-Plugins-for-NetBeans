/*
BSD License

Copyright (c) 2016-2018, Frédéric Yvon Vinet, Tim Boudreau
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

* Redistributions of source code must retain the above copyright
  notice, this list of conditions and the following disclaimer.
* Redistributions in binary form must reproduce the above copyright
  notice, this list of conditions and the following disclaimer in the
  documentation and/or other materials provided with the distribution.
* The name of its author may not be used to endorse or promote products
  derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR IMPLIED 
WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF 
MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT
SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, 
EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT
OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS 
INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN 
CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING
IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY 
OF SUCH DAMAGE.
*/


package org.nemesis.antlr.fold;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JEditorPane;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import org.netbeans.api.editor.fold.Fold;
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
final class CommitFolds implements Runnable {

    private boolean rendering;
    private final Document doc;
    private List<FoldInfo> infos;
    private final List<Integer> anchors;
    private long startTime;
    private final AtomicLong version;
    private final long stamp;
    private final Supplier<FoldOperation> opSupplier;
    private final BooleanSupplier firstSupplier;
    private static final Logger LOG = Logger.getLogger(CommitFolds.class.getName());
    static {
        LOG.setLevel(Level.ALL);
    }

    CommitFolds(Document doc, List<FoldInfo> infos, List<Integer> anchors, AtomicLong version, long stamp, ExtractionElementsFoldManager ref) {
        this(doc, infos, anchors, version, stamp, ref::operation, ref::first);
    }

    CommitFolds(Document doc, List<FoldInfo> infos, List<Integer> anchors, AtomicLong version, long stamp, Supplier<FoldOperation> opSupplier, BooleanSupplier firstSupplier) {
        this.doc = doc;
        this.infos = infos;
        this.version = version;
        this.stamp = stamp;
        this.anchors = anchors;
        this.opSupplier = opSupplier;
        this.firstSupplier = firstSupplier;
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

    public void run() {
        FoldOperation operation = opSupplier.get();
        if (operation == null) {
            LOG.log(Level.WARNING, "Operation null from {0} for {1}", new Object[] {opSupplier, this});
            return;
        }
        int caretPos = -1;
        if (!rendering) {
            startTime = System.currentTimeMillis();
            rendering = true;
            // retain import & initial comment states
            FoldHierarchy hier = operation.getHierarchy();
            if (hier != null) {
                JTextComponent comp = hier.getComponent();
                if (comp != null) {
                    Document doc = comp.getDocument();
                    if (doc != null) {
                        doc.render(this);
                    } else {
                        LOG.log(Level.INFO, "document null for {0}", operation);
                    }
                } else {
                    LOG.log(Level.INFO, "component null for {0}", operation);
                }
            } else {
                LOG.log(Level.INFO, "hierachy null for {0}", operation);
            }
            return;
        }
        boolean first = firstSupplier.getAsBoolean();
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
        operation.getHierarchy().lock();
        try {
            if (version.get() != stamp || operation.getHierarchy().getComponent().getDocument() != doc) {
                return;
            }
            int expandIndex = -1;
            if (caretPos >= 0) {
                for (int i = 0; i < anchors.size(); i++) {
                    int a = anchors.get(i);
                    if (a > caretPos) {
                        continue;
                    }
                    FoldInfo fi = infos.get(i);
                    if (a == caretPos) {
                        // do not expand comments if the pos is at the start, not within
                        FoldType ft = fi.getType();
                        if (ft.isKindOf(FoldType.INITIAL_COMMENT) || ft.isKindOf(FoldType.COMMENT) || ft.isKindOf(FoldType.DOCUMENTATION)) {
                            continue;
                        }
                    }
                    if (fi.getEnd() > caretPos) {
                        expandIndex = i;
                        break;
                    }
                }
            }
            if (expandIndex != -1) {
                infos = new ArrayList<>(infos);
                infos.set(expandIndex, expanded(infos.get(expandIndex)));
            }
            Map<FoldInfo, Fold> folds = operation.update(infos, null, null);
            if (folds == null) {
                // manager has been released.
                return;
            }
        } catch (BadLocationException e) {
            Exceptions.printStackTrace(e);
        } finally {
            operation.getHierarchy().unlock();
            LOG.log(Level.FINE, "Finish commiting folds with {0} folds on {1}", new Object[] {infos.size(), doc});
        }
        long endTime = System.currentTimeMillis();
        Logger.getLogger("TIMER").log(Level.FINE, "AFolds - 2", new Object[]{doc, endTime - startTime});
    }

}

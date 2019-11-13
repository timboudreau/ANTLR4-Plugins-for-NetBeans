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
package org.nemesis.antlr.navigator;

import java.awt.AWTEvent;
import java.awt.EventQueue;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import javax.swing.JList;
import javax.swing.ListModel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.text.Caret;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import org.nemesis.data.IndexAddressable;
import org.nemesis.data.SemanticRegion;
import org.nemesis.data.SemanticRegions;
import org.nemesis.data.named.NamedSemanticRegion;
import org.nemesis.data.named.NamedSemanticRegions;
import org.nemesis.extraction.Extraction;
import org.nemesis.extraction.key.ExtractionKey;
import org.nemesis.extraction.key.NamedRegionKey;
import org.nemesis.extraction.key.RegionsKey;
import org.openide.cookies.EditorCookie;
import org.openide.util.Mutex;

/**
 *
 * @author Tim Boudreau
 */
final class CaretTracker implements ChangeListener, PropertyChangeListener {

    private final JList<?> list;
    private JTextComponent listeningToComponent;
    private Caret listeningToCaret;
    private Document doc;
    private Extraction extraction;
    private final ExtractionKey<?> key;

    public CaretTracker(JList<?> list, ExtractionKey<?> key) {
        this.list = list;
        this.key = key;
    }

    synchronized void detach() {
        if (listeningToComponent != null) {
            listeningToComponent.removePropertyChangeListener("caret", this);
        }
        if (listeningToCaret != null) {
            listeningToCaret.removeChangeListener(this);
        }
        listeningToCaret = null;
        listeningToComponent = null;
        doc = null;
    }

    private void updateCaretListening(JTextComponent component) {
        if (component != listeningToComponent) {
            if (listeningToComponent != null) {
                listeningToComponent.removePropertyChangeListener("caret", this);
                if (listeningToCaret != null) {
                    listeningToCaret.removeChangeListener(this);
                    listeningToCaret = null;
                }
            }
            if (component != null) {
                listeningToComponent = component;
                listeningToComponent.addPropertyChangeListener("caret", this);
                listeningToCaret = listeningToComponent.getCaret();
                listeningToCaret.addChangeListener(this);
            }
        }
        if (!EventQueue.isDispatchThread()) {
            EventQueue.invokeLater(this::update);
        } else {
            update();
        }
    }

    public synchronized void track(Extraction extraction, EditorCookie ck) {
        Mutex.EVENT.readAccess(() -> {
            Document newDoc = ck != null ? ck.getDocument() : null;
            if (newDoc != doc) {
                if (newDoc != null) {
                    JTextComponent comp = ck.getOpenedPanes()[0];
                    updateCaretListening(comp);
                }
                doc = newDoc;
            }
            this.extraction = extraction;
            update();
        });
    }

    synchronized Extraction ext() {
        return extraction;
    }

    void update() {
        Extraction ext;
        Caret caret;
        synchronized (this) {
            ext = extraction;
            caret = listeningToCaret;
        }
        if (ext == null || !list.isDisplayable()) {
            return;
        }
        int pos = caret.getDot();
        if (key instanceof RegionsKey<?>) {
            RegionsKey<?> rk = (RegionsKey<?>) key;
            SemanticRegions<?> regions = extraction.regions(rk);
            SemanticRegion<?> region = regions.at(pos);
            if (region != null) {
                scrollTo(region);
            }
        } else if (key instanceof NamedRegionKey<?>) {
            NamedRegionKey<?> nrk = (NamedRegionKey<?>) key;
            NamedSemanticRegions<?> regions = ext.namedRegions(nrk);
            NamedSemanticRegion<?> region = regions.at(pos);
            if (region != null) {
                scrollTo(region);
            }
        }
    }

    @Override
    public void stateChanged(ChangeEvent e) {
        update();
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if ("caret".equals(evt.getPropertyName()) && evt.getNewValue() instanceof Caret) {
            if (listeningToCaret != null) {
                listeningToCaret.removeChangeListener(this);
                listeningToCaret = null;
            }
            listeningToCaret = (Caret) evt.getNewValue();
            listeningToCaret.addChangeListener(this);
        }
    }

    private void scrollTo(IndexAddressable.IndexAddressableItem region) {
        AWTEvent evt = EventQueue.getCurrentEvent();
        if (evt.getSource() != list) {
            ListModel<?> lm = list.getModel();
            int max = lm.getSize();
            int index = -1;
            for (int i = 0; i < max; i++) {
                Object item = lm.getElementAt(i);
                if (region.equals(item)) {
                    index = i;
                    break;
                }
            }
            if (index != -1) {
                int curr = list.getSelectedIndex();
                if (curr != index) {
                    Scroller.get(list).beginScroll(list, index);
                    list.setSelectedIndex(index);
                }
            }
        }
    }

}

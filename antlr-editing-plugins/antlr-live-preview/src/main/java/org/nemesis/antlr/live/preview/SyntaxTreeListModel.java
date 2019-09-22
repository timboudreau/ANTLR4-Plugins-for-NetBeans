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
package org.nemesis.antlr.live.preview;

import com.mastfrog.util.collections.CollectionUtils;
import java.awt.Color;
import java.awt.Component;
import java.awt.EventQueue;
import java.awt.Graphics;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.DefaultListSelectionModel;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.ListCellRenderer;
import javax.swing.ListModel;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import org.nemesis.antlr.live.preview.SyntaxTreeListModel.ModelEntry;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies.ErrorNodeTreeElement;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies.ParseTreeElement;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies.ParseTreeProxy;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies.ProxyToken;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies.RuleNodeTreeElement;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies.TerminalNodeTreeElement;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies.TokenAssociated;
import org.openide.windows.TopComponent;

/**
 *
 * @author Tim Boudreau
 */
final class SyntaxTreeListModel implements ListModel<ModelEntry> {

    private List<ModelEntry> entries = new ArrayList<>();
    private final List<ListDataListener> listeners = new ArrayList<>();

    private final DefaultListSelectionModel selectionModel
            = new DefaultListSelectionModel();

    SyntaxTreeListModel() {
        selectionModel.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    }

    ListSelectionModel selectionModel() {
        return selectionModel;
    }

    public int select(ParseTreeElement el) {
        for (int i = 0; i < entries.size(); i++) {
            ModelEntry me = entries.get(i);
            if (el.equals(me.el)) {
                selectionModel.setSelectionInterval(i, i);
                return i;
            }
        }
        return -1;
    }

    public JList<ModelEntry> createList() {
        JList<ModelEntry> list = new ParentCheckList(this);
        list.setSelectionModel(selectionModel);
        list.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        list.setCellRenderer(new MERenderer());
        return list;
    }

    public void listenForClicks(JList<ModelEntry> list, Supplier<List<ProxyToken>> supp, Consumer<int[]> consumer, BooleanSupplier disabled) {
        list.addMouseListener(new ME(supp, consumer, disabled));
    }

    final class ME extends MouseAdapter {

        private final Supplier<List<ProxyToken>> supp;
        private final Consumer<int[]> consumer;
        private final BooleanSupplier disabled;

        public ME(Supplier<List<ProxyToken>> supp, Consumer<int[]> consumer, BooleanSupplier disabled) {
            this.supp = supp;
            this.consumer = consumer;
            this.disabled = disabled;
        }

        @Override
        @SuppressWarnings("unchecked")
        public void mouseClicked(MouseEvent e) {
            if (disabled.getAsBoolean()) {
                return;
            }
            List<ProxyToken> toks = supp.get();
            if (toks.isEmpty()) {
                return;
            }
            JList<ModelEntry> list = (JList<ModelEntry>) e.getSource();
            int oldSel = list.getSelectedIndex();
            int index = list.locationToIndex(e.getPoint());
            if (index < 0 || index > list.getModel().getSize()) {
                return;
            }
            ModelEntry entry = list.getModel().getElementAt(index);
            if (entry != null) {
                int[] loc = entry.bounds(toks);
                if (loc.length == 2) {
                    consumer.accept(loc);
                }
            }
            list.setSelectedIndex(index);
            list.repaint(list.getCellBounds(oldSel < 0 ? index : oldSel, index));
        }
    }

    static class ParentCheckList extends JList<ModelEntry> {

        boolean parentFocused;

        public ParentCheckList(ListModel<ModelEntry> dataModel) {
            super(dataModel);
        }

        @Override
        public void paint(Graphics g) {
            TopComponent tc = (TopComponent) SwingUtilities.getAncestorOfClass(TopComponent.class, this);
            if (tc != null) {
                parentFocused = tc == TopComponent.getRegistry().getActivated();
            }
            super.paint(g);
        }

    }

    static class MERenderer implements ListCellRenderer<ModelEntry> {

        private final HtmlRendererImpl ren = new HtmlRendererImpl();

        @Override
        public Component getListCellRendererComponent(JList<? extends ModelEntry> list, ModelEntry value, int index, boolean isSelected, boolean cellHasFocus) {
            Component result = ren.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value.isError()) {
                ren.setHtml(true);
                ren.setText("<font color='!nb.errorColor'>" + value);
            } else if (value.isParserRule()) {
                ren.setHtml(true);
                ren.setText("<b>" + value);
            } else {
                ren.setHtml(true);
                ren.setText(value.toString());
            }
            ren.setForeground(list.getForeground());
            ren.setIndent(5 * value.depth());
            if (list instanceof ParentCheckList) {
                ren.setParentFocused(((ParentCheckList) list).parentFocused);
            }
            ren.setToolTipText(value.el.stringify());
            ren.setSelected(isSelected);
            ren.setLeadSelection(isSelected);
            if (isSelected) {
                ren.setCellBackground(list.getSelectionBackground());
            } else {
                ModelEntry sel = list.getSelectedValue();
                int dist = -1;
                if (sel != null) {
                    dist = sel.distanceFrom(value);
                }
                if (dist == -1) {
                    ren.setCellBackground(list.getBackground());
                } else {
                    ren.setCellBackground(backgroundFor(dist, list, sel.depth()));
                }
            }
            ((JComponent) result).setOpaque(true);
            return result;
        }

        private Color backgroundFor(int dist, JList<?> list, int totalDepth) {
            Color selBg = list.getSelectionBackground();
            Color bg = list.getBackground();
            float[] selBgHsb = new float[3];
            Color.RGBtoHSB(selBg.getRed(), selBg.getGreen(), selBg.getBlue(), selBgHsb);
            float[] bgHsb = new float[3];
            Color.RGBtoHSB(bg.getRed(), bg.getGreen(), bg.getBlue(), bgHsb);
            float[] changed = diff(selBgHsb, bgHsb, dist, totalDepth);
            return new Color(Color.HSBtoRGB(changed[0], changed[1], changed[2]));
        }

        private float[] diff(float[] a, float[] b, float dist, float of) {
            if (of == 0) {
                return a;
            }
            float frac = dist / of;
            float[] result = new float[a.length];
            for (int i=0; i < a.length; i++) {
                float av = a[i];
                float bv = b[i];
                float diff = (bv-av) * frac;
                result[i] = a[i] + diff;
            }
            return result;
        }
    }

    void update(ParseTreeProxy proxy) {
        if (EventQueue.isDispatchThread()) {
            doUpdate(proxy);
        } else {
            EventQueue.invokeLater(() -> {
                doUpdate(proxy);
            });
        }
    }

    int indexOf(ModelEntry en) {
        return entries.indexOf(en);
    }

    void doUpdate(ParseTreeProxy proxy) {
        ModelEntry selected = null;
        int sel = selectionModel.getLeadSelectionIndex();
        if (sel >= 0 && sel < entries.size()) {
            selected = entries.get(sel);
        }
        List<ParseTreeElement> els = proxy.allTreeElements();
        List<ModelEntry> newEntries = new ArrayList<>(els.size());
        for (ParseTreeElement el : proxy.parseTreeRoots()) {
            process(el, 0, newEntries);
        }
        int newSelected = -1;
        if (selected != null) {
            newSelected = newEntries.indexOf(selected);
        }
        List<ListDataEvent> events = new ArrayList<>(5);
        if (listeners.size() > 0) {
            diff(entries, newEntries, events);
            entries = newEntries;
            for (ListDataEvent evt : events) {
                for (ListDataListener l : listeners) {
                    switch (evt.getType()) {
                        case ListDataEvent.CONTENTS_CHANGED:
                            l.contentsChanged(evt);
                            break;
                        case ListDataEvent.INTERVAL_ADDED:
                            l.intervalAdded(evt);
                            break;
                        case ListDataEvent.INTERVAL_REMOVED:
                            l.intervalRemoved(evt);
                            break;
                    }
                }
            }
        } else {
            entries = newEntries;
        }
        if (newSelected >= 0) {
            selectionModel.setSelectionInterval(newSelected, newSelected);
        } else if (sel >= 0 && sel >= newEntries.size()) {
            selectionModel.setSelectionInterval(newEntries.size() - 1, newEntries.size() - 1);
        }
    }

    void process(ParseTreeElement el, int depth, List<ModelEntry> entries) {
        entries.add(new ModelEntry(el, depth));
        for (ParseTreeElement p : el) {
            process(p, depth + 1, entries);
        }
    }

    private void diff(List<ModelEntry> old, List<ModelEntry> nue, List<ListDataEvent> events) {
        if (old.equals(nue)) {
            return;
        }
        if (old.isEmpty()) {
            events.add(new ListDataEvent(this, ListDataEvent.INTERVAL_REMOVED, 0, old.size()));
            return;
        } else if (nue.isEmpty()) {
            events.add(new ListDataEvent(this, ListDataEvent.INTERVAL_ADDED, 0, nue.size()));
            return;
        }
        if (true) {
            events.add(new ListDataEvent(this, ListDataEvent.CONTENTS_CHANGED, 0, nue.size()));
        }
        Iterator<ModelEntry> oi = old.iterator();
        Iterator<ModelEntry> ni = nue.iterator();
        int diffStart = 0;
        while (oi.hasNext() && ni.hasNext()) {
            ModelEntry om = oi.next();
            ModelEntry nm = ni.next();
            if (!om.equals(nm)) {
                break;
            }
            diffStart++;
        }
        oi = CollectionUtils.reversed(old).iterator();
        ni = CollectionUtils.reversed(nue).iterator();
        int sameEndCount = 0;
        while (oi.hasNext() && ni.hasNext()) {
            ModelEntry om = oi.next();
            ModelEntry nm = ni.next();
            if (!om.equals(nm)) {
                break;
            }
            sameEndCount++;
        }
    }

    @Override
    public int getSize() {
        return entries.size();
    }

    @Override
    public ModelEntry getElementAt(int index) {
        return entries.get(index);
    }

    @Override
    public void addListDataListener(ListDataListener l) {
        listeners.add(l);
    }

    @Override
    public void removeListDataListener(ListDataListener l) {
        listeners.remove(l);
    }

    static final class ModelEntry {

        private final AntlrProxies.ParseTreeElement el;
        private final int depth;

        public ModelEntry(AntlrProxies.ParseTreeElement el, int depth) {
            this.el = el;
            this.depth = depth;
        }

        public int[] bounds(List<ProxyToken> tokens) {
            if (el instanceof TokenAssociated) {
                TokenAssociated e = (TokenAssociated) el;
                int startToken = e.startTokenIndex();
                int endToken = e.endTokenIndex();
                if (startToken >= 0 && startToken < tokens.size() && endToken >= 0 && endToken < tokens.size()) {
                    ProxyToken start = tokens.get(startToken);
                    ProxyToken end = tokens.get(endToken);
                    int[] result = new int[]{start.getStartIndex(), end.getEndIndex()};
                    if (result[0] < 0 || result[1] < 0 || result[1] < result[0]) {
                        return new int[0];
                    }
                    return result;
                }
            }
            return new int[0];
        }

        int distanceFrom(ModelEntry other) {
            if (other == this) {
                return 0;
            }
            AntlrProxies.ParseTreeElement o = other.el;
            int result = -1;
            AntlrProxies.ParseTreeElement curr = el;
            int dist = 0;
            while (curr != null) {
                if (curr == o) {
                    result = dist;
                    break;
                }
                curr = curr.parent();
                dist++;
            }
            return result;
        }

        public int depth() {
            return depth;
        }

        public boolean isError() {
            return el instanceof ErrorNodeTreeElement;
        }

        public boolean isParserRule() {
            return el instanceof RuleNodeTreeElement;
        }

        @Override
        public String toString() {
            if (el instanceof AntlrProxies.RuleNodeTreeElement) {
                RuleNodeTreeElement r = (RuleNodeTreeElement) el;
                return r.name();
            } else if (el instanceof AntlrProxies.TerminalNodeTreeElement) {
                TerminalNodeTreeElement t = (TerminalNodeTreeElement) el;
                return t.stringify();
            } else if (el instanceof ErrorNodeTreeElement) {
                ErrorNodeTreeElement e = (ErrorNodeTreeElement) el;
                return e.stringify();
            } else {
                return el.toString();
            }
        }

        public boolean equals(Object o) {
            return o instanceof ModelEntry
                    && ((ModelEntry) o).depth == depth
                    && toString().equals(o.toString());
        }

        public int hashCode() {
            return (depth * 4) + toString().hashCode();
        }
    }
}

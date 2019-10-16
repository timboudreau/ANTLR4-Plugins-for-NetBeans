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
import org.nemesis.antlr.live.parsing.extract.AntlrProxies.ProxyTokenType;
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
            ren.setToolTipText(value.tooltip());
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
            for (int i = 0; i < a.length; i++) {
                float av = a[i];
                float bv = b[i];
                float diff = (bv - av) * frac;
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
            process(proxy, el, 0, newEntries);
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

    void process(ParseTreeProxy proxy, ParseTreeElement el, int depth, List<ModelEntry> entries) {
        entries.add(new ModelEntry(el, depth, proxy));
        for (ParseTreeElement p : el) {
            process(proxy, p, depth + 1, entries);
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
        private String tooltip;

        public ModelEntry(AntlrProxies.ParseTreeElement el, int depth, ParseTreeProxy proxy) {
            this.el = el;
            this.depth = depth;
            tooltip = tooltip(proxy.tokens(), proxy.tokenTypes());
        }

        public String tooltip() {
            return tooltip;
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

        String tooltip(List<ProxyToken> tokens, List<ProxyTokenType> types) {
            if (el instanceof AntlrProxies.TerminalNodeTreeElement) {
                TerminalNodeTreeElement t = (TerminalNodeTreeElement) el;
                ProxyToken tok = tokens.get(t.startTokenIndex());
                int type = tok.getType();
                return types.get(type + 1).name() + " - " + el.depth();
            }
            return el.stringify() + " - " + depth;
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

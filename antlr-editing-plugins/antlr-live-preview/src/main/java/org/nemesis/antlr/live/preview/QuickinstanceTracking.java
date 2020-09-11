/*
 * Copyright 2016-2020 Tim Boudreau, Frédéric Yvon Vinet
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
import java.awt.BorderLayout;
import java.awt.EventQueue;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.Timer;
import javax.swing.border.Border;
import javax.swing.table.DefaultTableModel;
import org.nemesis.debug.api.Metrics;
import org.nemesis.debug.api.Trackables;
import org.nemesis.debug.api.Trackables.TrackingReference;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import org.openide.util.NbBundle.Messages;
import org.openide.windows.Mode;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;

@ActionID(
        category = "Tools",
        id = "org.nemesis.antlr.live.preview.QuickinstanceTracking"
)
@ActionRegistration(
        displayName = "#CTL_QuickinstanceTracking"
)
@ActionReference(path = "Menu/Tools", position = 185)
@Messages("CTL_QuickinstanceTracking=Antlr Instance Debug Tracking")
public final class QuickinstanceTracking implements ActionListener {

    private static boolean active;
    private static TC tc;

    @Override
    public void actionPerformed(ActionEvent e) {
        if (tc != null) {
            tc.close();
            tc = null;
        } else {
            tc = new TC();
            tc.open();
            tc.requestVisible();
        }
    }

    @Messages({
        "typeName=Type",
        "instances=Instances",
        "collected=Collected",})
    static final class TC extends TopComponent implements Consumer<Set<? extends TrackingReference<?>>>, ActionListener {

        private final DefaultTableModel mdl = new DefaultTableModel();
        private final Set<TrackingReference<?>> all = new HashSet<>();
        private final Map<Class<?>, Integer> collected = CollectionUtils.supplierMap(() -> 0);
        private final Map<Class<?>, Integer> history = new HashMap<>();
        private final Timer timer = new Timer(150000, this);
        private final Map<String, Object> metrics = new TreeMap<>();

        TC() {
            mdl.addColumn(Bundle.typeName());
            mdl.addColumn(Bundle.instances());
            mdl.addColumn(Bundle.collected());
            setLayout(new BorderLayout());
            JTable table = new JTable(mdl);
            JScrollPane scroll = new JScrollPane(table);
            Border empty = BorderFactory.createEmptyBorder();
            table.setBorder(empty);
            scroll.setBorder(empty);
            scroll.setViewportBorder(empty);
            add(scroll, BorderLayout.CENTER);
            setDisplayName(Bundle.instances());
        }

        @Override
        public void open() {
            Mode m = WindowManager.getDefault().findMode("properties");
            if (m != null) {
                m.dockInto(this);
            }
            super.open();
        }

        private boolean setTableMetric(String name, Object metric) {
            for (int i = 0; i < mdl.getRowCount(); i++) {
                String label = (String) mdl.getValueAt(i, 0);
                if (name.equals(label)) {
                    mdl.setValueAt(metric, i, 1);
                    return true;
                }
            }
            return false;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            Metrics.poll((k, v) -> {
                if (!setTableMetric(k, v)) {
                    mdl.addRow(new Object[]{k, v});
                }
                metrics.put(k, v);
            });
        }

        @Override
        public void accept(Set<? extends TrackingReference<?>> t) {
            if (!active) {
                return;
            }
            EventQueue.invokeLater(() -> {
                all.addAll(t);
                for (Iterator<TrackingReference<?>> it = all.iterator(); it.hasNext();) {
                    TrackingReference<?> tr = it.next();
                    if (!tr.isAlive()) {
                        it.remove();
                        collected.put(tr.type(), collected.get(tr.type()) + 1);
                    }
                }
                if (active) {
                    Map<Class<?>, Integer> m = CollectionUtils.supplierMap(() -> 0);
                    for (TrackingReference<?> tr : all) {
                        Integer val = m.get(tr.type()) + 1;
                        m.put(tr.type(), val);
                    }
                    Set<Class<?>> absent = new HashSet<>(history.keySet());
                    absent.removeAll(m.keySet());
                    history.putAll(m);
                    for (Class<?> k : absent) {
                        history.put(k, 0);
                    }
                    List<Map.Entry<Class<?>, Integer>> all = new ArrayList<>(history.entrySet());
                    Collections.sort(all, (a, b) -> {
                        return a.getKey().getSimpleName().compareToIgnoreCase(
                                b.getKey().getSimpleName());
                    });
                    // Ensure types not in the current collection we're being
                    // notified of don't disappear
                    for (int i = mdl.getRowCount() - 1; i >= 0; i--) {
                        mdl.removeRow(i);
                    }
                    for (Map.Entry<Class<?>, Integer> item : all) {
                        Integer collectedOfType = collected.get(item.getKey());
                        mdl.addRow(new Object[]{item.getKey().getSimpleName(),
                            item.getValue(), collectedOfType});
                    }
                    for (Map.Entry<String, Object> e : metrics.entrySet()) {
                        mdl.addRow(new Object[]{e.getKey(), e.getValue()});
                    }
                }
            });
        }

        @Override
        protected void componentOpened() {
            active = true;
            Trackables.listen(this);
            actionPerformed(null);
            timer.start();
        }

        @Override
        protected void componentClosed() {
            timer.stop();
            try {
                Trackables.unlisten(this);
                all.clear();
                collected.clear();
                for (int i = mdl.getRowCount() - 1; i >= 0; i--) {
                    mdl.removeRow(i);
                }
            } finally {
                active = false;
            }
        }

        @Override
        public int getPersistenceType() {
            return TopComponent.PERSISTENCE_NEVER;
        }
    }
}

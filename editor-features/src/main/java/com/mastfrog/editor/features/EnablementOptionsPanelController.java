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
package com.mastfrog.editor.features;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import static com.mastfrog.editor.features.EditorFeatureEnablementModelImpl.NO_CATEGORY;
import org.netbeans.api.editor.mimelookup.MimeLookup;
import org.netbeans.spi.options.OptionsPanelController;
import org.openide.awt.Mnemonics;
import org.openide.util.HelpCtx;
import org.openide.util.Lookup;
import org.openide.util.Utilities;

final class EnablementOptionsPanelController extends OptionsPanelController {

    private JPanel panel;
    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
    private final String mimeType;

    public EnablementOptionsPanelController(String mimeType) {
        this.mimeType = mimeType;
    }

    @Override
    public void update() {
        // do nothing
    }

    @Override
    public void applyChanges() {
        if (commit != null) {
            EventQueue.invokeLater(commit);
        }
    }

    @Override
    public void cancel() {
        // need not do anything special, if no changes have been persisted yet
    }

    @Override
    public boolean isValid() {
        return true;
    }

    @Override
    public boolean isChanged() {
        return changedTest == null ? false : changedTest.getAsBoolean();
    }

    @Override
    public HelpCtx getHelpCtx() {
        return null; // new HelpCtx("...ID") if you have a help set
    }

    @Override
    public JComponent getComponent(Lookup masterLookup) {
        return getPanel();
    }

    @Override
    public void addPropertyChangeListener(PropertyChangeListener l) {
        pcs.addPropertyChangeListener(l);
    }

    @Override
    public void removePropertyChangeListener(PropertyChangeListener l) {
        pcs.removePropertyChangeListener(l);
    }

    private JPanel getPanel() {
        if (panel == null) {
            panel = createPanel();
        }
        return panel;
    }

    void changed() {
        if (changedTest != null && changedTest.getAsBoolean()) {
            pcs.firePropertyChange(OptionsPanelController.PROP_CHANGED, false, true);
        }
        pcs.firePropertyChange(OptionsPanelController.PROP_VALID, null, null);
    }

    private JPanel createPanel() {
        EditorFeatures features = MimeLookup.getLookup(mimeType)
                .lookup(EditorFeatures.class);
        if (features != null) {
            return populatePanel(features, this::changed, (check, commit) -> {
                this.commit = commit;
                this.changedTest = check;
            });
        }
        return new JPanel();
    }

    public static JPanel populatePanel(EditorFeatures features, Runnable changeCallback, BiConsumer<BooleanSupplier, Runnable> cons) {
        List<EditorFeatureEnablementModel> all
                = features.enablableItems();
//        JPanel pnl = new JPanel(new GridBagLayout());
        JPanel pnl = new JPanel(new ColumnarLayout());
        pnl.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        if (!all.isEmpty()) {
            Collections.sort(all, (a, b) -> {
                return a.name().compareToIgnoreCase(b.name());
            });
            Map<String, List<EditorFeatureEnablementModel>> byCategory
                    = new TreeMap<>();
            int maxCategorySize = 0;
            for (EditorFeatureEnablementModel em : all) {
                String cat = em.category();
                if (cat == null) {
                    cat = NO_CATEGORY;
                }
                List<EditorFeatureEnablementModel> l = byCategory.get(cat);
                if (l == null) {
                    l = new ArrayList<>(all.size());
                    byCategory.put(cat, l);
                }
                l.add(em);
                maxCategorySize = Math.max(maxCategorySize, l.size());
            }
            for (Map.Entry<String, List<EditorFeatureEnablementModel>> en : byCategory.entrySet()) {
                String cat = NO_CATEGORY.equals(en.getKey()) ? "Features"
                        : en.getKey();
                boolean firstInCategory = true;
                for (Iterator<EditorFeatureEnablementModel> it = en.getValue().iterator(); it.hasNext();) {
                    if (firstInCategory) {
                        firstInCategory = false;
                        JLabel label = new JLabel(cat);
                        label.setVerticalTextPosition(SwingConstants.BOTTOM);
                        label.setVerticalAlignment(SwingConstants.BOTTOM);
                        Font f = label.getFont();
                        if (f != null) {
                            f = UIManager.getFont("controlFont");
                            if (f != null) {
                                label.setFont(f.deriveFont(Font.BOLD));
                            }
                        }
                        label.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, label.getForeground()));
                        pnl.add(label);
                    }
                    EditorFeatureEnablementModel e = it.next();
                    JCheckBox box = new JCheckBox();
                    box.setSelected(e.isEnabled());
                    Mnemonics.setLocalizedText(box, e.name());
                    String desc = e.description();
                    if (desc != null) {
                        box.setToolTipText(desc);
                    }
                    pnl.add(box);
                    box.addActionListener(new L(e, changeCallback));
                }
            }
            BooleanSupplier changedTest = () -> {
                for (EditorFeatureEnablementModel e : all) {
                    if (e.isChanged()) {
                        return true;
                    }
                }
                return false;
            };
            Runnable commit = () -> {
                for (EditorFeatureEnablementModel e : all) {
                    e.commit();
                }
            };
            cons.accept(changedTest, commit);
        } else {
            cons.accept(() -> false, () -> {
            });
        }
        return pnl;
    }
    private BooleanSupplier changedTest;
    private Runnable commit;

    private static final class L implements ActionListener {

        private final EditorFeatureEnablementModel mdl;
        private final Runnable onChange;

        public L(EditorFeatureEnablementModel mdl, Runnable onChange) {
            this.mdl = mdl;
            this.onChange = onChange;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            JCheckBox box = (JCheckBox) e.getSource();
            mdl.setEnabled(box.isSelected());
            onChange.run();
        }
    }

    /*
    public static void main(String[] args) {
        System.setProperty("swing.aatext", "true");
        System.setProperty("awt.useSystemAAFontSettings", "lcd_hrgb");
        JFrame jf = new JFrame();
        jf.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        JPanel pnl = EnablementOptionsPanelController.populatePanel(TestImpl.instance(), () -> {
        }, (a, b) -> {
        });
//        jf.setContentPane(new JScrollPane(pnl));
        jf.setContentPane(pnl);
        jf.pack();
        jf.setVisible(true);
    }
     */

    private static final class ColumnarLayout implements LayoutManager {

        private final int indent = Utilities.isMac() ? 16 : 12;
        private boolean needJustify;
        private Dimension lastSize = new Dimension(12000, 12000);
        private final List<List<Component>> compGroups = new ArrayList<>(48);
        private final List<Rectangle> groupBounds = new ArrayList<>();

        private void refreshGroups(Container container) {
            Insets ins = container.getInsets();
            compGroups.clear();
            List<Component> curr = new ArrayList<>(16);
            groupBounds.clear();
            int w = 0;
            int h = 0;
            int maxW = 0;
            for (Component c : container.getComponents()) {
                int adjustWidth = 0;
                int adjustHeight = 0;
                if (c instanceof JLabel) {
                    adjustWidth = indent;
                    adjustHeight = indent * 3;
                    if (!curr.isEmpty()) {
                        compGroups.add(curr);
                        groupBounds.add(new Rectangle(ins.left, ins.top, w, h));
                        curr = new ArrayList<>(16);
                        h = indent;
                    }
                }
                curr.add(c);
                Dimension d = c.getPreferredSize();
                int currw = d.width + adjustWidth;
                w = Math.max(w, currw);
                maxW = Math.max(w, maxW);
                h += d.height + adjustHeight;
            }
            System.out.println("hey");
            if (!curr.isEmpty()) {
                compGroups.add(curr);
                groupBounds.add(new Rectangle(ins.left, ins.top, w, h));
            }
            int x = ins.left;
            int y = ins.top;
            int bottom = Math.max(16, lastSize.height - (y + ins.bottom));
            int gap = 0;
            int totalWidth = 0;
            for (Rectangle r : groupBounds) {
                totalWidth += r.width;
            }
            if (lastSize.width > totalWidth) {
                gap = (lastSize.width - totalWidth) / groupBounds.size();
            }
            for (int i = 0; i < groupBounds.size(); i++) {
                Rectangle r = groupBounds.get(i);
                if (i > 0 && y + r.height > bottom) {
                    y = ins.top;
                    x += groupBounds.get(i - 1).width + Math.max((indent * 2), gap);
                }
                r.x = x;
                r.y = y;
                y += r.height;
            }
        }

        @Override
        public void layoutContainer(Container parent) {
            checkJustify(parent);
            Iterator<Rectangle> iter = groupBounds.iterator();
            Rectangle curr = null;
            Insets ins = parent.getInsets();
            int x = ins.left;
            int y = ins.top;
            for (Component c : parent.getComponents()) {
                int adjustx = 0;
                int adjustheight = 0;
                if (curr == null || c instanceof JLabel) {
                    if (iter.hasNext()) {
                        curr = iter.next();
                        x = curr.x;
                        y = curr.y;
                    }
                    adjustheight = 0;
                } else {
                    adjustx = indent;
                }
                Dimension d = c.getPreferredSize();
                c.setBounds(x + adjustx, y, d.width, d.height + adjustheight);
                y += adjustheight + d.height;
                if (c instanceof JLabel) {
                    y += indent;
                }
            }
        }

        private void justify(Container container) {
            refreshGroups(container);
        }

        private void checkJustify(Container container) {
            boolean doit = needJustify;
            if (!doit) {
                Dimension currSize = container.getSize();
                if (!currSize.equals(lastSize)) {
                    lastSize = currSize;
                    doit = true;
                }
            }
            if (doit) {
                needJustify = false;
                justify(container);
            }
        }

        @Override
        public void addLayoutComponent(String name, Component comp) {
            needJustify = true;
        }

        @Override
        public void removeLayoutComponent(Component comp) {
            needJustify = true;
        }

        @Override
        public Dimension preferredLayoutSize(Container parent) {
            checkJustify(parent);
            Rectangle result = new Rectangle();
            for (Rectangle r : groupBounds) {
                result.add(r);
            }
            System.out.println("PLS " + result);
            return result.getSize();
        }

        @Override
        public Dimension minimumLayoutSize(Container parent) {
            return preferredLayoutSize(parent);
        }
    }
}

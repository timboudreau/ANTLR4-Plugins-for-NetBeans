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
package org.nemesis.antlr.file.editor.ext;

import java.awt.EventQueue;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
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
import java.util.function.BooleanSupplier;
import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.UIManager;
import static org.nemesis.antlr.common.AntlrConstants.ANTLR_MIME_TYPE;
import static org.nemesis.antlr.file.editor.ext.EditorFeatureEnablementModelImpl.NO_CATEGORY;
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

    public EnablementOptionsPanelController() {
        this(ANTLR_MIME_TYPE);
    }

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
        JPanel pnl = new JPanel(new GridBagLayout());
        EditorFeatures features = MimeLookup.getLookup(mimeType)
                .lookup(EditorFeatures.class);
        if (features != null) {
            List<EditorFeatureEnablementModel> all
                    = features.enablableItems();
            if (!all.isEmpty()) {
                Collections.sort(all, (a, b) -> {
                    return a.name().compareToIgnoreCase(b.name());
                });
                Map<String, List<EditorFeatureEnablementModel>> byCategory
                        = new TreeMap<>();
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
                }
                GridBagConstraints c = new GridBagConstraints();
                c.anchor = GridBagConstraints.BASELINE_LEADING;
                c.weightx = 1;
                c.fill = GridBagConstraints.BOTH;
                c.gridwidth = 1;
                c.gridheight = 1;
                c.gridx = 0;
                c.gridy = 0;
                int ins = Utilities.isMac() ? 12 : 5;
                c.insets = new Insets(ins, ins, ins, 0);
                int cumulativeComps = 0;
                for (Map.Entry<String, List<EditorFeatureEnablementModel>> en : byCategory.entrySet()) {
                    boolean uncategorized = NO_CATEGORY.equals(en.getKey());
                    boolean firstInCategory = true;
                    for (Iterator<EditorFeatureEnablementModel> it = en.getValue().iterator(); it.hasNext();) {
                        c.weightx = 0;
                        c.insets.bottom = 0;
                        c.insets.left = ins;
                        if (!uncategorized && firstInCategory) {
                            firstInCategory = false;
                            JLabel label = new JLabel(en.getKey());
                            Font f = label.getFont();
                            if (f != null) {
                                f = UIManager.getFont("controlFont");
                                if (f != null) {
                                    label.setFont(f.deriveFont(Font.BOLD));
                                }
                            }
                            label.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, label.getForeground()));
                            pnl.add(label, c);
                            cumulativeComps++;
                            c.gridy++;
                            c.insets.left = ins * 2;
                        }
                        
                        EditorFeatureEnablementModel e = it.next();
                        JCheckBox box = new JCheckBox();
                        box.setSelected(e.isEnabled());
                        Mnemonics.setLocalizedText(box, e.name());
                        String desc = e.description();
                        if (desc != null) {
                            box.setToolTipText(desc);
                        }
                        if (!it.hasNext()) {
                            c.insets.bottom = ins;
                        }
                        pnl.add(box, c);
                        cumulativeComps++;
                        c.gridy++;
                        box.addActionListener(new L(e, this));
                        if (!it.hasNext()) {
                            c.weighty = 1;
                            c.gridy++;
                            // Spacer to force items to the top
                            // of the layout
                            pnl.add(new JPanel(), c);
                            if (cumulativeComps > all.size() / 2) {
                                c.gridy = 0;
                                c.gridx += 1;
                            }
                        }
                    }
                }
                changedTest = () -> {
                    for (EditorFeatureEnablementModel e : all) {
                        if (e.isChanged()) {
                            return true;
                        }
                    }
                    return false;
                };
                commit = () -> {
                    for (EditorFeatureEnablementModel e : all) {
                        e.commit();
                    }
                };
            } else {
                changedTest = () -> false;
                commit = () -> {
                };
            }
        }
        return pnl;
    }
    private BooleanSupplier changedTest;
    private Runnable commit;

    private static final class L implements ActionListener {

        private final EditorFeatureEnablementModel mdl;
        private final EnablementOptionsPanelController controller;

        public L(EditorFeatureEnablementModel mdl, EnablementOptionsPanelController ctrller) {
            this.mdl = mdl;
            this.controller = ctrller;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            JCheckBox box = (JCheckBox) e.getSource();
            mdl.setEnabled(box.isSelected());
            controller.changed();
        }
    }
}

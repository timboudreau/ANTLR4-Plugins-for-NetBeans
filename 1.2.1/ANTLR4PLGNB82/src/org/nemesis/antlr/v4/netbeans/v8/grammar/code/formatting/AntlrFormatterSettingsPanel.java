package org.nemesis.antlr.v4.netbeans.v8.grammar.code.formatting;

import javax.swing.JPanel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.formatting.AntlrFormatterSettings.NewlineStyle;
import org.openide.util.ChangeSupport;

/**
 *
 * @author Tim Boudreau
 */
public final class AntlrFormatterSettingsPanel extends JPanel implements ChangeListener {

    private final AntlrFormatterSettings settings;
    private final AntlrFormatterSettings orig;
    private final ChangeSupport supp = new ChangeSupport(this);

    public AntlrFormatterSettingsPanel() {
        this(AntlrFormatterSettings.getDefault());
    }

    public AntlrFormatterSettingsPanel(AntlrFormatterSettings settings) {
        initComponents();
        this.settings = settings;
        this.orig = settings.copy();
    }

    @Override
    public void addNotify() {
        super.addNotify();
        refresh();
        settings.addChangeListener(this);
    }

    @Override
    public void removeNotify() {
        settings.removeChangeListener(this);
        super.removeNotify();
    }

    public boolean isModified() {
        return !orig.equals(settings);
    }

    private void fire() {
        supp.fireChange();
    }

    public void addChangeListener(ChangeListener listener) {
        supp.addChangeListener(listener);
    }

    public void removeChangeListener(ChangeListener listener) {
        supp.removeChangeListener(listener);
    }

    private boolean refreshing;

    void refresh() {
        refreshing = true;
        try {
            newlineStyleBox.setSelectedItem(settings.getNewlineStyle());
            wrapLinesBox.setSelected(settings.isWrapLines());
            wrapLinesSpinner.setValue(settings.getWrapPoint());
            indentSizeSpinner.setValue(settings.getIndentSize());
            newlineBox.setSelected(settings.isNewlineAfterColon());
            wrapLinesSpinner.setEnabled(settings.isWrapLines());
        } finally {
            refreshing = false;
        }
    }

    @Override
    public void stateChanged(ChangeEvent e) {
        refresh();
    }

    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        newlineBox = new javax.swing.JCheckBox();
        wrapLinesBox = new javax.swing.JCheckBox();
        wrapLinesSpinner = new javax.swing.JSpinner();
        indentSizeLabel = new javax.swing.JLabel();
        indentSizeSpinner = new javax.swing.JSpinner();
        newlineStyleLabel = new javax.swing.JLabel();
        newlineStyleBox = new javax.swing.JComboBox<>();
        reflowBox = new javax.swing.JCheckBox();

        org.openide.awt.Mnemonics.setLocalizedText(newlineBox, org.openide.util.NbBundle.getMessage(AntlrFormatterSettingsPanel.class, "AntlrFormatterSettingsPanel.newlineBox.text")); // NOI18N
        newlineBox.setToolTipText(org.openide.util.NbBundle.getMessage(AntlrFormatterSettingsPanel.class, "AntlrFormatterSettingsPanel.newlineBox.toolTipText")); // NOI18N
        newlineBox.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                separateLineChanged(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(wrapLinesBox, org.openide.util.NbBundle.getMessage(AntlrFormatterSettingsPanel.class, "AntlrFormatterSettingsPanel.wrapLinesBox.text")); // NOI18N
        wrapLinesBox.setToolTipText(org.openide.util.NbBundle.getMessage(AntlrFormatterSettingsPanel.class, "AntlrFormatterSettingsPanel.wrapLinesBox.toolTipText")); // NOI18N
        wrapLinesBox.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                wrapBoxChanged(evt);
            }
        });

        wrapLinesSpinner.setModel(new javax.swing.SpinnerNumberModel(80, 8, null, 1));
        wrapLinesSpinner.setToolTipText(org.openide.util.NbBundle.getMessage(AntlrFormatterSettingsPanel.class, "AntlrFormatterSettingsPanel.wrapLinesSpinner.toolTipText")); // NOI18N
        wrapLinesSpinner.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                wrapPointChanged(evt);
            }
        });

        indentSizeLabel.setLabelFor(indentSizeSpinner);
        org.openide.awt.Mnemonics.setLocalizedText(indentSizeLabel, org.openide.util.NbBundle.getMessage(AntlrFormatterSettingsPanel.class, "AntlrFormatterSettingsPanel.indentSizeLabel.text")); // NOI18N
        indentSizeLabel.setToolTipText(org.openide.util.NbBundle.getMessage(AntlrFormatterSettingsPanel.class, "AntlrFormatterSettingsPanel.indentSizeLabel.toolTipText")); // NOI18N

        indentSizeSpinner.setModel(new javax.swing.SpinnerNumberModel(4, 1, 32, 1));
        indentSizeSpinner.setToolTipText(org.openide.util.NbBundle.getMessage(AntlrFormatterSettingsPanel.class, "AntlrFormatterSettingsPanel.indentSizeSpinner.toolTipText")); // NOI18N
        indentSizeSpinner.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                indentSizeChanged(evt);
            }
        });

        newlineStyleLabel.setLabelFor(newlineStyleBox);
        org.openide.awt.Mnemonics.setLocalizedText(newlineStyleLabel, org.openide.util.NbBundle.getMessage(AntlrFormatterSettingsPanel.class, "AntlrFormatterSettingsPanel.newlineStyleLabel.text")); // NOI18N
        newlineStyleLabel.setToolTipText(org.openide.util.NbBundle.getMessage(AntlrFormatterSettingsPanel.class, "AntlrFormatterSettingsPanel.newlineStyleLabel.toolTipText")); // NOI18N

        newlineStyleBox.setModel(AntlrFormatterSettings.NewlineStyle.listModel());
        newlineStyleBox.setToolTipText(org.openide.util.NbBundle.getMessage(AntlrFormatterSettingsPanel.class, "AntlrFormatterSettingsPanel.newlineStyleBox.toolTipText")); // NOI18N
        newlineStyleBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                blankLinePolicyChanged(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(reflowBox, org.openide.util.NbBundle.getMessage(AntlrFormatterSettingsPanel.class, "AntlrFormatterSettingsPanel.reflowBox.text")); // NOI18N
        reflowBox.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                reflowBoxChanged(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGap(12, 12, 12)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(newlineStyleLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 151, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(12, 12, 12)
                        .addComponent(newlineStyleBox, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(indentSizeLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 151, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(12, 12, 12)
                        .addComponent(indentSizeSpinner)))
                .addGap(12, 12, 12))
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(wrapLinesBox, javax.swing.GroupLayout.PREFERRED_SIZE, 159, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(wrapLinesSpinner))
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(newlineBox, javax.swing.GroupLayout.DEFAULT_SIZE, 490, Short.MAX_VALUE)
                        .addGap(4, 4, 4)))
                .addContainerGap())
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(reflowBox, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGap(12, 12, 12)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addGap(5, 5, 5)
                        .addComponent(newlineStyleLabel))
                    .addComponent(newlineStyleBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(12, 12, 12)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addGap(2, 2, 2)
                        .addComponent(indentSizeLabel))
                    .addComponent(indentSizeSpinner, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(wrapLinesSpinner, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(wrapLinesBox))
                .addGap(18, 18, 18)
                .addComponent(newlineBox)
                .addGap(18, 18, 18)
                .addComponent(reflowBox)
                .addContainerGap(63, Short.MAX_VALUE))
        );

        layout.linkSize(javax.swing.SwingConstants.VERTICAL, new java.awt.Component[] {indentSizeSpinner, newlineStyleBox, wrapLinesSpinner});

    }// </editor-fold>//GEN-END:initComponents

    private void blankLinePolicyChanged(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_blankLinePolicyChanged
        if (refreshing) {
            return;
        }
        boolean wasModified = isModified();
        NewlineStyle style = (NewlineStyle) newlineStyleBox.getSelectedItem();
        settings.setBlankLineAfterRule(style);
        if (isModified() != wasModified) {
            fire();
        }
    }//GEN-LAST:event_blankLinePolicyChanged

    private void indentSizeChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_indentSizeChanged
        if (refreshing) {
            return;
        }
        boolean wasModified = isModified();
        int indent = (Integer) indentSizeSpinner.getValue();
        settings.setIndentSize(indent);
        if (isModified() != wasModified) {
            fire();
        }
    }//GEN-LAST:event_indentSizeChanged

    private void wrapPointChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_wrapPointChanged
        if (refreshing) {
            return;
        }
        boolean wasModified = isModified();
        int wrapPoint = (Integer) wrapLinesSpinner.getValue();
        settings.setWrapPoint(wrapPoint);
        if (isModified() != wasModified) {
            fire();
        }
    }//GEN-LAST:event_wrapPointChanged

    private void wrapBoxChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_wrapBoxChanged
        if (refreshing) {
            return;
        }
        boolean wasModified = isModified();
        boolean wrap = wrapLinesBox.isSelected();
        settings.setWrapLines(wrap);
        if (isModified() != wasModified) {
            fire();
        }
    }//GEN-LAST:event_wrapBoxChanged

    private void separateLineChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_separateLineChanged
        if (refreshing) {
            return;
        }
        boolean wasModified = isModified();
        boolean separateLine = newlineBox.isSelected();
        settings.setNewlineAfterColon(separateLine);
        if (isModified() != wasModified) {
            fire();
        }
    }//GEN-LAST:event_separateLineChanged

    private void reflowBoxChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_reflowBoxChanged
        boolean wasModified = isModified();
        boolean reflow = reflowBox.isSelected();
        settings.setReflowBlockComments(reflow);
        if (isModified() != wasModified) {
            fire();
        }
    }//GEN-LAST:event_reflowBoxChanged

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel indentSizeLabel;
    private javax.swing.JSpinner indentSizeSpinner;
    private javax.swing.JCheckBox newlineBox;
    private javax.swing.JComboBox<AntlrFormatterSettings.NewlineStyle> newlineStyleBox;
    private javax.swing.JLabel newlineStyleLabel;
    private javax.swing.JCheckBox reflowBox;
    private javax.swing.JCheckBox wrapLinesBox;
    private javax.swing.JSpinner wrapLinesSpinner;
    // End of variables declaration//GEN-END:variables
}

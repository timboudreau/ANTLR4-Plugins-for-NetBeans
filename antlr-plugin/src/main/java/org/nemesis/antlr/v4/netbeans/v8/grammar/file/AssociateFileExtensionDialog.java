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
package org.nemesis.antlr.v4.netbeans.v8.grammar.file;

import java.awt.EventQueue;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.function.BiConsumer;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.preview.AdhocDataObject;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.preview.AdhocMimeTypes;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.preview.InvalidMimeTypeRegistrationException;
import org.openide.DialogDescriptor;
import org.openide.DialogDisplayer;
import org.openide.awt.Mnemonics;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataLoader;
import org.openide.loaders.DataLoaderPool;
import org.openide.loaders.DataObject;
import org.openide.util.HelpCtx;

/**
 *
 * @author Tim Boudreau
 */

public class AssociateFileExtensionDialog extends JPanel
        implements BiConsumer<String, String>, ActionListener, DocumentListener {

    private final DataObject obj;

    /**
     * Creates new form AssociateFileExtensionDialog
     */
    public AssociateFileExtensionDialog(DataObject obj) {
        this.obj = obj;
        initComponents();
        Mnemonics.setLocalizedText(fieldLabel, Bundle.fileExtension());
        Mnemonics.setLocalizedText(addButton, Bundle.add());
        associationsPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        associationsPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
        errorsPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        errorsPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
    }

    public static void showDialog(DataObject dob) {
        DialogDescriptor dlg = new DialogDescriptor(new AssociateFileExtensionDialog(dob), Bundle.associationsDialog(), true,
                new Object[]{DialogDescriptor.CLOSED_OPTION}, DialogDescriptor.CLOSED_OPTION,
                DialogDescriptor.DEFAULT_ALIGN, HelpCtx.DEFAULT_HELP, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                // do nothing
            }
        });
        DialogDisplayer.getDefault().notify(dlg);
    }

    public void addNotify() {
        super.addNotify();
        updateAssociations();
        AdhocMimeTypes.listenForRegistrations(this); // is a weak reference
        addButton.addActionListener(this);
        field.getDocument().addDocumentListener(this);
        field.requestFocus();
    }

    public void removeNotify() {
        addButton.removeActionListener(this);
        field.getDocument().removeDocumentListener(this);
        super.removeNotify();
    }

    private String mimeType() {
//            return obj.getPrimaryFile().getMIMEType();
        File file = FileUtil.toFile(obj.getPrimaryFile());
        Path path;
        if (file == null) {
            path = Paths.get("/nonexistent.g4");
        } else {
            path = file.toPath();
        }
        return AdhocMimeTypes.mimeTypeForPath(path);
    }

    private void updateAssociations() {
        StringBuilder sb = new StringBuilder();
        List<String> all = new ArrayList<>(AdhocMimeTypes.allExtensionsForMimeType(mimeType()));
        Collections.sort(all);
        for (String ext : all) {
            if (ext.startsWith(AdhocMimeTypes.FILE_EXTENSION_PREFIX)) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(ext);
        }
        if (sb.length() > 0) {
            associations.setText(sb.toString());
        } else {
            associations.setText(Bundle.noAssociations());
        }
        if (!field.getText().trim().isEmpty()) {
            addButton.setEnabled(validExtension());
        }
    }

    public void accept(String ext, String mime) {
        if (mimeType().equals(mime) && isShowing()) {
            EventQueue.invokeLater(this::updateAssociations);
        }
    }

    private void ensureLoaderIsActive() {
        Enumeration<DataLoader> ldrs = DataLoaderPool.getDefault().producersOf(AdhocDataObject.class);
        while (ldrs.hasMoreElements()) {
            DataLoader ldr = ldrs.nextElement();
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        String ext = field.getText().trim();
        try {
            ensureLoaderIsActive();
            AdhocMimeTypes.registerFileNameExtension(ext, mimeType());
            field.setText("");
        } catch (InvalidMimeTypeRegistrationException ex) {
            JOptionPane.showMessageDialog(this, ex.getLocalizedMessage(), ext, JOptionPane.ERROR);
        }
    }

    private boolean validExtension() {
        String ext = field.getText().trim();
        try {
            AdhocMimeTypes.validatePotentialRegistration(ext.trim(), mimeType());
            errors.setText("");
            return true;
        } catch (InvalidMimeTypeRegistrationException ex) {
            errors.setText(ex.getLocalizedMessage());
        }
        return false;
    }

    private void changed() {
        addButton.setEnabled(validExtension());
    }

    @Override
    public void insertUpdate(DocumentEvent e) {
        changed();
    }

    @Override
    public void removeUpdate(DocumentEvent e) {
        changed();
    }

    @Override
    public void changedUpdate(DocumentEvent e) {
        changed();
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        fieldLabel = new javax.swing.JLabel();
        field = new javax.swing.JTextField();
        addButton = new javax.swing.JButton();
        associationsPane = new javax.swing.JScrollPane();
        associations = new javax.swing.JTextArea();
        errorsPane = new javax.swing.JScrollPane();
        errors = new javax.swing.JTextArea();

        fieldLabel.setLabelFor(field);
        org.openide.awt.Mnemonics.setLocalizedText(fieldLabel, org.openide.util.NbBundle.getMessage(AssociateFileExtensionDialog.class, "AssociateFileExtensionDialog.fieldLabel.text")); // NOI18N

        field.setText(org.openide.util.NbBundle.getMessage(AssociateFileExtensionDialog.class, "AssociateFileExtensionDialog.field.text")); // NOI18N
        field.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusGained(java.awt.event.FocusEvent evt) {
                selectAllOnFocus(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(addButton, org.openide.util.NbBundle.getMessage(AssociateFileExtensionDialog.class, "AssociateFileExtensionDialog.addButton.text")); // NOI18N

        associationsPane.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 0));
        associationsPane.setViewportBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 0));

        associations.setEditable(false);
        associations.setBackground(javax.swing.UIManager.getDefaults().getColor("control"));
        associations.setColumns(20);
        associations.setForeground(javax.swing.UIManager.getDefaults().getColor("controlText"));
        associations.setRows(2);
        associations.setText(org.openide.util.NbBundle.getMessage(AssociateFileExtensionDialog.class, "AssociateFileExtensionDialog.associations.text")); // NOI18N
        associations.setWrapStyleWord(true);
        associations.setBorder(javax.swing.BorderFactory.createTitledBorder(org.openide.util.NbBundle.getMessage(AssociateFileExtensionDialog.class, "AssociateFileExtensionDialog.associations.border.title"))); // NOI18N
        associationsPane.setViewportView(associations);

        errorsPane.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 0));
        errorsPane.setViewportBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 0));

        errors.setEditable(false);
        errors.setBackground(javax.swing.UIManager.getDefaults().getColor("control"));
        errors.setColumns(20);
        errors.setForeground(javax.swing.UIManager.getDefaults().getColor("controlText"));
        errors.setLineWrap(true);
        errors.setRows(3);
        errors.setText(org.openide.util.NbBundle.getMessage(AssociateFileExtensionDialog.class, "AssociateFileExtensionDialog.errors.text")); // NOI18N
        errors.setToolTipText(org.openide.util.NbBundle.getMessage(AssociateFileExtensionDialog.class, "AssociateFileExtensionDialog.errors.toolTipText")); // NOI18N
        errors.setWrapStyleWord(true);
        errorsPane.setViewportView(errors);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(associationsPane, javax.swing.GroupLayout.DEFAULT_SIZE, 585, Short.MAX_VALUE)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(fieldLabel)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(field)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(addButton))
                    .addComponent(errorsPane))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(fieldLabel)
                    .addComponent(field, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(addButton))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(associationsPane, javax.swing.GroupLayout.DEFAULT_SIZE, 93, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(errorsPane, javax.swing.GroupLayout.DEFAULT_SIZE, 86, Short.MAX_VALUE)
                .addContainerGap())
        );
    }// </editor-fold>//GEN-END:initComponents

    private void selectAllOnFocus(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_selectAllOnFocus
        field.selectAll();
    }//GEN-LAST:event_selectAllOnFocus


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton addButton;
    private javax.swing.JTextArea associations;
    private javax.swing.JScrollPane associationsPane;
    private javax.swing.JTextArea errors;
    private javax.swing.JScrollPane errorsPane;
    private javax.swing.JTextField field;
    private javax.swing.JLabel fieldLabel;
    // End of variables declaration//GEN-END:variables
}

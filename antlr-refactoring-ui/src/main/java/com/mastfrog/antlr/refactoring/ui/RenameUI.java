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
package com.mastfrog.antlr.refactoring.ui;

import com.mastfrog.abstractions.Named;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.EventQueue;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultStyledDocument;
import org.netbeans.modules.refactoring.api.Problem;
import org.netbeans.modules.refactoring.api.RenameRefactoring;
import org.netbeans.modules.refactoring.spi.ui.CustomRefactoringPanel;
import org.openide.awt.Mnemonics;
import org.openide.filesystems.FileObject;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;
import org.openide.util.Mutex;
import org.openide.util.NbBundle.Messages;
import org.openide.util.Utilities;

/**
 *
 * @author Tim Boudreau
 */
@Messages({
    "# {0} - the name",
    "rename=Rename {0}",
    "# {0} - the name",
    "# {1} - the new name",
    "rename_detail=Rename {0} to {1}"
})
public class RenameUI extends BaseUI<RenameRefactoring> {

    private final String what;
    private RenamePanelProvider provider;

    RenameUI(Lookup lookup) {
        super(new RenameRefactoring(lookup));
        this.what = "dunno";
    }

    @Override
    public String getName() {
        return Bundle.rename(what);
    }

    private String newName() {
        String result = refactoring.getNewName();
        return result == null ? "" : result;
    }

    @Override
    public String getDescription() {
        return Bundle.rename_detail(what, newName());
    }

    @Override
    public boolean isQuery() {
        return false;
    }

    @Override
    public CustomRefactoringPanel getPanel(ChangeListener cl) {
        if (provider == null) {
            provider = new RenamePanelProvider(refactoring, cl);
        }
        return provider;
    }

    @Override
    public Problem setParameters() {
        if (provider != null) {
            refactoring.setNewName(provider.text());
        }
        return checkParameters();
    }

    @Override
    public Problem checkParameters() {
        return refactoring.checkParameters();
    }

    @Override
    public boolean hasParameters() {
        return true;
    }

    static final class RenamePanelProvider implements CustomRefactoringPanel, DocumentListener {

        private final RenameRefactoring refactoring;
        private final ChangeListener listener;
        private JPanel panel;
        private final DefaultStyledDocument document = new DefaultStyledDocument();
        private String name;

        RenamePanelProvider(RenameRefactoring refactoring, ChangeListener listener) {
            this.refactoring = refactoring;
            this.listener = listener;
        }

        String text() {
            String[] result = new String[]{""};
            document.render(() -> {
                try {
                    result[0] = document.getText(0, document.getLength());
                } catch (BadLocationException ex) {
                    Exceptions.printStackTrace(ex);
                }
            });
            return result[0];
        }

        @Override
        public void initialize() {
            try {
                // Note this method is NOT called on the AWT thread.
                // Not sure what it's for.
                String name = refactoring.getNewName();
                if (name == null) {
                    name = defaultName();
                }
                document.insertString(0, name, null);
            } catch (BadLocationException ex) {
                Exceptions.printStackTrace(ex);
            }
        }

        private String defaultName() {
            Named named = refactoring.getContext().lookup(Named.class);
            System.out.println("\n\nREF CTX CONTENTS: " + refactoring.getContext().lookupAll(Object.class));
            if (named != null) {
                name = named.name();
            }
            String name = this.name;
            if (name == null) {
                FileObject fo = refactoring.getRefactoringSource().lookup(FileObject.class);
                if (fo != null) {
                    name = fo.getName();
                } else {
                    name = Bundle.sample_name();
                }
            }
            return name;
        }

        @Messages({
            "title_rename=Rename",
            "name_field=&Name",
            "sample_name=SomeNameHere"
        })
        private JPanel createPanel() {
            int ins = Utilities.isMac() ? 12 : 5;
            JPanel panel = new JPanel(new GridBagLayout());
            panel.setName(Bundle.title_rename());
            JLabel label = new JLabel();
            label.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, ins));
            Mnemonics.setLocalizedText(label, Bundle.name_field());
            JTextField field = new JTextField(document, "", 48);
            label.setLabelFor(field);
            panel.setBorder(BorderFactory.createEmptyBorder(ins, ins, ins, ins));
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.gridx = gbc.gridy = 0;
            gbc.anchor = GridBagConstraints.BASELINE;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            panel.add(label, gbc);
            gbc.weighty = 1;
            gbc.gridx++;
            panel.add(field, gbc);
            document.addDocumentListener(this);
            field.addFocusListener(new FocusAdapter() {
                @Override
                public void focusGained(FocusEvent e) {
                    field.selectAll();
                }
            });
            panel.addPropertyChangeListener("ancestor", (evt) -> {
                if (evt.getNewValue() != null) {
                    EventQueue.invokeLater(() -> {
                        field.requestFocus();
                    });
                }
            });
            JPanel result = new JPanel(new BorderLayout());
            result.add(panel, BorderLayout.NORTH);
            return result;
        }

        @Override
        public Component getComponent() {
            if (panel == null) {
                panel = createPanel();
            }
            return panel;
        }

        private void change() {
            if (listener != null) {
                Mutex.EVENT.readAccess(() -> {
                    refactoring.setNewName(text());
                    listener.stateChanged(new ChangeEvent(this));
                });
            }
        }

        @Override
        public void insertUpdate(DocumentEvent e) {
            change();
        }

        @Override
        public void removeUpdate(DocumentEvent e) {
            change();
        }

        @Override
        public void changedUpdate(DocumentEvent e) {
        }
    }
}

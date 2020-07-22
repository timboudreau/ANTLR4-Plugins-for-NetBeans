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
package org.nemesis.antlr.project.extensions.actions;

import com.mastfrog.util.strings.Strings;
import java.awt.Color;
import java.awt.Component;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.prefs.Preferences;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.UIManager;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.JTextComponent;
import org.nemesis.antlr.project.spi.addantlr.AddAntlrCapabilities;
import org.nemesis.antlr.project.spi.addantlr.NewAntlrConfigurationInfo;
import org.nemesis.antlr.project.spi.addantlr.SkeletonGrammarType;
import org.nemesis.swing.combo.EnumComboBoxModel;
import org.netbeans.api.project.Project;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.awt.Mnemonics;
import org.openide.util.ChangeSupport;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.openide.util.NbPreferences;
import org.openide.util.Utilities;

/**
 *
 * @author Tim Boudreau
 */
@NbBundle.Messages(value = {"gen_listener=Generate &Listener Classes",
    "gen_listenerTip=If set, a ParseTreeListener interface and base class specific to your grammar will be generated",
    "gen_visitor=Generate &Visitor Classes",
    "gen_visitorTip=If set, a ParseTreeVisitor interface and base class specific to your grammar will be generated",
    "gen_both=Generate &Both Listener and Visitor Classes",
    "gen_bothTip=If set, ParseTreeListener and ParseTreeVisitor interface and base classes specific to your grammar will be generated",
    "antlr_version=&Antlr Version",
    "antlr_versionTip=<html>Choose the Antlr version - this list is derived from those hard-coded into this plugin,<br>and any versions found in your local Maven repository",
    "dirNameLabel=Antlr Sources &Folder Name",
    "dirNameLabelTip=Set the name of the antlr grammar folder (off the project root folder or your sources folder, depending on project type)",
    "createImportDir=Create &Imports Folder",
    "createImportDirTip=<html>If checked, create an imports folder for grammars that "
    + "define basic tokens your lexer will use.<p>Typically this is a subfolder of "
    + "the grammar root folder, but it does not have to be.",
    "generateSkeletonGrammar=Create a S&keleton Grammar",
    "generateSkeletonGrammarTip=If not set to NONE, create the skeleton of an Antlr grammar under the grammar source folder",
    "packageForGrammars=Create Java &Package",
    "packageForGrammarTip=<html>If set, create the folders for a Java package with this name under the grammar root folder."
    + "<br>Leave blank for no package (generated grammars will be placed in the default Java package &mdash; "
    + "this is<br>generally not a good idea).",
    "grammarName=Generated Grammar &Name",
    "grammarNameTip=Set the name of the skeleton grammar to be generated",
    "antlrOptions=Antlr Options"
})
final class AddAntlrConfigurationDialog extends JPanel implements DocumentListener, FocusListener {

    private final JRadioButton genListener = new JRadioButton(Bundle.gen_listener());
    private final JRadioButton genVisitor = new JRadioButton(Bundle.gen_visitor());
    private final JRadioButton genBoth = new JRadioButton(Bundle.gen_both());
    private final JComboBox<String> versionBox = new JComboBox<>();
    private final JLabel versionLabel = new JLabel(Bundle.antlr_version());
    private final JLabel dirNameLabel = new JLabel(Bundle.dirNameLabel());
    private final JTextField dirNameField = new JTextField("grammars");
    private final ButtonGroup grp = new ButtonGroup();
    private boolean hasValidName;
    private final ChangeSupport supp = new ChangeSupport(this);
    private final JLabel problemLabel = new JLabel("<html>&nbsp;");
    private final JComboBox<SkeletonGrammarType> skeletonGrammarTypeBox = EnumComboBoxModel.newComboBox(SkeletonGrammarType.defaultType());
    private final JCheckBox createImportDir = new JCheckBox(Bundle.createImportDir());
    private final JTextField importDirField = new JTextField("grammars/imports");
    private final JLabel skeletonGrammarLabel = new JLabel(Bundle.generateSkeletonGrammar());
    private final JLabel packageLabel = new JLabel(Bundle.packageForGrammars());
    private final JTextField packageTextField = new JTextField();
    private final JTextField grammarNameField = new JTextField();
    private final JLabel grammarNameLabel = new JLabel(Bundle.grammarName());
    private final JLabel antlrOptions = new JLabel(Bundle.antlrOptions());

    private String previousGrammarDir = "";
    private String previousImportDir = "";

    public static void main(String[] args) throws InterruptedException, InvocationTargetException {
        System.setProperty("awt.useSystemAAFontSettings", "lcd_hrgb");
        Font f = new Font("Arial", Font.PLAIN, 18);
        UIManager.put("controlFont", f);
        UIManager.put("Label.font", f);
        UIManager.put("TextField.font", f);
        UIManager.put("ComboBox.font", f);
        UIManager.put("CheckBox.font", f);
        UIManager.put("Button.font", f);
        UIManager.put("RadioButton.font", f);

        AddAntlrCapabilities cap = new AddAntlrCapabilities()
                .canChooseAntlrVersion(true)
                .canGenerateSkeletonGrammar(true)
                .canSetGrammarImportDir(false)
                .canSetGrammarSourceDir(false);
        EventQueue.invokeAndWait(() -> {
            AddAntlrConfigurationDialog.showDialog(null, "Foo", cap);
        });
        System.exit(0);
    }
    private final AddAntlrCapabilities caps;

    @SuppressWarnings("LeakingThisInConstructor")
    AddAntlrConfigurationDialog(String nameProposal, AddAntlrCapabilities caps, String packageProposal) {
        super(new GridBagLayout());
        this.caps = caps;
        packageTextField.setText(packageProposal);
        grammarNameField.setToolTipText(Bundle.grammarNameTip());
        grammarNameLabel.setToolTipText(Bundle.grammarNameTip());

        createImportDir.setToolTipText(Bundle.createImportDirTip());
        importDirField.setToolTipText(Bundle.createImportDirTip());

        skeletonGrammarLabel.setToolTipText(Bundle.generateSkeletonGrammarTip());
        skeletonGrammarTypeBox.setToolTipText(Bundle.generateSkeletonGrammarTip());

        packageLabel.setToolTipText(Bundle.packageForGrammarTip());
        packageTextField.setToolTipText(Bundle.packageForGrammarTip());

        versionLabel.setToolTipText(Bundle.antlr_versionTip());
        versionBox.setToolTipText(Bundle.antlr_versionTip());

        dirNameLabel.setToolTipText(Bundle.dirNameLabelTip());
        dirNameField.setToolTipText(Bundle.dirNameLabelTip());

        grammarNameField.setText(nameProposal);

        Mnemonics.setLocalizedText(grammarNameLabel, grammarNameLabel.getText());
        Mnemonics.setLocalizedText(dirNameLabel, dirNameLabel.getText());
        Mnemonics.setLocalizedText(createImportDir, createImportDir.getText());
        Mnemonics.setLocalizedText(skeletonGrammarLabel, skeletonGrammarLabel.getText());
        Mnemonics.setLocalizedText(versionLabel, versionLabel.getText());
        Mnemonics.setLocalizedText(packageLabel, packageLabel.getText());
        grammarNameLabel.setLabelFor(grammarNameField);
        packageLabel.setLabelFor(packageTextField);
        packageTextField.setColumns(64);
        skeletonGrammarTypeBox.setPrototypeDisplayValue(SkeletonGrammarType.COMBINED);
        dirNameField.setColumns(40);
        dirNameLabel.setLabelFor(dirNameField);
        versionLabel.setLabelFor(versionBox);
        Color errColor = UIManager.getColor("nb.errorForeground");
        if (errColor == null) {
            errColor = Color.RED;
        }
        problemLabel.setForeground(errColor);
        grp.add(genListener);
        grp.add(genVisitor);
        grp.add(genBoth);
        genBoth.setSelected(true);
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(5, 5, 5, 5);
        c.gridwidth = 1;
        c.anchor = GridBagConstraints.LINE_START;
        c.weightx = 1;
        c.weighty = 1;
        c.gridx = 0;
        c.gridy = 0;
        c.fill = GridBagConstraints.HORIZONTAL;
        add(versionLabel, c);
        c.gridx++;
        add(versionBox, c);
        DefaultComboBoxModel<String> mdl = new DefaultComboBoxModel<>();
        Set<AddAntlrToProjectAction.Version> av = AddAntlrToProjectAction.antlrVersions();
        if (!av.contains(new AddAntlrToProjectAction.Version(false, "4.7.2", 4, 7, 2))) {
            av.add(new AddAntlrToProjectAction.Version(false, "4.7.2", 4, 7, 2));
        }
        if (!av.contains(new AddAntlrToProjectAction.Version(false, "4.8-1", 4, 8, 1))) {
            av.add(new AddAntlrToProjectAction.Version(false, "4.8-1", 4, 8, 1));
        }
        for (AddAntlrToProjectAction.Version v : av) {
            mdl.addElement(v.toString());
        }
        List<AddAntlrToProjectAction.Version> l = new ArrayList<AddAntlrToProjectAction.Version>(av);
        if (!l.isEmpty()) {
            mdl.setSelectedItem(l.get(l.size() - 1).toString());
        } else {
            mdl.setSelectedItem("4.8-1");
        }
        versionBox.setModel(mdl);

        c.gridwidth = 1;
        c.gridy++;
        c.gridx = 0;
        add(skeletonGrammarLabel, c);
        c.gridx++;
        add(skeletonGrammarTypeBox, c);

        c.gridx = 0;
        c.gridy++;
        add(grammarNameLabel, c);
        c.gridx++;
        add(grammarNameField, c);

        c.gridx = 0;
        c.gridy++;
        add(packageLabel, c);
        c.gridx++;
        add(packageTextField, c);

        c.gridx = 0;
        c.gridy++;
        add(dirNameLabel, c);
        c.gridx++;
        add(dirNameField, c);

        c.gridy++;
        c.gridx = 0;
        add(createImportDir, c);
        c.gridx++;
        add(importDirField, c);

        c.gridy++;
        c.gridx = 0;
        add(antlrOptions, c);
        c.fill = GridBagConstraints.NONE;
        c.weightx = 0;
        antlrOptions.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, antlrOptions.getForeground()));

        c.fill = GridBagConstraints.HORIZONTAL;
        c.gridwidth = 2;
        c.gridy++;
        c.gridx = 0;
        c.insets = new Insets(10, 36, 5, 5);
        JRadioButton[] rbs = new JRadioButton[]{genListener, genVisitor, genBoth};
        for (int i = 0; i < rbs.length; i++) {
            JRadioButton b = rbs[i];
            add(b, c);
            c.gridy++;
            c.insets = new Insets(5, 36, 5, 5);
            Mnemonics.setLocalizedText(b, b.getText());
            String tip;
            switch (i) {
                case 0:
                    tip = Bundle.gen_listenerTip();
                    break;
                case 1:
                    tip = Bundle.gen_visitorTip();
                    break;
                case 2:
                    tip = Bundle.gen_bothTip();
                    break;
                default:
                    throw new AssertionError(i);
            }
            b.setToolTipText(tip);
        }

        c.anchor = GridBagConstraints.SOUTH;
        c.weightx = 1;
        c.weighty = 1;
        c.insets = new Insets(36, 12, 12, 12);
        c.gridx = 0;
        c.gridy++;
        add(problemLabel, c);
        if (!caps.canChooseAntlrVersion()) {
            versionLabel.setVisible(false);
            versionBox.setVisible(false);
        }
        if (!caps.canSetGrammarSourceDir()) {
            dirNameLabel.setVisible(false);
            dirNameField.setVisible(false);
            problemLabel.setVisible(false);
        } else {
            dirNameField.getDocument().addDocumentListener(this);
        }
        if (!caps.canSetGrammarImportDir()) {
            createImportDir.setVisible(false);
            importDirField.setVisible(false);
        } else {
            importDirField.getDocument().addDocumentListener(this);
        }
        if (!caps.canGenerateSkeletonGrammar()) {
            grammarNameField.setVisible(false);
            grammarNameLabel.setVisible(false);
            skeletonGrammarLabel.setVisible(false);
            skeletonGrammarTypeBox.setVisible(false);
        } else {
            skeletonGrammarTypeBox.addItemListener(it -> {
                updateSkeletonGrammarDeps();
            });
        }
        packageTextField.getDocument().addDocumentListener(this);
        createImportDir.addItemListener(ie -> {
            importDirField.setEnabled(createImportDir.isSelected());
            if (createImportDir.isSelected()) {
                refreshError();
            }
        });
        load();
        refreshError();
        grammarNameField.addFocusListener(this);
        packageTextField.addFocusListener(this);
        dirNameField.addFocusListener(this);
        importDirField.addFocusListener(this);
    }

    private void updateSkeletonGrammarDeps() {
        SkeletonGrammarType type = (SkeletonGrammarType) skeletonGrammarTypeBox.getSelectedItem();
        grammarNameField.setEnabled(type.isGenerate());
        grammarNameLabel.setEnabled(type.isGenerate());
    }

    private final Set<Component> initialFocus = new HashSet<>();

    @Override
    public void focusGained(FocusEvent e) {
        if (initialFocus.contains(e.getComponent())) {
            return;
        }
        if (e.getComponent() instanceof JTextComponent) {
            ((JTextComponent) e.getComponent()).selectAll();
            initialFocus.add(e.getComponent());
        }
    }

    @Override
    public void focusLost(FocusEvent e) {
    }

    private void refreshError() {
        change(GRAMMAR_DIR_FIELD);
        change(IMPORT_DIR_FIELD);
        change(PACKAGE_FIELD);
    }

    @Messages({
        "# {0} - packageName",
        "problemInvalidPackageName=Invalid Java package name: ''{0}''",
        "problemEmptyPackageNamePart=Empty package name part",
        "problemPackageStartsOrEndWithDot=Package may not start or end with '.'"
    })
    String checkPackageName() {
        String pkg = packageTextField.getText();
        if (pkg.isEmpty()) {
            return null;
        }
        if (pkg.charAt(0) == '.' || pkg.charAt(pkg.length() - 1) == '.') {
            return Bundle.problemPackageStartsOrEndWithDot();
        }
        String[] parts = pkg.split("\\.");
        for (String part : parts) {
            if (!Utilities.isJavaIdentifier(part)) {
                return Bundle.problemInvalidPackageName(part);
            }
            if (part.isEmpty()) {
                return Bundle.problemEmptyPackageNamePart();
            }
        }
        return null;
    }

    @NbBundle.Messages(value = {"# {0} - The project name", "add_antlr_support=Add Antlr Support to {0}"})
    public static NewAntlrConfigurationInfo showDialog(Project project, String nm, AddAntlrCapabilities caps) {

        String packageProposal = project == null ? "com.test"
                : NewAntlrConfigurationInfo.findBestJavaPackageSuggestionForGrammarsWhenAddingAntlr(project);

        String nameProposal = project == null ? "MyLanguage"
                : SkeletonGrammarType.grammarNamePrefix(project);

        AddAntlrConfigurationDialog dlg = new AddAntlrConfigurationDialog(nameProposal, caps, packageProposal);
        NotifyDescriptor no = new NotifyDescriptor(dlg, Bundle.add_antlr_support(nm), NotifyDescriptor.OK_CANCEL_OPTION, NotifyDescriptor.QUESTION_MESSAGE, new Object[]{NotifyDescriptor.OK_OPTION, NotifyDescriptor.CANCEL_OPTION}, NotifyDescriptor.OK_OPTION);
        if (caps.canSetGrammarSourceDir()) {
            dlg.addChangeListener(evt -> {
                if (!dlg.hasValidName) {
                    no.setValid(false);
                } else {
                    no.setValid(true);
                }
            });
        }
        Object result = DialogDisplayer.getDefault().notify(no);
        if (NotifyDescriptor.OK_OPTION.equals(result)) {
            return dlg.info();
        }
        return null;
    }

    public void addChangeListener(ChangeListener listener) {
        supp.addChangeListener(listener);
    }

    public void removeChangeListener(ChangeListener listener) {
        supp.removeChangeListener(listener);
    }

    public void fireChange() {
        supp.fireChange();
    }
    private String reason = "";

    private boolean updating;

    @Override
    public void removeNotify() {
        super.removeNotify();
        if (hasValidName) {
            save();
        }
    }

    private void save() {
        Preferences prefs = NbPreferences.forModule(AddAntlrConfigurationDialog.class);
        String grammarDirName = dirNameField.getText();
        String importDirName = importDirField.getText();
        prefs.put("grammarDirName", grammarDirName);
        prefs.put("importDirName", importDirName);
        prefs.putBoolean("createImportDir", createImportDir.isSelected());
    }

    private void load() {
        Preferences prefs = NbPreferences.forModule(AddAntlrConfigurationDialog.class);
        String grammarDirName = prefs.get("grammarDirName", "grammars");
        String importDirName = prefs.get("importDirName", "grammars/imports");
        boolean createActive = prefs.getBoolean("createImportDir", false);
        if (grammarDirName != null) {
            previousGrammarDir = grammarDirName;
            dirNameField.setText(grammarDirName);
        }
        if (importDirName != null) {
            previousImportDir = importDirName;
            importDirField.setText(importDirName);
        }
        createImportDir.setSelected(createActive);
        importDirField.setEnabled(createImportDir.isSelected());
        updateSkeletonGrammarDeps();
    }

    private static final int GRAMMAR_DIR_FIELD = 0;
    private static final int IMPORT_DIR_FIELD = 1;
    private static final int PACKAGE_FIELD = 2;
    private static final int NAME_FIELD = 3;

    @Messages({
        "# {0} - name",
        "problemInvalidGrammarName=Invalid grammar name: {0} (must be a legal Java identifier)",
        "problemNameMayNotStartOrEndWithDot=Grammar name may not contain a '.' character"
    })
    private String checkNameFieldText() {
        String txt = grammarNameField.getText();
        if (txt.isEmpty()) {
            return null;
        }
        if (!Utilities.isJavaIdentifier(txt)) {
            return Bundle.problemInvalidGrammarName(txt);
        }
        if (txt.charAt(0) == '.' || txt.charAt(txt.length() - 1) == '.') {
            return Bundle.problemNameMayNotStartOrEndWithDot();
        }
        return null;
    }

    @NbBundle.Messages(value = {
        "problemEmptyName=Empty folder name",
        "# {0} - folderName", "problemInvalidName=Invalid folder name ''{0}''",
        "leadingOrTrailingWhitespace=Folder may not have leading or trailing whitespace",
        "relativePathsNotAllowed=Folder name may not contain relative paths",
        "importDirAndGrammarDirCannotBeTheSame=Grammar source and import folder cannot be the same folder",
        "problemLeadingSlash=Absolute paths not allowed",
        "problemMultipleSlashes=Multiple slashes in path",})
    private void change(int from) {
        boolean fromGrammarDirField = from == GRAMMAR_DIR_FIELD;
        if (updating && !fromGrammarDirField) {
            return;
        }
        String oldReason = reason;
        String grammarDirName = trimTrailingSlashes(dirNameField.getText());
        boolean wasOk = hasValidName;
        String problem = checkFolderName(grammarDirName, false);
        hasValidName = problem == null;
        String importDirName = trimTrailingSlashes(importDirField.getText());

        switch (from) {
            case GRAMMAR_DIR_FIELD:
                updating = true;
                try {
                    if (importDirName.startsWith(previousGrammarDir + File.separatorChar) || importDirName.startsWith(previousGrammarDir + "/")
                            && importDirName.length() > grammarDirName.length() + 1) {
                        String tail = importDirName.substring(previousGrammarDir.length());
                        importDirField.setText(grammarDirName + tail);
                        previousImportDir = importDirField.getText();
                    }
                } finally {
                    updating = false;
                }
                break;
            case IMPORT_DIR_FIELD:
                if (createImportDir.isSelected() && problem == null) {
                    problem = checkFolderName(importDirName, true);
                    hasValidName = problem == null;
                }
                break;
            case PACKAGE_FIELD:
                problem = checkPackageName();
                if (problem != null) {
                    hasValidName = false;
                }

            case NAME_FIELD:
                problem = checkNameFieldText();
                if (problem != null) {
                    hasValidName = false;
                }
                break;
        }

        if (problem == null && importDirName.equals(grammarDirName)) {
            problem = Bundle.importDirAndGrammarDirCannotBeTheSame();
            hasValidName = false;
        }

        if (problem == null) {
            hasValidName = true;
        }
        reason = problem;
        previousGrammarDir = grammarDirName;
        previousImportDir = importDirName;
        if (wasOk != hasValidName || !Objects.equals(oldReason, reason)) {
            if (!hasValidName) {
                problemLabel.setText(reason);
            } else {
                // maintain the preferred height
                problemLabel.setText("<html>&nbsp;");
            }
            fireChange();
            invalidate();
            revalidate();
            repaint();
        }
    }

    private String checkFolderName(String txt, boolean allowPaths) {
        String reasonLocal = null;
        if (txt.isEmpty() || Strings.isBlank(txt)) {
            reasonLocal = Bundle.problemEmptyName();
        } else if (txt.indexOf(File.pathSeparatorChar) >= 0) {
            reasonLocal = Bundle.problemInvalidName(txt);
        } else if (!allowPaths && (txt.indexOf(File.separatorChar) >= 0)) {
            reasonLocal = Bundle.problemInvalidName(txt);
        } else if (allowPaths && (txt.contains(File.separatorChar + "..") || txt.contains(".." + File.separatorChar))) {
            reasonLocal = Bundle.relativePathsNotAllowed();
        } else if (allowPaths && txt.charAt(0) == '/' || txt.charAt(0) == '\\') {
            reasonLocal = Bundle.problemLeadingSlash();
        } else if (Character.isWhitespace(txt.charAt(0)) || Character.isWhitespace(txt.charAt(txt.length() - 1))) {
            reasonLocal = Bundle.leadingOrTrailingWhitespace();
        }
        if (allowPaths && reasonLocal == null && txt.length() > 1) {
            for (int i = 1; i < txt.length(); i++) {
                char prev = txt.charAt(i - 1);
                char curr = txt.charAt(i);
                if ((prev == '\\' || prev == '/') && (curr == '\\' || curr == '/')) {
                    reasonLocal = Bundle.problemMultipleSlashes();
                    break;
                }
            }
        }
        return reasonLocal;
    }

    @Override
    public void insertUpdate(DocumentEvent e) {
        change(e.getDocument() == dirNameField.getDocument() ? GRAMMAR_DIR_FIELD
                : e.getDocument() == importDirField.getDocument() ? IMPORT_DIR_FIELD
                : e.getDocument() == packageTextField.getDocument() ? PACKAGE_FIELD
                : NAME_FIELD);
    }

    @Override
    public void removeUpdate(DocumentEvent e) {
        change(e.getDocument() == dirNameField.getDocument() ? GRAMMAR_DIR_FIELD
                : e.getDocument() == importDirField.getDocument() ? IMPORT_DIR_FIELD
                : e.getDocument() == packageTextField.getDocument() ? PACKAGE_FIELD
                : NAME_FIELD);
    }

    @Override
    public void changedUpdate(DocumentEvent e) {
        // do nothing
    }

    private String trimTrailingSlashes(String s) {
        if (s.isEmpty()) {
            return s;
        }
        char end = s.charAt(s.length() - 1);
        if (end == '/' || end == File.separatorChar) {
            return s.substring(0, s.length() - 1);
        }
        return s;
    }

    public NewAntlrConfigurationInfo info() {
        String version = ((String) versionBox.getSelectedItem()).trim();
        if (version == null || version.isEmpty()) {
            version = "4.8-1";
        }
        SkeletonGrammarType skelType = caps.canGenerateSkeletonGrammar()
                ? (SkeletonGrammarType) skeletonGrammarTypeBox.getSelectedItem()
                : SkeletonGrammarType.NONE;
        return new NewAntlrConfigurationInfo(trimTrailingSlashes(dirNameField.getText()),
                genListener.isSelected() || genBoth.isSelected(),
                genVisitor.isSelected() || genBoth.isSelected(), version,
                skelType,
                trimTrailingSlashes(importDirField.getText()),
                createImportDir.isSelected(), packageTextField.getText(),
                grammarNameField.getText()
        );
    }

}

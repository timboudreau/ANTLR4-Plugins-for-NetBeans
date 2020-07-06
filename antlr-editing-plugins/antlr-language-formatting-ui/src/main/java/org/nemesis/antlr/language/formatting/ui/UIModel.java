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
package org.nemesis.antlr.language.formatting.ui;

import org.nemesis.antlr.language.formatting.config.ColonHandling;
import java.awt.EventQueue;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.ButtonModel;
import javax.swing.ComboBoxModel;
import javax.swing.DefaultButtonModel;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.WindowConstants;
import javax.swing.event.ChangeListener;
import org.nemesis.antlr.language.formatting.config.AntlrFormatterConfig;
import static org.nemesis.antlr.language.formatting.config.AntlrFormatterConfig.*;
import org.nemesis.antlr.language.formatting.config.OrHandling;
import org.openide.awt.Mnemonics;
import org.openide.util.NbBundle;
import org.openide.util.Utilities;

/**
 *
 * @author Tim Boudreau
 */
class UIModel {

    private final AntlrFormatterConfig config;

    UIModel(AntlrFormatterConfig config) {
        this.config = config;
    }

    AntlrFormatterConfig config() {
        return config;
    }

    public static UIModel create(AntlrFormatterConfig config) {
        return new UIModel(config);
    }

    public ButtonModel floatingIndentModel() {
        return new ConfigCheckboxModel(config, KEY_FLOATING_INDENT, config::isFloatingIndent, config::setFloatingIndent);
    }

    public ButtonModel wrapModel() {
        return new ConfigCheckboxModel(config, KEY_WRAP, config::isWrap, config::setWrap);
    }

    public ButtonModel blankLineBeforeRulesModel() {
        return new ConfigCheckboxModel(config, KEY_BLANK_LINE_BEFORE_RULES, config::isBlankLineBeforeRules, config::setBlankLineBeforeRules);
    }

    public ButtonModel spacesInsideParensModel() {
        return new ConfigCheckboxModel(config, KEY_SPACES_INSIDE_PARENS, config::isSpacesInsideParens, config::setSpacesInsideParens);
    }

    public ButtonModel semicolonOnNewLineModel() {
        return new ConfigCheckboxModel(config, KEY_SEMICOLON_ON_NEW_LINE, config::isSemicolonOnNewline, config::setSemicolonOnNewline);
    }

    public ButtonModel reflowLineCommentsModel() {
        return new ConfigCheckboxModel(config, KEY_REFLOW_LINE_COMMENTS, config::isReflowLineComments, config::setReflowLineComments);
    }

    public ComboBoxModel<Integer> maxLineLengthModel() {
        return new MaxLineLengthComboBoxModel(config);
    }

    public ComboBoxModel<Integer> indentModel() {
        return new IndentComboBoxModel(config);
    }

    public ButtonModel colonHandlingButtonModel(ColonHandling type) {
        DefaultButtonModel result = new ColonHandlingRadioButtonModel(config, type);
        if (type.equals(config.getColonHandling())) {
            result.setSelected(true);
        }
        return result;
    }

    public ButtonModel orHandlingButtonModel(OrHandling type) {
        DefaultButtonModel result = new OrHandlingRadioButtonModel(config, type);
        if (type.equals(config.getOrHandling())) {
            result.setSelected(true);
        }
        return result;
    }

    @NbBundle.Messages({
        "wrap=&Wrap Text",
        "maxLength=Ma&x Line Length",
        "colon=Colon &Handling",
        "floatingIndent=&Floating Indent",
        "tip_floatingIndent=Aligns outermost below the colon for that rule",
        "indent=Indent &Depth",
        "blankLineBeforeRules=Empty Line &Before Each Rule",
        "spacesInParens=Spaces Inside Innermost &Parentheses",
        "reflowLineComments=&Reflow Line Comments",
        "semicolonOnNewline=&Semicolon On New Line",
        "orHandling=Or and Parenthesis Handling"
    })
    public JPanel createFormattingPanel() {

        JPanel pnl = new JPanel(new GridBagLayout());
        pnl.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        int siDim = Utilities.isMac() ? 12 : 5;
        Insets standardInsets = new Insets(siDim, siDim, siDim, siDim);
        Insets indentedInsets = new Insets(siDim, siDim * 2, siDim, siDim);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1;
        gbc.anchor = GridBagConstraints.BASELINE_LEADING;
        gbc.gridwidth = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = standardInsets;

        JCheckBox wrapCheckBox = new JCheckBox();
        Mnemonics.setLocalizedText(wrapCheckBox, Bundle.wrap());
        wrapCheckBox.setModel(wrapModel());
        pnl.add(wrapCheckBox, gbc);

        JLabel maxLengthLabel = new JLabel();
        Mnemonics.setLocalizedText(maxLengthLabel, Bundle.maxLength());
        gbc.gridx++;
        gbc.anchor = GridBagConstraints.BASELINE_TRAILING;
        maxLengthLabel.setHorizontalAlignment(SwingConstants.TRAILING);
        pnl.add(maxLengthLabel, gbc);

        JComboBox<Integer> wrapPointComboBox = new JComboBox(maxLineLengthModel());
        maxLengthLabel.setLabelFor(wrapPointComboBox);
        gbc.gridx++;
        pnl.add(wrapPointComboBox, gbc);

        ChangeListener onWrapChange = ce -> {
            maxLengthLabel.setEnabled(wrapCheckBox.isSelected());
            wrapPointComboBox.setEnabled(wrapCheckBox.isSelected());
        };

        wrapCheckBox.addChangeListener(onWrapChange);

        gbc.anchor = GridBagConstraints.BASELINE_LEADING;
        gbc.gridx = 0;

        gbc.gridy++;
        JCheckBox floatingIndentCheckbox = new JCheckBox();
        floatingIndentCheckbox.setModel(floatingIndentModel());
        floatingIndentCheckbox.setToolTipText(Bundle.tip_floatingIndent());
        Mnemonics.setLocalizedText(floatingIndentCheckbox, Bundle.floatingIndent());
        pnl.add(floatingIndentCheckbox, gbc);

        JLabel indentAmountLabel = new JLabel();
        indentAmountLabel.setHorizontalAlignment(SwingConstants.TRAILING);
        Mnemonics.setLocalizedText(indentAmountLabel, Bundle.indent());
        gbc.anchor = GridBagConstraints.BASELINE_TRAILING;
        gbc.gridx++;
        pnl.add(indentAmountLabel, gbc);
        gbc.gridx++;

        JComboBox indentAmountComboBox = new JComboBox(indentModel());
        pnl.add(indentAmountComboBox, gbc);

        gbc.gridy++;
        gbc.gridx = 0;
        gbc.anchor = GridBagConstraints.BASELINE_LEADING;
        JCheckBox blankLineBeforeRulesCheckbox = new JCheckBox();
        blankLineBeforeRulesCheckbox.setModel(blankLineBeforeRulesModel());
        Mnemonics.setLocalizedText(blankLineBeforeRulesCheckbox, Bundle.blankLineBeforeRules());
        pnl.add(blankLineBeforeRulesCheckbox, gbc);

        gbc.gridy++;
        gbc.gridwidth = 3;
        gbc.gridx = 0;
        gbc.gridwidth = 1;

        JLabel colonHandlingLabel = new JLabel();
        Mnemonics.setLocalizedText(colonHandlingLabel, Bundle.colon());
        pnl.add(colonHandlingLabel, gbc);
        colonHandlingLabel.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, UIManager.getColor("textText")));

        gbc.gridwidth = 3;
        gbc.gridy++;
        gbc.insets = indentedInsets;

        ButtonGroup grp = new ButtonGroup();
        ColonHandling[] all = ColonHandling.values();
        List<JRadioButton> colonHandlingButtons = new ArrayList<>(ColonHandling.values().length);
        for (int i = 0; i < all.length; i++) {
            ColonHandling h = all[i];
            if (i == all.length - 1) {
                gbc.insets = new Insets(indentedInsets.top, indentedInsets.left, indentedInsets.right, indentedInsets.bottom * 2);
            }
            ButtonModel mdl = colonHandlingButtonModel(h);
            JRadioButton button = new JRadioButton();
            colonHandlingButtons.add(button);
            button.setModel(mdl);
            grp.add(button);
            Mnemonics.setLocalizedText(button, h.displayName());
            pnl.add(button, gbc);
            gbc.gridy++;
            if (i == 0) {
                colonHandlingLabel.setLabelFor(button);
            }
        }
        gbc.insets = standardInsets;

        gbc.gridy++;
        gbc.gridx = 0;
        gbc.gridwidth = 1;
        JLabel orHandlingLabel = new JLabel();
        Mnemonics.setLocalizedText(orHandlingLabel, Bundle.orHandling());
        pnl.add(orHandlingLabel, gbc);
        orHandlingLabel.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, UIManager.getColor("textText")));

        gbc.gridwidth = 3;
        gbc.gridy++;
        gbc.insets = indentedInsets;

        ButtonGroup grp2 = new ButtonGroup();
        OrHandling[] allOrs = OrHandling.values();
        List<JRadioButton> orHandlingButtons = new ArrayList<>(ColonHandling.values().length);
        for (int i = 0; i < allOrs.length; i++) {
            OrHandling h = allOrs[i];
            if (i == allOrs.length - 1) {
                gbc.insets = new Insets(indentedInsets.top, indentedInsets.left, indentedInsets.right, indentedInsets.bottom * 3);
            }
            ButtonModel mdl = orHandlingButtonModel(h);
            JRadioButton button = new JRadioButton();
            orHandlingButtons.add(button);
            button.setModel(mdl);
            grp2.add(button);
            Mnemonics.setLocalizedText(button, h.toString());
            pnl.add(button, gbc);
            gbc.gridy++;
            if (i == 0) {
                orHandlingLabel.setLabelFor(button);
            }
        }
        gbc.insets = standardInsets;


        gbc.gridy++;
        JCheckBox spacesInParensCheckbox = new JCheckBox();
        spacesInParensCheckbox.setModel(spacesInsideParensModel());
        Mnemonics.setLocalizedText(spacesInParensCheckbox, Bundle.spacesInParens());
        pnl.add(spacesInParensCheckbox, gbc);

        gbc.gridy++;
        JCheckBox semiOnNewLineCheckbox = new JCheckBox();
        semiOnNewLineCheckbox.setModel(semicolonOnNewLineModel());
        Mnemonics.setLocalizedText(semiOnNewLineCheckbox, Bundle.semicolonOnNewline());
        pnl.add(semiOnNewLineCheckbox, gbc);

        gbc.gridy++;
        JCheckBox reflowLineComments = new JCheckBox();
        reflowLineComments.setModel(reflowLineCommentsModel());
        Mnemonics.setLocalizedText(reflowLineComments, Bundle.reflowLineComments());
        pnl.add(reflowLineComments, gbc);

        class AL implements ActionListener, Runnable {

            @Override
            public void actionPerformed(ActionEvent e) {
                // get out of the way of updates
                EventQueue.invokeLater(this);
            }

            @Override
            public void run() {
                floatingIndentCheckbox.setEnabled(config.canEnableFloatingIndent());
                semiOnNewLineCheckbox.setEnabled(config.canEnableSemicolonOnNewLine());
            }
        }
        AL al = new AL();
        al.run();
        for (JRadioButton b : colonHandlingButtons) {
            b.addActionListener(al);
        }
        return pnl;
    }

    public static void main(String[] args) {
        UIModel mdl = new UIModel(new AntlrFormatterConfig(new MockPreferences()));
        mdl.config.addPropertyChangeListener((PropertyChangeEvent evt) -> {
            System.out.println(evt.getPropertyName() + " -> " + evt.getNewValue());
        });
        EventQueue.invokeLater(() -> {
            JFrame jf = new JFrame();
            jf.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
            jf.setContentPane(mdl.createFormattingPanel());
            jf.pack();
            jf.setVisible(true);
        });
    }
}

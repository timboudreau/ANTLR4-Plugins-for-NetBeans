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
        "semicolonOnNewline=&Semicolon On New Line"
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

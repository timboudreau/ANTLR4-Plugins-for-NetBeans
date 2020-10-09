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

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.Serializable;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JColorChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.plaf.basic.BasicButtonUI;
import static org.nemesis.antlr.live.language.AdhocErrorHighlighter.toggleHighlightAmbiguitiesAction;
import static org.nemesis.antlr.live.language.AdhocErrorHighlighter.toggleHighlightLexerErrorsAction;
import static org.nemesis.antlr.live.language.AdhocErrorHighlighter.toggleHighlightParserErrorsAction;
import org.nemesis.antlr.live.language.ColorUtils;
import org.nemesis.antlr.live.language.coloring.AdhocColoring;
import org.nemesis.antlr.live.language.coloring.AdhocColorings;
import org.nemesis.antlr.live.language.coloring.AttrTypes;
import org.nemesis.swing.ActivityIndicator;
import org.openide.util.NbBundle.Messages;

/**
 *
 * @author Tim Boudreau
 */
@Messages({
    "foreground=Foreground",
    "background=Background",
    "active=Active",
    "color=Color",
    "sampleText=<Sample Text>",
    "bold=Bold",
    "italic=Italic",
    "disableAll=Disable All Colors"
})
public final class AdhocColoringPanel extends JPanel implements ActionListener, PropertyChangeListener, MouseListener {

    private final JButton toggle1 = new JButton((Action) toggleHighlightAmbiguitiesAction());
    private final JButton toggle2 = new JButton(toggleHighlightParserErrorsAction());
    private final JButton toggle3 = new JButton(toggleHighlightLexerErrorsAction());
    private final JLabel label = new JLabel("<rule name>");
    private final JCheckBox active = new JCheckBox(Bundle.active());
    private final JCheckBox bold = new JCheckBox(Bundle.bold());
    private final JCheckBox italic = new JCheckBox(Bundle.italic());
    private final JRadioButton foreground = new JRadioButton(Bundle.foreground());
    private final JRadioButton background = new JRadioButton(Bundle.background());
    private final JButton colorButton = new JButton(Bundle.color());
    private final ButtonGroup group = new ButtonGroup();
    private final ActivityIndicator indicator = new ActivityIndicator();
    private final JButton disableAll = new JButton(Bundle.disableAll());
    private static final String CMD_DISABLE = "disable";
    private static final String CMD_ACTIVE = "active";
    private static final String CMD_FOREGROUND = "fg";
    private static final String CMD_BACKGROUND = "bg";
    private static final String CMD_BOLD = "bold";
    private static final String CMD_ITALIC = "italic";
    private static final String CMD_COLOR = "color";
    private static final String PROP_COLOR = "color";
    private Color color = Color.black;
    private final AdhocColorings colorings;
    private boolean updating;

    public AdhocColoringPanel(String key, AdhocColorings colorings) {
        this(colorings);
        setAdhocColoring(colorings, key);
    }

    @SuppressWarnings("LeakingThisInConstructor")
    public AdhocColoringPanel(AdhocColorings colorings) {
        this.colorings = colorings;
//        setLayout(new GridLayout(1, 5, 5, 5));
        setLayout(new GridBagLayout());
        group.add(foreground);
        group.add(background);
        colorButton.setUI(new BasicButtonUI());
//        colorButton.setBorderPainted(false);
        colorButton.setOpaque(true);
        colorButton.setBorder(BorderFactory.createLineBorder(Color.BLACK));
        colorButton.setMinimumSize(new Dimension(24, 24));
        colorButton.setOpaque(true);
        GridBagConstraints gbc = new GridBagConstraints();
        toggle1.setText("");
        toggle2.setText("");
        toggle3.setText("");
        toggle1.setContentAreaFilled(false);
        toggle2.setContentAreaFilled(false);
        toggle3.setContentAreaFilled(false);
        // The icon will paint itself with a different color after a 
        // toggle, but there is no mechanism to repaint the button directly
        // via the action
        toggle1.addMouseListener(this);
        toggle2.addMouseListener(this);
        toggle3.addMouseListener(this);
        gbc.anchor = GridBagConstraints.FIRST_LINE_START;
        gbc.insets = new Insets(0, 5, 2, 0);
        gbc.ipadx = 3;
        gbc.ipady = 3;
        gbc.gridx = 0;
        gbc.gridwidth = 1;
        gbc.gridheight = GridBagConstraints.RELATIVE;
        gbc.fill = GridBagConstraints.VERTICAL;
        add(toggle1, gbc);
        gbc.gridx++;
        add(toggle2, gbc);
        gbc.gridx++;
        add(toggle3, gbc);
        gbc.gridx++;

        gbc.weightx = 1.0;
        add(label, gbc);
        gbc.weightx = 0;
        gbc.anchor = GridBagConstraints.FIRST_LINE_END;
        gbc.gridx++;
        add(indicator, gbc);
        // Hide this for now - doesn't work and too easy to accidentally hit
//        gbc.gridx++;
//        add(disableAll, gbc);
        gbc.gridx++;
        gbc.gridwidth = 2;
        add(colorButton, gbc);
        gbc.gridwidth = 1;
        gbc.gridx += 2;
        add(active, gbc);
        gbc.gridx++;
        add(foreground, gbc);
        gbc.gridx++;
        add(background, gbc);
        gbc.gridx++;
        add(bold, gbc);
        gbc.gridx++;
        add(italic, gbc);
        active.setActionCommand(CMD_ACTIVE);
        active.addActionListener(this);
        colorButton.setActionCommand(CMD_COLOR);
        colorButton.addActionListener(this);
        foreground.setActionCommand(CMD_FOREGROUND);
        foreground.addActionListener(this);
        background.setActionCommand(CMD_BACKGROUND);
        background.addActionListener(this);
        bold.setActionCommand(CMD_BOLD);
        bold.addActionListener(this);
        italic.setActionCommand(CMD_ITALIC);
        italic.addActionListener(this);
        disableAll.setActionCommand(CMD_DISABLE);
        disableAll.addActionListener(this);

        addPropertyChangeListener(this);
    }

    public void indicateActivity() {
        indicator.trigger();
    }

    public void setColor(Color color) {
        if (!this.color.equals(color)) {
            Color old = this.color;
            this.color = color;
            firePropertyChange(PROP_COLOR, old, this.color);
        }
    }

    public Color getColor() {
        return color;
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        ((Component) e.getSource()).repaint();
    }

    @Override
    public void mousePressed(MouseEvent e) {
        // do nothing
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        // do nothing
    }

    @Override
    public void mouseEntered(MouseEvent e) {
        // do nothing
    }

    @Override
    public void mouseExited(MouseEvent e) {
        // do nothing
    }

    @Override
    @Messages("colorTitle=Highlighting Color")
    public void actionPerformed(ActionEvent e) {
        if (listeningSuspended || info == null || updating) {
            return;
        }
        switch (e.getActionCommand()) {
            case CMD_ACTIVE:
                info.setActive(active.isSelected());
                break;
            case CMD_FOREGROUND:
                info.setForeground(true);
                break;
            case CMD_BACKGROUND:
                info.setForeground(false);
                break;
            case CMD_COLOR:
                JColorChooser ch = new JColorChooser(info.coloring.color());
                int dlgResult
                        = JOptionPane.showConfirmDialog(this,
                                ch, Bundle.colorTitle(), JOptionPane.OK_CANCEL_OPTION);
                if (dlgResult == JOptionPane.OK_OPTION) {
                    Color c = ch.getColor();
                    info.setColor(c);
                    suspendListening(() -> {
                        updateFrom(info);
                    });
                }
                break;
            case CMD_BOLD:
                info.setBold(bold.isSelected());
                break;
            case CMD_ITALIC:
                info.setItalic(italic.isSelected());
                break;
            case CMD_DISABLE:
                colorings.disableAll();
                break;
        }
        updateFrom(info);
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if (listeningSuspended) {
            return;
        }
        updateFrom(info);
    }

    public void setAdhocColoring(AdhocColorings colorings, String key) {
        setColoringInfo(new ColoringInfo(key, colorings.get(key), colorings));
    }

    void refreshFromColoring() {
        updateFrom(info);
    }

    private ColoringInfo info;

    private void setColoringInfo(ColoringInfo info) {
        ColoringInfo old = this.info;
        this.info = info;
        suspendListening(() -> {
            detachFrom(old);
            attachTo(info);
        });
    }

    @Override
    public void addNotify() {
        super.addNotify();
        attachTo(info);
    }

    @Override
    public void removeNotify() {
        detachFrom(info);
        super.removeNotify();
    }

    private void detachFrom(ColoringInfo info) {
        if (info != null) {
            info.colorings.removePropertyChangeListener(info.key, this);
        }
    }

    private void attachTo(ColoringInfo info) {
        if (info != null && isDisplayable()) {
            info.colorings.addPropertyChangeListener(info.key, this);
            updateFrom(info);
        }
    }

    private void updateFrom(ColoringInfo info) {
        if (info == null) {
            return;
        }
        updating = true;
        try {
            label.setText(info.key);
            setColor(info.coloring.color());
            if (info.isForeground()) {
                colorButton.setForeground(info.coloring.color());
                colorButton.setBackground(ColorUtils.editorBackground());
                group.setSelected(foreground.getModel(), true);
            } else {
                colorButton.setBackground(info.coloring.color());
                colorButton.setForeground(ColorUtils.editorForeground());
                group.setSelected(background.getModel(), true);
            }
            italic.setSelected(info.coloring.isItalic());
            bold.setSelected(info.coloring.isBold());
            active.setSelected(info.isActive());
            invalidate();
            revalidate();
            repaint();
        } finally {
            updating = false;
        }
    }

    private boolean listeningSuspended;

    private void suspendListening(Runnable run) {
        boolean oldValue = listeningSuspended;
        listeningSuspended = true;
        try {
            run.run();
        } finally {
            listeningSuspended = false;
        }
    }

    static final class ColoringInfo implements Serializable {

        private static final long serialVersionUID = 1;
        final String key;
        final AdhocColoring coloring;
        final AdhocColorings colorings;

        ColoringInfo(String key, AdhocColoring coloring, AdhocColorings colorings) {
            this.key = key;
            this.coloring = coloring;
            this.colorings = colorings;
        }

        public boolean isForeground() {
            return coloring.isForegroundColor();
        }

        public void setActive(boolean active) {
            colorings.setFlag(key, AttrTypes.ACTIVE, active);
        }

        public void setBold(boolean bold) {
            colorings.setFlag(key, AttrTypes.BOLD, bold);
        }

        public void setItalic(boolean italic) {
            colorings.setFlag(key, AttrTypes.ITALIC, italic);
        }

        public void setColor(Color color) {
            colorings.setColor(key, color);
        }

        public boolean isActive() {
            return coloring.isActive();
        }

        public void setForeground(boolean foreground) {
            colorings.setForeground(key, foreground);
        }
    }
}

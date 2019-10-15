package org.nemesis.antlr.live.preview;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.Serializable;
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
import org.nemesis.antlr.live.language.coloring.AdhocColoring;
import org.nemesis.antlr.live.language.coloring.AdhocColorings;
import org.nemesis.antlr.live.language.coloring.AttrTypes;
import org.nemesis.antlr.live.language.ColorUtils;
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
    "italic=Italic"
})
public final class AdhocColoringPanel extends JPanel implements ActionListener, PropertyChangeListener {

    private final JLabel label = new JLabel("<rule name>");
    private final JCheckBox active = new JCheckBox(Bundle.active());
    private final JCheckBox bold = new JCheckBox(Bundle.bold());
    private final JCheckBox italic = new JCheckBox(Bundle.italic());
    private final JRadioButton foreground = new JRadioButton(Bundle.foreground());
    private final JRadioButton background = new JRadioButton(Bundle.background());
    private final JButton colorButton = new JButton(Bundle.color());
    private final ButtonGroup group = new ButtonGroup();
    private static final String CMD_ACTIVE = "active";
    private static final String CMD_FOREGROUND = "fg";
    private static final String CMD_BACKGROUND = "bg";
    private static final String CMD_BOLD = "bold";
    private static final String CMD_ITALIC = "italic";
    private static final String CMD_COLOR = "color";
    private static final String PROP_COLOR = "color";
    private Color color = Color.black;

    public AdhocColoringPanel(String key, AdhocColorings colorings) {
        this();
        setAdhocColoring(colorings, key);
    }

    @SuppressWarnings("LeakingThisInConstructor")
    public AdhocColoringPanel() {
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
        gbc.anchor = GridBagConstraints.FIRST_LINE_START;
        gbc.insets = new Insets(0, 5, 0, 0);
        gbc.ipadx = 3;
        gbc.ipady = 3;
        gbc.gridx = 0;
        gbc.gridwidth = 1;
        gbc.gridheight = GridBagConstraints.RELATIVE;
        gbc.fill = GridBagConstraints.VERTICAL;
        gbc.weightx = 1.0;
        add(label, gbc);
        gbc.weightx = 0;
        gbc.anchor = GridBagConstraints.FIRST_LINE_END;
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

        addPropertyChangeListener(this);
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
    @Messages("colorTitle=Highlighting Color")
    public void actionPerformed(ActionEvent e) {
        if (listeningSuspended || info == null) {
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
        active.setSelected(info.isActive());
        invalidate();
        revalidate();
        repaint();
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

        public ColoringInfo(String key, AdhocColoring coloring, AdhocColorings colorings) {
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

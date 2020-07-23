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
package com.mastfrog.antlr.project.helpers.ant;

import com.mastfrog.function.state.Bool;
import com.mastfrog.function.state.Obj;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.geom.AffineTransform;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.border.Border;
import org.openide.DialogDescriptor;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import static org.openide.NotifyDescriptor.OK_CANCEL_OPTION;
import static org.openide.NotifyDescriptor.OK_OPTION;
import org.openide.util.NbBundle.Messages;

/**
 *
 * @author Tim Boudreau
 */
final class UpgradeProjectUI {

    static void show(List<Upgrader> upgraders) {
        EventQueue.invokeLater(() -> {
            showUI(upgraders);
        });
    }

    @Messages({
        "titleAntlrProjectsCanBeUpgraded=Upgrade Ant-Based Antlr Projects",
        "msgUpgrade=Some Ant-based Antlr can be upgraded.  Do it now?"
    })
    private static void showUI(List<Upgrader> upgraders) {
        if (upgraders.isEmpty()) {
            return;
        }
        Set<Upgrader> dontAsks = new HashSet<>();

        JPanel outer = new JPanel(new BorderLayout());
        outer.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        JLabel msg = new JLabel(Bundle.msgUpgrade());
        outer.add(msg, BorderLayout.NORTH);
        msg.setBorder(BorderFactory.createEmptyBorder(12, 0, 12, 0));

        JPanel inner = new JPanel(new GridBagLayout());

        JScrollPane innerScroll = new JScrollPane(inner);
        innerScroll.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, UIManager.getColor("controlShadow")));
        innerScroll.setViewportBorder(EMPTY);

        outer.add(inner, BorderLayout.CENTER);
        inner.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridwidth = 1;
        gbc.gridheight = 1;
        gbc.ipadx = 24;
        gbc.ipady = 24;
        gbc.anchor = GridBagConstraints.NORTH;
        gbc.weightx = 1;
        gbc.weighty = 1;
        gbc.fill = GridBagConstraints.BOTH;
        boolean first = true;

        Obj<NotifyDescriptor> descHolder = Obj.create();
        Runnable validitySetter = () -> {
            if (descHolder.get() != null) {
                boolean valid = !upgraders.isEmpty();
                descHolder.get().setValid(valid );
            }
        };

        for (Upgrader up : upgraders) {
            JPanel pnl = createUpgradePanel(first, up, upgraders, validitySetter, dontAsks);
            inner.add(pnl, gbc);
//            gbc.gridx=0;
            gbc.gridy++;
            first = false;
        }

        DialogDescriptor dlg = new DialogDescriptor(outer,
                Bundle.titleAntlrProjectsCanBeUpgraded(),
                true,
                OK_CANCEL_OPTION,
                OK_OPTION,
                ae -> {
                    // do nothing
                });
        descHolder.set(dlg);

        if (!upgraders.isEmpty() && OK_OPTION.equals(DialogDisplayer.getDefault().notify(dlg))) {
            UpgradableProjectDetector.runUpgrades(upgraders, dontAsks);
        }

    }

    private static final Border EMPTY = BorderFactory.createEmptyBorder();

    @Messages({
        "# {0} - projectName",
        "upgradeProject=Upgrade {0}",
        "dontUpgrade=Don't Upgrade",
        "dontUpgradeOrAsk=Don't Upgrade and Don't Ask Again For This Version",})
    private static JPanel createUpgradePanel(boolean first, Upgrader upgrader, List<Upgrader> all,
            Runnable onChange, Set<Upgrader> dontAsks) {
        JPanel pnl = new JPanel(new GridBagLayout());
        if (!first) {
            pnl.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, UIManager.getColor("controlShadow")));
        } else {
            pnl.setBorder(EMPTY);
        }
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.FIRST_LINE_START;
        gbc.weightx = 1;
        gbc.weighty = 0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 3;
        gbc.gridheight = 1;
        JLabel lbl = new JLabel(Bundle.upgradeProject(upgrader.projectDisplayName()));
        lbl.setFont(lbl.getFont().deriveFont(Font.BOLD));
        lbl.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, lbl.getForeground()));

        pnl.add(lbl, gbc);
        gbc.gridy++;

        JTextArea area = new JTextArea(upgrader.toString());
        area.setBorder(EMPTY);
        area.setColumns(40);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setEditable(false);
        area.setBackground(pnl.getBackground());
        area.setFont(area.getFont().deriveFont(AffineTransform.getScaleInstance(0.9F, 0.9F)));

        gbc.ipadx = 24;
        pnl.add(area, gbc);

        gbc.ipadx = 5;
        JCheckBox dontUpgrade = new JCheckBox(Bundle.dontUpgrade());
        JCheckBox dontAsk = new JCheckBox(Bundle.dontUpgradeOrAsk());
        dontUpgrade.setHorizontalTextPosition(SwingConstants.LEADING);
        dontAsk.setHorizontalTextPosition(SwingConstants.LEADING);
        dontUpgrade.setFont(area.getFont());
        dontAsk.setFont(area.getFont());
        gbc.anchor = GridBagConstraints.LAST_LINE_END;
        gbc.gridy++;
        gbc.gridwidth = 1;
        gbc.gridx = 2;
        pnl.add(dontUpgrade, gbc);
        gbc.gridy++;
        pnl.add(dontAsk, gbc);

        Bool dontAskRunning = Bool.create();
        Color areaOrig = area.getForeground();
        Color lblOrig = lbl.getForeground();
        Color boxOrig = dontAsk.getForeground();
        Color areaShadow = dimmed(area);
        Color lblShadow = dimmed(lbl);
        Color boxShadow = dimmed(dontAsk);

        Runnable updateColors = () -> {
            if (all.contains(upgrader)) {
                area.setForeground(areaOrig);
                lbl.setForeground(lblOrig);
                dontAsk.setForeground(boxOrig);
                dontUpgrade.setForeground(boxOrig);
            } else {
                area.setForeground(areaShadow);
                lbl.setForeground(lblShadow);
                if (!dontAsk.isSelected()) {
                    dontAsk.setForeground(boxShadow);
                }
                if (!dontUpgrade.isSelected()) {
                    dontUpgrade.setForeground(boxShadow);
                }
            }
        };

        dontUpgrade.addActionListener(al -> {
            dontAskRunning.ifUntrue(() -> {
                if (dontUpgrade.isSelected()) {
                    all.remove(upgrader);
                } else if (!dontUpgrade.isSelected()) {
                    if (!all.contains(upgrader)) {
                        all.add(upgrader);
                        dontAsk.setSelected(false);
                    }
                }
            });
            onChange.run();
            updateColors.run();
        });

        dontAsk.addActionListener(al -> {
            dontAskRunning.set();
            try {
                if (!dontAsk.isSelected() && !all.contains(upgrader)) {
                    if (!dontUpgrade.isSelected()) {
                        all.add(upgrader);
                    }
                    dontAsks.remove(upgrader);
                } else if (dontAsk.isSelected()) {
                    all.remove(upgrader);
                    dontAsks.add(upgrader);
//                    dontAsk.setSelected(true);
                }
            } finally {
                dontAskRunning.set(false);
            }
            onChange.run();
            updateColors.run();
        });

        return pnl;
    }

    private static Color dimmed(Component comp) {
        Color fg = comp.getForeground();
        Color bg = comp.getBackground();
        float[] fgHsb = new float[3];
        float[] bgHsb = new float[3];
        Color.RGBtoHSB(fg.getRed(), fg.getGreen(), fg.getBlue(), fgHsb);
        Color.RGBtoHSB(bg.getRed(), bg.getGreen(), bg.getBlue(), bgHsb);
        float bdiff = fgHsb[2] - bgHsb[2];
        fgHsb[2] = bgHsb[2] + (bdiff * 0.675F);
        return new Color(Color.HSBtoRGB(fgHsb[0], fgHsb[1], fgHsb[2]));
    }

    public static void main(String[] args) throws InterruptedException, InvocationTargetException {
        System.setProperty("awt.useSystemAAFontSettings", "lcd_hrgb");
        Font f = new Font("Arial", Font.PLAIN, 24);
        UIManager.put("controlFont", f);
        UIManager.put("Label.font", f);
        UIManager.put("TextField.font", f);
        UIManager.put("ComboBox.font", f);
        UIManager.put("CheckBox.font", f);
        UIManager.put("Button.font", f);
        UIManager.put("RadioButton.font", f);
        UIManager.put("TextArea.font", f);
        class FakeUpgrader implements Upgrader {

            private boolean dontAsk;
            private final String projectName, text;
            private boolean upgraded;

            public FakeUpgrader(String projectName, String text) {
                this.projectName = projectName;
                this.text = text;
            }

            public String toString() {
                return text;
            }

            @Override
            public String projectDisplayName() {
                return projectName;
            }

            @Override
            public boolean upgrade() throws Exception {
                upgraded = true;
                return true;
            }

            @Override
            public void dontAskAnymore() {
                dontAsk = true;
            }

            @Override
            public boolean isDontAsk() {
                return dontAsk;
            }
        }

        List<Upgrader> ugs = new ArrayList<>(Arrays.asList(
                new FakeUpgrader("Quorum Language", "The project Quorum Language can be "
                        + "upgraded from Antlr 3.5.2 to Antlr 500.5.5 and module version "
                        + "7.1 to 7.9")
                ,
                new FakeUpgrader("Markdown Grammar", "The project Markdown Grammar can be "
                        + "upgraded to module version 2.0.71 which may enable additional features."),
                new FakeUpgrader("Yasl", "The project Yasl can be "
                        + "upgraded from Antlr 3.5.2 to Antlr 500.5.5 and module version "
                        + "7.1 to 7.9"),
                new FakeUpgrader("Toothpaste", "The project Toothpaste can become "
                        + "one with universe 3.5.2 to and 500.5.5 and module version "
                        + "2.1 to 8.1"),
                new FakeUpgrader("Ouvre Tubers", "The project Ouvre Tubers can be upgraded "
                        + "to wear its Sunday suit and jump up and down if you want it to"
                        + " have some fun now and then, or it can just be upgraded from "
                        + "27.3 to 28.1")

        ));

        EventQueue.invokeAndWait(() -> {
            UpgradeProjectUI.showUI(ugs);
        });
        System.exit(0);
    }
}

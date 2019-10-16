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

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import org.nemesis.antlr.project.AntlrConfiguration;
import org.nemesis.antlr.project.spi.NewAntlrConfigurationInfo;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectUtils;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.awt.Mnemonics;
import org.openide.awt.StatusDisplayer;
import org.openide.util.Exceptions;
import org.openide.util.NbBundle.Messages;
import org.openide.util.Utilities;

public final class AddAntlrToProject extends AbstractAction {

    private final Project context;
    private final Function<NewAntlrConfigurationInfo, CompletionStage<Boolean>> adder;
    private static final Logger LOG = Logger.getLogger(AddAntlrToProject.class.getName());
    private static final String DEFAULT_ANTLR = "4.7.2";

    public AddAntlrToProject(Project context) {
        super(Bundle.CTL_AddAntlrToProject());
        this.context = context;
        adder = AntlrConfiguration.antlrAdder(context);
        setEnabled(adder != null);
    }

    @Override
    public void actionPerformed(ActionEvent ev) {
        assert adder != null;
        String name = ProjectUtils.getInformation(context).getDisplayName();
        NewAntlrConfigurationInfo info = Dlg.showDialog(name);
        if (info != null) {
            CompletionStage<Boolean> fut = adder.apply(info);
            fut.handleAsync(new InfoNotifier(name));
        }
    }

    @Messages({"# {0} - the project name",
        "adding_antlr=Adding Antlr to {0}",
        "# {0} - the project name",
        "adding_antlr_failed=Adding Antlr failed for {0}",
        "# {0} - the project name",
        "adding_antlr_succeeded=Adding Antlr succeeded for {0}",})
    static final class InfoNotifier implements BiFunction<Boolean, Throwable, Boolean>, Runnable {

        private final String projectName;
        private volatile boolean result;

        public InfoNotifier(String projectName) {
            this.projectName = projectName;
            StatusDisplayer.getDefault().setStatusText(Bundle.adding_antlr(projectName));
        }

        @Override
        public Boolean apply(Boolean t, Throwable u) {
            result = t == null ? false : t;
            if (u != null) {
                LOG.log(Level.SEVERE, "Failed adding Antlr support to "
                        + projectName, u);
            }
            return t;
        }

        @Override
        public void run() {
            if (result) {
                StatusDisplayer.getDefault().setStatusText(Bundle.adding_antlr_succeeded(projectName));
            } else {
                StatusDisplayer.getDefault().setStatusText(Bundle.adding_antlr_failed(projectName));
            }
        }
    }

    @Messages({"gen_listener=Generate &Listener Classes", "gen_visitor=Generate &Visitor Classes",
        "gen_both=Generate &Both Listener and Visitor Classes", "antlr_version=&Antlr Version"})
    static final class Dlg extends JPanel {

        private final JRadioButton genListener = new JRadioButton(Bundle.gen_listener());
        private final JRadioButton genVisitor = new JRadioButton(Bundle.gen_visitor());
        private final JRadioButton genBoth = new JRadioButton(Bundle.gen_both());
        private final JComboBox<String> versionBox = new JComboBox<>();
        private final JLabel lbl = new JLabel(Bundle.antlr_version());
        private final ButtonGroup grp = new ButtonGroup();
        private static final Pattern PAT = Pattern.compile("[\\d.]+");

        Dlg() {
            super(new GridBagLayout());
            lbl.setLabelFor(versionBox);
            grp.add(genListener);
            grp.add(genVisitor);
            grp.add(genBoth);
            genBoth.setSelected(true);
            setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
            GridBagConstraints c = new GridBagConstraints();
            c.insets = new Insets(5, 5, 5, 5);
            c.gridwidth = 2;
            c.anchor = GridBagConstraints.LINE_START;
            c.weightx = 1.0;
            c.gridx = 0;
            c.gridy = 0;
            c.fill = GridBagConstraints.BOTH;
            for (JRadioButton b : new JRadioButton[]{genListener, genVisitor, genBoth}) {
                add(b, c);
                c.gridy++;
                Mnemonics.setLocalizedText(b, b.getText());
            }
            c.gridwidth = 1;
            add(lbl, c);
            c.gridx++;
            add(versionBox, c);
            Mnemonics.setLocalizedText(lbl, lbl.getText());
            DefaultComboBoxModel<String> mdl = new DefaultComboBoxModel<>();
            Set<Version> av = antlrVersions();
            if (!av.contains(new Version(false, "4.7.2", 4, 7, 2))) {
                av.add(new Version(false, "4.7.2", 4, 7, 2));
            }
            for (Version v : av) {
                mdl.addElement(v.toString());
            }
            List<Version> l = new ArrayList<Version>(av);
            if (!l.isEmpty()) {
                mdl.setSelectedItem(l.get(l.size() - 1).toString());
            } else {
                mdl.setSelectedItem("4.7.2");
            }

            versionBox.setModel(mdl);

        }

        @Messages({
            "# {0} - The project name",
            "add_antlr_support=Add Antlr Support to {0}"})
        public static NewAntlrConfigurationInfo showDialog(String nm) {
            Dlg dlg = new Dlg();
            NotifyDescriptor no = new NotifyDescriptor(dlg, Bundle.add_antlr_support(nm),
                    NotifyDescriptor.OK_CANCEL_OPTION,
                    NotifyDescriptor.QUESTION_MESSAGE, new Object[]{NotifyDescriptor.OK_OPTION, NotifyDescriptor.CANCEL_OPTION},
                    NotifyDescriptor.OK_OPTION);
            Object result = DialogDisplayer.getDefault().notify(no);
            if (NotifyDescriptor.OK_OPTION.equals(result)) {
                return dlg.info();
            }
            return null;
        }

        public NewAntlrConfigurationInfo info() {

            String version = ((String) versionBox.getSelectedItem()).trim();
            if (version.isEmpty()) {
                version = "4.7.2";
            }
            return new NewAntlrConfigurationInfo(
                    genListener.isSelected() || genBoth.isSelected(),
                    genVisitor.isSelected() || genBoth.isSelected(),
                    version);
        }
    }

    static class Version implements Comparable<Version> {

        final boolean snapshot;

        private final String name;

        private final int[] comps;

        Version(boolean snapshot, String name, int... comps) {
            this.snapshot = snapshot;
            this.name = name;
            this.comps = comps;
        }

        @Override
        public String toString() {
            return name;
        }

        String internalToString() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < comps.length; i++) {
                sb.append(comps[i]);
                if (i != comps.length - 1) {
                    sb.append('.');
                }
            }
            return sb.toString();
        }

        @Override
        public int hashCode() {
            int result = snapshot ? 0 : 1;
            for (int i = comps.length - 1; i >= 0; i--) {
                result += (10000 * i) * comps[i];
            }
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (!(obj instanceof Version)) {
                return false;
            }
            final Version other = (Version) obj;
            return Arrays.equals(this.comps, other.comps)
                    && name.equals(other.name);
        }

        static Version fromPath(Path p) {
            String s = p.getFileName().toString();
            List<Integer> l = new ArrayList<>();
            int max = s.length();
            StringBuilder sb = new StringBuilder();
            loop:
            for (int i = 0; i < max; i++) {
                char c = s.charAt(i);
                if (i == 0 && !Character.isDigit(c)) {
                    return null;
                }
                switch (c) {
                    case '.':
                    case '-':
                        if (sb.length() == 0) {
                            return null;
                        }
                        l.add(Integer.parseInt(sb.toString()));
                        sb.setLength(0);
                        break;
                    case '0':
                    case '1':
                    case '2':
                    case '3':
                    case '4':
                    case '5':
                    case '6':
                    case '7':
                    case '8':
                    case '9':
                        sb.append(c);
                        break;
                    default:
                        // Hit a postfix
                        break loop;
                }
            }
            if (sb.length() > 0) {
                l.add(Integer.parseInt(sb.toString()));
            }
            if (l.isEmpty()) {
                return null;
            }
            boolean snap = s.endsWith("-SNAPSHOT") || s.endsWith("-dev");
            return new Version(snap, s, (int[]) Utilities.toPrimitiveArray(l.toArray(new Integer[0])));
        }

        @Override
        public int compareTo(Version o) {
            int minMax = Math.min(comps.length, o.comps.length);
            for (int i = 0; i < minMax; i++) {
                int a = comps[i];
                int b = o.comps[i];
                int result = Integer.compare(a, b);
                if (result != 0) {
                    return result;
                }
            }
            if (snapshot && !o.snapshot) {
                return -1;
            } else if (!snapshot && o.snapshot) {
                return 1;
            }
            return Integer.compare(comps.length, o.comps.length);
        }
    }

    static final Set<Version> antlrVersions() {
        Path home = Paths.get(System.getProperty("user.home"));
        Path m2antlrDir = home.resolve(".m2/repository/org/antlr/antlr4");
        Set<Version> versions = new TreeSet<>();
        if (Files.exists(m2antlrDir) && Files.isDirectory(m2antlrDir)) {
            try (Stream<Path> str = Files.list(m2antlrDir)) {
                str.filter(pth -> {
                    return Files.isDirectory(pth);
                }).forEach(pth -> {
                    Version v = Version.fromPath(pth);
                    if (v != null) {
                        versions.add(v);
                    }
                });
            } catch (IOException ex) {
                Exceptions.printStackTrace(ex);
            }
        }
        return versions;
    }

    static final String findHighestLocalAntlrVersion() {
        Set<Version> versions = antlrVersions();
        if (versions.size() > 0) {
            return new ArrayList<>(versions).get(versions.size() - 1).toString();
        } else {
            return DEFAULT_ANTLR;
        }
    }
}

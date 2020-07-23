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

import static com.mastfrog.antlr.project.helpers.ant.AddAntBasedAntlrSupport.CUSTOM_NAMESPACE;
import static com.mastfrog.antlr.project.helpers.ant.AddAntBasedAntlrSupport.buildExtensionHash;
import static com.mastfrog.antlr.project.helpers.ant.AddAntBasedAntlrSupport.buildExtensionProjectRelativePath;
import static com.mastfrog.antlr.project.helpers.ant.AddAntBasedAntlrSupport.buildScriptStream;
import static com.mastfrog.antlr.project.helpers.ant.AddAntBasedAntlrSupport.hashStream;
import static com.mastfrog.antlr.project.helpers.ant.LambdaUtils.ifNotNull;
import static com.mastfrog.antlr.project.helpers.ant.LambdaUtils.ifTrue;
import static com.mastfrog.antlr.project.helpers.ant.LambdaUtils.lkp;
import static com.mastfrog.util.preconditions.Checks.notNull;
import com.mastfrog.util.strings.Escaper;
import com.mastfrog.util.strings.Strings;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import org.nemesis.antlr.wrapper.AntlrVersion;
import org.netbeans.api.annotations.common.NonNull;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectInformation;
import org.netbeans.api.project.ProjectManager;
import org.netbeans.api.project.ProjectUtils;
import org.netbeans.spi.project.AuxiliaryConfiguration;
import org.netbeans.spi.project.support.ant.AntProjectHelper;
import org.netbeans.spi.project.support.ant.EditableProperties;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.modules.SpecificationVersion;
import org.openide.util.Mutex;
import org.openide.util.NbBundle.Messages;
import org.openide.util.NbPreferences;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 *
 * @author Tim Boudreau
 */
class ProjectUpgrader implements Mutex.ExceptionAction<Boolean>, Upgrader {

    private final Project project;
    private final AuxiliaryConfiguration config;
    private final ModuleVersionInfo versionInfo;
    private final FileObject buildExtensionFile;
    private final AntProjectHelper helper;
    private final ModuleVersionInfo currentInfo;
    private final Element configuration;
    private static final SpecificationVersion maxVersionRequiringUpdate
            = new SpecificationVersion("2.0.0");

    ProjectUpgrader(Project project, AuxiliaryConfiguration config,
            ModuleVersionInfo versionInfo, FileObject buildExtensionFile,
            AntProjectHelper helper, ModuleVersionInfo currentInfo,
            Element configuration) {
        this.project = project;
        this.config = config;
        this.versionInfo = versionInfo;
        this.buildExtensionFile = buildExtensionFile;
        this.helper = helper;
        this.currentInfo = currentInfo;
        this.configuration = configuration;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        } else if (o == null) {
            return false;
        } else if (!(o instanceof ProjectUpgrader)) {
            return false;
        }
        ProjectUpgrader pu = (ProjectUpgrader) o;
        return project.getProjectDirectory().equals(pu.project.getProjectDirectory());
    }

    @Override
    public int hashCode() {
        return project.getProjectDirectory().getPath().hashCode();
    }

    @Override
    public String projectDisplayName() {
        return ProjectUtils.getInformation(project).getDisplayName();
    }

    private String versionsPrefsNodeName() {
        String omv = versionInfo.moduleVersion().toString().replace('.', '-');
        String cmv = currentInfo.moduleVersion().toString().replace('.', '-');
        return "up_" + cmv + "_" + omv;
    }

    private Preferences versionsPreferences() {
        Preferences prefs = NbPreferences.forModule(ProjectUpgrader.class);
        return prefs.node(versionsPrefsNodeName());
    }

    public boolean isDontAsk() {
        Preferences prefs = versionsPreferences();
        // There is a low limit on Preferences key length, so we use a hash
        // of the file path to stay under it
        String path = project.getProjectDirectory().getPath();
        String hashedKey = Strings.escape(Strings.hash(path), Esc.INSTANCE);
        return prefs.getBoolean(hashedKey, false);
    }

    public void dontAskAnymore() {
        Preferences prefs = versionsPreferences();
        // There is a low limit on Preferences key length, so we use a hash
        // of the file path to stay under it
        String path = project.getProjectDirectory().getPath();
        String hashedKey = Strings.escape(Strings.hash(path), Esc.INSTANCE);
        prefs.putBoolean(hashedKey, true);
    }

    static class Esc implements Escaper {

        static final Esc INSTANCE = new Esc();

        @Override
        public CharSequence escape(char c) {
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
                return null;
            }
            return "";
        }
    }

    @Messages({
        "# {0} - projectName",
        "# {1} - oldAntlrVersion",
        "# {2} - newAntlrVersion",
        "# {3} - newModuleVersion",
        "upgrade=Project ''{0}'' can be upgraded from Antlr {1} to Antlr {2}"
        + " and Antlr plugin version {3}.  Perform upgrade?",
        "# {0} - projectName",
        "# {1} - newPluginVersion",
        "upgradeNoAntlrChange=Project ''{0}'' can be upgraded to "
        + " Antlr plugin version {1}.  Perform upgrade?",})
    @Override
    public String toString() {
        ProjectInformation info = ProjectUtils.getInformation(project);
        if (versionInfo.isSameAntlrVersion(currentInfo)) {
            return Bundle.upgradeNoAntlrChange(info.getDisplayName(), currentInfo.moduleVersion());
        }
        return Bundle.upgrade(info.getDisplayName(), versionInfo.antlrVersionRaw(),
                currentInfo.antlrVersionRaw(), currentInfo.moduleVersion());
    }

    public static ProjectUpgrader needsUpgrade(Project project) {
        ModuleVersionInfo currentInfo = currentInfo();
        return ifNotNull("no build extension", () -> project.getProjectDirectory().getFileObject(buildExtensionProjectRelativePath().toString()),
                buildExtensionFile
                -> ifNotNull("No aux configuration", lkp(project, AuxiliaryConfiguration.class),
                        acon
                        -> ifNotNull("no helper", () -> Hacks.helperFor(project), helper
                                -> ifNotNull("no config fragment", () -> configurationFragment(acon),
                                configuration
                                -> ifNotNull("version info", () -> versionInfo(configuration),
                                        moduleVersionInfo
                                        -> ifTrue(moduleVersionInfo.mayNeedUpgrade() && currentInfo.isNewerThan(moduleVersionInfo), ()
                                                -> new ProjectUpgrader(project,
                                                acon,
                                                moduleVersionInfo,
                                                buildExtensionFile,
                                                helper,
                                                currentInfo,
                                                configuration)
                                        ))))));
    }

    static Element configurationFragment(AuxiliaryConfiguration acon) {
        return acon.getConfigurationFragment("antlr", CUSTOM_NAMESPACE, true);
    }

    static ModuleVersionInfo versionInfo(Element elem) {
        Element el = moduleElement(elem);
        String moduleVersionRaw = el.getAttribute("version");
        String antlrVersionRaw = el.getAttribute("antlrversion");
        if (moduleVersionRaw == null || antlrVersionRaw == null) {
            return null;
        }
        String buildScriptHash = el.getAttribute("buildextensionhash");
        if (buildScriptHash == null) {
            return null;
        }
        ModuleVersionInfo info = new ModuleVersionInfo(moduleVersionRaw, antlrVersionRaw, buildScriptHash);
        return info;
    }

    private static Element moduleElement(Element elem) {
        NodeList moduleNodes = elem.getElementsByTagNameNS(CUSTOM_NAMESPACE, "module");
        if (moduleNodes == null || moduleNodes.getLength() != 1) {
            return null;
        }
        Node moduleNode = moduleNodes.item(0);
        if (!(moduleNode instanceof Element)) {
            return null;
        }
        return (Element) moduleNode;
    }

    @Override
    public boolean upgrade() throws Exception {
        Boolean result = ProjectManager.mutex(true, project).writeAccess(this);
        return result == null ? false : result;
    }

    @Override
    public Boolean run() throws Exception {
        List<? extends PropertyReplacer> replacers = replacers();
        boolean buildScriptChanged = !versionInfo.buildScriptHash().equals(currentInfo.buildScriptHash());
        if (buildScriptChanged || !replacers.isEmpty()) {
            return reallyDoUpgrade(replacers, buildScriptChanged);
        }
        return false;
    }

    private boolean reallyDoUpgrade(List<? extends PropertyReplacer> replacers, boolean buildScriptChanged) throws Exception {
        boolean haveChanges = false;
        if (!replacers.isEmpty()) {
            EditableProperties props = helper.getProperties(AntProjectHelper.PROJECT_PROPERTIES_PATH);
            boolean propsChanged = false;
            for (PropertyReplacer p : replacers) {
                propsChanged |= p.replace(props);
            }
            if (propsChanged) {
                helper.putProperties(AntProjectHelper.PROJECT_PROPERTIES_PATH, props);
                haveChanges = true;
            }
        }
        if (buildScriptChanged) {
            FileObject ext = buildExtensionFile;
            boolean reallyUpdateBuildScript = true;
            if (ext == null) {
                ext = FileUtil.createData(project.getProjectDirectory(),
                        AddAntBasedAntlrSupport.buildExtensionProjectRelativePath().toString());
                haveChanges = true;
            } else {
                if (ext.getSize() > 0) {
                    try (InputStream in = ext.getInputStream()) {
                        String hash = hashStream(in);
                        if (!hash.equals(versionInfo.buildScriptHash())) {
                            // the build script was manually modified
                            reallyUpdateBuildScript = false;
                        }
                    }
                }
            }
            if (reallyUpdateBuildScript) {
                try (InputStream in = buildScriptStream()) {
                    try (OutputStream out = ext.getOutputStream()) {
                        FileUtil.copy(in, out);
                    }
                    haveChanges = true;
                }
            }
        }
        if (haveChanges) {
            AddAntBasedAntlrSupport.writeTrackingInfo(config);
            ProjectManager.getDefault().saveProject(project);
        }
        return haveChanges;
    }

    static ModuleVersionInfo currentInfo() {
        return new ModuleVersionInfo(AntlrVersion.moduleVersion(), AntlrVersion.version(), buildExtensionHash());
    }

    private Element depProps(Element moduleElement) {
        if (moduleElement == null) {
            return null;
        }
        NodeList nl = moduleElement.getElementsByTagNameNS(CUSTOM_NAMESPACE, "versiondependentproperties");
        if (nl != null && nl.getLength() > 0) {
            return (Element) nl.item(0);
        }
        return null;
    }

    private List<? extends PropertyReplacer> replacers() {
        if (versionInfo.isSameAntlrVersion(currentInfo)) {
            return Collections.emptyList();
        }
        Element depProps = depProps(configuration);
        if (depProps != null) {
            NodeList nl = depProps.getChildNodes();
            List<PropertyReplacer> replacers = new ArrayList<>();
            for (int i = 0; i < nl.getLength(); i++) {
                Node n = nl.item(i);
                if (n instanceof Element && "property".equals(((Element) n).getTagName())) {
                    Element el = (Element) n;
                    String name = el.getAttribute("name");
                    if (name != null) {
                        String prefix = el.getAttribute("prefix");
                        String suffix = el.getAttribute("suffix");
                        replacers.add(new PropertyReplacer(name, prefix, suffix,
                                versionInfo.antlrVersionRaw(), currentInfo.antlrVersionRaw()));
                    }
                }
            }
            return replacers;
        }
        return Collections.emptyList();
    }

    private static class PropertyReplacer {

        private final String propertyName;
        private final String prefix;
        private final String suffix;
        private final String oldValue;
        private final String newValue;

        public PropertyReplacer(String propertyName, String prefix, String suffix, String oldValue, String newValue) {
            this.propertyName = propertyName;
            this.prefix = prefix;
            this.suffix = suffix;
            this.oldValue = oldValue;
            this.newValue = newValue;
        }

        public String toString() {
            return propertyName + ": " + prefix + oldValue + suffix + " --> " + prefix + newValue + suffix;
        }

        public boolean replace(EditableProperties properties) {
            String value = properties.getProperty(propertyName);
            if (value == null) {
                return false;
            }
            String lookFor = prefix + oldValue + suffix;
            String replaceWith = prefix + newValue + suffix;
            String revisedValue = Strings.literalReplaceAll(lookFor, replaceWith, value);
            if (!value.equals(revisedValue)) {
                properties.put(propertyName, revisedValue);
                return true;
            }
            return false;
        }
    }

    static final class ModuleVersionInfo implements Comparable<ModuleVersionInfo> {

        private final String moduleVersionRaw;
        private final String antlrVersionRaw;
        private final String buildScriptHash;

        ModuleVersionInfo(@NonNull String moduleVersionRaw, @NonNull String antlrVersionRaw, @NonNull String buildScriptHash) {
            this.moduleVersionRaw = notNull("moduleVersionRaw", moduleVersionRaw);
            this.antlrVersionRaw = notNull("antlrVersionRaw", antlrVersionRaw);
            this.buildScriptHash = notNull("buildScriptHash", buildScriptHash);
        }

        boolean mayNeedUpgrade() {
            boolean result = maxVersionRequiringUpdate.compareTo(moduleVersion()) > 0;
            return result;
        }

        String moduleVersionRaw() {
            return moduleVersionRaw;
        }

        String antlrVersionRaw() {
            return antlrVersionRaw;
        }

        boolean isNewerThan(ModuleVersionInfo info) {
            int comp = compareTo(info);
            return comp > 0;
        }

        @Override
        public int compareTo(ModuleVersionInfo o) {
            int result = moduleVersion().compareTo(o.moduleVersion());
            if (result == 0) {
                result = antlrVersion().compareTo(o.antlrVersion());
            }
            return result;
        }

        boolean isSameAntlrVersion(ModuleVersionInfo other) {
            return other == this ? true : other.antlrVersion().equals(antlrVersion());
        }

        String buildScriptHash() {
            return buildScriptHash;
        }

        @Override
        public String toString() {
            return "antlr-ant-" + moduleVersion() + ":antlr-" + antlrVersion() + ":" + buildScriptHash;
        }

        public SpecificationVersion moduleVersion() {
            try {
                return new SpecificationVersion(moduleVersionRaw);
            } catch (NumberFormatException e) {
                Logger.getLogger(ModuleVersionInfo.class.getName()).log(Level.INFO,
                        "Bad module version '" + moduleVersionRaw + "'", e);
                return new SpecificationVersion("10000.0.0");
            }
        }

        public SpecificationVersion antlrVersion() {
            String munged = antlrVersionRaw.endsWith("-SNAPSHOT")
                    ? antlrVersionRaw.substring(0, antlrVersionRaw.length() - "-SNAPSHOT".length())
                    : antlrVersionRaw;
            munged = munged.replace('-', '.');
            try {
                return new SpecificationVersion(munged);
            } catch (NumberFormatException e) {
                Logger.getLogger(ModuleVersionInfo.class.getName()).log(Level.INFO,
                        "Bad antlr version '" + moduleVersionRaw + "'", e);
                return new SpecificationVersion("10000.0.0");
            }
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 11 * hash + Objects.hashCode(this.moduleVersionRaw);
            hash = 11 * hash + Objects.hashCode(this.antlrVersionRaw);
            hash = 11 * hash + Objects.hashCode(this.buildScriptHash);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            } else if (obj == null) {
                return false;
            }
            if (!(obj instanceof ModuleVersionInfo)) {
                return false;
            }
            final ModuleVersionInfo other = (ModuleVersionInfo) obj;
            return other.moduleVersion().equals(moduleVersion())
                    && other.antlrVersion().equals(antlrVersion())
                    && other.buildScriptHash.equals(buildScriptHash);
        }
    }

}

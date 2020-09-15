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
package org.nemesis.antlr.project.helpers.maven;

import com.mastfrog.function.throwing.ThrowingTriFunction;
import com.mastfrog.util.collections.CollectionUtils;
import java.io.Externalizable;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.nemesis.antlr.project.AntlrConfiguration;
import static org.nemesis.antlr.project.helpers.maven.MavenFolderStrategy.scheduleCacheSave;
import org.nemesis.antlr.project.impl.FoldersHelperTrampoline;
import org.nemesis.antlr.projectupdatenotificaton.ProjectUpdates;
import org.netbeans.api.project.Project;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

/**
 *
 * @author Tim Boudreau
 */
final class MavenInfo implements Serializable {

    static final long serialVersionUID = 2L;
    public static final String ANTLR_MAVEN_DEFAULT_OUTPUT_DIRECTORY
            = // sourceDirectory
            "generated-sources/antlr4";
    public static final String ANTLR_MAVEN_DEFAULT_SOURCE_DIRECTORY
            = // libDirectory (imports)
            "src/main/antlr4";
    public static final String ANTLR_MAVEN_DEFAULT_LIB_DIRECTORY
            = // excludes
            "src/main/antlr4/imports";
    public static final String ANTLR_MAVEN_DEFAULT_INCLUDES = "**/*.g4";
    public static final String ANTLR_PROP_OUTPUT_DIRECTORY = "outputDirectory";
    public static final String ANTLR_PROP_SOURCE_DIRECTORY = "sourceDirectory";
    public static final String ANTLR_PROP_LIB_DIRECTORY = "libDirectory";
    public static final String ANTLR_PROP_EXCLUDES = "excludes";
    public static final String ANTLR_PROP_INCLUDES = "includes";
    public static final String ANTLR_PROP_FORCE_ATN = "forceATN";
    public static final String ANTLR_PROP_ATN = "atn";
    public static final String ANTLR_PROP_VISITOR = "visitor";
    public static final String ANTLR_PROP_LISTENER = "listener";

    private final String projectDir;
    private transient MavenAntlrConfiguration cachedInfo;
    private PomInfo info;

    MavenInfo(Project project) {
        FileObject fo = project.getProjectDirectory();
        projectDir = FileUtil.toFile(fo).toPath().toString();
    }

    private MavenInfo(Path dir, PomInfo pomInfo) {
        this.projectDir = dir.toString();
        this.info = pomInfo;
    }

    @Override
    public String toString() {
        return "MavenInfo(" + projectDir + ")";
    }

    Path projectDir() {
        return Paths.get(projectDir);
    }

    static MavenAntlrConfiguration createAntlrConfig(MavenInfo info, Path baseDir) {
        if (info == null) {
            return null; // broken POM
        }
        String[] keys = new String[]{
            ANTLR_PROP_INCLUDES,
            ANTLR_PROP_EXCLUDES,
            ANTLR_PROP_LIB_DIRECTORY,
            ANTLR_PROP_SOURCE_DIRECTORY,
            ANTLR_PROP_OUTPUT_DIRECTORY,
            ANTLR_PROP_LISTENER,
            ANTLR_PROP_VISITOR,
            ANTLR_PROP_ATN,
            ANTLR_PROP_FORCE_ATN
        };

        String[] defaults = new String[]{
            ANTLR_MAVEN_DEFAULT_INCLUDES, //includes
            "", // excludes
            ANTLR_MAVEN_DEFAULT_LIB_DIRECTORY, // libDirctory
            ANTLR_MAVEN_DEFAULT_SOURCE_DIRECTORY, //sourceDirectory
            ANTLR_MAVEN_DEFAULT_OUTPUT_DIRECTORY, //outputDirectory
            "true", // listener
            "false", //visitor
            "false", //atn
            "forceAtn", //forceAtn
        };
        PomInfo pomInfo = info.pomInfo();
        Map<String, String> vals = info.getConfigValues(keys, defaults);
        Path buildDir = baseDir.resolve(pomInfo.buildDir);
        Path importDir = baseDir.resolve(vals.get(ANTLR_PROP_LIB_DIRECTORY));
        Path sourceDir = baseDir.resolve(vals.get(ANTLR_PROP_SOURCE_DIRECTORY));
        Path outDir = buildDir.resolve(vals.get(ANTLR_PROP_OUTPUT_DIRECTORY));
        Path buildOutput = baseDir.resolve(pomInfo.buildOutput);
        Path testOutput = baseDir.resolve(pomInfo.testOutput);
        Path sources = baseDir.resolve(pomInfo.sources);
        Path testSources = baseDir.resolve(pomInfo.testSources);
        boolean listener = isTrue(vals.get(ANTLR_PROP_LISTENER));
        boolean visitor = isTrue(vals.get(ANTLR_PROP_VISITOR));
        boolean atn = isTrue(vals.get(ANTLR_PROP_ATN));
        boolean forceATN = isTrue(vals.get(ANTLR_PROP_FORCE_ATN));
        String includePattern = vals.get(ANTLR_PROP_INCLUDES);
        String excludePattern = vals.get(ANTLR_PROP_EXCLUDES);
        Charset encoding = pomInfo.encoding;
        return new MavenAntlrConfiguration(importDir, sourceDir, outDir, listener,
                visitor, atn, forceATN, includePattern, excludePattern,
                encoding, buildDir, buildOutput, testOutput, sources, testSources);
    }

    private static boolean isTrue(String propertyValue) {
        return propertyValue != null && ("true".equalsIgnoreCase(propertyValue) || "yes".equalsIgnoreCase(propertyValue)
                || "1".equals(propertyValue));
    }

    public Map<String, String> getConfigValues(String[] keys, String[] defaults) {
        assert keys.length == defaults.length;
        Map<String, String> result = new HashMap<>();
        PomInfo currentInfo = pomInfo();
        if (currentInfo != null) {
            for (int i = 0; i < keys.length; i++) {
                String key = keys[i];
                String def = defaults[i];
                String val = currentInfo.configurationValues.getOrDefault(key, def);
                result.put(key, val);
            }
        }
        return result;
    }


    public MavenAntlrConfiguration pluginInfo() {
        if (cachedInfo != null) {
            if (info != null && !info.isUpToDate()) {
                cachedInfo = null;
                info = null;
            }
        }
        if (cachedInfo != null) {
            return cachedInfo;
        }
        return cachedInfo = createAntlrConfig(this, projectDir());
    }

    public PomInfo pomInfo() {
        if (info != null && info.isUpToDate()) {
            return info;
        }
        PomInfo result = info = createInfo();
        if (result != null) {
            scheduleCacheSave();
        }
        return result;
    }

    private static Map<String, Long> knownNonAntlr = new ConcurrentHashMap<>();

    static Map<String, Long> knownNonAntlrProjects() {
        return new HashMap<>(knownNonAntlr);
    }

    static void putKnownNonAntlr(Map<String, Long> items) {
        knownNonAntlr.putAll(items);
    }

    private PomInfo createInfo() {
        try {
            // Ensure a quick negative test if we have already determined that
            // this is a project which does not have Antlr support in it's
            // or it's parents' POM files - otherwise we will parse all the
            // way back to the topmost parent project every time the IDE
            // scans sources
            Path pom = projectDir().resolve("pom.xml");
            long currLastModified = Files.getLastModifiedTime(pom).toMillis();
            Long lm = knownNonAntlr.get(pom.toString());
            if (lm != null) {
                if (lm.longValue() >= currLastModified) {
                    return new PomInfo(Collections.emptyMap());
                } else {
                    knownNonAntlr.remove(pom.toString());
                }
            }

            info = PomInfo.create(pom);
            if (info == null) {
                knownNonAntlr.put(pom.toString(), currLastModified);
                info = new PomInfo(Collections.emptyMap());
            }
        } catch (Exception ex) {
            Logger.getLogger(MavenInfo.class.getName()).log(Level.INFO,
                    "Could not resolve poms for " + projectDir, ex);
            info = new PomInfo(Collections.emptyMap());
        }
        return info;
    }

    public static void main(String[] args) throws Exception {
//        Path path = Paths.get("/home/tim/work/foreign/ANTLR4-Plugins-for-NetBeans/antlr-editing-plugins/tokens-file-grammar/pom.xml");
//        Path path = Paths.get("/home/tim/work/personal/mastfrog-parent/revision-pomInfo-plugin/pom.xml");
        Path path = Paths.get("/home/tim/work/personal/timeseries/crypto/pom.xml");
        PomInfo info = PomInfo.create(path);
        MavenInfo mi = new MavenInfo(path.getParent(), info);
        System.out.println(mi); // println ok
        PomInfo pi = mi.createInfo();
        System.out.println("PI: " + pi); // println ok
        MavenAntlrConfiguration pluginInfo = mi.pluginInfo();
        AntlrConfiguration config = FoldersHelperTrampoline.getDefault().newAntlrConfiguration(pluginInfo.antlrImportDir(), pluginInfo.antlrSourceDir(), pluginInfo.antlrOutputDir(),
                pluginInfo.listener(), pluginInfo.visitor(), pluginInfo.atn(), pluginInfo.forceATN(), pluginInfo.includePattern(), pluginInfo.excludePattern(), pluginInfo.encoding(), pluginInfo.buildDir(), "Maven", pluginInfo.isGuessedConfig(), pluginInfo.buildOutput(), pluginInfo.testOutput(), pluginInfo.javaSources(), pluginInfo.testSources());

        System.out.println("FINAL CONFIG:\n" + config); // println ok
    }

    private static final class PomInfo implements Externalizable {

        static final long serialVersionUID = -2023L;
        private static final int rev =1;
        private final Map<String, String> configurationValues = new HashMap<>(32);
        private final Map<Path, Long> lastModified;
        private Charset encoding = Charset.defaultCharset();
        private String buildDir = "target";
        private String buildOutput = "target/classes";
        private String testOutput = "target/test-classes";
        private String sources = "src/main/java";
        static ThreadLocal<Set<Path>> currentlyLoading = ThreadLocal.withInitial(HashSet::new);
        static Set<Path> blacklistedPoms = new HashSet<>();
        private String testSources = "src/test/java";

        PomInfo(Map<Path, Long> lastModified) {
            this.lastModified = lastModified == null ? Collections.emptyMap() : lastModified;
        }

        public PomInfo() {
            this.lastModified = new HashMap<>();
        }

        @Override
        public void writeExternal(ObjectOutput out) throws IOException {
            out.writeInt(1);
            Map<String, Long> lm = new HashMap<>(lastModified.size());
            for (Map.Entry<Path, Long> e : lastModified.entrySet()) {
                lm.put(e.getKey().toString(), e.getValue());
            }
            out.writeObject(lm);
            out.writeObject(configurationValues);
            out.writeUTF(encoding.name());
            out.writeUTF(buildDir);
            out.writeUTF(buildOutput);
            out.writeUTF(testOutput);
            out.writeUTF(sources);
            out.writeUTF(testSources);
        }

        @Override
        public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
            int rev = in.readInt();
            switch(rev) {
                case 1 :
                    Map<String,Long> lm = (Map<String,Long>) in.readObject();
                    for (Map.Entry<String, Long> e : lm.entrySet()) {
                        lastModified.put(Paths.get(e.getKey()), e.getValue());
                    }
                    configurationValues.putAll((Map<String,String>) in.readObject());
                    encoding = Charset.forName(in.readUTF());
                    buildDir = in.readUTF();
                    buildOutput = in.readUTF();
                    testOutput = in.readUTF();
                    sources = in.readUTF();
                    testSources = in.readUTF();
                    break;
                default :
                    throw new IOException("Unknown ser rev " + rev);
            }
        }



        public boolean isUpToDate() {
            if (lastModified.isEmpty()) {
                return false;
            }
            for (Map.Entry<Path, Long> e : lastModified.entrySet()) {
                if (Files.exists(e.getKey())) {
                    try {
                        long current = Files.getLastModifiedTime(e.getKey()).toMillis();
                        if (current > e.getValue()) {
                            return false;
                        }
                    } catch (IOException ex) {
                        return false;
                    }
                }
            }
            return true;
        }

        void include(Map<String, String> info) {
            configurationValues.putAll(info);
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder(512)
                    .append("PomInfo(").append(encoding.name())
                    .append(" configVals ").append(configurationValues.size())
                    .append(' ').append(lastModified)
                    .append(' ');
            for (Map.Entry<String, String> e : configurationValues.entrySet()) {
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append('\'').append(e.getKey()).append('\'').append(" = ").append('\'')
                        .append(e.getValue()).append('\'');
            }
            return sb.append(' ').toString();
        }

        static PomInfo create(Path path) throws Exception {
//            new Exception("Create pom pomInfo: " + path).printStackTrace();
            if (Files.exists(path)) {
                List<PomFileAnalyzer> all = new ArrayList<>();
                Map<Path, Long> watches = new HashMap<>();
                return withParentPoms(path, watches, all, PomInfo::onAllRelativePomsFound);
            }
            return null;
        }

        static void logPoms(List<PomFileAnalyzer> all) throws IOException {
            StringBuilder sb = new StringBuilder();
            for (PomFileAnalyzer a : all) {
                if (sb.length() > 0) {
                    sb.append(" -> ");
                }
                sb.append(a.getArtifactId());
            }
            System.out.println(sb); // println ok
        }

        static void noteDependencies(List<? extends PomFileAnalyzer> l) {
            Path last = null;
            for (PomFileAnalyzer p : l) {
                Path curr = p.projectFolder();
                if (last != null) {
                    ProjectUpdates.pathDependencies(last, curr);
                }
                last = curr;
            }
        }

        static PomInfo createPomInfo(Map<Path, Long> lastModified, List<PomFileAnalyzer> parents, PomFileResolver resolv) throws Exception {
            PomInfo result = new PomInfo(lastModified);
            lastModified.put(PomFileResolver.settingsFile().toPath(), PomFileResolver.settingsFileLastModified());
            if (!parents.isEmpty()) {
                PropertyResolver props = new PropertyResolver(parents.get(0).pomFile().toFile(), resolv, parents.get(0));

                // Ensure that changes in parent projects will trigger a reread of
                // antlr pomInfo
                noteDependencies(parents);

                for (PomFileAnalyzer a : CollectionUtils.reversed(parents)) {
                    PomFileAnalyzer.PluginConfigurationInfo info = a.antlrPluginInfo();
                    // RESOLVE PROPERTIES HERE
                    // EXTRACT LIBDIR, INCLUDES, ENCODING, EXCLUDES
                    Map<String, String> items = info.props;
                    Map<String, String> newItems = new HashMap<>();
                    for (Map.Entry<String, String> e : items.entrySet()) {
                        String key = e.getKey().trim();
                        String value = e.getValue().trim();
                        if (value.contains("${")) {
                            value = props.substituteInto(value);
                            value = value.replaceAll("\\s+", " ");
                        }
                        newItems.put(key, value);
                    }
                    result.include(newItems);
                }

                String enc = result.configurationValues.get("encoding");
                if (enc == null && props != null) {
                    enc = props.substituteInto(props.resolve("project.build.sourceEncoding"));
                }
                if (enc != null) {
                    try {
                        Charset cs = Charset.forName(enc);
                        result.encoding = cs;
                    } catch (Exception ex) {
                        Logger.getLogger(PomInfo.class.getName()).log(Level.INFO,
                                "Could not resolve encoding '" + enc + "'", ex);
                    }
                }
                String buildDir = props.substituteInto(props.resolve("project.build.directory"));
                if (buildDir != null) {
                    result.setBuildDir(buildDir);
                } else {
                    props.addProperty("project.build.directory", "target");
                }
                List<String> keys = new ArrayList<>(result.configurationValues.keySet());
                for (String key : keys) {
                    String val = result.configurationValues.get(key);
                    if (val != null && val.contains("${")) {
                        result.configurationValues.put(key, props.substituteInto(val));
                    }
                }

                String buildOutput = props.substituteInto(props.resolve("project.build.outputDirectory"));
                String testOutput = props.substituteInto(props.resolve("project.build.testOutputDirectory"));
                String testSource = props.substituteInto(props.resolve("project.build.testSourceDirectory"));

                if (buildOutput != null) {
                    result.setBuildOutputDir(buildOutput);
                }
                if (testOutput != null) {
                    result.setTestOutputDir(testOutput);
                }
                if (testSource != null) {
                    result.setTestSourceDir(testSource);
                }

                for (String s : new String[]{ANTLR_PROP_ATN, ANTLR_PROP_FORCE_ATN, ANTLR_PROP_LISTENER, ANTLR_PROP_VISITOR}) {
                    String key = "antlr4." + s;
                    String val = props.resolve(key);
                    if (val != null && !val.isEmpty()) {
                        result.configurationValues.put(s, val);
                    }
                }
            }
            return result;
        }

        static PomInfo onAllRelativePomsFound(PomFileAnalyzer last, Map<Path, Long> lastModified, List<PomFileAnalyzer> parents) throws Exception {
            PomFileResolver resolv = PomFileResolver.context(last.projectFolder());
            return onAllRelativePomsFound(last, lastModified, parents, resolv);
        }

        static PomInfo onAllRelativePomsFound(PomFileAnalyzer last, Map<Path, Long> lastModified, List<PomFileAnalyzer> parents, PomFileResolver resolv) throws Exception {
            logPoms(parents);
            String parentGroup = last.getParentGroupID();
            if (parentGroup != null && !parentGroup.isEmpty()) {
                String parentArtifact = last.getParentArtifactID();
                String parentVersion = last.getParentVersion();
                if (parentArtifact != null) {
                    File resolved = resolv == null ? null : resolv.resolve(parentGroup, parentArtifact, parentVersion);
                    if (resolved != null && !lastModified.containsKey(resolved.toPath())) {
                        lastModified.put(resolved.toPath(), resolved.lastModified());
                        try {
                            PomFileAnalyzer a = new PomFileAnalyzer(resolved);
                            parents.add(a);
                            return onAllRelativePomsFound(a, lastModified, parents, resolv);
                        } catch (IOException ioe) {
                            Logger.getLogger(PomInfo.class.getName()).log(Level.INFO, "Exception reading pom for " + resolved, ioe);
                        }
                    }
                }
            }
            return createPomInfo(lastModified, parents, resolv);
        }


        static PomInfo withParentPoms(Path path, Map<Path, Long> lastModifieds, List<PomFileAnalyzer> all, ThrowingTriFunction<PomFileAnalyzer, Map<Path, Long>, List<PomFileAnalyzer>, PomInfo> onDone) throws Exception {
            if (!Files.exists(path)) {
                if (!all.isEmpty()) {
                    return onDone.apply(all.get(all.size() - 1), lastModifieds, all);
                } else {
                    return null;
                }
            }
            // XXX this code runs a LOT during scanning and could probably benefit
            // from a timed cache for repeatedly looked up chains.
            Set<Path> curr = currentlyLoading.get();
            try {
                if (curr.contains(path) || blacklistedPoms.contains(path)) {
                    return null;
                }
                curr.add(path);
                PomFileAnalyzer a = new PomFileAnalyzer(path.toFile());
                PomInfo result;
                try {
                    lastModifieds.put(path, Files.getLastModifiedTime(path).toMillis());
                    result = a.<PomInfo>inContext(doc -> {
                        all.add(a);
                        String parentGroup = a.getParentGroupID();
                        String parentArtifact = a.getParentArtifactID();
                        String parentVersion = a.getParentVersion();
                        boolean hasParent = parentGroup != null && !parentGroup.isEmpty()
                                && parentArtifact != null && !parentArtifact.isEmpty()
                                && parentVersion != null && !parentVersion.isEmpty();

                        if (hasParent) {
                            String parentRelativePath = a.getParentRelativePath();
                            Path parentPom;
                            if (parentRelativePath == null || parentRelativePath.isEmpty()) {
                                parentPom = path.getParent().getParent().resolve("pom.xml");
                                boolean found = false;
                                if (Files.exists(parentPom) && !Files.isDirectory(parentPom)) {
                                    PomFileAnalyzer test = new PomFileAnalyzer(parentPom.toFile());
                                    if (parentGroup.equals(test.getGroupID()) && parentArtifact.equals(test.getArtifactId())
                                            && parentVersion.equals(test.getVersion())) {
                                        found = true;
                                    }
                                }
                                if (!found) {
                                    PomFileResolver loc = PomFileResolver.localRepo();
                                    File parentPomFile = loc.resolve(parentGroup, parentArtifact, parentVersion);
                                    if (parentPomFile != null) {
                                        parentPom = parentPomFile.toPath();
                                    }
                                }
                            } else {
                                parentPom = "..".equals(parentRelativePath)
                                        || "../pom.xml".equals(parentRelativePath)
                                        ? path.getParent().getParent()
                                        : path.getParent().resolve(parentRelativePath);
                            }

                            if (Files.isDirectory(parentPom)) {
                                parentPom = parentPom.resolve("pom.xml");
                            }
//                            parentPom = parentPom.toFile().getAbsoluteFile().toPath();
                            parentPom = parentPom.toAbsolutePath();
                            boolean recursing = currentlyLoading.get().contains(parentPom);
                            if (!recursing && !blacklistedPoms.contains(parentPom) && !lastModifieds.containsKey(parentPom) && Files.exists(parentPom)) {
                                return withParentPoms(parentPom, lastModifieds, all, onDone);
                            } else {
                                return onDone.apply(a, lastModifieds, all);
                            }
                        } else {
                            return onDone.apply(a, lastModifieds, all == null ? Collections.emptyList() : all);
                        }
                    });
                    return result;
                } finally {
                    curr.remove(path);
                }
            } catch (IOException | StackOverflowError ioe) {
                blacklistedPoms.add(path);
                Logger.getLogger(PomInfo.class.getName()).log(Level.INFO,
                        "Exception processing poms.  Blacklisting " + path,
                        ioe);
            }
            if (!all.isEmpty()) {
                return onDone.apply(all.get(all.size() - 1), lastModifieds, all);
            } else {
                return null;
            }
        }

        private void setBuildDir(String buildDir) {
            if (buildDir != null) {
                this.buildDir = buildDir;
            }
        }

        private void setTestSourceDir(String testSource) {
            if (testSource != null) {
                this.testSources = testSource;
            }
        }

        private void setTestOutputDir(String testOutput) {
            if (testOutput != null) {
                this.testOutput = testOutput;
            }
        }

        private void setBuildOutputDir(String buildOutput) {
            if (buildOutput != null) {
                this.buildOutput = buildOutput;
            }
        }
    }
}

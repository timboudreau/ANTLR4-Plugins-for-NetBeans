package org.nemesis.antlr.project.helpers.maven;

import com.mastfrog.function.throwing.ThrowingTriFunction;
import com.mastfrog.util.collections.CollectionUtils;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.nemesis.antlr.projectupdatenotificaton.ProjectUpdates;
import org.netbeans.api.project.Project;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

/**
 *
 * @author Tim Boudreau
 */
final class MavenInfo {

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

    private final Path projectDir;
    private PomInfo info;

    MavenInfo(Project project) {
        FileObject fo = project.getProjectDirectory();
        projectDir = FileUtil.toFile(fo).toPath();
    }

    private MavenInfo(Path dir, PomInfo pomInfo) {
        this.projectDir = dir;
        this.info = pomInfo;
    }

    Path projectDir() {
        return projectDir;
    }

    static MavenAntlrConfiguration createAntlrConfig(MavenInfo info, Path baseDir) {
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
        Map<String, String> vals = info.getConfigValues(keys, defaults);
        Path buildDir = baseDir.resolve(info.info.buildDir);
        Path importDir = baseDir.resolve(vals.get(ANTLR_PROP_LIB_DIRECTORY));
        Path sourceDir = baseDir.resolve(vals.get(ANTLR_PROP_SOURCE_DIRECTORY));
        Path outDir = buildDir.resolve(vals.get(ANTLR_PROP_OUTPUT_DIRECTORY));
        Path buildOutput = baseDir.resolve(info.info.buildOutput);
        Path testOutput = baseDir.resolve(info.info.testOutput);
        Path sources = baseDir.resolve(info.info.sources);
        Path testSources = baseDir.resolve(info.info.testSources);
        boolean listener = isTrue(vals.get(ANTLR_PROP_LISTENER));
        boolean visitor = isTrue(vals.get(ANTLR_PROP_VISITOR));
        boolean atn = isTrue(vals.get(ANTLR_PROP_ATN));
        boolean forceATN = isTrue(vals.get(ANTLR_PROP_FORCE_ATN));
        String includePattern = vals.get(ANTLR_PROP_INCLUDES);
        String excludePattern = vals.get(ANTLR_PROP_EXCLUDES);
        Charset encoding = info.info.encoding;
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
        PomInfo currentInfo = info();
        for (int i = 0; i < keys.length; i++) {
            String key = keys[i];
            String def = defaults[i];
            String val = currentInfo.configurationValues.getOrDefault(key, def);
            result.put(key, val);
        }
        return result;
    }

    private MavenAntlrConfiguration cachedInfo;

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
        return cachedInfo = createAntlrConfig(this, projectDir);
    }

    public PomInfo info() {
        if (info != null && info.isUpToDate()) {
            return info;
        }
        return info = createInfo();
    }

    private PomInfo createInfo() {
        try {
            info = PomInfo.create(projectDir.resolve("pom.xml"));
        } catch (Exception ex) {
            Logger.getLogger(MavenInfo.class.getName()).log(Level.INFO,
                    "Could not resolve poms for " + projectDir, ex);
            info = new PomInfo(Collections.emptyMap());
        }
        return info;
    }

    public static void main(String[] args) throws Exception {
        Path path = Paths.get("/home/tim/work/foreign/ANTLR4-Plugins-for-NetBeans/antlr-editing-plugins/tokens-file-grammar/pom.xml");
        PomInfo info = PomInfo.create(path);
        MavenInfo mi = new MavenInfo(path.getParent(), info);
    }

    private static final class PomInfo {

        private final Map<String, String> configurationValues = new HashMap<>();
        private final Map<Path, Long> lastModified;
        private Charset encoding = Charset.defaultCharset();
        private String buildDir = "target";
        private String buildOutput = "target/classes";
        private String testOutput = "target/test-classes";
        private String sources = "src/main/java";
        private String testSources = "src/test/java";

        PomInfo(Map<Path, Long> lastModified) {
            this.lastModified = lastModified;
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
            StringBuilder sb = new StringBuilder(512);
            for (Map.Entry<String, String> e : configurationValues.entrySet()) {
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append('\'').append(e.getKey()).append('\'').append(" = ").append('\'')
                        .append(e.getValue()).append('\'');
            }
            return sb.toString();
        }

        static PomInfo create(Path path) throws Exception {
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
            System.out.println(sb);
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
            PropertyResolver props = new PropertyResolver(parents.get(0).pomFile().toFile(), resolv, parents.get(0));

            // Ensure that changes in parent projects will trigger a reread of
            // antlr info
            noteDependencies(parents);

//            logPoms(parents);

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
            if (enc == null) {
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
            return result;
        }

        static PomInfo onAllRelativePomsFound(PomFileAnalyzer last, Map<Path, Long> lastModified, List<PomFileAnalyzer> parents) throws Exception {
            PomFileResolver resolv = PomFileResolver.context(last.projectFolder());
            return onAllRelativePomsFound(last, lastModified, parents, resolv);
        }

        static PomInfo onAllRelativePomsFound(PomFileAnalyzer last, Map<Path, Long> lastModified, List<PomFileAnalyzer> parents, PomFileResolver resolv) throws Exception {
            String parentGroup = last.getParentGroupID();
            if (parentGroup != null && !parentGroup.isEmpty()) {
                String parentArtifact = last.getParentArtifactID();
                String parentVersion = last.getParentVersion();
                if (parentArtifact != null) {
                    File resolved = resolv.resolve(parentGroup, parentArtifact, parentVersion);
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
            PomFileAnalyzer a = new PomFileAnalyzer(path.toFile());
            try {
                lastModifieds.put(path, Files.getLastModifiedTime(path).toMillis());
                return a.<PomInfo>inContext(doc -> {
                    all.add(a);
                    boolean hasParent = a.getParentGroupID() != null;
                    if (hasParent) {
                        String parentRelativePath = a.getParentRelativePath();
                        if (parentRelativePath == null) {
                            parentRelativePath = "..";
                        }
                        Path parentPom = path.getParent().resolve(parentRelativePath);
                        if (Files.isDirectory(parentPom)) {
                            parentPom = parentPom.resolve("pom.xml");
                        }
                        if (Files.exists(parentPom) && !lastModifieds.containsKey(parentPom)) {
                            return withParentPoms(parentPom, lastModifieds, all, onDone);
                        } else {
                            return onDone.apply(a, lastModifieds, all);
                        }
                    } else {
                        return onDone.apply(a, lastModifieds, null);
                    }
                });
            } catch (IOException ioe) {
                Logger.getLogger(PomInfo.class.getName()).log(Level.INFO, "Exception processing poms", ioe);
                if (!all.isEmpty()) {
                    return onDone.apply(all.get(all.size() - 1), lastModifieds, all);
                } else {
                    return null;
                }
            }
        }

        private void setBuildDir(String buildDir) {
            this.buildDir = buildDir;
        }

        private void setTestSourceDir(String testSource) {
            this.testSources = testSource;
        }

        private void setTestOutputDir(String testOutput) {
            this.testOutput = testOutput;
        }

        private void setBuildOutputDir(String buildOutput) {
            this.buildOutput = buildOutput;
        }
    }
}

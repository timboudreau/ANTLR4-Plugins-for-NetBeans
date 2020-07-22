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
package org.nemesis.antlr.project.impl;

import com.mastfrog.function.state.Bool;
import com.mastfrog.util.collections.CollectionUtils;
import com.mastfrog.util.collections.IntList;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.nemesis.antlr.project.Folders;
import org.nemesis.antlr.project.spi.FolderQuery;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectUtils;
import org.netbeans.api.project.SourceGroup;
import org.netbeans.api.project.Sources;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.Exceptions;

/**
 *
 * @author Tim Boudreau
 */
public class InferredConfig {

    // It would require adding some painful friend dependencies access
    // all of these constants directly - see JavaProjectConstants
    private static final String JAVA = "java";
    private static final String RESOURCES = "resources";
    private static final String MODULE = "modules";
    private static final String ANTLR = "antlr";
    private static final String ANTLR4 = "antlr4";
    private static final String OLD_ANTLR_MODULE_SOURCE_GROUP = "Source ANTLR grammars";

    private final Path projectDir;
    private final Map<Folders, Set<Path>> inferences = CollectionUtils.supplierMap(HashSet::new);
    private boolean fullScanPerformed;

    InferredConfig(Project project, Folders initialQueryTarget, FolderQuery initialQuery) {
        projectDir = FileUtil.toFile(project.getProjectDirectory()).toPath();
        Sources sources = ProjectUtils.getSources(project);
        if (sources != null) {
            SourceGroupsInferencer inferencer = new SourceGroupsInferencer(initialQuery.relativeTo());
            inferencer.processSourceGroups(JAVA, sources.getSourceGroups(JAVA));
            inferencer.processSourceGroups(RESOURCES, sources.getSourceGroups(RESOURCES));
            inferencer.processSourceGroups(JAVA, sources.getSourceGroups(MODULE));
            inferencer.processSourceGroups(ANTLR, sources.getSourceGroups(OLD_ANTLR_MODULE_SOURCE_GROUP));
            inferencer.processSourceGroups(ANTLR, sources.getSourceGroups(ANTLR));
            inferencer.processSourceGroups(ANTLR, sources.getSourceGroups(ANTLR4));
            inferencer.finishWithSourceGroups(initialQuery);
            for (Map.Entry<Folders, Set<Path>> e : inferencer.inferences.entrySet()) {
                if (e.getKey() != null && e.getValue() != null && !e.getValue().isEmpty()) {
                    inferences.put(e.getKey(), e.getValue());
                }
            }
        }
        if (initialQuery.relativeTo() != null) {
            try {
                scan(initialQuery.relativeTo());
                fullScanPerformed = true;
            } catch (IOException ex) {
                Exceptions.printStackTrace(ex);
            }
        }
    }
    private static final Map<Project, InferredConfig> configs = new WeakHashMap<>();

    static synchronized InferredConfig get(Project project, Folders initialQueryTarget, FolderQuery query) {
        InferredConfig config = configs.get(project);
        if (config == null) {
            config = new InferredConfig(project, initialQueryTarget, query);
            configs.put(project, config);
        }
        return config;
    }

    private void inferencesImproved() {
        FoldersHelperTrampoline.getDefault().evictConfiguration(projectDir);
    }

    Iterable<Path> query(Folders folder, FolderQuery q) {
        if (!fullScanPerformed && q.relativeTo() != null) {
            try {
                boolean changed = scan(q.relativeTo());
                if (changed) {
                    inferencesImproved();
                }
            } catch (IOException ex) {
                Exceptions.printStackTrace(ex);
            }
        }
        return inferences.get(folder);
    }

    private static class SourceGroupsInferencer {

        private final Map<Folders, Set<Path>> inferences = CollectionUtils.supplierMap(HashSet::new);
        private final Map<Path, String> rootTypes = new HashMap<>();
        private final Map<String, Set<Path>> rootsForType = CollectionUtils.supplierMap(HashSet::new);
        private final Map<Path, String> displayNameForGroup = new HashMap<>();
        private final Set<Path> probablyGenerated = new HashSet<>();
        private final Path projectDir;
        private final Map<Path, String> groupNameForPath = new HashMap<>();

        SourceGroupsInferencer(Path projectDir) {
            this.projectDir = projectDir;
        }

        void processSourceGroups(String category, SourceGroup[] groups) {
            if (groups != null && groups.length > 0) {
                for (SourceGroup sg : groups) {
                    FileObject fo = sg.getRootFolder();
                    if (fo != null) {
                        Path path = toPath(fo);
                        if (path != null) {
                            rootTypes.put(path, category);
                            groupNameForPath.put(path, sg.getName());
                            rootsForType.get(category).add(path);
                            if (sg.getClass().getSimpleName().equals("GeneratedSourceGroup")) {
                                probablyGenerated.add(path);
                            }
                        }
                    }
                }
            }
        }

        private boolean isProbableGenerated(Path path) {
            if (probablyGenerated.contains(path)) {
                return true;
            }
            String nm = groupNameForPath.getOrDefault(path, "");
            boolean result = nm.toLowerCase().contains("gen");
            if (!result) {
                nm = displayNameForGroup.getOrDefault(path, "");
                result = nm.toLowerCase().contains("gen");
            }
            return result;

        }

        private boolean isProbableTest(Path path) {
            String nm = groupNameForPath.getOrDefault(path, "");
            boolean result = nm.toLowerCase().contains("test");
            if (!result) {
                nm = displayNameForGroup.getOrDefault(path, "");
                result = nm.toLowerCase().contains("test");
            }
            return result;
        }

        private void finishWithSourceGroups(FolderQuery query) {
            boolean queryFileConsumed = false;
            for (Map.Entry<String, Set<Path>> e : rootsForType.entrySet()) {
                Set<Path> all = e.getValue();
                if (all.size() == 1) {
                    switch (e.getKey()) {
                        case MODULE:
                        case JAVA:
                            Path p = all.iterator().next();
                            if (query.relativeTo() != null && isGrammarFile(query.relativeTo()) && query.relativeTo().startsWith(p)) {
                                Path rel = p.relativize(projectDir);
                                if (looksLikeImportChild(rel)) {
                                    queryFileConsumed = true;
                                    inferences.get(Folders.ANTLR_IMPORTS).add(p);
                                } else {
                                    queryFileConsumed = true;
                                    inferences.get(Folders.ANTLR_GRAMMAR_SOURCES).add(p);
                                }
                            } else {
                                inferences.get(Folders.JAVA_SOURCES).add(all.iterator().next());
                            }
                            break;
                        case RESOURCES:
                            inferences.get(Folders.RESOURCES).add(all.iterator().next());
                            break;
                        case ANTLR:
                            inferences.get(Folders.ANTLR_GRAMMAR_SOURCES).add(all.iterator().next());
                            break;
                    }
                } else {
                    for (Path p : all) {
                        Path rel = p.relativize(projectDir);
                        switch (e.getKey()) {
                            case MODULE:
                            case JAVA:
                                boolean looksLikeTests = containsName(rel, "test", "unit") || displayNameForGroup.getOrDefault(p, "").toLowerCase().contains("test");
                                boolean looksLikeGenerated = probablyGenerated.contains(p)
                                        || containsName(rel, "gen", "generated", "generated-sources")
                                        || displayNameForGroup.getOrDefault(p, "").toLowerCase().contains("gener");
                                if (query.relativeTo() != null && isGrammarFile(query.relativeTo()) && query.relativeTo().startsWith(p)) {
                                    if (looksLikeImportChild(rel)) {
                                        queryFileConsumed = true;
                                        inferences.get(Folders.ANTLR_IMPORTS).add(p);
                                    } else {
                                        queryFileConsumed = true;
                                        assert p != null;
                                        assert inferences.get(Folders.ANTLR_GRAMMAR_SOURCES) != null;
                                        inferences.get(Folders.ANTLR_GRAMMAR_SOURCES).add(p);
                                    }
                                    continue;
                                } else {
                                    if (looksLikeTests) {
                                        inferences.get(Folders.JAVA_TEST_SOURCES).add(p);
                                    } else if (looksLikeGenerated) {
                                        inferences.get(Folders.JAVA_GENERATED_SOURCES).add(p);
                                    } else {
                                        inferences.get(Folders.JAVA_SOURCES).add(p);
                                    }
                                }
                                break;
                            case ANTLR:
                                if (containsName(p, "import", "imports")) {
                                    inferences.get(Folders.ANTLR_IMPORTS).add(p);
                                } else {
                                    inferences.get(Folders.ANTLR_GRAMMAR_SOURCES).add(p);
                                }
                                break;
                            case RESOURCES:
                                if (containsName(p, "test", "tests")) {
                                    inferences.get(Folders.TEST_RESOURCES).add(p);
                                } else {
                                    inferences.get(Folders.RESOURCES).add(p);
                                }
                                break;
                        }
                    }
                }
            }
        }
    }

    private boolean scan(Path queriedFor) throws IOException {
        Map<Folders, Set<Path>> copy = new EnumMap<>(inferences);
        SubtreeScanner scanner = scanToProjectRoot(projectDir, queriedFor);
        Set<Path> sourceFiles = scanner.foldersForExtensions.get("java");
        if (!sourceFiles.isEmpty()) {
            for (Path p : sourceFiles) {
                String pkg = scanner.packageForDir.get(p);
                if (pkg != null) {
                    String[] parts = pkg.split("\\.");
                    Path p1 = p;
                    for (int i = parts.length-1; i >= 0; i--) {
                        if (p1.getFileName().toString().equals(parts[i])) {
                            p1 = p1.getParent();
                        }
                    }
                    if (p1 != p && p1 != null) {
                        Bool found = Bool.create();
                        Path pathRoot = p1;
                        Files.list(projectDir.resolve(p)).forEach(child -> {
                            found.ifUntrue(() -> {
                                String nm = child.getFileName().toString();
                                if (nm.endsWith("Parser.java") || nm.endsWith("Lexer.java")) {
                                    found.set();
                                    inferences.get(Folders.JAVA_GENERATED_SOURCES).clear();
                                    inferences.get(Folders.JAVA_GENERATED_SOURCES).add(pathRoot);
                                }
                                if (nm.endsWith("Test.java") || nm.startsWith("Test")) {
                                    found.set();
                                    inferences.get(Folders.JAVA_TEST_SOURCES).add(pathRoot);
                                }
                            });
                        });
                        found.ifUntrue(() -> {
                            inferences.get(Folders.JAVA_SOURCES).add(pathRoot);
                        });
                    }
                }
            }
        }
        return !copy.equals(inferences);
    }

    static SubtreeScanner scanToProjectRoot(Path projectRoot, Path queriedFile) throws IOException {
        Set<Path> scanned = new HashSet<>();
        SubtreeScanner scanner = new SubtreeScanner(projectRoot, scanned);
        if (!Files.isDirectory(queriedFile)) {
            queriedFile = queriedFile.getParent();
        }
        while (queriedFile != null && !projectRoot.getParent().equals(queriedFile)) {
            scanner.reset();
            Files.walkFileTree(queriedFile, EnumSet.of(FileVisitOption.FOLLOW_LINKS), 12, scanner);
            queriedFile = queriedFile.getParent();
        }
        return scanner;
    }

    public static void main(String[] args) throws IOException {
        Path test = Paths.get("/home/tim/work/foreign/ANTLR4-Plugins-for-NetBeans/ANTLRTestProjects/antbased/Options/src/anotherorg/anotherpackage/MySuperClass2.java");
        Path root = Paths.get("/home/tim/work/foreign/ANTLR4-Plugins-for-NetBeans/ANTLRTestProjects/antbased/Options");
//        Path test = Paths.get("/home/tim/work/foreign/ANTLR4-Plugins-for-NetBeans/antlr-editing-plugins/antlr-language-grammar/src/main/antlr4/org/nemesis/antlr/ANTLRv4Lexer.g4");
//        Path root = Paths.get("/home/tim/work/foreign/ANTLR4-Plugins-for-NetBeans/antlr-editing-plugins/antlr-language-grammar");
        scanToProjectRoot(root, test);
    }

    static class SubtreeScanner implements FileVisitor<Path> {

        private final Path projectRoot;
        private final Set<Path> scannedDirs;
        private final IntList countStack = IntList.create();
        private final Map<Path, Integer> fileCountForDir = new HashMap<>();
        private final Map<Path, Set<String>> extensionsInDir = CollectionUtils.supplierMap(HashSet::new);
        private final Map<String, Set<Path>> foldersForExtensions = CollectionUtils.supplierMap(HashSet::new);
        private final Map<Path, String> packageForDir = new HashMap<>();

        public SubtreeScanner(Path projectRoot, Set<Path> scannedDirs) {
            this.projectRoot = projectRoot;
            this.scannedDirs = scannedDirs;
        }

        void reset() {
            countStack.clear();
        }

        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
            if (scannedDirs.contains(dir)) {
                return FileVisitResult.SKIP_SUBTREE;
            }
            countStack.add(0);
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            if (attrs.isRegularFile() || attrs.isSymbolicLink()) {
                int countIndex = countStack.size() - 1;
                countStack.set(countIndex, countStack.get(countIndex) + 1);
                String ext = extension(file);
                if (ext != null) {
                    Path rel = projectRoot.relativize(file.getParent());
                    extensionsInDir.get(rel).add(ext);
                    foldersForExtensions.get(ext).add(rel);
                    if ("java".equals(ext) && !packageForDir.containsKey(rel)) {
                        String pk = scanForPackage(file);
                        if (pk != null) {
                            packageForDir.put(rel, pk);
                        }
                    }
                }
            }
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
            if (exc != null) {
                return FileVisitResult.TERMINATE;
            }
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
            if (exc != null) {
                return FileVisitResult.TERMINATE;
            }
            if (scannedDirs.contains(dir)) {
                return FileVisitResult.CONTINUE;
            }
            scannedDirs.add(dir);
            int fileCount = countStack.last();
            fileCountForDir.put(projectRoot.relativize(dir), fileCount);
            countStack.removeAt(countStack.size() - 1);
            return FileVisitResult.CONTINUE;
        }

        private List<Path> sortedPaths(Set<Path> paths) {
            List<Path> result = new ArrayList<>(paths);
            Collections.sort(result, (Path a, Path b) -> {
                return Integer.compare(a.getNameCount(), b.getNameCount());
            });
            return result;
        }

        public String toString() {
            StringBuilder sb = new StringBuilder();
            List<Path> sortedFileCounts = sortedPaths(fileCountForDir.keySet());
            for (Path p : sortedFileCounts) {
                int count = fileCountForDir.get(p);
                Set<String> exts = extensionsInDir.get(p);
                String javaPk = packageForDir.get(p);
                if (javaPk != null || count > 0 || exts != null && !exts.isEmpty()) {
                    sb.append(p).append('\t').append(count).append("\t").append(exts)
                            .append(" ").append(javaPk)
                            .append('\n');
                }
            }
            return sb.toString();
        }

        private static final Pattern PKG_PATTERN = Pattern.compile(".*package\\s+(\\S+)\\s*;");

        private String scanForPackage(Path file) throws IOException {
            // Keep the buffer size small - usually it will be in the first
            // few lines
            try (Scanner scanner = new Scanner(
                    new BufferedInputStream(Files.newInputStream(file,
                            StandardOpenOption.READ), 256), UTF_8)) {
                // will not match a comment after the package statement,
                // or split across multiple lines - but this *is*
                // best-effort
                String line;
                while (scanner.hasNext()) {
                    line = scanner.nextLine();
                    Matcher m = PKG_PATTERN.matcher(line);
                    if (m.find()) {
                        return m.group(1);
                    }
                }
            }
            return null;
        }
    }

    private static Path toPath(FileObject fo) {
        File file = FileUtil.toFile(fo);
        return file != null ? file.toPath() : null;
    }

    private static boolean containsName(Path path, String... names) {
        boolean result = false;
        for (Path p : path) {
            for (String name : names) {
                result = p.toString().equalsIgnoreCase(name);
                if (result) {
                    break;
                }
            }
            if (result) {
                break;
            }
        }
        return result;
    }

    private static boolean looksLikeImportChild(Path rel) {
        Path curr = null;
        for (Path name : rel) {
            if (curr == null) {
                curr = name;
            } else {
                curr = curr.resolve(name);
            }
            if ("imports".equals(name) || "import".equals(name)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isGrammarFile(Path path) {
        String name = path.getFileName().toString();
        return name.endsWith(".g4") || name.endsWith(".g");
    }

    private static boolean isJavaSourceFile(Path path) {
        String name = path.getFileName().toString();
        return name.endsWith(".java") || name.endsWith(".groovy");
    }

    private static Path probableImportDirParent(Path root, Path testFile) {
        testFile = testFile.getParent();
        while (testFile != null && !testFile.equals(root)) {
            String nm = testFile.getFileName().toString();
            switch (nm) {
                case "import":
                case "imports":
                    return testFile;
            }
        }
        return null;
    }

    private static String extension(Path file) {
        Path p = file.getFileName();
        String s = p.toString();
        int ix = s.lastIndexOf('.');
        if (ix > 0 && ix < s.length() - 1) {
            return s.substring(ix + 1);
        }
        return null;
    }

}

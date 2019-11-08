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
package com.mastfrog.type.code.generation;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import javax.xml.xpath.XPathExpressionException;

/**
 *
 * @author Tim Boudreau
 */
abstract class PomFileResolver {

    public abstract File resolve(String groupID, String artifactID, String version);

    public final File resolve(String groupID, String artifactID) {
        return resolve(groupID, artifactID, null);
    }

    public PomFileResolver add(PomFileResolver next) {
        return new MetaResolver(this, next);
    }

    public static PomFileResolver context(Path ctx) throws IOException {
        return new ContextPomFileResolver(ctx);
    }

    public static PomFileResolver localRepo(File repoDir) {
        return new LocalRepoResolver(repoDir);
    }

    public static PomFileResolver localRepo() {
        return localRepo(localRepoFolder());
    }

    private static File localRepoFolder;

    public static long settingsFileLastModified() {
        File f = settingsFile();
        if (f.exists()) {
            return f.lastModified();
        }
        return 0;
    }

    public static File settingsFile() {
        Path home = Paths.get(System.getProperty("user.home"));
        if (Files.exists(home)) {
            Path m2 = home.resolve(".m2");
            if (Files.exists(m2)) {
                Path repo = m2.resolve("repository");
                Path settings = m2.resolve("settings.xml");
                if (Files.exists(settings)) {
                    return settings.toFile();
                }
            }
        }
        return home.resolve(".m2/settings.xml").toFile();
    }

    public static File localRepoFolder() {
        if (localRepoFolder != null) {
            return localRepoFolder;
        }
        Path home = Paths.get(System.getProperty("user.home"));
        if (Files.exists(home)) {
            Path m2 = home.resolve(".m2");
            if (Files.exists(m2)) {
                Path repo = m2.resolve("repository");
                Path settings = m2.resolve("settings.xml");
                if (Files.exists(settings)) {
                    try {
                        Path pathFromSettings = new MavenSettingsFile(settings).getLocalRepoLocation();
                        if (pathFromSettings != null) {
                            repo = pathFromSettings;
                        }
                    } catch (IOException | XPathExpressionException ex) {
                        Logger.getLogger(PomFileResolver.class.getName()).log(Level.INFO,
                                "Could not resolve local repo from settings file " + settings);
                    }
                    return localRepoFolder = repo.toFile();
                }
            }
        }
        return home.resolve(".m2/repository").toFile();
    }

    static void findFiles(List<? super Path> entries, String name, Path root) throws IOException {
        try (Stream<Path> str = Files.walk(root, FileVisitOption.FOLLOW_LINKS)) {
            str.filter(pth -> {
                return name.equals(pth.getFileName().toString());
            }).forEach(entries::add);
        }
    }

    private static class ContextPomFileResolver extends PomFileResolver {

        private final Map<String, File> files = new HashMap<>();

        ContextPomFileResolver(Path root) throws IOException {
            List<Path> entries = new ArrayList<>(5);
            findFiles(entries, "pom.xml", root);
            for (Path fe : entries) {
                PomFileAnalyzer ana = new PomFileAnalyzer(fe.toFile());
                String key = ana.getGroupID() + ':' + ana.getArtifactId();
                files.put(key, fe.toFile());
                if (ana.getVersion() != null) {
                    key = key + ':' + ana.getVersion();
                    files.put(key, fe.toFile());
                }
            }
        }

        @Override
        public File resolve(String groupID, String artifactID, String version) {
            String key = groupID + ":" + artifactID + (version == null ? "" : ':' + version);
            if (groupID == null || groupID.isEmpty()) {
                throw new IllegalArgumentException(groupID + ":" + artifactID + ":" + version);
            }
//            System.out.println("Resolve '" + key + "'\t" + files.get(key));
//            if (files.get(key) == null) {
//                for (String k : files.keySet()) {
//                    System.out.println("   '" + k + "'");
//                }
//            }
            return files.get(key);
        }
    }

    private static class LocalRepoResolver extends PomFileResolver {

        private final File repoDir;

        LocalRepoResolver(File repoDir) {
            this.repoDir = repoDir;
        }

        @Override
        public File resolve(String groupID, String artifactID, String version) {
            String path = groupID.replace('.', '/');
            File loc = new File(repoDir, path);
            if (loc.exists() && loc.isDirectory()) {
                loc = new File(loc, artifactID);
            } else {
                return null;
            }
            loc = new File(loc, version);
            if (!loc.exists() || !loc.isDirectory()) {
                return null;
            }
            File pom = new File(loc, artifactID + '-' + version + ".pom");
            return pom.exists() && pom.isFile() ? pom : null;
        }
    }

    private static final class MetaResolver extends PomFileResolver {

        private final List<PomFileResolver> delegates = new ArrayList<>();

        MetaResolver(PomFileResolver... analyzers) {
            delegates.addAll(Arrays.asList(analyzers));
        }

        @Override
        public PomFileResolver add(PomFileResolver next) {
            delegates.add(next);
            return this;
        }

        @Override
        public File resolve(String parentGroupID, String parentArtifactID, String version) {
            for (PomFileResolver res : delegates) {
                File result = res.resolve(parentGroupID, parentArtifactID, version);
                if (result != null) {
                    return result;
                }
            }
            return null;
        }
    }

    /*
    public static String nodeToString(Document document) throws IOException {
        return nodeToString(document, 4);
    }

    public static String nodeToString(Document document, int indent) throws IOException {
        OutputFormat format = new OutputFormat(document);
        format.setStandalone(true);
        format.setPreserveEmptyAttributes(true);
        format.setAllowJavaNames(true);
        format.setPreserveSpace(true);
        format.setIndent(indent);
        Writer out = new StringWriter();
        XMLSerializer serializer = new XMLSerializer(out, null);
        serializer.serialize(document);
        return out.toString() + '\n';
    }
     */
}

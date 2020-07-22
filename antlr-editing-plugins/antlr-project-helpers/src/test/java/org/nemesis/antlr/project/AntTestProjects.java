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
package org.nemesis.antlr.project;

import com.mastfrog.util.collections.CollectionUtils;
import static com.mastfrog.util.collections.CollectionUtils.map;
import com.mastfrog.util.path.UnixPath;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.nemesis.antlr.project.AntTestProjects.GrammarFileEntry;
import org.netbeans.api.project.Project;
import org.netbeans.modules.java.j2seproject.J2SEProject;
import org.netbeans.modules.project.ant.AntBasedProjectFactorySingleton;
import static org.netbeans.modules.project.ant.AntBasedProjectFactorySingleton.LOG;
import static org.netbeans.modules.project.ant.AntBasedProjectFactorySingleton.PROJECT_NS;
import org.netbeans.modules.project.ant.ProjectXMLCatalogReader;
import org.netbeans.modules.project.ant.ProjectXMLKnownChecksums;
import org.netbeans.spi.project.FileOwnerQueryImplementation;
import org.netbeans.spi.project.ProjectState;
import org.netbeans.spi.project.support.ant.AntBasedProjectType;
import org.netbeans.spi.project.support.ant.AntProjectHelper;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.BaseUtilities;
import org.openide.util.Exceptions;
import org.openide.util.NbBundle;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 *
 * @author Tim Boudreau
 */
public enum AntTestProjects implements Iterable<GrammarFileEntry> {

    Channels(
            new GrammarFileEntry(
                    UnixPath.get("grammar"),
                    UnixPath.get("LexerGrammarWithChannels.g4"))),
    CodeCompletion(
            new GrammarFileEntry(
                    UnixPath.get("grammar/org/mypackage"),
                    UnixPath.get("CombinedGrammar.g4")),
            new GrammarFileEntry(
                    UnixPath.get("grammar/imports"),
                    UnixPath.get("ImportedLexerGrammar.g4")),
            new GrammarFileEntry(
                    UnixPath.get("grammar/imports"),
                    UnixPath.get("ImportedLexerGrammar2.g4")),
            new GrammarFileEntry(
                    UnixPath.get("grammar/org/mypackage"),
                    UnixPath.get("LexerGrammar.g4")),
            new GrammarFileEntry(
                    UnixPath.get("grammar/org/mypackage"),
                    UnixPath.get("ParserGrammar.g4"))),
    CodeFolding(
            new GrammarFileEntry(
                    UnixPath.get("grammar"),
                    UnixPath.get("Action.g4")),
            new GrammarFileEntry(
                    UnixPath.get("grammar"),
                    UnixPath.get("BlockComment.g4"))),
    GlobalActions(
            new GrammarFileEntry(
                    UnixPath.get("grammar"),
                    UnixPath.get("GlobalCombinedActions1.g4")),
            new GrammarFileEntry(
                    UnixPath.get("grammar"),
                    UnixPath.get("GlobalLexerActions1.g4")),
            new GrammarFileEntry(
                    UnixPath.get("grammar"),
                    UnixPath.get("GlobalLexerActions2.g4")),
            new GrammarFileEntry(
                    UnixPath.get("grammar"),
                    UnixPath.get("GlobalLexerActions3.g4")),
            new GrammarFileEntry(
                    UnixPath.get("grammar"),
                    UnixPath.get("GlobalParserActions1.g4")),
            new GrammarFileEntry(
                    UnixPath.get("grammar"),
                    UnixPath.get("GlobalParserActions2.g4")),
            new GrammarFileEntry(
                    UnixPath.get("grammar"),
                    UnixPath.get("GlobalParserActions3.g4"))),
    Grammars(
            new GrammarFileEntry(
                    UnixPath.get("grammar"),
                    UnixPath.get("CombinedGrammar.g4")),
            new GrammarFileEntry(
                    UnixPath.get("grammar/imports"),
                    UnixPath.get("ImportedCombinedGrammar.g4")),
            new GrammarFileEntry(
                    UnixPath.get("grammar/imports"),
                    UnixPath.get("ImportedLexerGrammar.g4")),
            new GrammarFileEntry(
                    UnixPath.get("grammar/imports"),
                    UnixPath.get("ImportedParserGrammar.g4")),
            new GrammarFileEntry(
                    UnixPath.get("grammar"),
                    UnixPath.get("LexerGrammar.g4")),
            new GrammarFileEntry(
                    UnixPath.get("grammar"),
                    UnixPath.get("ParserGrammar.g4"))),
    LexerRules(
            new GrammarFileEntry(
                    UnixPath.get("grammar"),
                    UnixPath.get("FragmentDeclarations.g4")),
            new GrammarFileEntry(
                    UnixPath.get("grammar/imports"),
                    UnixPath.get("ImportedLexerGrammar.g4")),
            new GrammarFileEntry(
                    UnixPath.get("grammar"),
                    UnixPath.get("SemanticPredicate.g4")),
            new GrammarFileEntry(
                    UnixPath.get("grammar"),
                    UnixPath.get("TokenDeclarationWithLabel.g4")),
            new GrammarFileEntry(
                    UnixPath.get("grammar"),
                    UnixPath.get("TokenDeclarations.g4")),
            new GrammarFileEntry(
                    UnixPath.get("grammar"),
                    UnixPath.get("TokenWithAction.g4")),
            new GrammarFileEntry(
                    UnixPath.get("grammar"),
                    UnixPath.get("TokenWithLexerCommands.g4"))),
    Options(
            new GrammarFileEntry(
                    UnixPath.get("grammar"),
                    UnixPath.get("AllPossibleOptionsOutOfPackage.g4")),
            new GrammarFileEntry(
                    UnixPath.get("grammar/myorg/mypackage"),
                    UnixPath.get("AllPossibleOptionsWithinAPackage1.g4")),
            new GrammarFileEntry(
                    UnixPath.get("grammar/myorg/mypackage"),
                    UnixPath.get("AllPossibleOptionsWithinAPackage2.g4"))),
    ParserRules(
            new GrammarFileEntry(
                    UnixPath.get("grammar"),
                    UnixPath.get("LabelledAlternatives.g4")),
            new GrammarFileEntry(
                    UnixPath.get("grammar"),
                    UnixPath.get("LocalsInitAfter.g4")),
            new GrammarFileEntry(
                    UnixPath.get("grammar"),
                    UnixPath.get("ParserRuleElementAssocOption.g4")),
            new GrammarFileEntry(
                    UnixPath.get("grammar"),
                    UnixPath.get("ParserRuleElementFailOption.g4")),
            new GrammarFileEntry(
                    UnixPath.get("grammar"),
                    UnixPath.get("ParserRuleOptions.g4")),
            new GrammarFileEntry(
                    UnixPath.get("grammar"),
                    UnixPath.get("ReturnsLabels.g4")),
            new GrammarFileEntry(
                    UnixPath.get("grammar"),
                    UnixPath.get("RuleExceptionManagement.g4"))),
    Tokens(new GrammarFileEntry(
            UnixPath.get("grammar"),
            UnixPath.get("GrammarWithTokens.g4")));

    private final GrammarFileEntry[] entries;

    AntTestProjects(GrammarFileEntry... entries) {
        this.entries = entries;
    }

    @Override
    public Iterator<GrammarFileEntry> iterator() {
        return CollectionUtils.toIterator(entries);
    }

    private Project project;
    private static AntBasedProjectType projectType;
    static Set<Project> projects = new HashSet<>();

    static AntBasedProjectType projectType() {
        if (projectType != null) {
            return projectType;
        }
        Map<String, Object> map = map("type").to(J2SEProject.TYPE)
                .map("iconResource").to("org/netbeans/modules/java/j2seproject/ui/resources/j2seProject.png")
                .map("sharedName").to("data")
                .map("sharedNamespace").to(J2SEProject.PROJECT_CONFIGURATION_NAMESPACE)
                .map("privateName").to("data")
                .map("privateNamespace").to("http://www.netbeans.org/ns/j2se-project-private/1")
                .map("className").to(J2SEProject.class.getName())
                .build();
        return projectType = AntBasedProjectFactorySingleton.create(map);
    }

    public Project project() throws URISyntaxException, IOException, SAXException {
        if (project != null) {
            return project;
        }
        System.out.println("DIR IS " + dir());
        System.out.println("PROJECT XML " + projectXML() + " for " + name());
        Document doc = loadProjectXml(projectXML().toAbsolutePath().toFile());
        assertNotNull(doc, "Loaded null for doc " + dir().resolve("build.xml").toAbsolutePath());
        AntProjectHelper helper = AntBasedProjectFactorySingleton.HELPER_CALLBACK.createHelper(projectDir(), doc, new ProjectStateImpl(), projectType());
        Project result = project = new J2SEProject(helper);
        projects.add(result);
        return result;
    }

    public Path projectXML() throws URISyntaxException, IOException {
        return dir().resolve("nbproject/project.xml");
    }

    private Document loadProjectXml(File projectDiskFile) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        InputStream is = new FileInputStream(projectDiskFile);
        try {
            FileUtil.copy(is, baos);
        } finally {
            is.close();
        }
        byte[] data = baos.toByteArray();
        InputSource src = new InputSource(new ByteArrayInputStream(data));
        src.setSystemId(BaseUtilities.toURI(projectDiskFile).toString());
        try {
//            Document projectXml = XMLUtil.parse(src, false, true, Util.defaultErrorHandler(), null);
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder;
            try {
                builder = factory.newDocumentBuilder();
            } catch (ParserConfigurationException x) {
                throw new SAXException(x);
            }
            builder.setErrorHandler(org.openide.xml.XMLUtil.defaultErrorHandler());
            Document projectXml = builder.parse(src);
            LOG.finer("parsed document");
//            dumpFields(projectXml);
            Element projectEl = projectXml.getDocumentElement();
            LOG.finer("got document element");
//            dumpFields(projectXml);
//            dumpFields(projectEl);
            String namespace = projectEl.getNamespaceURI();
            LOG.log(Level.FINER, "got namespace {0}", namespace);
            if (!PROJECT_NS.equals(namespace)) {
                LOG.log(Level.FINE, "{0} had wrong root element namespace {1} when parsed from {2}",
                        new Object[]{projectDiskFile, namespace, baos});
//                dumpFields(projectXml);
//                dumpFields(projectEl);
                System.out.println("PROJECT NS NOT NAMESPACE");
                return null;
            }
            if (!"project".equals(projectEl.getLocalName())) { // NOI18N
                LOG.log(Level.FINE, "{0} had wrong root element name {1} when parsed from {2}",
                        new Object[]{projectDiskFile, projectEl.getLocalName(), baos});
                System.out.println("WRONT ROOT ELEMENT");
                return null;
            }
            ProjectXMLKnownChecksums checksums = new ProjectXMLKnownChecksums();
            if (!checksums.check(data)) {
                LOG.log(Level.FINE, "Validating: {0}", projectDiskFile);
                try {
                    ProjectXMLCatalogReader.validate(projectEl);
                    checksums.save();
                } catch (SAXException x) {
                    Element corrected = ProjectXMLCatalogReader.autocorrect(projectEl, x);
                    if (corrected != null) {
                        projectXml.replaceChild(corrected, projectEl);
                        // Try to correct on disk if possible.
                        // (If not, any changes from the IDE will write out a corrected file anyway.)
                        if (projectDiskFile.canWrite()) {
                            OutputStream os = new FileOutputStream(projectDiskFile);
                            try {
                                org.openide.xml.XMLUtil.write(projectXml, os, "UTF-8");
                            } finally {
                                os.close();
                            }
                        }
                    } else {
                        throw x;
                    }
                }
            }
            return projectXml;
        } catch (SAXException e) {
            IOException ioe = new IOException(projectDiskFile + ": " + e, e);
            String msg = e.getMessage().
                    // org/apache/xerces/impl/msg/XMLSchemaMessages.properties validation (3.X.4)
                    replaceFirst("^cvc-[^:]+: ", ""). // NOI18N
                    replaceAll("http://www.netbeans.org/ns/", ".../"); // NOI18N
            Exceptions.attachLocalizedMessage(ioe, NbBundle.getMessage(AntBasedProjectFactorySingleton.class,
                    "AntBasedProjectFactorySingleton.parseError",
                    projectDiskFile.getName(), msg));
            throw ioe;
        }
    }

    public FileObject projectDir() throws URISyntaxException, IOException {
        return FileUtil.toFileObject(FileUtil.normalizeFile(dir().toAbsolutePath().toFile()));
    }

    public Path buildFile() throws URISyntaxException, IOException {
        Path result = dir().resolve("build.xml");
        assert Files.exists(result);
        return result;
    }

    public Set<String> names() {
        Set<String> result = new TreeSet<>();
        for (GrammarFileEntry ge : entries) {
            result.add(ge.name());
        }
        return result;
    }

    public FileObject fileObject(String name) throws URISyntaxException, IOException {
        return FileUtil.toFileObject(FileUtil.normalizeFile(file(name).toFile()));
    }

    public Path file(String name) throws URISyntaxException, IOException {
        for (GrammarFileEntry entry : entries) {
            if (entry.filename.rawName().equals(name) || entry.filename.toString().equals(name)) {
                Path path = entry.resolve(dir());
                if (!Files.exists(path)) {
                    throw new IOException("Not present: " + path);
                }
                return path;
            }
        }
        throw new IllegalArgumentException("No such file " + name + " in " + name());
    }

    public Path dir() throws URISyntaxException, IOException {
        Path testHelperDir = ProjectTestHelper.projectBaseDir();
        Path rel = testHelperDir.resolve("../../ANTLRTestProjects/antbased/" + name());
        assertTrue(Files.exists(rel), rel.toString() + "   abs " + rel.toAbsolutePath());
        assertTrue(Files.isDirectory(rel));
        return rel.toFile().getAbsoluteFile().getCanonicalFile().toPath();
    }

    public static class GrammarFileEntry implements Comparable<GrammarFileEntry> {

        private final UnixPath subpath;
        private final UnixPath filename;

        public GrammarFileEntry(UnixPath subpath, UnixPath filename) {
            this.subpath = subpath;
            this.filename = filename;
        }

        public String toCode() {
            return "new GrammarFileEntry(\n    UnixPath.get(\"" + subpath + "\"),\n    UnixPath.get(\"" + filename + "\"))";
        }

        @Override
        public String toString() {
            return subpath.resolve(filename).toString();
        }

        @Override
        public int compareTo(GrammarFileEntry o) {
            return filename.rawName().compareTo(o.filename.rawName());
        }

        public Path resolve(Path projectDir) {
            return projectDir.resolve(subpath.toNativePath()).resolve(filename.toNativePath());
        }

        public String name() {
            return filename.rawName();
        }
    }

    /*

    public static void main(String... ignored) {
        String list = "Channels/grammar/LexerGrammarWithChannels.g4\n"
                + "CodeCompletion/grammar/imports/ImportedLexerGrammar.g4\n"
                + "CodeCompletion/grammar/imports/ImportedLexerGrammar2.g4\n"
                + "CodeCompletion/grammar/org/mypackage/CombinedGrammar.g4\n"
                + "CodeCompletion/grammar/org/mypackage/LexerGrammar.g4\n"
                + "CodeCompletion/grammar/org/mypackage/ParserGrammar.g4\n"
                + "CodeFolding/grammar/Action.g4\n"
                + "CodeFolding/grammar/BlockComment.g4\n"
                + "GlobalActions/grammar/GlobalCombinedActions1.g4\n"
                + "GlobalActions/grammar/GlobalLexerActions1.g4\n"
                + "GlobalActions/grammar/GlobalLexerActions2.g4\n"
                + "GlobalActions/grammar/GlobalLexerActions3.g4\n"
                + "GlobalActions/grammar/GlobalParserActions1.g4\n"
                + "GlobalActions/grammar/GlobalParserActions2.g4\n"
                + "GlobalActions/grammar/GlobalParserActions3.g4\n"
                + "Grammars/grammar/CombinedGrammar.g4\n"
                + "Grammars/grammar/LexerGrammar.g4\n"
                + "Grammars/grammar/ParserGrammar.g4\n"
                + "Grammars/grammar/imports/ImportedCombinedGrammar.g4\n"
                + "Grammars/grammar/imports/ImportedLexerGrammar.g4\n"
                + "Grammars/grammar/imports/ImportedParserGrammar.g4\n"
                + "LexerRules/grammar/FragmentDeclarations.g4\n"
                + "LexerRules/grammar/SemanticPredicate.g4\n"
                + "LexerRules/grammar/TokenDeclarationWithLabel.g4\n"
                + "LexerRules/grammar/TokenDeclarations.g4\n"
                + "LexerRules/grammar/TokenWithAction.g4\n"
                + "LexerRules/grammar/TokenWithLexerCommands.g4\n"
                + "LexerRules/grammar/imports/ImportedLexerGrammar.g4\n"
                + "Options/grammar/AllPossibleOptionsOutOfPackage.g4\n"
                + "Options/grammar/myorg/mypackage/AllPossibleOptionsWithinAPackage1.g4\n"
                + "Options/grammar/myorg/mypackage/AllPossibleOptionsWithinAPackage2.g4\n"
                + "ParserRules/grammar/LabelledAlternatives.g4\n"
                + "ParserRules/grammar/LocalsInitAfter.g4\n"
                + "ParserRules/grammar/ParserRuleElementAssocOption.g4\n"
                + "ParserRules/grammar/ParserRuleElementFailOption.g4\n"
                + "ParserRules/grammar/ParserRuleOptions.g4\n"
                + "ParserRules/grammar/ReturnsLabels.g4\n"
                + "ParserRules/grammar/RuleExceptionManagement.g4\n"
                + "Tokens/grammar/GrammarWithTokens.g4";
        Map<String, List<GrammarFileEntry>> m = CollectionUtils.supplierMap(ArrayList::new);
        for (String s : list.split("\n")) {
            UnixPath up = UnixPath.get(s);
            String con = up.getName(0).toString();
            UnixPath filename = up.getFileName();
            UnixPath sub = up.subpath(1, up.getNameCount()).getParent();
            m.get(con).add(new GrammarFileEntry(sub, filename));
            Collections.sort(m.get(con));
//            System.out.println(con + " ( " + sub + "  " + filename.rawName());
        }
        List<String> names = new ArrayList<>(m.keySet());
        Collections.sort(names);
        for (int i = 0; i < names.size(); i++) {
            String n = names.get(i);
            List<GrammarFileEntry> l = m.get(n);
            StringBuilder sb = new StringBuilder(n).append("(\n");
            for (int j = 0; j < l.size(); j++) {
                GrammarFileEntry g = l.get(j);
                sb.append(g.toCode());
                if (j != l.size() - 1) {
                    sb.append(",\n");
                }
            }
            if (i != names.size() - 1) {
                sb.append("),\n");
            } else {
                sb.append(");\n");
            }
            System.out.println(sb);
        }
    }
     */
    private static class ProjectStateImpl implements ProjectState {

        public ProjectStateImpl() {
        }

        @Override
        public void markModified() {
            // do nothing
        }

        @Override
        public void notifyDeleted() throws IllegalStateException {
            // do nothing
        }
    }

    public static final class FOQ implements FileOwnerQueryImplementation {

        @Override
        public Project getOwner(URI uri) {
            File file = new File(uri);
            Path filePath = file.toPath().toAbsolutePath();
            for (Project p : projects) {
                Path projDir = FileUtil.toFile(p.getProjectDirectory()).toPath();
                if (filePath.startsWith(projDir)) {
                    return p;
                }
            }
            return null;
        }

        @Override
        public Project getOwner(FileObject fo) {
            Path filePath = FileUtil.toFile(fo).toPath().toAbsolutePath();
            for (Project p : projects) {
                Path projDir = FileUtil.toFile(p.getProjectDirectory()).toPath();
                if (filePath.startsWith(projDir)) {
                    return p;
                }
            }
            return null;
        }
    }
}

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

import static com.mastfrog.antlr.project.helpers.ant.AntFoldersHelperImplementation.ANTLR_TASK_CLASSPATH;
import static com.mastfrog.antlr.project.helpers.ant.AntFoldersHelperImplementation.ENCODING_PROPERTY;
import static com.mastfrog.antlr.project.helpers.ant.AntFoldersHelperImplementation.GENERATE_LISTENER_PROPERTY;
import static com.mastfrog.antlr.project.helpers.ant.AntFoldersHelperImplementation.GENERATE_VISITOR_PROPERTY;
import static com.mastfrog.antlr.project.helpers.ant.AntFoldersHelperImplementation.IMPORT_DIR_PROPERTY;
import static com.mastfrog.antlr.project.helpers.ant.AntFoldersHelperImplementation.OUTPUT_DIR_PROPERTY;
import static com.mastfrog.antlr.project.helpers.ant.AntFoldersHelperImplementation.SOURCE_DIR_PROPERTY;
import static com.mastfrog.antlr.project.helpers.ant.AntFoldersHelperImplementation.VERSION;
import static com.mastfrog.antlr.project.helpers.ant.AntFoldersHelperImplementation.VERSION_PROPERTY;
import static com.mastfrog.antlr.project.helpers.ant.AntFoldersHelperImplementation.asAuxProp;
import com.mastfrog.function.throwing.ThrowingTriConsumer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import static org.nemesis.antlr.common.AntlrConstants.ANTLR_MIME_TYPE;
import org.nemesis.antlr.project.spi.addantlr.NewAntlrConfigurationInfo;
import org.nemesis.antlr.projectupdatenotificaton.ProjectUpdates;
import org.nemesis.antlr.spi.language.AntlrMimeTypeRegistration;
import org.nemesis.antlr.wrapper.AntlrVersion;
import org.netbeans.api.progress.ProgressHandle;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectManager;
import org.netbeans.api.project.ant.AntBuildExtender;
import org.netbeans.api.project.ant.AntBuildExtender.Extension;
import org.netbeans.api.project.libraries.Library;
import org.netbeans.api.queries.FileEncodingQuery;
import org.netbeans.spi.java.project.classpath.ProjectClassPathExtender;
import org.netbeans.spi.project.AuxiliaryConfiguration;
import org.netbeans.spi.project.AuxiliaryProperties;
import org.netbeans.spi.project.support.ant.AntProjectHelper;
import org.netbeans.spi.project.support.ant.EditableProperties;
import org.netbeans.spi.project.support.ant.PropertyEvaluator;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.Exceptions;
import org.openide.util.Mutex;
import org.openide.util.NbBundle.Messages;
import org.openide.xml.XMLUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Does all the machinations of adding Antlr support to a project. All
 * validation that the objects passed to the constructor are present and usable
 * should be done ahead of time.
 *
 * @author Tim Boudreau
 */
final class AddAntBasedAntlrSupport implements Runnable {

    private final Project project;
    private final FileObject dir;
    private final FileObject nbproject;
    private final NewAntlrConfigurationInfo info;
    private final CompletableFuture<Boolean> whenDone;
    private final AntBuildExtender extender;
    private final PropertyEvaluator eval;
    private final AuxiliaryConfiguration acon;
    private final AuxiliaryProperties aux;
    private final Library antlrRuntimeLibrary;
    private final Library antlrAntTaskLibrary;
    private final AntProjectHelper helper;
    private final ProjectClassPathExtender classpathExtender;
    private final Library antlrToolLibrary;
    private final Element configurationElement;

    AddAntBasedAntlrSupport(Project project, FileObject dir,
            FileObject nbproject, NewAntlrConfigurationInfo info, CompletableFuture<Boolean> whenDone,
            AntBuildExtender extender, PropertyEvaluator eval, AuxiliaryConfiguration acon,
            AuxiliaryProperties aux, Library runtimeLib, Library taskLib, AntProjectHelper helper,
            ProjectClassPathExtender classpathExtender, Library antlrToolLib,
            Element configurationElement) {
        this.project = project;
        this.dir = dir;
        this.nbproject = nbproject;
        this.info = info;
        this.whenDone = whenDone;
        this.extender = extender;
        this.eval = eval;
        this.acon = acon;
        this.aux = aux;
        this.antlrRuntimeLibrary = runtimeLib;
        this.antlrAntTaskLibrary = taskLib;
        this.helper = helper;
        this.classpathExtender = classpathExtender;
        this.antlrToolLibrary = antlrToolLib;
        this.configurationElement = configurationElement;
    }

    @Override
    public void run() {
        try {
            whenDone.complete(addAntlr());
            ProjectUpdates.notifyPathChanged(FileUtil.toFile(project.getProjectDirectory()).toPath());
        } catch (Exception e) {
            whenDone.completeExceptionally(e);
        }
    }

    class Adder implements Mutex.ExceptionAction<Boolean> {

        private Runnable postRun;
        private ProgressHandle progress;

        @Messages("adding=Adding Antlr Support")
        @Override
        public Boolean run() throws Exception {
            try {
                progress = ProgressHandle.createHandle(Bundle.adding());
                progress.setInitialDelay(1);
                // 1 - creating source folders
                // 2 - modifying project metadata
                // 3 - adding build extension
                // 4 - adding libraries to project
                // 5 - update project properties
                // 6 - generate grammar

                int steps = info.skeletonType().isGenerate() ? 6 : 7;
                progress.start(steps, 2000);
                return AntlrMimeTypeRegistration.runExclusiveForProject(ANTLR_MIME_TYPE,
                        project, dir, this::doRun);
            } catch (Exception | Error ex) {
                ex.printStackTrace();
                Exceptions.printStackTrace(ex);
                return false;
            }
        }

        public void finish(boolean success) {
            try {
                if (success = info.skeletonType().isGenerate() && postRun != null) {
                    progress.progress(Bundle.stepFormattingGrammar(), 5);
                    postRun.run();
                }
            } finally {
                progress.finish();
            }
        }

        @Messages({
            "stepCreatingSourceFolders=Creating source folders...",
            "stepModifyingProjectMetadata=Adding source folders to project...",
            "stepAddingBuildExtension=Adding build extension...",
            "stepAddingLibrariesToProject=Adding libraries...",
            "stepUpdatingMetadata=Updating project metadata...",
            "stepGeneratedGrammar=Generating and grammar...",
            "stepFormattingGrammar=Generating and grammar...",
            "# {0} - projectName",
            "stepDone=Antlr support added to {0}"
        })
        private Boolean doRun() throws Exception {
            FileObject projectDir = project.getProjectDirectory();

            progress.progress(Bundle.stepCreatingSourceFolders(), 1);

            String grammarDirName = info.antlrDirName();

            FileObject antlrSrc = project.getProjectDirectory().getFileObject(grammarDirName);
            if (antlrSrc == null) {
                antlrSrc = FileUtil.createFolder(projectDir, grammarDirName);
            }

            String importDirName = info.importDirName();
            FileObject antlrImports = null;
            if (info.createImportDir() && importDirName != null && !importDirName.isEmpty()) {
//                        antlrImports = project.getProjectDirectory().createD
                antlrImports = projectDir.getFileObject(importDirName);
                if (antlrImports == null) {
                    antlrImports = FileUtil.createFolder(projectDir, importDirName);
                }
            } else {
                // If not specified, don't create the directory, just leave the metadata
                // anticipating the common case - that the imports directory is underneath
                // the source root
                importDirName = grammarDirName + "/imports";
            }

            boolean includeImportsInSourceRoots
                    = antlrImports != null && !FileUtil.isParentOf(antlrSrc, antlrImports);

            progress.progress(Bundle.stepModifyingProjectMetadata(), 2);
            modifyProjectSourceRoots(includeImportsInSourceRoots);

            progress.progress(Bundle.stepAddingLibrariesToProject(), 3);
            extender.addLibrary(antlrAntTaskLibrary);
            extender.addLibrary(antlrToolLibrary);

            boolean cpAdded = classpathExtender.addLibrary(antlrRuntimeLibrary);

            progress.progress(Bundle.stepAddingBuildExtension(), 3);
            FileObject antlrBuildImpl;
            try (InputStream in = AddAntBasedAntlrSupport.class.getResourceAsStream("antlr-build-impl.xml")) {
                if (in == null) {
                    throw new IOException("antlr-build-impl.xml not adjacent to "
                            + getClass().getName() + " on classpath");
                }
                antlrBuildImpl = nbproject.getFileObject("antlr-build-impl", "xml");
                if (antlrBuildImpl == null) {
                    antlrBuildImpl = nbproject.createData("antlr-build-impl", "xml");
                }
                try (OutputStream out = antlrBuildImpl.getOutputStream()) {
                    FileUtil.copy(in, out);
                }
            }
            Extension ext = extender.addExtension("antlr", antlrBuildImpl);
            ext.addDependency("-pre-pre-compile", "antlr4");
            ext.addDependency("-init-check", "-antlr4.init");

            progress.progress(Bundle.stepUpdatingMetadata(), 4);
            addOrUpdateAntlrTaskProperties(grammarDirName, importDirName);

            ensureBuiltClassesExcludesGrammarFiles();
            ProjectManager.getDefault().saveProject(project);

            if (info.skeletonType().isGenerate()) {
                progress.progress(Bundle.stepGeneratedGrammar(), 4);
                postRun = info.skeletonType().generate(info.generatedGrammarName(),
                        project, antlrSrc, antlrImports, info.javaPackage(), findCharset());
            }
            return true;
        }

        private Charset findCharset() {
            Charset charset = null;
            String enc = eval.evaluate("source.encoding");
            if (enc != null) {
                try {
                    charset = Charset.forName(enc);
                } catch (IllegalCharsetNameException | UnsupportedCharsetException ex) {
                    // ok
                }
            }
            if (charset == null) {
                FileObject fo = project.getProjectDirectory().getFileObject("build.xml");
                if (fo != null) {
                    charset = FileEncodingQuery.getEncoding(fo);
                } else {
                    charset = FileEncodingQuery.getDefaultEncoding();
                }
            }
            return charset;
        }

        void writeTrackingInfo() {
            String uri = "http://mastfrog.com/antlr-ant/1";
            Document doc = XMLUtil.createDocument("x", "http://mastfrog.com/antlr-ant/1", null, null);
            Element el = doc.createElementNS(uri, "antlr-info");
            Element child = doc.createElementNS(uri, "revinfo");
            child.setAttribute("moduleversion", AntlrVersion.moduleVersion());
            child.setAttribute("antlrversion", AntlrVersion.version());
//            el.appendChild(child);
            doc.getDocumentElement().appendChild(el);
            acon.putConfigurationFragment(el, true);
        }

        void addOrUpdateAntlrTaskProperties(String grammarDirName, String importDirName) {
            boolean hasGenSourceDir = eval.getProperty("build.generated.sources.dir") != null;
            boolean hasGeneratedDir = eval.getProperty("build.generated.dir") != null;

            // If we do this first, addExtension clobbers it
            if (!hasGenSourceDir) {
                aux.put("build.generated.sources.dir", "${build.dir}/generated-sources", true);
            }
            if (!hasGeneratedDir) {
                aux.put("build.generated.dir", "${build.dir}/generated", true);
            }

            String cp = "${libs." + antlrAntTaskLibrary.getName() + ".classpath}:"
                    + "${libs." + antlrToolLibrary.getName() + ".classpath}";

            aux.put(ANTLR_TASK_CLASSPATH, cp, true);
            aux.put(GENERATE_LISTENER_PROPERTY, Boolean.toString(info.generateListener()), true);
            aux.put(GENERATE_VISITOR_PROPERTY, Boolean.toString(info.generateVisitor()), true);
            aux.put(SOURCE_DIR_PROPERTY, grammarDirName, true);
            aux.put(IMPORT_DIR_PROPERTY, importDirName, true);
            aux.put(OUTPUT_DIR_PROPERTY, "${build.generated.sources.dir}/antlr", true);
            aux.put(VERSION_PROPERTY, Integer.toString(VERSION), true);
            aux.put(ENCODING_PROPERTY, "${source.encoding}", true);
        }

        void ensureBuiltClassesExcludesGrammarFiles() {
            // **/*.g4
            EditableProperties props = helper.getProperties(AntProjectHelper.PROJECT_PROPERTIES_PATH);
            if (helper != null) {
                String prop = props.getProperty("build.classes.excludes");
                String origProp = prop;
                if (prop == null || prop.trim().isEmpty()) {
                    prop = "**/*.g4";
                } else {
                    if (!prop.contains("**/*.g4")) {
                        prop += ",**/*.g4";
                    }
                }
                if (!Objects.equals(prop, origProp)) {
                    props.setProperty("build.classes.excludes", prop);
                    helper.putProperties(AntProjectHelper.PROJECT_PROPERTIES_PATH, props);
                }
            }
        }

        void modifyProjectSourceRoots(boolean includeImportDir) throws Exception {
            // The element we are handed by AuxiliaryConfiguration has NO parent document,
            // so we need a new document so that we can create nodes to add.
            Document doc = XMLUtil.createDocument("project", "http://www.netbeans.org/ns/project/1", null, null);
            Element config = doc.createElementNS("http://www.netbeans.org/ns/project/1", "configuration");
            doc.getDocumentElement().appendChild(config);
            Element replacementElement = doc.createElementNS("http://www.netbeans.org/ns/j2se-project/3", "data");
            config.appendChild(replacementElement);
            XMLUtil.copyDocument(configurationElement, replacementElement, "http://www.netbeans.org/ns/j2se-project/3");

            sourceRootElements(replacementElement, (rootsParent, rootsById, rootsInOrderOfOccurrence) -> {
                if (rootsParent == null) {
                    throw new IOException("Did not find a source-roots element in " + Hacks.nodeToString(configurationElement));
                }
                Element antlrSoureRootNode = null;
                if (!rootsById.containsKey(asAuxProp(SOURCE_DIR_PROPERTY))) {
                    antlrSoureRootNode = doc.createElementNS("http://www.netbeans.org/ns/j2se-project/3", "root");
                    antlrSoureRootNode.setAttribute("id", asAuxProp(SOURCE_DIR_PROPERTY));
                    antlrSoureRootNode.setAttribute("name", Bundle.antlr());
                }
                Element importsSourceRootNode = null;
                if (includeImportDir && !rootsById.containsKey(asAuxProp(IMPORT_DIR_PROPERTY))) {
                    importsSourceRootNode
                            = doc.createElementNS("http://www.netbeans.org/ns/j2se-project/3", "root");
                    importsSourceRootNode.setAttribute("id", asAuxProp(IMPORT_DIR_PROPERTY));
                    importsSourceRootNode.setAttribute("name", Bundle.imports());
                }
                Element before = rootsInOrderOfOccurrence.isEmpty() ? null : rootsInOrderOfOccurrence.get(0);
                boolean changed = false;
                if (before == null) {
                    // no source roots?  weird.
                    if (antlrSoureRootNode != null) {
                        rootsParent.appendChild(antlrSoureRootNode);
                        changed = true;
                    }
                    if (importsSourceRootNode != null) {
                        rootsParent.appendChild(importsSourceRootNode);
                        changed = true;
                    }
                } else {
                    if (antlrSoureRootNode != null) {
                        rootsParent.insertBefore(antlrSoureRootNode, before);
                        changed = true;
                    }
                    if (importsSourceRootNode != null) {
                        rootsParent.insertBefore(importsSourceRootNode, before);
                        changed = true;
                    }
                }
                if (changed) {
                    helper.putPrimaryConfigurationData(replacementElement, true);
                }
            });
        }

        private void sourceRootElements(Element parentOfSourceRoots, ThrowingTriConsumer<Element, Map<String, Element>, List<Element>> c) throws Exception {
            NodeList nl = parentOfSourceRoots.getChildNodes();
            Map<String, Element> all = new HashMap<>();
            List<Element> order = new ArrayList<>();
            Element rootParent = null;
            for (int i = 0; i < nl.getLength(); i++) {
                Node item = nl.item(i);
                if (item instanceof Element && "source-roots".equals(item.getNodeName())) {
                    rootParent = (Element) item;
                    NodeList rootItems = item.getChildNodes();
                    for (int j = 0; j < rootItems.getLength(); j++) {
                        Node possibleRoot = rootItems.item(j);
                        if (possibleRoot instanceof Element && "root".equals(possibleRoot.getNodeName())) {
                            Element root = (Element) possibleRoot;
                            String id = root.getAttribute("id");
                            order.add(root);
                            all.put(id, root);
                        }
                    }
                    break;
                }
            }
            c.apply(rootParent, all, order);
        }
    }

    @Messages({"antlr=Antlr Sources", "imports=Antlr Imports"})
    private boolean addAntlr() throws Exception {
        Adder adder = new Adder();
        Boolean result = ProjectManager.mutex().writeAccess(adder);
        boolean res = result == null ? false : result;
        adder.finish(res);
        return res;
    }
}

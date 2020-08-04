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

import static com.mastfrog.antlr.project.helpers.ant.AntFoldersHelperImplementationFactory.LOG;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import org.nemesis.antlr.project.Folders;
import org.nemesis.antlr.project.spi.AntlrConfigurationImplementation;
import org.nemesis.antlr.project.spi.FolderLookupStrategyImplementation;
import org.nemesis.antlr.project.spi.FolderQuery;
import org.nemesis.antlr.project.spi.OwnershipQuery;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectUtils;
import org.netbeans.api.project.SourceGroup;
import org.netbeans.api.project.Sources;
import org.netbeans.api.queries.FileEncodingQuery;
import org.netbeans.spi.project.support.ant.PropertyEvaluator;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

/**
 *
 * @author Tim Boudreau
 */
public final class AntFoldersHelperImplementation implements FolderLookupStrategyImplementation, OwnershipQuery {

    private final Project project;
    public static final int VERSION = 1;
    public static final String SOURCE_DIR_PROPERTY = "antlr4.source.dir";
    public static final String OUTPUT_DIR_PROPERTY = "antlr4.output.dir";
    public static final String IMPORT_DIR_PROPERTY = "antlr4.import.dir";
    public static final String ANTLR_TASK_CLASSPATH = "antlr4.task.classpath";
    public static final String VERSION_PROPERTY = "antlr.project.support.version";
    public static final String GENERATE_VISITOR_PROPERTY = "antlr.generate.listener";
    public static final String GENERATE_LISTENER_PROPERTY = "antlr.generate.visitor";
    public static final String GENERATE_ATN_PROPERTY = "antlr.generate.atn";
    public static final String FORCE_ATN_PROPERTY = "antlr.force.atn";
    public static final String ENCODING_PROPERTY = "antlr.encoding";
    public static final String ANTLR_PACKAGE = "antlr.package";

    static final String J2SE_PROJECT_MAIN_SOURCES = "src.dir";
    static final String J2SE_PROJECT_UNIT_TEST_SOURCES = "test.src.dir";
    static final String J2SE_PROJECT_BUILD_CLASSES = "build.classes.dir";
    static final String J2SE_PROJECT_TEST_BUILD_CLASSES = "build.test.classes.dir";
    static final String J2SE_PROJECT_JAVA_SOURCE_GROUP = "java";

    public AntFoldersHelperImplementation(Project project) {
        this.project = project;
    }

    static String asAuxProp(String prop) {
        if (!prop.startsWith("auxiliary.")) {
            prop = "auxiliary." + prop;
        }
        return prop;
    }

    static String ref(String what) {
        if (!what.startsWith("${")) {
            return "${" + what + "}";
        }
        return what;
    }

    Charset charset() {
        Optional<PropertyEvaluator> evalOpt = AntFoldersHelperImplementationFactory.evaluator(project);
        if (evalOpt.isPresent()) {
            String cs = evalOpt.get().evaluate(ENCODING_PROPERTY);
            if (cs != null) {
                try {
                    return Charset.forName(cs);
                } catch (IllegalCharsetNameException | UnsupportedCharsetException ex) {
                    LOG.log(Level.INFO, "Bad charset name in " + project + ": " + cs, ex);
                }
            }
        }
        FileObject buildFile = project.getProjectDirectory().getFileObject("build.xml");
        if (buildFile != null) {
            return FileEncodingQuery.getEncoding(buildFile);
        }
        return FileEncodingQuery.getDefaultEncoding();
    }

    @Override
    public Folders findOwner(Path file) {
        Path fld = projectFolder(IMPORT_DIR_PROPERTY, true);
        if (fld != null && file.startsWith(fld)) {
            return Folders.ANTLR_IMPORTS;
        }
        fld = projectFolder(SOURCE_DIR_PROPERTY, true);
        if (fld != null && file.startsWith(fld)) {
            return Folders.ANTLR_GRAMMAR_SOURCES;
        }
        fld = projectFolder(J2SE_PROJECT_MAIN_SOURCES, true);
        if (fld != null && file.startsWith(fld)) {
            return Folders.JAVA_SOURCES;
        }
        fld = projectFolder(OUTPUT_DIR_PROPERTY, true);
        if (fld != null && file.startsWith(fld)) {
            return Folders.JAVA_GENERATED_SOURCES;
        }
        return null;
    }

    boolean booleanProperty(String prop) {
        switch(prop) {
            case FORCE_ATN_PROPERTY :
            case GENERATE_ATN_PROPERTY :
            case GENERATE_LISTENER_PROPERTY :
            case GENERATE_VISITOR_PROPERTY :
                prop = asAuxProp(prop);
        }
        Optional<PropertyEvaluator> evalOpt = AntFoldersHelperImplementationFactory.evaluator(project);
        if (evalOpt.isPresent()) {
            PropertyEvaluator ev = evalOpt.get();
            String val = ev.evaluate(prop);
            if (val == null) {
                return false;
            }
            switch (val.toLowerCase()) {
                case "true":
                case "yes":
                case "on":
                    return true;
                default:
                    return false;
            }
        }
        return false;
    }

    Path projectFolder(String prop, boolean mustExist) {
        switch (prop) {
            case SOURCE_DIR_PROPERTY:
            case OUTPUT_DIR_PROPERTY:
            case IMPORT_DIR_PROPERTY:
            case ANTLR_TASK_CLASSPATH:
            case VERSION_PROPERTY:
            case GENERATE_LISTENER_PROPERTY:
            case GENERATE_VISITOR_PROPERTY:
                prop = asAuxProp(prop);
        }
        Optional<PropertyEvaluator> evalOpt = AntFoldersHelperImplementationFactory.evaluator(project);
        if (evalOpt.isPresent()) {
            String value = evalOpt.get().getProperty(prop);
            Path result = null;
            if (value != null && !value.isEmpty()) {
                if (value.charAt(0) == File.separatorChar) {
                    result = Paths.get(value);
                } else {
                    Path base = FileUtil.toFile(project.getProjectDirectory()).toPath();
                    result = base.resolve(value);
                }
                if (mustExist && (!Files.exists(result) || !Files.isDirectory(result))) {
                    result = null;
                }
            }
            return result;
        }
        return null;
    }

    @Override
    public Iterable<Path> find(Folders folder, FolderQuery query) throws IOException {
        Path lookIn = null;
        switch (folder) {
            case ANTLR_IMPORTS:
                lookIn = projectFolder(IMPORT_DIR_PROPERTY, true);
                break;
            case ANTLR_GRAMMAR_SOURCES:
                lookIn = projectFolder(SOURCE_DIR_PROPERTY, true);
                break;
            case JAVA_SOURCES:
                Sources sources = ProjectUtils.getSources(project);
                if (sources != null) {
                    SourceGroup[] groups = sources.getSourceGroups(J2SE_PROJECT_JAVA_SOURCE_GROUP);
                    List<Path> fos = new ArrayList<>();
                    for (SourceGroup g : groups) {
                        if (looksLikeMainJavaSources(g)) {
                            FileObject fo = g.getRootFolder();
                            if (fo != null) {
                                fos.add(toPath(fo));
                            }
                        }
                    }
                    if (!fos.isEmpty()) {
                        return fos;
                    } else {
                        lookIn = projectFolder(J2SE_PROJECT_MAIN_SOURCES, true);
                    }
                }
                break;
            case JAVA_GENERATED_SOURCES:
                lookIn = projectFolder(OUTPUT_DIR_PROPERTY, true);
                break;
            case CLASS_OUTPUT:
                lookIn = projectFolder(J2SE_PROJECT_BUILD_CLASSES, true);
                break;
            case JAVA_TEST_SOURCES:
                lookIn = projectFolder(J2SE_PROJECT_UNIT_TEST_SOURCES, true);
                break;
            case TEST_CLASS_OUTPUT:
                lookIn = projectFolder(J2SE_PROJECT_TEST_BUILD_CLASSES, true);
                break;
        }
        return lookIn == null ? Collections.emptySet() : iterable(lookIn);
    }

    private static boolean looksLikeMainJavaSources(SourceGroup g) {
        String nm = g.getName();
        if (nm != null) {
            nm = nm.toLowerCase();
            if (nm.contains("main") || nm.contains("src")) {
                if (!nm.contains("test")) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public String name() {
        return "Ant";
    }

    @Override
    public <T> T get(Class<T> type) {
        if (type == AntlrConfigurationImplementation.class) {
            return type.cast(new AntConfigImpl(this));
        }
        if (type == OwnershipQuery.class) {
            return type.cast(this);
        }
        return FolderLookupStrategyImplementation.super.get(type);
    }

}

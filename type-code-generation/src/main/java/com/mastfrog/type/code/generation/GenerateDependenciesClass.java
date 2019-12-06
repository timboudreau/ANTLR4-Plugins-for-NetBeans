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

import com.mastfrog.function.throwing.ThrowingConsumer;
import com.mastfrog.java.vogon.ClassBuilder;
import com.mastfrog.util.collections.CollectionUtils;
import static com.mastfrog.util.collections.CollectionUtils.setOf;
import com.mastfrog.util.file.FileUtils;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.lang.model.element.Modifier;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;
import org.netbeans.modules.classfile.Access;
import org.netbeans.modules.classfile.ClassFile;

/**
 * Annotation processors generate a LOT of code in the project, and reference
 * type names. It is useful if they can also tell the user what dependencies
 * (and, where possible, transitive dependencies) they will need to set up to
 * compile their module(s). So we need a way to map classes back to maven
 * coordinates.
 *
 * A giant list of classes to import would be a PITA to manually create, and
 * nightmarish to maintain, and would not be robust to repackaging refactorings.
 * However, scanning the POM files, manifests and classes in JARs lets us
 * generate an Enum of known types and libraries
 * <i>into</i> the sources of registration-annotation-processors, which it can
 * then use. So this little nightmare does that, as follows;
 * <ul>
 * <li>Find the root project relative to this one</li>
 * <li>Scan all pom files from there</li>
 * <li>Attribute dependencies (resolve properties down to the base POM), so we
 * have complete dependency info</li>
 * <li>Scan the JAR for every class name <i>imported by sources in any project
 * here</i> (this is likely to be a superset of what any annotation processor
 * might use), making not of any Public-Packages manifest entry to filter the
 * list, reading the class files to ignore non-public ones, and create a mapping
 * from class-name:maven-coordinates</li>
 * <li>Generate an enum called Libraries of every library, using minimal sane
 * names based on enough of the artifactId to be unique and intuitive</li>
 * <li>Generate an enum of classes where each enum entry has a (hopefully)
 * intuitive name</li>
 * </ul>
 * The one flaw in this methodology is: While the poms are parsed in a consisten
 * order, if a new maven project is introduced that has a class, say, "Lexer"
 * which sorts higher than one that already exists, that one will wind up with
 * the name LEXER in the enum (of course, you'll know it the first time you test
 * the annotation processors and the output is uncompilable). If needed, some
 * hard coded preferred ordering could be added, though that is ugly.
 *
 * @author Tim Boudreau
 */
public class GenerateDependenciesClass {

    // A blacklist of classes and fqn prefixes that are very unlikely ever
    // to be needed for code generation, to reduce clutter
    static final Set<String> blacklist = setOf("org.nemesis.antlr.live.language",
            "org.nemesis.antlr.file.G4Resolver_", "org.nemesis.antlr.live.preview",
            "org.nemesis.adhoc.mime.types", "org.nemesis.antlr.memory",
            "com.mastfrog.util.cache",
            "org.antlr.v4.tool", "com.mastfrog.util.collections",
            "org.openide.filesystems.FileChangeListener",
            "org.netbeans.api.editor.caret",
            "org.nemesis.jfs", "com.mastfrog.bits", "org.nemesis.distance",
            "org.nemesis.antlr.ANTLRv4", "org.nemesis.antlr.common.extractiontypes",
            "com.mastfrog.util.strings.Escaper", "org.openide.filesystems.FileStateInvalidException",
            "org.openide.filesystems.Repository", "org.nemesis.antlr.file.impl",
            "org.nemesis.antlr.live.parsing", "org.nemesis.antlr.live",
            "org.nemesis.antlr.navigator.DuplicateCheckingList",
            "org.nemesis.antlr.file.IR", "org.nemesis.antlr.project.helpers.maven",
            "org.nemesis.antlr.compilation", "org.nemesis.data.named.SerializationContext",
            "org.netbeans.spi.editor.hints.Severity", "org.nemesis.data.named.SerializationCallback",
            "com.mastfrog.graph.algorithm.Score", "javax.swing",
            "java.util.concurrent.ThreadLocalRandom", "org.openide.actions.ToolsAction",
            "java.beans.VetoableChangeListener", "org.nemesis.debug", "org.nemesis.misc.utils",
            "java.io.ObjectInput", "java.io.ObjectInputStream", "java.io.ObjectOutput",
            "java.io.ObjectOutputStream", "java.security", "org.openide.filesystems.FileEvent",
            "org.netbeans.spi.queries.FileEncodingQueryImplementation",
            "org.openide.filesystems.FileAttributeEvent",
            "java.lang.reflect", "org.netbeans.modules.editor.indent.spi.ExtraLock",
            "org.nemesis.antlr.refactoring.common.FileObjectHolder",
            "java.nio.file.attribute.FileTime",
            "org.openide.filesystems.FileSystem",
            "java.awt.Graphics", "java.awt.GridBagConstraints",
            "java.awt.LayoutManager", "java.awt.GridBagLayout", "java.awt.Insets",
            "org.antlr.runtime.misc", "com.mastfrog.graph.IntGraphVisitor",
            "com.mastfrog.graph.IntGraph", "java.awt.event.ItemEvent",
            "java.awt.KeyboardFocusManager", "java.awt.Point",
            "java.awt.event", "java.awt.Rectangle", "java.awt.geom",
            "java.util.concurrent.locks.ReentrantLock",
            "java.util.concurrent.locks.ReentrantReadWriteLock",
            "java.awt.RenderingHints", "java.awt.Shape",
            "java.awt.Toolkit",
            "java.awt.BorderLayout", "java.io.BufferedReader",
            "java.io.ByteArrayOutputStream", "java.awt.Dimension",
            "java.awt.FlowLayout", "java.awt.FontMetrics",
            "org.nemesis.antlr.navigator.ComponentIsActiveChecker",
            "java.util.concurrent.CountDownLatch",
            "org.netbeans.api.editor.document.CustomUndoDocument",
            "org.openide.text.DataEditorSupport",
            "java.io.UnsupportedEncodingException",
            "org.netbeans.spi.lexer.MutableTextInput",
            "org.netbeans.lib.editor.util.swing.MutablePositionRegion",
            "org.nemesis.data.impl.MutableEndSupplier",
            "org.nemesis.antlr.language.formatting.ui.MockPreferences",
            "org.netbeans.editor.MultiKeymap",
            "org.nemesis.antlr.language.formatting.ui.MaxLineLengthComboBoxModel",
            "org.nemesis.antlr.project.MavenAntlrSourceFactoryPresent",
            "org.nemesis.antlrformatting.api.LogicalLexingStateCriteriaBuilder",
            "org.antlr.v4.runtime.atn", "org.netbeans.api.lexer.LanguagePath",
            "org.netbeans.spi.lexer.LanguageProvider",
            "org.openide.windows.IO", "java.nio.file.InvalidPathException",
            "org.antlr.v4.runtime.misc",
            "org.nemesis.antlr.language.formatting.ui.install.Installer",
            "org.openide.windows.InputOutput",
            "org.openide.awt.HtmlRenderer",
            "org.netbeans.spi.editor.hints.HintsController",
            "org.netbeans.spi.editor.highlighting.HighlightsSequence",
            "ignoreme.placeholder", "org.antlr.v4.runtime.dfa",
            "java.lang.annotation.Target",
            "java.lang.annotation.RetentionPolicy",
            "org.nemesis.antlr.error.highlighting",
            "org.nemesis.antlr.refactoring.impl.EnsureInstantRenamersAreRemovedPluginFactory",
            "org.nemesis.antlr.file.file.G4GotoDeclarationAction", "org.nemesis.antlr.project.impl",
            "org.openide.windows.OutputListener",
            "org.openide.windows.OutputEvent",
            "org.openide.windows.OutputWriter", "org.antlr.v4.automata",
            "org.nemesis.parse.recorder",
            "org.nemesis.source.impl.", "org.openide.cookies.PrintCookie",
            "org.nemesis.antlr.refactoring.impl.RenameQueryResultTrampoline",
            "org.antlr.v4.runtime.RuntimeMetaData",
            "org.nemesis.antlrformatting.api.SimpleCollator",
            "org.nemesis.data.impl.SizedArrayValueSupplier",
            "org.nemesis.antlr.project.spi.SingleIterable",
            "org.nemesis.antlr.file.refactoring.SingletonWhereUsedCreationStrategy",
            "org.netbeans.spi.editor.mimelookup.MimeDataProvider",
            "org.nemesis.antlr.fold.DocOrFileKey",
            "org.nemesis.antlrformatting.spi.DocumentReformatRunner"
    );

    static final Set<String> skipProjects = setOf(
            "simple-test-language",
            "misc-utils", "protoc-antlr",
            "antlr-input-nb", "antlr-refactoring-ui", "jfs", "jfs-nb",
            "registration-annotation-processors", "antlr-project-extensions",
            "java-file-grammar", "tokens-file-grammar", "antlr-suite",
            "child-with-changed-antlr-dir", "other-parent-that-changes-encoding",
            "parent-that-changes-antlr-dirs", "antlr-project-helpers-maven",
            "debug-api", "debug-ui", "test-fixtures-support",
            "type-code-generation"
    );

    static final Set<String> wrapperProjectFolderNames = setOf(
            "antlr-wrapper",
            "mastfrog-utils-wrapper"
    );

    static final Set<String> swingWhitelist = setOf(
            "javax.swing.event.ChangeListener",
            "javax.swing.event.ChangeEvent",
            "javax.swing.event.ActionListener",
            "javax.swing.event.ActionEvent",
            "javax.swing.ListModel",
            "javax.swing.Icon",
            "javax.swing.ImageIcon",
            "javax.swing.Action",
            "javax.swing.InputMap",
            "javax.swing.ActionMap",
            "javax.swing.KeyStroke",
            "javax.swing.ButtonModel",
            "javax.swing.ComboBoxModel",
            "javax.swing.TreeModel",
            "javax.swing.SingleSelectionModel",
            "javax.swing.ListSelectionModel",
            "javax.swing.text.TextAction",
            "javax.swing.text.Document",
            "javax.swing.text.Caret",
            "javax.swing.text.Position",
            "javax.swing.text.StyledDocument",
            "javax.swing.text.AttributeSet",
            "javax.swing.text.AbstractDocument",
            "javax.swing.text.EditorKit",
            "javax.swing.text.Segment",
            "javax.swing.text.BadLocationException"
    );

    static final boolean includeJavaLangClasses = true;

    private static boolean isBlacklisted(Path dir) {
        boolean blacklisted = skipProjects.contains(dir.getFileName().toString());
        if (blacklisted) {
            return true;
        }
        return blacklisted;
    }

    private static boolean isBlacklisted(String name) {
        if (name.startsWith("javax")) {
            if (swingWhitelist.contains(name)) {
                return false;
            }
            return true;
        }
        if (name.endsWith("Trampoline") || name.endsWith("Accessor")) {
            return true;
        }
        if (name.endsWith("GotoDeclarationAction")) {
            return true;
        }
        if (name.endsWith("_ReformatTaskFactory")) {
            return true;
        }
        if (name.endsWith("_IMPORTS") || name.endsWith("_HIGHLIGHT_REFRESH_TRIGGER")) {
            return true;
        }
        if (name.contains("AntlrKeys_")) {
            return true;
        }
        if (name.endsWith(".Bundle")) {
            return true;
        }
        if (name.contains("_Registration_")) {
            return true;
        }
        if (blacklist.contains(name)) {
            return true;
        }
        for (String item : blacklist) {
            if (name.startsWith(item)) {
                return true;
            }
        }
        return false;
    }

    public static void main(String... ignored) throws Exception {
        new GenerateDependenciesClass().go();
    }

    private ClassBuilder<String> typesEnumBuilder(String pkg, String typeName) {
        return ClassBuilder.forPackage(pkg)
                .named(typeName)
                .docComment("<b>DO NOT MODIFY THIS FILE!</b> ", "This file is generated by "
                        + "analyzing the POMs and class files used in all subprojects of the"
                        + "parent project for class names that are likely to be used in code"
                        + "generation. It will be regenerated at build time.")
                .withModifier(Modifier.PUBLIC)
                .implementing("TypeName")
                .importing("java.util.Set", "java.util.EnumSet",
                        "com.mastfrog.annotation.AnnotationUtils")
                .toEnum()
                .privateFinalField("fqn").ofType("String")
                .privateFinalField("lib").ofType("Libraries")
                .constructor(con -> {
                    con.addArgument("String", "fqn")
                            .addArgument("Libraries", "lib")
                            .body(bb -> {
                                bb.assign("this.fqn").toExpression("fqn")
                                        .assign("this.lib").toExpression("lib");
                            });
                })
                .constructor(con -> {
                    con.addArgument("String", "fqn")
                            .body(bb -> {
                                bb.assign("this.fqn").toExpression("fqn")
                                        .assign("this.lib").toExpression("null");
                            });
                })
                .overridePublic("toString", mb -> {
                    mb.returning("String").body(bb -> {
                        bb.returningValue(vb -> {
                            vb.stringConcatenation()
                                    .invoke("qnameNotouch").inScope()
                                    .with().literal(":")
                                    .with().expression("fqn")
                                    .with().literal("<-")
                                    .with().expression("(lib == null ? \"jdk\" : lib)")
                                    .endConcatenation();
                        });
                    });
                })
                .overridePublic("qname").docComment("Fetch fully qualified name of the class.").returning("String")
                .body(bb -> {
                    bb.invoke("touch").inScope().returning("fqn");
                })
                .overridePublic("qnameNotouch")
                .docComment("Fetch the fully qualified name without adding this libraries origin to the set of known-used libraries")
                .returning("String").bodyReturning("fqn")
                .overridePublic("simpleName").returning("String")
                .docComment("Fetch the simple name of the class")
                .body(bb -> {
                    bb.invoke("touch").inScope().returningInvocationOf("simpleName")
                            .withArgument("fqn")
                            .on("AnnotationUtils");
                })
                .overridePublic("origin").docComment("Fetch the library this class is in, if non-JDK").returning("Library").bodyReturning("lib")
                .privateMethod("touch", mb -> {
                    mb.body(bb -> {

                        bb.ifNotNull("lib", ifbb -> {

                            ifbb.iff().booleanExpression("!REPORTED.contains(lib)")
                                    .invoke("add")
                                    .withArgument("lib")
                                    .on("TOUCHED").endIf();
                        });
                    });

                })
                .field("TOUCHED", fb -> {
                    fb.withModifier(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                            .initializedFromInvocationOf("noneOf")
                            .withArgument("Libraries.class").on("EnumSet")
                            .ofType("Set<Libraries>");
                })
                .field("REPORTED", fb -> {
                    fb.withModifier(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                            .initializedFromInvocationOf("noneOf")
                            .withArgument("Libraries.class").on("EnumSet")
                            .ofType("Set<Libraries>");
                })
                .publicMethod("touchedMessage", mb -> {
                    mb.withModifier(Modifier.STATIC)
                            .returning("String")
                            .body(bb -> {
                                bb.iff(ib -> {
                                    ib.invokeAsBoolean("isEmpty").on("TOUCHED").returningStringLiteral("");
                                });
                                bb.declare("result").initializedWithNew("StringBuilder").withArgument(1024).inScope()
                                        .as("StringBuilder");
                                bb.invoke("append").withStringLiteral(
                                        "\nGenerated code requires the following dependencies:\n").on("result");
                                bb.simpleLoop("Libraries", "lib", slb -> {
                                    slb.over("TOUCHED", loopBlock -> {
                                        loopBlock.ifNotNull("lib", ifBlock -> {
                                            ifBlock.invoke("append")
                                                    .withArgument("lib.toXML()").on("result");
                                            ifBlock.invoke("add").withArgument("lib")
                                                    .on("REPORTED");
                                        });
                                    });
                                });
                                bb.invoke("clear").on("TOUCHED");
                                bb.returningInvocationOf("toString").on("result");
                            });
                })
                .publicMethod("touchedMessage", mb -> {
                    mb.withModifier(Modifier.STATIC)
                            .addArgument("Object", "what")
                            .returning("String")
                            .body(bb -> {
                                bb.iff(ib -> {
                                    ib.invokeAsBoolean("isEmpty").on("TOUCHED").returningStringLiteral("");
                                });
                                bb.declare("result").initializedWithNew("StringBuilder").withArgument(1024).inScope()
                                        .as("StringBuilder");
                                bb.invoke("append").withStringLiteral("\nCode generated by ").on("result");
//                                bb.invoke("append").withArgument("what.getClass().getSimpleName()");
                                bb.invoke("append").withArgumentFromInvoking("getSimpleName")
                                        .onInvocationOf("getClass").on("what").on("result");
                                bb.invoke("append").withStringLiteral(" and other annotation processors "
                                        + "requires the following dependencies be set for this project:\n")
                                        .on("result");
                                bb.simpleLoop("Libraries", "lib", slb -> {
                                    slb.over("TOUCHED", loopBlock -> {
                                        loopBlock.ifNotNull("lib", ifBlock -> {
                                            ifBlock.invoke("append")
                                                    .withArgument("lib.toXML()").on("result");
                                            ifBlock.invoke("add").withArgument("lib")
                                                    .on("REPORTED");
                                        });
                                    });
                                });
                                bb.invoke("clear").on("TOUCHED");
                                bb.returningInvocationOf("toString").on("result");
                            });
                })
                .publicMethod("forName", mb -> {
                    mb.returning(typeName).withModifier(PUBLIC, STATIC).addArgument("String", "fqn").body(bb -> {
                        bb.simpleLoop(typeName, "type", slb -> {
                            slb.over("values()", loopBlock -> {
                                loopBlock.iff(ib -> {
                                    ib.invoke("equals").withArgument("fqn").on("type.fqn").equals().expression("true")
                                            .endCondition().returning("type");

                                });
                            });
                        }).returning("null");
                    });
                });
    }

    public void go() throws Exception {
        Path root = this.topLevelBaseDir();
        ClassBuilder<String> knownTypes = typesEnumBuilder("org.nemesis.registration.typenames", "KnownTypes");
        ClassBuilder<String> jdkTypes = typesEnumBuilder("org.nemesis.registration.typenames", "JdkTypes");

        ClassBuilder<String> librariesClassBuilder = ClassBuilder.forPackage("org.nemesis.registration.typenames")
                .named("Libraries")
                .docComment("<b>DO NOT MODIFY THIS FILE!</b> ", "This file is generated by "
                        + "analyzing the POMs and class files used in all subprojects of the"
                        + "parent project for class names that are likely to be used in code"
                        + "generation. It will be regenerated at build time.")
                .withModifier(Modifier.PUBLIC)
                .implementing("Library")
                .toEnum()
                .privateFinalField("groupId").ofType("String")
                .privateFinalField("artifactId").ofType("String")
                .privateFinalField("version").ofType("String")
                .overridePublic("groupId").returning("String").bodyReturning("groupId")
                .overridePublic("artifactId").returning("String").bodyReturning("artifactId")
                .overridePublic("version").returning("String").bodyReturning("version")
                .overridePublic("toString", mb -> {
                    mb.returning("String").body(bb -> {
                        bb.returningValue(vb -> {
                            vb.stringConcatenation().expression("groupId").with().literal(":")
                                    .with().expression("artifactId").with().literal(":")
                                    .with().expression("version").endConcatenation();
                        });
                    });
                });

        List<FieldInfo> fields = new ArrayList<>(1000);
        List<FieldInfo> jdkFields = new ArrayList<>(1000);
        Set<DepVer> deps = new TreeSet<DepVer>();
        Map<DepVer, String> fieldNameForDependency = new HashMap<>();
        Set<String> usedFieldNames = new HashSet<>();
        Set<String> usedJdkFieldNames = new HashSet<>();
        if (includeJavaLangClasses) {
            String[] coreClasses = new String[]{
                "AutoCloseable", "CharSequence", "Comparable",
                "Iterable", "Boolean", "Byte", "Character",
                "Class", "ClassLoader", "Enum", "Double", "Float",
                "Integer", "Long", "Math", "Number", "Object",
                "Package", "Process", "ProcessBuilder",
                "Runtime", "Short", "String", "StringBuilder",
                "Thread", "ThreadLocal", "Throwable", "Void",
                "System", "IllegalArgumentException", "IllegalStateException",
                "NullPointerException", "UnsupportedOperationException",
                "SuppressWarnings"
            };
            for (String c : coreClasses) {
                c = "java.lang." + c;
                String minName = minimalName(c, usedJdkFieldNames);
                jdkFields.add(new FieldInfo(minName, null, c));
            }
        }

        findAllDependencies(topLevelBaseDir(), (projects, libraries, jdkClasses, remapWrapperModules) -> {
            for (Map.Entry<String, DepVer> e : libraries.entrySet()) {
                deps.add(e.getValue());
            }

            Set<String> usedLibNames = new HashSet<>();
            List<String> names = sortByTypeName(libraries.keySet());
            Set<DepVer> seenDvs = new HashSet<>();
            for (String typeName : names) {
                if (isBlacklisted(typeName)) {
                    continue;
                }
                DepVer dv = libraries.get(typeName);
                if (!seenDvs.contains(dv)) {
                    fieldNameForDependency.put(dv, minimalLibName(dv.artifactId, usedLibNames));
                    seenDvs.add(dv);
                }
                String fieldName = minimalName(typeName, usedFieldNames);
                fields.add(new FieldInfo(fieldName, dv, typeName));
            }
            for (String jdkClass : jdkClasses) {
                if (isBlacklisted(jdkClass)) {
                    continue;
                }
                jdkFields.add(new FieldInfo(minimalName(jdkClass, usedFieldNames), null, jdkClass));
            }
        });
        ClassBuilder.EnumConstantBuilder<ClassBuilder<String>> lecb = librariesClassBuilder.enumConstants();
        List<DepVer> depsSortedByFieldName = new ArrayList<>(deps);
        for (Iterator<DepVer> it = depsSortedByFieldName.iterator(); it.hasNext();) {
            if (fieldNameForDependency.get(it.next()) == null) {
                it.remove();
            }
        }
        Collections.sort(depsSortedByFieldName, (a, b) -> {
            String aa = fieldNameForDependency.get(a);
            String bb = fieldNameForDependency.get(b);
            return aa.compareTo(bb);
        });
        Set<String> usedLibNames = new HashSet<>();
        for (DepVer lib : depsSortedByFieldName) {
            String libFieldName = minimalLibName(lib.artifactId, usedLibNames);
            fieldNameForDependency.put(lib, libFieldName);
            lecb.addWithArgs(libFieldName)
                    .withStringLiteral(lib.groupId).withStringLiteral(lib.artifactId)
                    .withStringLiteral(lib.version).inScope();
        }
        lecb.endEnumConstants();
        librariesClassBuilder.constructor(con -> {
            con.addArgument("String", "groupId")
                    .addArgument("String", "artifactId")
                    .addArgument("String", "version").body(bb -> {
                bb.assign("this.groupId").toExpression("groupId")
                        .assign("this.artifactId").toExpression("artifactId")
                        .assign("this.version").toExpression("version");
            });
        });

        Collections.sort(fields);
        ClassBuilder.EnumConstantBuilder<ClassBuilder<String>> ecb = knownTypes.enumConstants();
        for (FieldInfo f : fields) {
            if (f.depVer != null) {
                String fieldName = fieldNameForDependency.get(f.depVer);
                if (fieldName == null) {
                    System.out.println("No field name for " + f.fqn);
                    continue;
                }
                ecb.addWithArgs(f.fieldName).withStringLiteral(f.fqn)
                        .withArgument("Libraries." + fieldNameForDependency.get(f.depVer))
                        .inScope();
            } else {
                ecb.addWithArgs(f.fieldName).withStringLiteral(f.fqn)
                        .inScope();
            }
        }
        ecb.endEnumConstants();

        ecb = jdkTypes.enumConstants();
        for (FieldInfo f : jdkFields) {
            if (f.depVer != null) {
                String fieldName = fieldNameForDependency.get(f.depVer);
                if (fieldName == null) {
                    System.out.println("No field name for " + f.fqn);
                    continue;
                }
                ecb.addWithArgs(f.fieldName).withStringLiteral(f.fqn)
                        .withArgument("Libraries." + fieldNameForDependency.get(f.depVer))
                        .inScope();
            } else {
                ecb.addWithArgs(f.fieldName).withStringLiteral(f.fqn)
                        .inScope();
            }
        }
        ecb.endEnumConstants();

//        String clazz = dependencies.build();
        Path sources = root.resolve("registration-annotation-processors/src/main/java/org/nemesis/registration/typenames");
        if (!Files.exists(sources)) {
            throw new IOException(sources + " does not exist");
        }
        Path file = sources.resolve(knownTypes.className() + ".java");
        FileUtils.writeUtf8(file, knownTypes.build());
        System.out.println("WRITE " + file);

        file = sources.resolve(jdkTypes.className() + ".java");
        System.out.println("WRITE " + file);
        FileUtils.writeUtf8(file, jdkTypes.build());

        file = sources.resolve(librariesClassBuilder.className() + ".java");
        System.out.println("WRITE " + file);
        FileUtils.writeUtf8(file, librariesClassBuilder.build());
    }

    static final String minimalLibName(String artifactId, Set<String> used) {
        // A few special-cases - org.netbeans.contrib.yenta.api gets turned into "API"
        if ("org-netbeans-contrib-yenta".equals(artifactId) && !used.contains("YENTA")) {
            used.add("YENTA");
            return "YENTA";
        }
        if ("antlr-wrapper".equals(artifactId) && !used.contains("ANTLR_WRAPPER")) {
            used.add("ANTLR_WRAPPER");
            return "ANTLR_WRAPPER";
        }
        String[] parts = artifactId.split("\\-");
        StringBuilder sb = new StringBuilder();
        for (int i = parts.length - 1; i >= 0; i--) {
            String p = parts[i];
            if (i == parts.length - 1 && i > 0 && ("api".equals(p) || "spi".equals(p) || "impl".equals(p))) {
                i--;
                p = parts[i];
            }
            if (sb.length() > 0) {
                sb.insert(0, '_');
            }
            p = camelCaseToUnderscores(p);
            sb.insert(0, p.toUpperCase());
            if (!used.contains(sb.toString())) {
                String result = sb.toString();
                if (!"LANGUAGE".equals(result)) {
                    used.add(result);
                    return result;
                }
            }
        }
        for (int i = 1;; i++) {
            String suffix = "_" + i;
            if (!used.contains(sb.toString() + suffix)) {
                String result = sb.toString() + suffix;
                used.add(result);
                return result;
            }
        }

    }

    static class FieldInfo implements Comparable<FieldInfo> {

        private final String fieldName;
        private final DepVer depVer;
        private final String fqn;

        public FieldInfo(String fieldName, DepVer depVer, String fqn) {
            this.fieldName = fieldName;
            this.depVer = depVer;
            this.fqn = fqn;
        }

        @Override
        public int compareTo(FieldInfo o) {
            return fieldName.compareTo(o.fieldName);
        }

        @Override
        public String toString() {
            if (depVer != null) {
                return fqn + " in " + depVer.toCoordinates();
            } else {
                return "JDK: " + fqn;
            }
        }
    }

    private List<String> sortByTypeName(Collection<String> all) {
        List<String> list = new ArrayList<>(all);
        Collections.sort(list, (a, b) -> {
            String[] aParts = a.split("\\.");
            String[] bParts = a.split("\\.");
            int apos = aParts.length - 1;
            int bpos = bParts.length - 1;
            while (apos > 0 && bpos > 0) {
                int result = aParts[apos].compareToIgnoreCase(bParts[bpos]);
                if (result != 0) {
                    return result;
                }
                apos--;
                bpos--;
            }
            return 0;
        });
        return list;
    }

    static final Pattern V = Pattern.compile("^v\\d+$");

    static String minimalName(String fqn, Set<String> used) {
        fqn = fqn.replace("MIME", "Mime")
                .replace("URL", "Url").replace("URI", "Uri")
                .replace("ANTLR", "Antlr");
        String[] parts = fqn.split("\\.");
        StringBuilder sb = new StringBuilder();
        boolean keepGoing = false;
        for (int i = parts.length - 1; i >= 0; i--) {
            String p = parts[i];
            // Omit unhelpful package components
            if ("api".equals(p) || "spi".equals(p) || "runtime".equals(p)) {
                continue;
            }
            if (V.matcher(p).find()) {
                keepGoing = true;
            }
            if (i > 2 && V.matcher(parts[i-1]).find()) {
                keepGoing = true;
            }
            if (i == parts.length - 1 && p.length() > 3 && p.startsWith("IO") && Character.isUpperCase(p.charAt(2))) {
                p = p.substring(0, 2) + "_" + p.substring(2, p.length());
            }
            if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '_') {
                sb.insert(0, '_');
            }
            p = camelCaseToUnderscores(p);
            sb.insert(0, p.toUpperCase());
            if (!used.contains(sb.toString()) && !keepGoing) {
                String result = sb.toString();
                used.add(result);
                return result;
            }
            keepGoing = false;
        }
        for (int i = 1;; i++) {
            String suffix = "_" + i;
            if (!used.contains(sb.toString() + suffix)) {
                String result = sb.toString() + suffix;
                used.add(result);
            }
        }
    }

    public static String camelCaseToUnderscores(CharSequence s) {
        StringBuilder sb = new StringBuilder();
        int max = s.length();
        for (int i = 0; i < max; i++) {
            char c = s.charAt(i);
            if (i == 0) {
                sb.append(Character.toUpperCase(c));
            } else {
                if (Character.isLowerCase(s.charAt(i - 1)) && Character.isUpperCase(c)) {
                    sb.append('_');
                }
                sb.append(Character.toUpperCase(c));
            }
        }
        return sb.toString();
    }

    public interface ThrowingQuadConsumer<A, B, C, D> {

        void accept(A a, B b, C c, D d) throws Exception;
    }

    private void findAllDependencies(Path root, ThrowingQuadConsumer<Set<ProjectInfo>, Map<String, DepVer>, Set<String>, Map<DepVer, DepVer>> consumer) throws Exception {
        Set<DepVer> allDeps = new HashSet<>();
        Set<ProjectInfo> result = new TreeSet<>();
        Set<DepVer> allLocalProjectCoordinates = new HashSet<>();
        Map<String, DepVer> dependencyManaged = new HashMap<>();
        Set<String> jdkClasses = new TreeSet<>();
        Map<DepVer, DepVer> remapWrappers = new HashMap<>();
        PomFileResolver resolver = PomFileResolver.context(root);
        findAllPomFiles(root, pom -> {
            PomFileAnalyzer ana = new PomFileAnalyzer(pom.toFile());
            ana.inContext(doc -> {
                String packaging = ana.getPackaging();
                if ("pom".equals(packaging)) {
                    Set<DepVer> dv = new HashSet<>();
                    ana.findDependencies(dv, true);
                    PropertyResolver res = new PropertyResolver(pom.toFile(), resolver, ana);
                    for (DepVer d : dv) {
                        if (d.groupId.contains("${") || d.version.contains("${")) {
                            d = res.resolve(d);
                        }
                        dependencyManaged.put(d.toIdentifier(), d);
                    }
                }
                if ("nbm".equals(packaging) || "jar".equals(packaging)) {
                    boolean isWrapper = wrapperProjectFolderNames.contains(pom.getParent().getFileName().toString());

                    DepVer projectCoordinates = ana.toDepVer();
                    PropertyResolver res = new PropertyResolver(pom.toFile(), resolver, ana);

                    if (projectCoordinates.groupId.contains("${") || projectCoordinates.version.contains("${")) {
                        projectCoordinates = res.resolve(projectCoordinates);
                    }

                    ProjectInfo info = new ProjectInfo(projectCoordinates, pom, "nbm".equals(packaging));
                    allLocalProjectCoordinates.add(projectCoordinates);
                    allDeps.add(projectCoordinates);
                    ana.findDependencies(info.dependencies, false);
                    if (isWrapper) {
                        for (DepVer dv : info.dependencies) {
                            remapWrappers.put(dv, projectCoordinates);
                        }
                    }
                    result.add(info);
                    if ("pom".equals(packaging)) {
                        Set<String> pkgs = ana.publicPackages();
                        for (String pkg : pkgs) {
                            if (!"-".equals(pkg)) {
                                info.publicPackages.add(pkg);
                            }
                        }
                    }

                    Path javaSources = pom.getParent().resolve("src/main/java");
                    if (Files.exists(javaSources)) {
                        findAllImports(javaSources, info.importedTypes, info.localPackages, info.topLevelTypes, jdkClasses);
                    }
                    javaSources = pom.getParent().resolve("target/generated-sources/antlr4");
                    if (Files.exists(javaSources)) {
                        findAllImports(javaSources, info.importedTypes, info.localPackages, info.topLevelTypes, jdkClasses);
                    }
                    javaSources = pom.getParent().resolve("target/generated-sources/annotations");
                    if (Files.exists(javaSources)) {
                        findAllImports(javaSources, info.importedTypes, info.localPackages, info.topLevelTypes, jdkClasses);
                    }
                }
            });
        });

        if (dependencyManaged.isEmpty()) {
            throw new IllegalStateException("Dependency managed info is empty");
        }

        Set<String> allUsedClasses = new HashSet<>();
        for (ProjectInfo p : result) {
            p.rewriteDependencies(dependencyManaged);
            allDeps.addAll(p.dependencies);
            allUsedClasses.addAll(p.topLevelTypes);
            allUsedClasses.addAll(p.importedTypes);
        }
        Set<DepVer> nonLocal = new HashSet<>(allDeps);
        PomFileResolver repo = PomFileResolver.localRepo();
        Map<String, DepVer> libraryForType = new HashMap<>(20000);
        for (DepVer dv : nonLocal) {
            File repoPom = repo.resolve(dv.groupId, dv.artifactId, dv.version);
            String jarName = repoPom.getName().toString();
            jarName = jarName.substring(0, jarName.length() - 4) + ".jar";
            File jarFile = new File(repoPom.getParent(), jarName);
            if (!jarFile.exists()) {
                throw new AssertionError("No local jar for " + dv.toCoordinates() + " looking for " + jarFile.getPath());
            } else {
                classNamesIn(jarFile, allUsedClasses, className -> {
                    DepVer remapped = remapWrappers.get(dv);
                    libraryForType.put(className, remapped == null ? dv : remapped);
                });
            }
        }
        consumer.accept(result, libraryForType, jdkClasses, remapWrappers);
    }

    private boolean isPublic(JarFile file, JarEntry entry) throws IOException {
        try (InputStream in = file.getInputStream(entry)) {
            ClassFile classFile = new ClassFile(in, false);
            if (classFile.isSynthetic()) {
                return false;
            }
            int access = classFile.getAccess();
            return (access & Access.PUBLIC) != 0;
        }
    }

    private void classNamesIn(File jarFile, Set<String> knownClasses, Consumer<String> c) throws IOException {
        try (JarFile jar = new JarFile(jarFile)) {
            Manifest manifest = jar.getManifest();
            Attributes attrs = manifest.getMainAttributes();
            String pubPackages = attrs.getValue("OpenIDE-Module-Public-Packages");
            Predicate<String> filter = s -> true;
            if (pubPackages != null) {
                if (!"-".equals(pubPackages.trim())) {
                    filter = GlobFilter.create(pubPackages.trim());
                }
            }
            for (JarEntry e : CollectionUtils.toIterable(jar.entries())) {
                String name = e.getName();
                if (name.endsWith(".class")) {
                    if (!isPublic(jar, e)) {
                        continue;
                    }
                    name = name.substring(0, name.length() - 6).replace('/', '.');
                    if (filter.test(name)) {
                        name = name.replace('$', '.'); // look like an import
                        int tailStart = name.lastIndexOf('.');
                        if (tailStart > 0 && tailStart < name.length() - 1) {
                            String tail = name.substring(tailStart + 1);
                            boolean allDigits = true;
                            for (int i = 0; i < tail.length(); i++) {
                                if (!Character.isDigit(tail.charAt(i))) {
                                    allDigits = false;
                                }
                            }
                            if (allDigits) {
                                continue;
                            }
                        }
                        if (knownClasses.contains(name)) {
                            c.accept(name);
                        }
                    }
                }
            }
        }
    }

    static final class ProjectInfo implements Comparable<ProjectInfo> {

        private final DepVer coordinates;
        private final Path pomFile;
        Set<DepVer> dependencies = new TreeSet<>();
        Set<String> publicPackages = new TreeSet<>();
        Set<String> importedTypes = new TreeSet<>();
        Set<String> topLevelTypes = new TreeSet<>();
        Set<String> localPackages = new TreeSet<>();

        public ProjectInfo(DepVer coordinates, Path pomFile, boolean isNbm) {
            this.coordinates = coordinates;
            this.pomFile = pomFile;
        }

        @Override
        public int compareTo(ProjectInfo o) {
            return coordinates.compareTo(o.coordinates);
        }

        void rewriteDependencies(Map<String, DepVer> withVersions) {
            Set<DepVer> qualified = new TreeSet<>();
            for (DepVer dv : dependencies) {
                if (dv.groupId.contains("${") || dv.artifactId.contains("${")) {
                    DepVer nue = withVersions.get(dv.toIdentifier());
                    if (nue == null) {
                        throw new IllegalStateException("Did not find " + dv + " in " + withVersions);
                    }
                    qualified.add(nue);
                    continue;
                } else if ("0.0.0-?".equals(dv.version)) {
                    DepVer nue = withVersions.get(dv.toIdentifier());
                    if (nue == null) {
                        throw new IllegalStateException("No qualified " + dv.toIdentifier()
                                + " for " + dv.toIdentifier() + " in " + withVersions);
                    }
                    qualified.add(nue);
                    continue;
                }
                qualified.add(dv);
            }
            dependencies.clear();
            dependencies.addAll(qualified);
        }
    }

    private static final Pattern IMPORT_PATTERN = Pattern.compile("^\\s*?import\\s+(\\S+)\\;.*$");

    private void findAllImports(Path dir, Set<String> imports, Set<String> localPackages, Set<String> topLevelTypes, Set<String> jdkClasses) throws Exception {
        try (Stream<Path> str = Files.walk(dir)) {
            str.filter((pdir) -> {
                return Files.isDirectory(pdir);
            }).forEach(pkg -> {
                try {
                    boolean hasClasses = Files.list(pkg).anyMatch(fl -> {
                        return !Files.isDirectory(fl) && fl.getFileName().toString().endsWith(".java");
                    });
                    if (hasClasses) {
                        String packageName = dir.relativize(pkg).toString().replace(File.separatorChar, '.');
                        localPackages.add(packageName);
                    }
                } catch (IOException ex) {
                    throw new AssertionError(ex);
                }
            });
        }
        findAllJavaSources(dir, src -> {
            String topLevelType = dir.relativize(src).toString().replace(File.separatorChar, '.');
            // remove .java
            topLevelType = topLevelType.substring(0, topLevelType.length() - 5);
            topLevelTypes.add(topLevelType);
            Path pkgPath = dir.relativize(src.getParent());
            localPackages.add(pkgPath.toString().replace(File.separatorChar, '.'));
            try (Stream<CharSequence> str = FileUtils.lines(src, UTF_8)) {
                str.forEach(line -> {
                    Matcher m = IMPORT_PATTERN.matcher(line);
                    if (m.find()) {
                        String imported = m.group(1);
                        if (imported.indexOf('*') >= 0) {
                            return;
                        }
                        if (!imported.startsWith("java")) {
                            imports.add(m.group(1));
                        } else {
                            jdkClasses.add(m.group(1));
                        }
                    }
                });
            }
        });
        for (Iterator<String> it = imports.iterator(); it.hasNext();) {
            String imported = it.next();
            for (String lp : localPackages) {
                if (imported.startsWith(lp)) {
                    String sub = imported.substring(lp.length());
                    if (sub.indexOf('.') < 0) {
                        it.remove();
                    }
                }
            }
        }
    }

    private Set<Path> findAllJavaSources(Path root, ThrowingConsumer<Path> javafileConsumer) throws Exception {
        Set<Path> poms = new LinkedHashSet<>();
        try (Stream<Path> str = Files.walk(root)) {
            str.filter(pth -> {
                return Files.isDirectory(pth) || pth.getFileName().toString().endsWith(".java");
            }).forEach(pomOrDir -> {
                if (!Files.isDirectory(pomOrDir)) {
                    poms.add(pomOrDir);
                    try {
                        javafileConsumer.accept(pomOrDir);
                    } catch (Exception ex) {
                        throw new AssertionError(ex);
                    }
                }
            });

        }
        return poms;
    }

    private Set<Path> findAllPomFiles(Path root, ThrowingConsumer<Path> pomfileConsumer) throws Exception {
        Set<Path> poms = new HashSet<>();
        try (Stream<Path> str = Files.walk(root)) {
            str.filter(pth -> {
                if (pth.toString().contains("antlr-plugin")) {
                    return false;
                }
                return (Files.isDirectory(pth))
                        || "pom.xml".equals(pth.getFileName().toString());
            }).forEach(pomOrDir -> {
                if (!Files.isDirectory(pomOrDir)) {
                    if (isBlacklisted(pomOrDir.getParent())) {
                        return;
                    }
                    poms.add(pomOrDir);
                }
            });
        }
        if (!poms.contains(root.resolve("pom.xml"))) {
            throw new IllegalStateException("Root pom not found");
        }
        List<Path> paths = new ArrayList<>(poms);
        Collections.sort(paths, (a, b) -> {
            return Integer.compare(a.getNameCount(), b.getNameCount());
        });
        for (Path p : paths) {
            try {
                pomfileConsumer.accept(p);
            } catch (Exception ex) {
                throw new AssertionError(ex);
            }

        }
        return poms;
    }

    public Path topLevelBaseDir() throws URISyntaxException {
        Path baseDir = Paths.get(GenerateDependenciesClass.class
                .getProtectionDomain().getCodeSource()
                .getLocation().toURI()).getParent().getParent().getParent();
        return baseDir;
    }
}

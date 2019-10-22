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
package org.nemesis.registration;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.logging.Level;
import javax.annotation.processing.Filer;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import static javax.lang.model.element.Modifier.DEFAULT;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PROTECTED;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;
import static javax.lang.model.element.Modifier.SYNCHRONIZED;
import javax.lang.model.element.Name;
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import static org.nemesis.registration.GotoDeclarationProcessor.DECLARATION_TOKEN_PROCESSOR_TYPE;
import static org.nemesis.registration.GotoDeclarationProcessor.GOTO_ANNOTATION;
import static org.nemesis.registration.KeybindingsAnnotationProcessor.ANTLR_ACTION_ANNO_TYPE;
import static org.nemesis.registration.LanguageRegistrationProcessor.REGISTRATION_ANNO;
import com.mastfrog.annotation.processor.LayerGeneratingDelegate;
import com.mastfrog.java.vogon.ClassBuilder;
import com.mastfrog.java.vogon.LinesBuilder;
import com.mastfrog.annotation.validation.AnnotationMirrorMemberTestBuilder;
import com.mastfrog.annotation.AnnotationUtils;
import static com.mastfrog.annotation.AnnotationUtils.capitalize;
import static com.mastfrog.annotation.AnnotationUtils.simpleName;
import static com.mastfrog.annotation.AnnotationUtils.stripMimeType;
import com.mastfrog.util.collections.CollectionUtils;
import org.openide.filesystems.FileUtil;
import org.openide.filesystems.annotations.LayerBuilder;
import org.openide.filesystems.annotations.LayerGenerationException;

/**
 *
 * @author Tim Boudreau
 */
public class LanguageRegistrationDelegate extends LayerGeneratingDelegate {

    public static final int DEFAULT_MODE = 0;
    public static final int MORE = -2;
    public static final int SKIP = -3;
    public static final int DEFAULT_TOKEN_CHANNEL = 0;
    public static final int HIDDEN = 1;
    public static final int MIN_CHAR_VALUE = 0;
    public static final int MAX_CHAR_VALUE = 1114111;
    private BiPredicate<? super AnnotationMirror, ? super Element> mirrorTest;

    private static Map<String, Integer> lexerClassConstants() {
        Map<String, Integer> result = new HashMap<>();
        result.put("DEFAULT_MODE", 0);
        result.put("MORE", -2);
        result.put("SKIP", -3);
        result.put("DEFAULT_TOKEN_CHANNEL", 0);
        result.put("HIDDEN", 1);
        result.put("MIN_CHAR_VALUE", 0);
        result.put("MAX_CHAR_VALUE", 1114111);
        return result;
    }

    @Override
    protected boolean validateAnnotationMirror(AnnotationMirror mirror, ElementKind kind, Element element) {
        return mirrorTest.test(mirror, element);
    }

    @Override
    protected void onInit(ProcessingEnvironment env, AnnotationUtils utils) {
        mirrorTest = utils.multiAnnotations().whereAnnotationType(REGISTRATION_ANNO, mb -> {
            mb.testMember("name").stringValueMustNotBeEmpty()
                    .stringValueMustBeValidJavaIdentifier()
                    .build()
                    .testMember("mimeType")
                    .validateStringValueAsMimeType().build()
                    .testMember("lexer", lexer -> {
                        lexer.asTypeSpecifier()
                                .hasModifier(PUBLIC)
                                .isSubTypeOf("org.antlr.v4.runtime.Lexer")
                                .build();
                    })
                    .testMemberAsAnnotation("parser", parserMember -> {
                        parserMember.testMember("type").asTypeSpecifier(pType -> {
                            pType.isSubTypeOf("org.antlr.v4.runtime.Parser")
                                    .hasModifier(PUBLIC);
                        }).build()
                                .testMember("entryPointRule")
                                .intValueMustBeGreaterThanOrEqualTo(0)
                                .build()
                                .testMember("parserStreamChannel")
                                .intValueMustBeGreaterThanOrEqualTo(0)
                                .build()
                                .testMember("helper").asTypeSpecifier(helperType -> {
                            helperType.isSubTypeOf("org.nemesis.antlr.spi.language.NbParserHelper")
                                    .doesNotHaveModifier(PRIVATE)
                                    .mustHavePublicNoArgConstructor();
                        }).build();
                    })
                    .testMemberAsAnnotation("categories", cb -> {
                        cb.testMember("tokenIds")
                                .intValueMustBeGreaterThan(0).build();
                        cb.testMemberAsAnnotation("colors", colors -> {
                            Consumer<AnnotationMirrorMemberTestBuilder<?>> c = ammtb -> {
                                ammtb.arrayValueSizeMustEqualOneOf(
                                        "Color arrays must be empty, or be RGB "
                                        + "or RGBA values from 0-255", 0, 3, 4)
                                        .intValueMustBeLessThanOrEqualTo(255)
                                        .intValueMustBeGreaterThanOrEqualTo(0);
                            };
                            for (String s : new String[]{"fg", "bg", "underline", "waveUnderline"}) {
                                colors.testMember(s, c);
                            }
                            colors.testMember("themes")
                                    .stringValueMustNotContain('/').build();
                        });
                    })
                    .testMemberAsAnnotation("syntax", stb -> {
                        stb.testMember("hooks")
                                .asType(hooksTypes -> {
                                    hooksTypes.isAssignable(
                                            "org.nemesis.antlr.spi.language.DataObjectHooks")
                                            .nestingKindMustNotBe(NestingKind.LOCAL)
                                            .build();
                                })
                                .valueAsTypeElement()
                                .mustHavePublicNoArgConstructor()
                                .testContainingClasses(cc -> {
                                    cc.nestingKindMayNotBe(NestingKind.LOCAL)
                                            .doesNotHaveModifier(PRIVATE);
                                })
                                .build()
                                .build();
                    }).testMemberAsAnnotation("genericCodeCompletion", gcc -> {
                gcc.testMember("ignoreTokens")
                        .intValueMustBeGreaterThan(0).build();
            });
            ;
        }).build();
        System.out.println("MIRROR TEST: \n" + mirrorTest);
    }

    private final Map<String, Set<VariableElement>> gotos = new HashMap<>();

    @Override
    protected boolean processFieldAnnotation(VariableElement var, AnnotationMirror mirror, RoundEnvironment roundEnv) throws Exception {
        if (GOTO_ANNOTATION.equals(mirror.getAnnotationType().toString())) {
            String mime = utils().annotationValue(mirror, "mimeType", String.class);
            if (mime != null) {
                Set<VariableElement> ves = gotos.get(mime);
                if (ves == null) {
                    ves = new HashSet<>();
                    gotos.put(mime, ves);
                }
                ves.add(var);
            }
            return false;
        }
        return true;
    }

    private boolean useDeclarationTokenProcessor(String mimeType) {
        return gotos.containsKey(mimeType);
    }

    private void preemptivelyCheckGotos(RoundEnvironment env) throws Exception {
        for (Element e : utils().findAnnotatedElements(env, GOTO_ANNOTATION)) {
            if (e instanceof VariableElement) {
                AnnotationMirror am = utils().findAnnotationMirror(e, GOTO_ANNOTATION);
                if (am != null) {
                    processFieldAnnotation((VariableElement) e, am, env);
                }
            }
        }
    }

    private List<Element> findAntlrActionElements(RoundEnvironment env, String mimeType) {
        Set<Element> all = utils().findAnnotatedElements(env, ANTLR_ACTION_ANNO_TYPE);
        Map<AnnotationMirror, Element> result = new HashMap<>();
        for (Element el : all) {
            AnnotationMirror mir = utils().findAnnotationMirror(el, ANTLR_ACTION_ANNO_TYPE);
            String mime = utils().annotationValue(mir, "mimeType", String.class);
            if (mimeType.equals(mime)) {
                result.put(mir, el);
            }
        }
        List<Map.Entry<AnnotationMirror, Element>> l = new ArrayList<>(result.entrySet());

        Collections.sort(l, (a, b) -> {
            Integer first = utils().annotationValue(a.getKey(), "order", Integer.class);
            Integer second = utils().annotationValue(b.getKey(), "order", Integer.class);
            return first == null || second == null ? 0 : first.compareTo(second);
        });
        List<Element> res = new ArrayList<>(l.size());
        for (Map.Entry<AnnotationMirror, Element> e : l) {
            res.add(e.getValue());
        }
        return res;
    }

    @Override
    protected boolean processTypeAnnotation(TypeElement type, AnnotationMirror mirror, RoundEnvironment roundEnv) throws Exception {
        if (ANTLR_ACTION_ANNO_TYPE.equals(mirror.getAnnotationType().toString())) {
            return false;
        }
        String mimeType = utils().annotationValue(mirror, "mimeType", String.class);
        preemptivelyCheckGotos(roundEnv);
        String prefix = utils().annotationValue(mirror, "name", String.class);
        LexerProxy lexerProxy = LexerProxy.create(mirror, type, utils());
        if (lexerProxy == null) {
            return true;
        }
        TypeMirror tokenCategorizerClass = utils().typeForSingleClassAnnotationMember(mirror, "tokenCategorizer");

        AnnotationMirror parserHelper = utils().annotationValue(mirror, "parser", AnnotationMirror.class);
        ParserProxy parser = null;
        if (parserHelper != null) {
            parser = createParserProxy(parserHelper, utils());
            if (parser == null) {
                return true;
            }
            generateParserClasses(parser, lexerProxy, type, mirror, parserHelper, prefix);
        }

        generateTokensClasses(type, mirror, lexerProxy, tokenCategorizerClass, prefix, parser);

        AnnotationMirror fileInfo = utils().annotationValue(mirror, "file", AnnotationMirror.class);
        if (fileInfo != null && !roundEnv.errorRaised()) {
            String extension = utils().annotationValue(fileInfo, "extension", String.class, ".");
            if (extension != null || !".".equals(extension)) {
                for (char c : extension.toCharArray()) {
                    if (Character.isWhitespace(c)) {
                        utils().fail("File extension '" + extension + "' contains whitespace", type, fileInfo);
                        return false;
                    }
                }
                generateDataObjectClassAndRegistration(fileInfo, extension, mirror, prefix, type, parser, lexerProxy, roundEnv);
            }
        }
        AnnotationMirror codeCompletion = utils().annotationValue(mirror, "genericCodeCompletion", AnnotationMirror.class);
        if (codeCompletion != null) {
            generateCodeCompletion(prefix, mirror, codeCompletion, type, mimeType, lexerProxy, parser);
        }
        // Returning true could stop LanguageFontsColorsProcessor from being run
        return false;
    }

    private void generateCodeCompletion(String prefix, AnnotationMirror mirror, AnnotationMirror codeCompletion, TypeElement on, String mimeType, LexerProxy lexer, ParserProxy parser) throws IOException {
        String generatedClassName = prefix + "GenericCodeCompletion";
        ClassBuilder<String> cb = ClassBuilder.forPackage(utils().packageName(on)).named(generatedClassName)
                .importing(lexer.lexerClassFqn(), parser.parserClassFqn(),
                        "java.io.IOException", "javax.swing.text.Document",
                        "org.antlr.v4.runtime.CommonTokenStream",
                        "org.antlr.v4.runtime.Parser",
                        "org.nemesis.antlr.completion.grammar.GrammarCompletionProvider",
                        "org.nemesis.source.api.GrammarSource",
                        "org.netbeans.api.editor.mimelookup.MimeRegistration",
                        "org.netbeans.spi.editor.completion.CompletionProvider",
                        "org.nemesis.antlrformatting.api.Criteria"
                ).extending("GrammarCompletionProvider")
                .annotatedWith("MimeRegistration", ab -> {
                    ab.addArgument("mimeType", mimeType)
                            .addClassArgument("service", "CompletionProvider");
                })
                .withModifier(PUBLIC, FINAL)
                .staticImport(lexer.lexerClassFqn() + ".*")
                .privateMethod("createParser", mb -> {
                    mb.withModifier(STATIC)
                            .addArgument("Document", "doc")
                            .returning("Parser")
                            .throwing("IOException")
                            .body(bb -> {
                                int streamChannel = 0;
                                AnnotationMirror parserInfo = utils().annotationValue(mirror, "parser", AnnotationMirror.class);
                                if (parserInfo != null) {
                                    streamChannel = utils().annotationValue(parserInfo, "parserStreamChannel", Integer.class, 0);
                                }
                                bb.declare("lexer")
                                        .initializedByInvoking("createAntlrLexer")
                                        .withArgumentFromInvoking("stream")
                                        .onInvocationOf("find")
                                        .withArgument("doc")
                                        .withStringLiteral(mimeType)
                                        .on("GrammarSource")
                                        .on(prefix + "Hierarchy").as(lexer.lexerClassSimple());
                                bb.declare("stream").initializedByInvoking("new CommonTokenStream")
                                        .withArgument("lexer").withArgument(streamChannel)
                                        .inScope().as("CommonTokenStream");

                                bb.returningInvocationOf("new " + parser.parserClassSimple()).withArgument("stream").inScope();

                            });
                })
                .field("CRITERIA", fb -> {
                    fb.withModifier(PRIVATE, STATIC, FINAL)
                            .initializedFromInvocationOf("forVocabulary")
                            .withArgument("VOCABULARY")
                            .on("Criteria").ofType("Criteria");
                })
                .constructor(con -> {
                    con.setModifier(PUBLIC).body(bb -> {
                        bb.invoke("super")
                                .withArgument(generatedClassName + "::createParser")
                                .withArgument(("ignored -> false"))
                                .withArgumentFromInvoking("anyOf", ib -> {
                                    Set<Integer> ignore = new TreeSet<>(utils().annotationValues(codeCompletion, "ignoreTokens", Integer.class));
                                    for (Integer i : ignore) {
                                        ib.withArgument(lexer.lexerClassSimple() + "." + lexer.tokenName(i));
                                    }
                                    ib.on("CRITERIA");
                                })
                                .inScope();
                    });
                });

        writeOne(cb);

    }

    static ParserProxy createParserProxy(AnnotationMirror parserHelper, AnnotationUtils utils) {
        int entryPointRuleId = utils.annotationValue(parserHelper, "entryPointRule", Integer.class, Integer.MIN_VALUE);
        TypeMirror parserClass = utils.typeForSingleClassAnnotationMember(parserHelper, "type");
        return ParserProxy.create(entryPointRuleId, parserClass, utils);
    }

    private static int dataObjectClassCount = 0;

    // DataObject generation
    private void generateDataObjectClassAndRegistration(AnnotationMirror fileInfo, String extension, AnnotationMirror mirror, String prefix, TypeElement type, ParserProxy parser, LexerProxy lexer, RoundEnvironment roundEnv) throws Exception {
        dataObjectClassCount++;
        String mimeType = utils().annotationValue(mirror, "mimeType", String.class);
        if ("<error>".equals(mimeType)) {
            utils().fail("Could not get mime type", type);
            return;
        }
        String iconBaseTmp = utils().annotationValue(fileInfo, "iconBase", String.class, "");
        String pkg = processingEnv.getElementUtils().getPackageOf(type).getQualifiedName().toString();
        if (pkg != null && !pkg.isEmpty() && !iconBaseTmp.isEmpty() && iconBaseTmp.indexOf('/') < 0) {
            // Allow relative paths if the icon file is in the same package
            iconBaseTmp = pkg.replace('.', '/') + '/' + iconBaseTmp;
        }
        String iconBase = iconBaseTmp;
        String dataObjectPackage = pkg + ".file";
        String dataObjectClassName = prefix + "DataObject";
        String dataObjectFqn = dataObjectPackage + "." + dataObjectClassName;
        String actionPath = "Loaders/" + mimeType + "/Actions";
        boolean multiview = utils().annotationValue(fileInfo, "multiview", Boolean.class, false);
        // Leave DataObject subclass non-final for subclassing to programmatically
        // register it in tests, in order to be able to create a fake DataEditorSupport.Env
        // over it
        ClassBuilder<String> cl = ClassBuilder.forPackage(dataObjectPackage).named(dataObjectClassName)
                .importing("org.openide.awt.ActionID", "org.openide.awt.ActionReference",
                        "org.openide.awt.ActionReferences", "org.openide.filesystems.FileObject",
                        "org.openide.filesystems.MIMEResolver", "org.openide.loaders.DataObject",
                        "org.openide.loaders.DataObjectExistsException", "org.openide.loaders.MultiDataObject",
                        "org.openide.loaders.MultiFileLoader", "org.openide.util.Lookup",
                        //                        "javax.annotation.processing.Generated",
                        "org.openide.util.NbBundle.Messages"
                ).staticImport(dataObjectFqn + ".ACTION_PATH")
                //                .annotatedWith("Generated").addArgument("value", getClass().getName()).addArgument("comments", versionString()).closeAnnotation()
                .extending("MultiDataObject")
                .withModifier(PUBLIC)/* .withModifier(FINAL) */
                .field("ACTION_PATH").withModifier(STATIC).withModifier(PUBLIC)
                .withModifier(FINAL).initializedTo(LinesBuilder.stringLiteral(actionPath)).ofType(STRING);

        List<String> hooksClass = utils().typeList(fileInfo, "hooks", "org.nemesis.antlr.spi.language.DataObjectHooks");
        Set<String> hooksMethods
                = hooksClass.isEmpty()
                ? Collections.emptySet()
                : generateHookMethods(fileInfo, type, hooksClass.get(0), cl);

        if (multiview) {
            cl.importing("org.netbeans.core.spi.multiview.MultiViewElement",
                    "org.netbeans.core.spi.multiview.text.MultiViewEditorElement",
                    "org.openide.windows.TopComponent"
            );
        }
        addActionAnnotations(cl, fileInfo);
        String msgName = "LBL_" + prefix + "_LOADER";
        String sourceTabName = multiview ? "LBL_" + prefix + "_SOURCE_TAB" : null;
        cl.annotatedWith("Messages", ab -> {
            String msgValue = msgName + "=" + prefix + " files";
            if (sourceTabName != null) {
                ab.addArrayArgument("value", arr -> {
                    arr.literal(msgValue).literal(sourceTabName + "=" + prefix + " Source");
                });
            } else {
                ab.addArgument("value", msgValue);
            }
        });
//        cl.annotatedWith("Messages").addArgument("value", msgName + "=" + prefix + " files\n" + sourceTabName + "=" + prefix + " Source").closeAnnotation();
        cl.annotatedWith("DataObject.Registration", annoBuilder -> {
            annoBuilder.addArgument("mimeType", mimeType)
                    .addArgument("iconBase", iconBase)
                    .addArgument("displayName", "#" + msgName) // xxx localize
                    .addArgument("position", 1536 + dataObjectClassCount)
                    .closeAnnotation();
        });
        cl.annotatedWith("MIMEResolver.ExtensionRegistration", ab -> {
            ab.addArgument("displayName", "#" + msgName)
                    .addArgument("mimeType", mimeType)
                    .addArgument("extension", extension)
                    .addArgument("position", 1536 + dataObjectClassCount)
                    .closeAnnotation();
        });
        cl.constructor(cb -> {
            cb.setModifier(PUBLIC)
                    .addArgument("FileObject", "pf")
                    .addArgument("MultiFileLoader", "loader")
                    .throwing("DataObjectExistsException")
                    .body(bb -> {
                        bb.invoke("super").withArgument("pf").withArgument("loader").inScope();
                        bb.log(Level.FINE).stringLiteral(dataObjectClassName)
                                .argument("pf.getPath()")
                                .logging("Create a new {0} for {1}");
//                    bb.statement("if (pf.getPath().startsWith(\"Editors\")) new Exception(pf.getPath()).printStackTrace(System.out)");
                        bb.invoke("registerEditor").withArgument(LinesBuilder.stringLiteral(mimeType))
                                .withArgument(multiview)
                                .inScope().endBlock();
                        if (hooksMethods.contains("notifyCreated")) {
                            cb.annotatedWith("SuppressWarnings")
                                    .addArgument("value", "LeakingThisInConstructor")
                                    .closeAnnotation();
                            bb.invoke("notifyCreated").withArgument("this").on("HOOKS");
                        }
                    });
        });
        cl.override("associateLookup").returning("int").withModifier(PROTECTED).body().returning("1").endBlock();

        if (multiview) {
            cl.method("createEditor").addArgument("Lookup", "lkp").withModifier(PUBLIC).withModifier(STATIC)
                    .annotatedWith("MultiViewElement.Registration", ab -> {
                        ab.addArgument("displayName", '#' + sourceTabName)
                                .addArgument("iconBase", iconBase)
                                .addArgument("mimeType", mimeType)
                                .addExpressionArgument("persistenceType", "TopComponent.PERSISTENCE_ONLY_OPENED")
                                .addArgument("preferredID", prefix)
                                .addArgument("position", 1000).closeAnnotation();
                    })
                    .returning("MultiViewEditorElement")
                    .body()
                    .log("Create editor for", Level.FINER, "lkp.lookup(DataObject.class)")
                    .returning("new MultiViewEditorElement(lkp)").endBlock();
        }

        for (String s : new String[]{"copyAllowed", "renameAllowed", "moveAllowed", "deleteAllowed"}) {
            Boolean val = utils().annotationValue(fileInfo, s, Boolean.class);
            if (val != null) {
                createFoMethod(cl, s, val);
            }
        }
        String editorKitFqn = generateEditorKitClassAndRegistration(dataObjectPackage, mirror, prefix, type, mimeType, parser, lexer, roundEnv);
        cl.importing(EDITOR_KIT_TYPE)
                .method("createEditorKit", mb -> {
                    mb.returning("EditorKit");
                    mb.addArgument("FileObject", "file")
                            .withModifier(STATIC)
                            .body(bb -> {
                                bb.log(Level.FINER)
                                        .argument("file.getPath()")
                                        .stringLiteral(simpleName(editorKitFqn))
                                        .logging("Fetch editor kit {1} for {0}");
                                bb.returning(simpleName(editorKitFqn) + ".INSTANCE").endBlock();
                            });
                });

        LayerBuilder layer = layer(type);

        String editorKitFile = "Editors/" + mimeType + "/" + editorKitFqn.replace('.', '-') + ".instance";
        layer(type).file(editorKitFile)
                .methodvalue("instanceCreate", dataObjectFqn, "createEditorKit")
                .stringvalue("instanceOf", EDITOR_KIT_TYPE)
                .write();

        layer.folder("Actions/" + AnnotationUtils.stripMimeType(mimeType))
                .stringvalue("displayName", prefix).write();
        writeOne(cl);
    }

    static final Set<String> EXPECTED_HOOK_METHODS = new HashSet<>(Arrays.asList(
            "notifyCreated",
            "decorateLookup",
            "createNodeDelegate",
            "handleDelete",
            "handleRename",
            "handleCopy",
            "handleMove",
            "handleCreateFromTemplate",
            "handleCopyRename"));

    static final Consumer<ClassBuilder<?>> hookMethodGenerator(String hookMethod) {
        switch (hookMethod) {
            case "notifyCreated":
                return cb -> {
                };
            case "decorateLookup":
                return decorateLookupInvoker;
            case "createNodeDelegate":
                return createNodeDelegateInvoker;
            case "handleDelete":
                return handleDeleteInvoker;
            case "handleRename":
                return handleRenameInvoker;
            case "handleCopy":
                return handleCopyInvoker;
            case "handleMove":
                return handleMoveInvoker;
            case "handleCreateFromTemplate":
                return handleCreateFromTemplateInvoker;
            case "handleCopyRename":
                return handleCopyRenameInvoker;
            default:
                throw new AssertionError("Not a hook method: " + hookMethod);
        }
    }

    private static final Consumer<ClassBuilder<?>> decorateLookupInvoker = (cb) -> {
        cb.importing("org.openide.util.Lookup", "org.openide.loaders.DataObject",
                "java.util.function.Supplier", "org.openide.util.lookup.InstanceContent",
                "org.openide.util.lookup.AbstractLookup", "org.openide.util.lookup.ProxyLookup")
                .field("lookupContent").withModifier(PRIVATE, FINAL).initializedTo("new InstanceContent()").ofType("InstanceContent")
                .field("contributedLookup").withModifier(PRIVATE, FINAL).initializedTo("new AbstractLookup(lookupContent)").ofType("Lookup")
                .field("lkp").withModifier(PRIVATE).ofType("Lookup")
                .overridePublic("getLookup").returning("Lookup")
                .body(bb -> {
                    bb.synchronizeOn("lookupContent", sbb -> {
                        sbb.ifNotNull("lkp").returning("lkp").endIf();
//                        sbb.ifCondition().variable("lkp").notEquals().expression("null")
//                                .endCondition().returning("lkp").endBlock().endIf();
                        sbb.declare("superLookup").initializedByInvoking("getLookup")
                                .onInvocationOf("getCookieSet").inScope().as("Lookup");
                        sbb.invoke("decorateLookup")
                                .withArgument("this")
                                .withArgument("lookupContent")
                                .withArgument("() -> superLookup")
                                .on("HOOKS");
                        sbb.returning("lkp = new ProxyLookup(contributedLookup, superLookup)");
                    });
                });
    };

    private static final Consumer<ClassBuilder<?>> createNodeDelegateInvoker = cb -> {
        cb.importing("org.openide.nodes.Node")
                .overrideProtected("createNodeDelegate", mb -> {
                    mb.returning("Node")
                            .body(bb -> {
                                bb.returningInvocationOf("createNodeDelegate")
                                        .withArgument("this")
                                        .withLambdaArgument().body()
                                        .returningInvocationOf("createNodeDelegate").on("super").endBlock()
                                        .on("HOOKS");
                            });
                });
    };

    private static final Consumer<ClassBuilder<?>> handleDeleteInvoker = cb -> {
        cb.overrideProtected("handleDelete", mb -> {

            mb.throwing("IOException").body(bb -> {
                bb.invoke("handleDelete")
                        .withArgument("this")
                        .withLambdaArgument().body()
                        .invoke("handleDelete").on("super").endBlock()
                        .on("HOOKS");
            });
        });
    };

    private static final Consumer<ClassBuilder<?>> handleRenameInvoker = cb -> {
        cb.overrideProtected("handleRename")
                .addArgument("String", "name")
                .returning("FileObject")
                .throwing("IOException")
                .body(bb -> {
                    bb.returningInvocationOf("handleRename")
                            .withArgument("this")
                            .withArgument("name")
                            .withLambdaArgument(lb -> {
                                lb.withArgument("String", "newName")
                                        .body(lbb -> {
                                            lbb.returningInvocationOf("handleRename")
                                                    .withArgument("newName")
                                                    .on("super");
                                        });
                            })
                            .on("HOOKS");
                });
    };

    private static final Consumer<ClassBuilder<?>> handleCopyInvoker = cb -> {
        cb.importing("org.openide.loaders.DataFolder", "org.openide.filesystems.FileObject",
                "org.openide.loaders.DataObject")
                .overrideProtected("handleCopy")
                .throwing("IOException")
                .addArgument("DataFolder", "fld")
                .returning("DataObject")
                .throwing("IOException")
                .body(bb -> {
                    bb.returningInvocationOf("handleCopy")
                            .withArgument("this")
                            .withArgument("fld")
                            .withLambdaArgument(lb -> {
                                lb.withArgument("DataFolder", "newFld")
                                        .body(lbb -> {
                                            lbb.returningInvocationOf("handleCopy")
                                                    .withArgument("newFld")
                                                    .on("super");
                                        });
                            })
                            .on("HOOKS");
                });
        ;
    };

    private static final Consumer<ClassBuilder<?>> handleMoveInvoker = cb -> {
        cb.importing("org.openide.loaders.DataFolder",
                "org.openide.loaders.DataObject",
                "org.openide.filesystems.FileObject")
                .overrideProtected("handleMove")
                .returning("FileObject")
                .addArgument("DataFolder", "target")
                .throwing("IOException")
                .body(bb -> {
                    bb.returningInvocationOf("handleMove")
                            .withArgument("this")
                            .withArgument("target")
                            .withLambdaArgument(lb -> {
                                lb.withArgument("DataFolder", "newTarget")
                                        .body(lbb -> {
                                            lbb.returningInvocationOf("handleMove")
                                                    .withArgument("newTarget")
                                                    .on("super");
                                        });

                            })
                            .on("HOOKS");
                });
        ;
    };

    private static final Consumer<ClassBuilder<?>> handleCreateFromTemplateInvoker = cb -> {
        cb.importing("org.openide.loaders.DataFolder", "org.openide.loaders.DataObject")
                .overrideProtected("handleCreateFromTemplate")
                .addArgument("DataFolder", "in")
                .addArgument("String", "name")
                .returning("DataObject")
                .throwing("IOException")
                .body(bb -> {
                    bb.returningInvocationOf("handleCreateFromTemplate")
                            .withArgument("this")
                            .withArgument("in")
                            .withArgument("name")
                            .withLambdaArgument(lb -> {
                                lb.withArgument("DataFolder", "newIn")
                                        .withArgument("String", "newName")
                                        .body(lbb -> {
                                            lbb.returningInvocationOf("handleCreateFromTemplate")
                                                    .withArgument("newIn")
                                                    .withArgument("newName")
                                                    .on("super");
                                        });
                            })
                            .on("HOOKS");
                });
        ;
    };

    private static final Consumer<ClassBuilder<?>> handleCopyRenameInvoker = cb -> {
        cb.importing("org.openide.loaders.DataFolder", "org.openide.loaders.DataObject")
                .overrideProtected("handleCopyRename")
                .addArgument("DataFolder", "in")
                .addArgument("String", "name")
                .addArgument("String", "ext")
                .returning("DataObject")
                .throwing("IOException")
                .body(bb -> {
                    bb.returningInvocationOf("handleCopyRename")
                            .withArgument("this")
                            .withArgument("in")
                            .withArgument("name")
                            .withArgument("ext")
                            .withLambdaArgument(lb -> {
                                lb.withArgument("DataFolder", "newIn")
                                        .withArgument("String", "newName")
                                        .withArgument("String", "newExt")
                                        .body(lbb -> {
                                            lbb.returningInvocationOf("handleCopyRename")
                                                    .withArgument("newIn")
                                                    .withArgument("newName")
                                                    .withArgument("newExt")
                                                    .on("super");
                                        });
                                ;
                            })
                            .on("HOOKS");
                });
    };

    private Set<String> findMethods(TypeElement type, AnnotationMirror fileInfo, String hooksClassFqn) {
        TypeElement te = processingEnv.getElementUtils().getTypeElement(hooksClassFqn);
        if (te == null) {
            utils().fail("Could not find " + hooksClassFqn + " on classpath", type, fileInfo);
        }
        List<ExecutableElement> methods = new ArrayList<>();
        Set<String> names = new HashSet<>();
        boolean foundReachableConstructor = false;
        for (Element e : te.getEnclosedElements()) {
            if (e.getKind() == ElementKind.METHOD) {
                ExecutableElement ee = (ExecutableElement) e;
                if (EXPECTED_HOOK_METHODS.contains(ee.getSimpleName().toString())) {
                    names.add(ee.getSimpleName().toString());
                    methods.add(ee);
                }
            } else if (e.getKind() == ElementKind.CONSTRUCTOR) {
                ExecutableElement con = (ExecutableElement) e;
                if (con.getModifiers().contains(PUBLIC) && con.getParameters().isEmpty()) {
                    foundReachableConstructor = true;
                }
            }
        }
        if (!foundReachableConstructor) {
            utils().fail("Could not find a public no-argument constructor on '" + hooksClassFqn
                    + " to generate hook methods", te);
        }
//        List<String> methods = new ArrayList<>();
        return names;
    }

    private Set<String> generateHookMethods(AnnotationMirror fileInfo, TypeElement type, String hooksClassFqn, ClassBuilder<?> cl) {
        Set<String> overridden = findMethods(type, fileInfo, hooksClassFqn);
        if (overridden.isEmpty()) {
            return overridden;
        }
        cl.importing(hooksClassFqn);
        cl.importing("java.io.IOException", "org.openide.loaders.DataObject");
        cl.field("HOOKS")
                .withModifier(PRIVATE, STATIC, FINAL)
                .initializedTo("new " + simpleName(hooksClassFqn) + "()")
                .ofType(simpleName(hooksClassFqn));

        for (String mth : overridden) {
            hookMethodGenerator(mth).accept(cl);
        }
        return overridden;
    }

    private static final String EDITOR_KIT_TYPE = "javax.swing.text.EditorKit";
    private static final String NB_EDITOR_KIT_TYPE = "org.netbeans.modules.editor.NbEditorKit";

    private String generateEditorKitClassAndRegistration(String dataObjectPackage, AnnotationMirror registrationAnno, String prefix, TypeElement type, String mimeType, ParserProxy parser, LexerProxy lexer, RoundEnvironment env) throws Exception {
        String editorKitName = prefix + "EditorKit";
        String syntaxName = generateSyntaxSupport(mimeType, type, dataObjectPackage, registrationAnno, prefix, lexer);
        ClassBuilder<String> cl = ClassBuilder.forPackage(dataObjectPackage)
                .named(editorKitName)
                .docComment("This is the Swing editor kit that will be used for ", prefix, " files.",
                        " It adds some custom actions invokable by keyboard.")
                .withModifier(FINAL)
                .importing(
                        //                        "javax.annotation.processing.Generated",
                        NB_EDITOR_KIT_TYPE,
                        EDITOR_KIT_TYPE, "org.openide.filesystems.FileObject")
                .extending(simpleName(NB_EDITOR_KIT_TYPE))
                //                .annotatedWith("Generated").addArgument("value", getClass().getName()).addArgument("comments", versionString()).closeAnnotation()
                .field("MIME_TYPE").withModifier(PRIVATE).withModifier(STATIC).withModifier(FINAL)
                .initializedWith(mimeType)
                .field("INSTANCE").withModifier(STATIC).withModifier(FINAL)
                .initializedTo("new " + editorKitName + "()").ofType("EditorKit")
                .constructor().setModifier(PRIVATE).emptyBody()
                .override("getContentType").withModifier(PUBLIC).returning(STRING).body().returning("MIME_TYPE").endBlock();
        if (syntaxName != null) {
            cl.importing("org.netbeans.editor.SyntaxSupport", "org.netbeans.editor.BaseDocument")
                    .override("createSyntaxSupport").returning("SyntaxSupport")
                    .withModifier(PUBLIC).addArgument("BaseDocument", "doc")
                    .body(bb -> {
                        bb.log(Level.FINEST).argument("doc").logging(
                                "Create ExtSyntax " + syntaxName + " for {0}");
                        bb.returning("new " + syntaxName + "(doc)").endBlock();
                    });
        }

        String lineComment = utils().annotationValue(registrationAnno, "lineCommentPrefix", String.class);

        List<String> actionTypes = new ArrayList<>();
        if (lineComment != null) {
            actionTypes.add("ToggleCommentAction");
        }
        if (!actionTypes.isEmpty()) {
            cl.importing("javax.swing.Action", "javax.swing.text.TextAction");
            for (String actionType : actionTypes) {
                if (actionType.indexOf('.') > 0) {
                    cl.importing(actionType);
                }
            }
            cl.overrideProtected("createActions")
                    .returning("Action[]")
                    .body(bb -> {
                        bb.log(Level.FINEST)
                                .stringLiteral(lineComment)
                                .logging("Return actions enhanced with ToggleCommentAction for ''{0}''");

                        bb.declare("additionalActions").initializedAsNewArray("Action", alb -> {
                            if (gotos.containsKey(mimeType)) {
                                Set<VariableElement> all = gotos.get(mimeType);
                                if (!all.isEmpty()) {
                                    cl.importing("org.nemesis.antlr.spi.language.NbAntlrUtils");
                                    alb.invoke("createGotoDeclarationAction", ib -> {
                                        for (VariableElement el : all) {
                                            Element enc = el.getEnclosingElement();
                                            if (enc instanceof TypeElement) {
                                                TypeElement te = (TypeElement) enc;
                                                String exp = te.getQualifiedName() + "."
                                                        + el.getSimpleName();
                                                ib.withArgument(exp);
                                            }
                                        }
                                        ib.on("NbAntlrUtils");
                                    });
                                }
                            }
                            for (String tp : actionTypes) {
                                if ("ToggleCommentAction".equals(tp)) {
                                    alb.add("new " + simpleName(tp) + "(" + LinesBuilder.stringLiteral(lineComment) + ")");
                                } else {
                                    alb.add("new " + /*simpleName(tp)*/ tp + "()");
                                }
                                List<Element> found = findAntlrActionElements(env, mimeType);
                                for (Element e : found) {
                                    if (e instanceof TypeElement) {
                                        cl.importing(((TypeElement) e).getQualifiedName().toString());
                                        alb.add("new " + e.getSimpleName() + "()");
                                    } else if (e instanceof ExecutableElement) {
                                        TypeElement owner = AnnotationUtils.enclosingType(e);
                                        cl.importing(owner.getQualifiedName().toString());
                                        alb.add(owner.getSimpleName() + "." + e.getSimpleName() + "()");
                                    }
                                }
                            }
                            // Get the actions from keybindings annotations
                            for (String invocation : getAll(KeybindingsAnnotationProcessor.actionsKey(mimeType))) {
                                alb.add(invocation);
                            }
                            alb.closeArrayLiteral();
                        });

                        bb.returningInvocationOf("augmentList").withArgumentFromInvoking("createActions")
                                .on("super").withArgument("additionalActions").on("TextAction")
                                .endBlock();
                    });
        }

        writeOne(cl);
        return dataObjectPackage + "." + editorKitName;
    }

    private boolean hasSyntax(AnnotationMirror registrationAnno) {
        AnnotationMirror syntax = utils().annotationValue(registrationAnno, "syntax", AnnotationMirror.class);
        if (syntax == null) {
            return false;
        }
        List<Integer> commentTokens = utils().annotationValues(syntax, "commentTokens", Integer.class);
        List<Integer> whitespaceTokens = utils().annotationValues(syntax, "whitespaceTokens", Integer.class);
        List<Integer> bracketSkipTokens = utils().annotationValues(syntax, "bracketSkipTokens", Integer.class);
        return !commentTokens.isEmpty() || !whitespaceTokens.isEmpty() || !bracketSkipTokens.isEmpty();
    }

    private static final String EXT_SYNTAX_TYPE = "org.netbeans.editor.ext.ExtSyntaxSupport";
    private static final String BASE_DOCUMENT_TYPE = "org.netbeans.editor.BaseDocument";
    private static final String EDITOR_TOKEN_ID_TYPE = "org.netbeans.editor.TokenID";
    private static final String EDITOR_TOKEN_CATEGORY_TYPE = "org.netbeans.editor.TokenCategory";

    private String generateSyntaxSupport(String mimeType, Element on, String dataObjectPackage, AnnotationMirror registrationAnno, String prefix, LexerProxy lexer) throws IOException {
        PackageElement pkg = processingEnv.getElementUtils().getPackageOf(on);
        AnnotationMirror syntax = utils().annotationValue(registrationAnno, "syntax", AnnotationMirror.class);
        if (syntax == null) {
            return null;
        }
        List<Integer> commentTokens = new ArrayList<>(new HashSet<>(utils().annotationValues(syntax, "commentTokens", Integer.class)));
        List<Integer> whitespaceTokens = new ArrayList<>(new HashSet<>(utils().annotationValues(syntax, "whitespaceTokens", Integer.class)));
        List<Integer> bracketSkipTokens = new ArrayList<>(new HashSet<>(utils().annotationValues(syntax, "bracketSkipTokens", Integer.class)));
        Collections.sort(commentTokens);
        Collections.sort(whitespaceTokens);
        Collections.sort(bracketSkipTokens);
        if (commentTokens.isEmpty() && whitespaceTokens.isEmpty() && bracketSkipTokens.isEmpty()) {
            return null;
        }
        String generatedClassName = prefix + "ExtSyntax";
        ClassBuilder<String> cl = ClassBuilder.forPackage(dataObjectPackage).named(generatedClassName)
                .importing(EXT_SYNTAX_TYPE, BASE_DOCUMENT_TYPE, EDITOR_TOKEN_ID_TYPE, "java.util.Arrays",
                        pkg.getQualifiedName() + "." + prefix + "Tokens"
                )
                .extending(simpleName(EXT_SYNTAX_TYPE))
                .withModifier(FINAL)
                .constructor(cb -> {
                    cb.addArgument(simpleName(BASE_DOCUMENT_TYPE), "doc");
                    cb.body(bb -> {
                        bb.invoke("super").withArgument("doc").inScope();
                        bb.endBlock();
                    });
                });
        if (!commentTokens.isEmpty()) {
            cl.field("COMMENT_TOKENS").withModifier(PRIVATE).withModifier(STATIC)
                    .withModifier(FINAL)
                    .docComment("The set of tokens specified to represent "
                            + "comments in the " + registrationAnno.getAnnotationType()
                            + " that generated this class")
                    .initializedAsArrayLiteral("TokenID", alb -> {
                        for (int id : commentTokens) {
                            String fieldName = lexer.toFieldName(id);
                            if (fieldName == null) {
                                utils().fail("No token id for " + id);
                                continue;
                            }
                            alb.field(fieldName).of(prefix + "Tokens");
                        }
                    });
            cl.override("getCommentTokens").withModifier(PUBLIC).returning("TokenID[]")
                    .body(fieldReturner("COMMENT_TOKENS"));
        }
        if (!bracketSkipTokens.isEmpty()) {
            cl.field("BRACKET_SKIP_TOKENS").withModifier(PRIVATE).withModifier(STATIC)
                    .withModifier(FINAL).initializedTo(tokensArraySpec(prefix, bracketSkipTokens, lexer))
                    .ofType("TokenID[]");
            cl.override("getBracketSkipTokens").withModifier(PROTECTED).returning("TokenID[]")
                    .body(fieldReturner("BRACKET_SKIP_TOKENS"));
        }
        if (!whitespaceTokens.isEmpty()) {
            if (whitespaceTokens.size() == 1) {
                cl.overridePublic("isWhitespaceToken").returning("boolean")
                        .addArgument("TokenID", "tokenId").addArgument("char[]", "buffer")
                        .addArgument("int", "offset").addArgument("int", "tokenLength")
                        .body(bb -> {
                            bb.returning("tokenId.getNumericID() == " + whitespaceTokens.get(0)).endBlock();
                        });
            } else {
                cl.field("WHITESPACE_TOKEN_IDS", fb -> {
                    fb.withModifier(PRIVATE).withModifier(STATIC).withModifier(FINAL);
                    fb.docComment("A sorted and usable for binary search list "
                            + "of token ids");
                    cl.importing(lexer.lexerClassFqn());
                    fb.initializedAsArrayLiteral("int", alb -> {
                        for (int ws : whitespaceTokens) {
                            String tokenField = lexer.lexerClassSimple()
                                    + "." + lexer.tokenName(ws);
                            alb.add(tokenField);
                        }
                    });
                }).overridePublic("isWhitespaceToken").returning("boolean")
                        .addArgument("TokenID", "tokenId").addArgument("char[]", "buffer")
                        .addArgument("int", "offset").addArgument("int", "tokenLength")
                        .body(bb -> {
                            bb.returning("Arrays.binarySearch(WHITESPACE_TOKEN_IDS, tokenId.getNumericID()) >= 0").endBlock();
                        });
            }
        }
        if (useDeclarationTokenProcessor(mimeType)) {
            String className = capitalize(stripMimeType(mimeType)) + simpleName(DECLARATION_TOKEN_PROCESSOR_TYPE);
            cl.overridePublic("createDeclarationTokenProcessor").returning(className)
                    .addArgument("String", "varName")
                    .addArgument("int", "startPos")
                    .addArgument("int", "endPos")
                    .body(bb -> {
                        bb.log(Level.FINER).argument("varName").argument("startPos").argument("endPos")
                                .logging(cl.className() + ".createDeclarationTokenProcessor({0},{1},{2}");
                        bb.returningNew(nb -> {
                            nb.withArgument("varName")
                                    .withArgument("startPos")
                                    .withArgument("endPos")
                                    .withArgumentFromInvoking("getDocument").inScope()
                                    .ofType(className);
                        });
                    });

            declPositionMethod("findDeclarationPosition", className, cl);
            declPositionMethod("findLocalDeclarationPosition", className, cl);
        }
        writeOne(cl);
        return generatedClassName;
    }

    static void declPositionMethod(String name, String className, ClassBuilder<?> cl) {
        cl.overridePublic(name)
                .addArgument("String", "varName")
                .addArgument("int", "varPos")
                .returning("int")
                .body(bb -> {
                    bb.declare("result").initializedByInvoking("getDeclarationPosition").onInvocationOf("createDeclarationTokenProcessor")
                            .withArgument("varName").withArgument("varPos").withArgument(-1)
                            .inScope().as("int");
                    bb.log(Level.FINEST).argument("varName").argument("varPos").argument("result")
                            .logging(className + "." + name + "({0}, {1}) returning {2}");
                    bb.returning("result");
                });
    }

    private Consumer<ClassBuilder.BlockBuilder<?>> fieldReturner(String name) {
        return bb -> {
//            bb.returning(name).endBlock();
            bb.returningInvocationOf("copyOf").withArgument(name).withArgument(name + ".length").on("Arrays").endBlock();
        };
    }

    String tokensArraySpec(String prefix, List<Integer> ids, LexerProxy lexer) {
        String tokensClass = prefix + "Tokens";
        StringBuilder sb = new StringBuilder();
        for (Integer id : ids) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            String fieldName = lexer.toFieldName(id);
            if (fieldName == null) {
                utils().fail("No token id for " + id);
                continue;
            }
            sb.append(tokensClass).append('.').append(fieldName);
        }
        sb.insert(0, "new TokenID[] {");
        return sb.append('}').toString();
    }

    private void createFoMethod(ClassBuilder<String> bldr, String name, boolean value) {
        char[] c = name.toCharArray();
        c[0] = Character.toUpperCase(c[0]);
        String methodName = "is" + new String(c);
        bldr.override(methodName).withModifier(PUBLIC).returning("boolean").body().returning(Boolean.toString(value)).endBlock();
    }

    private void addActionAnnotations(ClassBuilder<String> cl, AnnotationMirror fileInfo) {
        Set<String> excludedActions = new HashSet<>(utils().annotationValues(fileInfo, "excludedActions", String.class));
        int position = 1;
        ClassBuilder.ArrayValueBuilder<ClassBuilder.AnnotationBuilder<ClassBuilder<String>>> annoBuilder = cl.annotatedWith("ActionReferences").addArrayArgument("value");
        for (int i = 0; i < DEFAULT_ACTIONS.length; i++) {
            String a = DEFAULT_ACTIONS[i];
            boolean separator = a.charAt(0) == '-';
            if (separator) {
                a = a.substring(1);
            }
            if (excludedActions.contains(a)) {
                continue;
            }
            String[] parts = a.split("/");
            if (parts.length != 2) {
                utils().fail("parts must be action-category / action-id, e.g. "
                        + "System/org.openide.filesystems.FilesystemAction - "
                        + "'" + a + "' does not match this spec.");
                continue;
            }
            String category = parts[0];
            String actionId = parts[1];
            ClassBuilder.AnnotationBuilder<?> ab = annoBuilder.annotation("ActionReference")
                    .addExpressionArgument("path", "ACTION_PATH")
                    .addArgument("position", position * 100);
            if (separator) {
                ab.addArgument("separatorAfter", ++position * 100);
            }
            ab.addAnnotationArgument("id", "ActionID", aid -> {
                aid.addArgument("category", category).addArgument("id", actionId).closeAnnotation();
            });
            ab.closeAnnotation();
            position++;
        }
        annoBuilder.closeArray().closeAnnotation();
    }

    // Parser generation
    private void generateParserClasses(ParserProxy parser, LexerProxy lexer,
            TypeElement type, AnnotationMirror mirror,
            AnnotationMirror parserInfo, String prefix) throws IOException {
        String nbParserName = prefix + "NbParser";
        Name pkg = processingEnv.getElementUtils().getPackageOf(type).getQualifiedName();
        String tokenTypeName = prefix + "Token";

        boolean changeSupport = utils().annotationValue(parserInfo, "changeSupport", Boolean.class, false);
        int streamChannel = utils().annotationValue(parserInfo, "streamChannel", Integer.class, 0);

        TypeMirror helperClass = utils().typeForSingleClassAnnotationMember(parserInfo, "helper");
        boolean hasExplicitHelperClass = helperClass != null
                && !"org.nemesis.antlr.spi.language.NbParserHelper".equals(helperClass.toString());
        String helperClassName = hasExplicitHelperClass ? helperClass.toString() : prefix + "ParserHelper";

        String entryPointType = parser.parserEntryPointReturnTypeFqn();
        String entryPointSimple = parser.parserEntryPointReturnTypeSimple();
        String parserResultType = "AntlrParseResult";

        ClassBuilder<String> cl = ClassBuilder.forPackage(pkg).named(nbParserName)
                .withModifier(PUBLIC).withModifier(FINAL)
                .importing("org.netbeans.modules.parsing.api.Snapshot", "org.netbeans.modules.parsing.api.Task",
                        "org.netbeans.modules.parsing.spi.Parser", "org.netbeans.modules.parsing.spi.SourceModificationEvent",
                        //                        "javax.annotation.processing.Generated",
                        "org.nemesis.source.api.GrammarSource",
                        "org.nemesis.source.api.ParsingBag", "org.openide.util.Exceptions", "javax.swing.event.ChangeListener",
                        entryPointType, "org.netbeans.modules.parsing.spi.ParserFactory", "java.util.Collection",
                        "java.util.concurrent.atomic.AtomicBoolean", "java.util.function.BooleanSupplier",
                        "org.nemesis.antlr.spi.language.NbParserHelper", "org.nemesis.antlr.spi.language.AntlrParseResult",
                        "org.nemesis.antlr.spi.language.SyntaxError", "java.util.function.Supplier",
                        "org.nemesis.extraction.Extractor", "org.nemesis.extraction.Extraction",
                        "org.antlr.v4.runtime.CommonTokenStream", "java.util.Optional", "java.util.List",
                        "org.nemesis.antlr.spi.language.IterableTokenSource", lexer.lexerClassFqn(),
                        parser.parserClassFqn()
                )
                //                .annotatedWith("Generated").addArgument("value", getClass().getName()).addArgument("comments", versionString()).closeAnnotation()
                .extending("Parser")
                .docComment("NetBeans parser wrapping ", parser.parserClassSimple(), " using entry point method ", parser.parserEntryPoint().getSimpleName(), "()."
                        + "  For the most part, you will not use this class directly, but rather register classes that are interested in processing"
                        + " parse results for this MIME type, and then get passed them when a file is modified, opened, or reparsed for some other reason.");
        if (changeSupport) {
            cl.importing("java.util.Set", "org.openide.util.WeakSet", "org.openide.util.ChangeSupport")
                    .field("INSTANCES").withModifier(FINAL).withModifier(PRIVATE).withModifier(STATIC).initializedTo("new WeakSet<>()").ofType("Set<" + nbParserName + ">")
                    .field("changeSupport").withModifier(FINAL).withModifier(PRIVATE).initializedTo("new ChangeSupport(this)").ofType("ChangeSupport");
        }
        cl.field("HELPER")
                .docComment("Helper class which can talk to the NetBeans adapter layer via protected methods which are not otherwise exposed.")
                .withModifier(FINAL).withModifier(PRIVATE).withModifier(STATIC).initializedTo("new " + helperClassName + "()").ofType(helperClassName)
                .field("cancelled").withModifier(FINAL).withModifier(PRIVATE).initializedTo("new AtomicBoolean()").ofType("AtomicBoolean")
                .field("lastResult").withModifier(PRIVATE).ofType(parserResultType)
                .constructor().annotatedWith("SuppressWarnings").addArgument("value", "LeakingThisInConstructor").closeAnnotation().setModifier(PUBLIC)
                .body(bb -> {
                    if (changeSupport) {
                        bb.lineComment("Languages which have alterable global configuration need to fire changes from their parser;");
                        bb.lineComment("This allows us to track all live instances without leaking memory, so languageSettingsChanged()");
                        bb.lineComment("can trigger events from all live parsers for this language");
                        bb.debugLog("Created a new " + nbParserName);
                        bb.invoke("add").withArgument("this").on("INSTANCES").endBlock();
                    } else {
                        bb.lineComment("To enable support for firing changes, set changeSupport on the")
                                .lineComment("generating annotation to true").endBlock();
                    }
                })
                .method("parse", (mb) -> {
                    mb.docComment("Parse a document or file snapshot associated with a parser task.");
                    mb.override().addArgument("Snapshot", "snapshot").addArgument("Task", "task").addArgument("SourceModificationEvent", "event")
                            .withModifier(PUBLIC)
                            .body(block -> {
                                block.statement("assert snapshot != null")
                                        .log(nbParserName + ".parse({0})", Level.FINER, "snapshot")
                                        .debugLog(nbParserName + ".parse on snapshot")
                                        .invoke("set").withArgument("false").on("cancelled")
                                        .declare("snapshotSource").initializedByInvoking("find")
                                        .withArgument("snapshot").withArgument(tokenTypeName + ".MIME_TYPE")
                                        .on("GrammarSource").as("GrammarSource<Snapshot>")
                                        .declare("bag").initializedByInvoking("forGrammarSource")
                                        .withArgument("snapshotSource")
                                        .on("ParsingBag").as("ParsingBag")
                                        .trying().declare("result").initializedByInvoking("parse")
                                        .withArgument("bag").withArgument("cancelled::get").on(nbParserName).as(parserResultType)
                                        .synchronizeOn("this")
                                        .statement("lastResult = result").endBlock()
                                        .catching("Exception")
                                        .statement("Exceptions.printStackTrace(thrown)")
                                        .endTryCatch().endBlock();
                            });
                })
                .method("parse", mb -> {
                    mb.docComment("Convenience method for initiating a parse programmatically rather than via the NetBeans parser infrastructure.");
                    mb.addArgument("ParsingBag", "bag").throwing("Exception").addArgument("BooleanSupplier", "cancelled").returning("AntlrParseResult")
                            .withModifier(PUBLIC).withModifier(STATIC)
                            .body(bb -> {
                                bb.declare("lexer").initializedByInvoking("createAntlrLexer").withArgument("bag.source().stream()")
                                        .on(prefix + "Hierarchy").as(lexer.lexerClassSimple());
                                bb.declare("tokenSource").initializedByInvoking("createWrappedTokenSource")
                                        .withArgument("lexer")
                                        .on(prefix + "Hierarchy").as("IterableTokenSource");
                                bb.declare("stream").initializedWith("new CommonTokenStream(tokenSource, " + streamChannel + ")")
                                        .as("CommonTokenStream");
                                bb.declare("parser").initializedWith("new " + parser.parserClassSimple() + "(stream)").as(parser.parserClassSimple());
                                bb.blankLine();
                                bb.lineComment("Invoke the hook method allowing the helper to attach error listeners, or ");
                                bb.lineComment("configure the parser or lexer before using them.");
                                bb.declare("maybeSnapshot").initializedByInvoking("lookup").withArgument("Snapshot.class").onInvocationOf("source").on("bag").as("Optional<Snapshot>");
                                bb.blankLine();
                                bb.lineComment("The registered GrammarSource implementations will synthesize a Snapshot if the GrammarSource");
                                bb.lineComment("was not created from one (as happens in tests), if a Document or a FileObject is present.");
                                bb.lineComment("Most code touching extraction does not require a snapshot be present.  So this will only");
                                bb.lineComment("be null for a GrammarSource created from a String or CharStream.");
                                bb.declare("snapshot").initializedWith("maybeSnapshot.isPresent() ? maybeSnapshot.get() : null").as("Snapshot");

                                bb.declare("errors").initializedByInvoking("parserCreated").withArgument("lexer").withArgument("parser")
                                        .withArgument("snapshot")
                                        .on("HELPER").as("Supplier<List<? extends SyntaxError>>");
                                bb.blankLine();
                                bb.lineComment("Here we actually trigger the Antlr parse");
                                bb.declare("tree").initializedByInvoking(parser.parserEntryPoint().getSimpleName().toString())
                                        .on("parser").as(entryPointSimple);
                                bb.blankLine();
                                bb.lineComment("Look up the extrator(s) for this mime type");
                                bb.declare("extractor")
                                        .initializedByInvoking("forTypes").withArgument(prefix + "Token.MIME_TYPE")
                                        .withArgument(entryPointSimple + ".class").on("Extractor")
                                        .as("Extractor<? super " + entryPointSimple + ">");
                                bb.blankLine();
                                bb.lineComment("Run extraction, pulling out data needed to create navigator panels,");
                                bb.lineComment("code folds, etc.  Anything needing the extracted sets of regions and data");
                                bb.lineComment("can get it from the parse result");
                                bb.declare("extraction").initializedByInvoking("extract")
                                        .withArgument("tree")
                                        .withArgumentFromInvoking("source").on("bag")
                                        .withArgument("cancelled")
                                        .withArgument("tokenSource")
                                        //.withArgument("bag.source()")
                                        //                                        .withLambdaArgument().body().returning("new CommonTokenStream(lexer, -1)").endBlock()
                                        .on("extractor").as("Extraction");
                                bb.lineComment("// discard the cached tokens used for token extraction");
                                bb.invoke("dispose").on("tokenSource");
                                bb.blankLine();
                                bb.lineComment("Now create a parser result and object to populate it, and allow the");
                                bb.lineComment("helper's hook method to add anything it needs, such as semantic error");
                                bb.lineComment("checking, or other data resolved from the extraction or parse tree - ");
                                bb.lineComment("this allows existing Antlr code to be used.");
                                bb.declare("result").initializedWith("new AntlrParseResult[1]").as("AntlrParseResult[]");
                                bb.declare("thrown").initializedWith("new Exception[1]").as("Exception[]");
                                bb.invoke("newParseResult").withArgument("snapshot")
                                        .withArgument("extraction").withLambdaArgument().withArgument("pr").withArgument("contents").body()
                                        .trying()
                                        .lineComment("Call the hook method to let the helper run any additional analysis,")
                                        .lineComment("semantic error checking, etc")
                                        .invoke("parseCompleted")
                                        .withArgument(prefix + "Token.MIME_TYPE")
                                        .withArgument("tree").withArgument("extraction")
                                        .withArgument("contents").withArgument("cancelled")
                                        .withArgument("errors")
                                        .on("HELPER")
                                        .statement("result[0] = pr")
                                        .catching("Exception")
                                        .lineComment("The hook method may throw an exception, which we will need to catch")
                                        .statement("thrown[0] = ex")
                                        .as("ex").endTryCatch().endBlock()
                                        .on(prefix + "Hierarchy");

                                bb.ifNotNull("thrown[0]")
                                        .statement("Exceptions.printStackTrace(thrown[0])").endIf();

//                                bb.ifCondition().variable("thrown[0]").notEquals().expression("null").endCondition()
//                                        .statement("Exceptions.printStackTrace(thrown[0])").endBlock().endIf();
                                bb.returning("result[0]");
                                bb.endBlock();
                            });
                })
                .method("getResult", (mb) -> {
                    mb.withModifier(PUBLIC)
                            .withModifier(SYNCHRONIZED)
                            .addArgument("Task", "task").override().returning(parserResultType)
                            .body().returning("lastResult").endBlock();
                })
                .override("addChangeListener").addArgument("ChangeListener", "l").withModifier(PUBLIC)
                .body(bb -> {
                    if (changeSupport) {
                        bb.statement("changeSupport.addChangeListener(l)").endBlock();
                    } else {
                        bb.lineComment("do nothing").endBlock();
                    }
                })
                .override("removeChangeListener").addArgument("ChangeListener", "l").withModifier(PUBLIC)
                .body(bb -> {
                    if (changeSupport) {
                        bb.statement("changeSupport.removeChangeListener(l)").endBlock();
                    } else {
                        bb.lineComment("do nothing").endBlock();
                    }
                })
                // public void cancel (@NonNull CancelReason reason, @NullAllowed SourceModificationEvent event) {}
                .override("cancel")
                .docComment("Cancel the last parse if it is still running.")
                .withModifier(PUBLIC).addArgument("CancelReason", "reason")
                .addArgument("SourceModificationEvent", "event")
                .body(bb -> {
                    bb.log("Parse cancelled due to {0}", Level.FINEST, "reason");
                    bb.invoke("set").withArgument("true").on("cancelled").endBlock();
                })
                .override("cancel")
                .annotatedWith("SuppressWarnings").addArgument("value", "deprecation").closeAnnotation()
                .withModifier(PUBLIC)
                .body(bb -> {
                    bb.log("Parse cancelled", Level.FINEST);
                    bb.invoke("set").withArgument("true").on("cancelled").endBlock();
                });
        if (changeSupport) {
            cl.method("forceReparse")
                    .docComment("Cause all instance of this parser to fire a change event, triggering a "
                            + "reparse.  This is used when the underlying <i>model</i> of the language or "
                            + "project changes (say, a library was added or the source level was changed).")
                    .withModifier(PRIVATE).body().invoke("fireChange").on("changeSupport").endBlock()
                    .method("languageSettingsChanged").withModifier(PUBLIC).withModifier(STATIC)
                    .body(bb -> {
                        bb.lineComment("Cause all existing instances of this class to fire a change, triggering");
                        bb.lineComment("a re-parse, in the event of a global change of some sort.");
                        bb.simpleLoop(nbParserName, "parser").over("INSTANCES")
                                .invoke("forceReparse").on("parser").endBlock();
                        bb.endBlock();
                    });
        }

        if (!hasExplicitHelperClass) {
            // <P extends Parser, L extends Lexer, R extends Result, T extends ParserRuleContext> {
            cl.innerClass(helperClassName).extending("NbParserHelper<" + parser.parserClassSimple() + ", " + lexer.lexerClassSimple()
                    + ", AntlrParseResult, " + entryPointSimple + ">")
                    .withModifier(PRIVATE).withModifier(FINAL).withModifier(STATIC).build();
        }

        cl.importing("org.netbeans.api.editor.mimelookup.MimeRegistration")
                .innerClass(prefix + "ParserFactory").publicStaticFinal().extending("ParserFactory")
                .docComment("Registers our parse with the NetBeans parser infrastructure.")
                //                .annotatedWith("Generated").addArgument("value", getClass().getName()).addArgument("comments", versionString()).closeAnnotation()
                .annotatedWith("MimeRegistration").addExpressionArgument("mimeType", prefix + "Token.MIME_TYPE").addArgument("position", Integer.MAX_VALUE - 1000)
                .addClassArgument("service", "ParserFactory").closeAnnotation()
                .method("createParser").override().withModifier(PUBLIC).returning("Parser")
                .addArgument("Collection<Snapshot>", "snapshots")
                .body().returning("new " + nbParserName + "()").endBlock()
                .build();

        cl.importing("org.netbeans.modules.parsing.spi.TaskFactory",
                "org.nemesis.antlr.spi.language.NbAntlrUtils")
                .method("createErrorHighlighter", mb -> {
                    mb.docComment("Creates a highlighter for source errors from the parser.");
                    mb.withModifier(PUBLIC).withModifier(STATIC)
                            .annotatedWith("MimeRegistration").addExpressionArgument("mimeType", prefix + "Token.MIME_TYPE").addArgument("position", Integer.MAX_VALUE - 1000)
                            .addClassArgument("service", "TaskFactory").closeAnnotation()
                            .returning("TaskFactory")
                            .body(bb -> {
                                bb.returningInvocationOf("createErrorHighlightingTaskFactory")
                                        .withArgument(prefix + "Token.MIME_TYPE")
                                        .on("NbAntlrUtils").endBlock();
                            });
                });

        writeOne(cl);
        if (utils().annotationValue(parserInfo, "generateSyntaxTreeNavigatorPanel", Boolean.class, false)) {
            generateSyntaxTreeNavigatorPanel(type, mirror, parser.parserEntryPoint());
        }
        if (utils().annotationValue(parserInfo, "generateExtractionDebugNavigatorPanel", Boolean.class, false)) {
            generateExtractionDebugPanel(type, mirror, parser.parserEntryPoint());
        }
    }

    // Navigator generation
    private void generateExtractionDebugPanel(TypeElement type, AnnotationMirror mirror, ExecutableElement entryPointMethod) throws IOException {
        String mimeType = utils().annotationValue(mirror, "mimeType", String.class);
        Name pkg = processingEnv.getElementUtils().getPackageOf(type).getQualifiedName();
        String generatedClassName = type.getSimpleName() + "_ExtractionNavigator_Registration";
        ClassBuilder<String> cb = ClassBuilder.forPackage(pkg).named(generatedClassName)
                .withModifier(PUBLIC, FINAL)
                .docComment("Provides a generic navigator panel for the current extraction for plugin debugging")
                .importing("org.netbeans.spi.navigator.NavigatorPanel",
                        "org.nemesis.antlr.navigator.AbstractAntlrListNavigatorPanel")
                .method("createExtractorNavigatorPanel", mb -> {
                    mb.returning("NavigatorPanel")
                            .annotatedWith("NavigatorPanel.Registration", ab -> {
                                ab.addArgument("displayName", "Extraction")
                                        .addArgument("mimeType", mimeType)
                                        .addArgument("position", 1000);
                            }).withModifier(PUBLIC, STATIC)
                            .body().returningInvocationOf("createExtractionDebugPanel")
                            .on("AbstractAntlrListNavigatorPanel").endBlock();
                });
        writeOne(cb);

    }

    private void generateSyntaxTreeNavigatorPanel(TypeElement type, AnnotationMirror mirror, ExecutableElement entryPointMethod) throws IOException {
        String mimeType = utils().annotationValue(mirror, "mimeType", String.class);
        AnnotationMirror fileType = utils().annotationValue(mirror, "file", AnnotationMirror.class);

        String icon = fileType == null ? null : utils().annotationValue(fileType, "iconBase", String.class);
        Name pkg = processingEnv.getElementUtils().getPackageOf(type).getQualifiedName();
        String generatedClassName = type.getSimpleName() + "_SyntaxTreeNavigator_Registration";
        String entryPointType = entryPointMethod.getReturnType().toString();
        String entryPointSimple = simpleName(entryPointType.toString());
        ClassBuilder<String> cb = ClassBuilder.forPackage(pkg).named(generatedClassName)
                .importing("java.util.function.BiConsumer", "org.antlr.v4.runtime.ParserRuleContext",
                        "org.antlr.v4.runtime.tree.ParseTree", "org.nemesis.antlr.navigator.SimpleNavigatorRegistration",
                        "org.nemesis.extraction.ExtractionRegistration", "org.nemesis.extraction.ExtractorBuilder",
                        "org.nemesis.extraction.key.RegionsKey", "org.antlr.v4.runtime.Token", entryPointType)
                .withModifier(PUBLIC)
                .docComment("Provides a generic Navigator panel which displays the syntax tree of the current file.")
                .constructor(c -> {
                    c.setModifier(PRIVATE).body().statement("throw new AssertionError()").endBlock();
                }).field("TREE_NODES", fb -> {
            fb.annotatedWith("SimpleNavigatorRegistration", ab -> {
                if (icon != null) {
                    ab.addArgument("icon", icon);
                }
                ab.addArgument("mimeType", mimeType)
                        .addArgument("order", 20000)
                        .addArgument("displayName", "Syntax Tree").closeAnnotation();
            });
            fb.withModifier(STATIC).withModifier(FINAL)
                    .initializedFromInvocationOf("create").withArgument("String.class").withStringLiteral("tree")
                    .on("RegionsKey").ofType("RegionsKey<String>");
            ;
        }).method("extractTree", mb -> {
            mb.withModifier(STATIC)
                    .annotatedWith("ExtractionRegistration")
                    .addArgument("mimeType", mimeType)
                    .addClassArgument("entryPoint", entryPointSimple)
                    .closeAnnotation()
                    .addArgument("ExtractorBuilder<? super " + entryPointSimple + ">", "bldr")
                    .body(bb -> {
                        bb.statement("bldr.extractingRegionsUnder( TREE_NODES ).whenRuleType( ParserRuleContext.class )\n"
                                + "                .extractingKeyAndBoundsFromRuleWith( " + generatedClassName + "::ruleToString ).finishRegionExtractor()")
                                .endBlock();
                    });
        }).insertText(additionalSyntaxNavigatorMethods());
        writeOne(cb);
    }

    private String additionalSyntaxNavigatorMethods() throws IOException {
        InputStream in = LanguageRegistrationProcessor.class.getResourceAsStream("additional-syntax-navigator-methods.txt");
        if (in == null) {
            throw new Error("additional-syntax-navigator-methods.txt is not on classpath next to " + LanguageRegistrationProcessor.class);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        FileUtil.copy(in, out);
        StringBuilder sb = new StringBuilder();
        for (String line : new String(out.toByteArray(), UTF_8).split("\n")) {
            String l = line.trim();
            if (l.startsWith("//") || l.startsWith("#")) {
                continue;
            }
            sb.append(line).append('\n');
        }
        return sb.toString();
    }

    private String generateTokenCategoryWrapper(TypeElement on, String prefix) throws IOException {
        String generatedClassName = prefix + "TokenCategory";
        ClassBuilder<String> cb = ClassBuilder.forPackage(processingEnv.getElementUtils().getPackageOf(on).getQualifiedName())
                .named(generatedClassName)
                .importing(EDITOR_TOKEN_CATEGORY_TYPE)
                .implementing("TokenCategory")
                .withModifier(FINAL)
                .field("category").withModifier(PRIVATE, FINAL).ofType(STRING)
                .constructor(con -> {
                    con.addArgument(STRING, "category").body().statement("this.category = category").endBlock();
                })
                .override("getName").withModifier(PUBLIC).returning(STRING).body().returning("category").endBlock()
                .override("getNumericID").withModifier(PUBLIC).returning("int").body().returning("0").endBlock()
                .override("equals", mb -> {
                    mb.withModifier(PUBLIC).returning("boolean").addArgument("Object", "o")
                            .body(bb -> {
                                bb.iff().variable("this").equals().expression("o").endCondition()
                                        .returning("true")
                                        .elseIf().variable("o").equals().expression("null").endCondition()
                                        .returning("false")
                                        .elseIf().variable("o").instanceOf().expression(generatedClassName).endCondition()
                                        .returningInvocationOf("equals")
                                        .withArgument("o.toString()")
                                        .on("category")
                                        .orElse().returning("false").endIf();
                            });
                })
                .override("hashCode", mb -> {
                    mb.withModifier(PUBLIC).returning("int").body().returningInvocationOf("hashCode").on("category").endBlock();
                })
                .override("toString", mb -> {
                    mb.withModifier(PUBLIC).returning(STRING).body().returning("category").endBlock();
                });
        writeOne(cb);
        return generatedClassName;
    }

    // Lexer generation
    private void generateTokensClasses(TypeElement type, AnnotationMirror mirror, LexerProxy proxy, TypeMirror tokenCategorizerClass, String prefix, ParserProxy parser) throws Exception {

        List<AnnotationMirror> categories = utils().annotationValues(mirror, "categories", AnnotationMirror.class);

        // Figure out if we are generating a token categorizer now, so we have the class name for it
        String tokenCatName = willHandleCategories(categories, tokenCategorizerClass, prefix, utils());
        String tokenTypeName = prefix + "Token";
        String mimeType = utils().annotationValue(mirror, "mimeType", String.class);
        if ("<error>".equals(mimeType)) {
            utils().fail("Mime type invalid: " + mimeType);
            return;
        }
        Name pkg = processingEnv.getElementUtils().getPackageOf(type).getQualifiedName();
        String tokensTypeName = prefix + "Tokens";

        boolean hasSyntaxInfo = hasSyntax(mirror);
        String tokenWrapperClass = hasSyntaxInfo ? generateTokenCategoryWrapper(type, prefix) : null;
        // First class will be an interface that extends NetBeans' TokenId - we will implement it
        // as an inner class on our class with constants for each token type
        ClassBuilder<String> cb = ClassBuilder.forPackage(pkg)
                .named(prefix + "Token").toInterface().extending("Comparable<" + tokenTypeName + ">")
                .extending("TokenId")
                .docComment("Token interface for ", prefix, " extending the TokenId type from NetBeans' Lexer API.",
                        " Instances for types defined by the lexer " + proxy.lexerType() + " can be found as static fields on ",
                        tokensTypeName, " in this package (", pkg, "). ",
                        "Generated by ", getClass().getSimpleName(), " from fields on ", proxy.lexerClassSimple(), " specified by "
                        + "an annotation on ", type.getSimpleName())
                .makePublic()
                .importing("org.netbeans.api.lexer.TokenId", proxy.lexerClassFqn())
                //                .importing("javax.annotation.processing.Generated")
                //                .annotatedWith("Generated").addArgument("value", getClass().getName()).addArgument("comments", versionString()).closeAnnotation()
                .field("MIME_TYPE").withModifier(FINAL).withModifier(STATIC).withModifier(PUBLIC).initializedTo(LinesBuilder.stringLiteral(mimeType)).ofType("String")
                .method("ordinal").override().docComment("Returns the same ordinal as the generated ANTLR grammar uses for this token, e.g. " + proxy.lexerClassSimple() + ".VOCABULARY.someTokenName")
                .returning("int").closeMethod()
                .method("symbolicName").docComment("Returns the symbolic name of this token - the name it has in the originating ANTLR grammar.").returning(STRING).closeMethod()
                .method("literalName").docComment("Returns the literal name of this token's ordinal "
                + "in the generated ANTLR grammar's Vocabulary.  The literal name is only defined "
                + "for tokens which have no wildcard content (e.g. keywords, symbols - not, say, user-defined names of methods)").returning(STRING).closeMethod()
                .method("displayName").docComment("Returns the display name of this token, as defined in the generated lexer's Vocabulary.").returning(STRING).closeMethod()
                .method("primaryCategory").override().docComment("Returns the category specified in the annotation (or, if unspecified, a heuristic-derived category).").returning(STRING).closeMethod()
                .method("compareTo").docComment("Tokens compare on their ordinal.").override().returning("int").addArgument(tokenTypeName, "other").withModifier(DEFAULT)
                .body().returning("ordinal() > other.ordinal() ? 1 : ordinal() == other.ordinal() ? 0 : -1").endBlock()
                .method("name").annotatedWith("Override").closeAnnotation().returning("String").withModifier(DEFAULT)
                .docComment("Returns a name for this token, which is one of the following in order of precedence:\n"
                        + "<ul><li>The literal name</li>\n<li>The symbolic name</li>\n<li>The display name</li>\n"
                        + "<li>The string value of this token's ordinal</li></ul>")
                .body(bb -> {
                    bb.returningValue().ternary().invoke("literalName").inScope().notEquals().expression("null")
                            .endCondition().invoke("literalName").inScope()
                            .ternary().invoke("symbolicName").inScope().notEquals().expression("null")
                            .endCondition().invoke("symbolicName").inScope().ternary()
                            .invoke("displayName").inScope().notEquals().expression("null")
                            .endCondition().invoke("displayName").inScope().invoke("toString").withArgumentFromInvoking("ordinal").inScope().on("Integer");

                });
//                .body().returning("literalName() != null ? literalName() \n: symbolicName() != null \n? symbolicName() \n: "
//                        + "displayName() != null \n? displayName() \n: Integer.toString(ordinal())").endBlock();

        if (tokenWrapperClass != null) {
            cb.implementing(EDITOR_TOKEN_ID_TYPE);
            cb.importing(EDITOR_TOKEN_CATEGORY_TYPE);
            cb.override("getCategory").returning("TokenCategory")
                    .withModifier(DEFAULT).body()
                    .returning("new " + tokenWrapperClass + "(primaryCategory())").endBlock();
            cb.override("getName").returning(STRING)
                    .withModifier(DEFAULT).body()
                    .returning("name()").endBlock();
            cb.override("getNumericID").returning("int").withModifier(DEFAULT).body()
                    .returning("ordinal()").endBlock();
        }

        String tokensImplName = prefix + "TokenImpl";

        // This class will contain constants for every token type, and methods
        // for looking them up by name, character, etc.
        ClassBuilder<String> toks = ClassBuilder.forPackage(pkg).named(tokensTypeName)
                .makePublic().makeFinal()
                .docComment("Entry point for actual Token instances for the " + prefix + " language."
                        + "\nGenerated by ", getClass().getSimpleName(),
                        " from fields on ", proxy.lexerClassSimple(),
                        " from annotations on ", type.getSimpleName() + ".")
                .importing("java.util.Arrays", "java.util.HashMap", "java.util.Map", "java.util.Optional",
                        "java.util.List", "java.util.ArrayList", "java.util.Collections",
                        "org.nemesis.antlr.spi.language.highlighting.TokenCategorizer",
                        //                        "javax.annotation.processing.Generated",
                        proxy.lexerClassFqn())
                //                .annotatedWith("Generated").addArgument("value", getClass().getName()).addArgument("comments", versionString()).closeAnnotation()
                // Static categorizer field - fall back toExpression a heuristic categorizer if nothing is specified
                .field("CATEGORIZER").withModifier(FINAL)/*.withModifier(PRIVATE)*/.withModifier(STATIC)
                .initializedTo(tokenCatName == null ? "TokenCategorizer.heuristicCategorizer()" : "new " + tokenCatName + "()")
                .ofType("TokenCategorizer")
                // Make it non-instantiable
                .constructor().setModifier(PRIVATE).body().statement("throw new AssertionError()").endBlock();

        // Initialize arrays and maps of name -> token type for use by lookup methods
        // Some of this information we cannot collect at annotation processing time, because name arrays
        // are lazily initialized in the code generated by Antlr.  So loop over all tokens
        // and collect that at runtime here:
        ClassBuilder.BlockBuilder<ClassBuilder<String>> initMapsBlock = toks.staticBlock();
        initMapsBlock.declare("charsList").initializedWith("new ArrayList<>(ALL.length)").as("List<Character>");
        initMapsBlock.declare("tokForCharMap").initializedWith("new HashMap<>(ALL.length)").as("Map<Character, " + tokenTypeName + ">");
        initMapsBlock.simpleLoop(tokenTypeName, "tok")
                .over("ALL")
                .declare("symName").initializedByInvoking("symbolicName").on("tok").as(STRING)
                .declare("litName").initializedByInvoking("literalName").on("tok").as(STRING)
                .declare("litStripped").initializedWith("stripQuotes(litName)").as(STRING)
                .ifNotNull("symName")
                .invoke("put").withArgument("symName").withArgument("tok").on("bySymbolicName")
                .endIf()
                .ifNotNull("litStripped")
                .invoke("put").withArgument("litStripped").withArgument("tok").on("byLiteralName")
                .iff().invoke("length").on("litStripped").equals().literal(1).endCondition()
                .invoke("put").withArgument("litStripped.charAt(0)").withArgument("tok").on("tokForCharMap")
                .invoke("add").withArgument("litStripped.charAt(0)").on("charsList")
                .endIf()
                .endIf()
                .endBlock()
                .invoke("sort").withArgument("charsList").on("Collections")
                .statement("chars = new char[charsList.size()]")
                .statement("tokForChar = new " + tokenTypeName + "[charsList.size()]")
                .forVar("i").condition().lessThan().invoke("size").on("charsList").endCondition().running()
                .statement("chars[i] = charsList.get(i)")
                .statement("tokForChar[i] = tokForCharMap.get(charsList.get(i))")
                .endBlock()
                .endBlock();

        // Lookup for tokens which are a single character, look them up by character
        toks.method("forSymbol").addArgument("char", "ch").returning("Optional<" + tokenTypeName + ">")
                .docComment("Look up the token id for a single character such as ';'.")
                .withModifier(STATIC).withModifier(PUBLIC).body()
                .declare("ix").initializedWith("Arrays.binarySearch(chars, ch)").as("int")
                .iff().variable("ix").greaterThanOrEqualto().literal(0).endCondition()
                .returning("Optional.of(tokForChar[ix])").endIf()
                .returning("Optional.empty()").endBlock();

        // Lookup by symbolic name method
        toks.method("forSymbolicName")
                .docComment("Look up a token by its name in the ANTLR grammar.")
                .addArgument("String", "name").returning("Optional<" + tokenTypeName + ">")
                .withModifier(STATIC).withModifier(PUBLIC).body().returning("Optional.ofNullable(bySymbolicName.get(name))").endBlock();

        // Lookup by expression name method
        toks.method("forLiteralName")
                .docComment("Look up a token by its literal text content, usable for tokens whose text is "
                        + "entirely defined in the ANTLR grammar (symbols, keywords, things with no wildcards or user defined text).")
                .addArgument("String", "name").returning("Optional<" + tokenTypeName + ">")
                .withModifier(STATIC).withModifier(PUBLIC).body().returning("Optional.ofNullable(byLiteralName.get(name))").endBlock();

        // Helper method - expression names in antlr generated code come surrounded in
        // single quotes - strip those
        toks.method("stripQuotes").addArgument("String", "name").returning("String")
                .docComment("ANTLR Vocabulary instances return text content in quotes, which need to be removed.")
                .withModifier(STATIC)/*.withModifier(PRIVATE)*/.body()
                .iff().variable("name").notEquals().expression("null").and().invoke("isEmpty").on("name").equals().expression("false").and()
                .invoke("charAt").withArgument("0").on("name").equals().literal('\'').and().invoke("charAt").withArgument("name.length()-1")
                .on("name").equals().literal('\'').endCondition().returning("name.substring(1, name.length()-1)").endIf()
                .returning("name").endBlock();

        // Our implementation of the TokenId sub-interface defined as the first class above,
        // implementing all of its methods
        ClassBuilder<String> tokensImpl = ClassBuilder.forPackage(pkg).named(tokensImplName)
                .makeFinal().importing(proxy.lexerClassFqn())
                .lineComment("This ought to be a private inner class of\n" + prefix + "Tokens, but that "
                        + "wreaks havoc with NetBeans parsing, since it\ntries to access the nested"
                        + " class before javac has reached \nit - only the case with generated "
                        + "sources.\nSo we do it like this for now.")
                //        toks.innerClass(tokensImplName).withModifier(FINAL).withModifier(STATIC).withModifier(PRIVATE)
                //                .docComment("Implementation of " + tokenTypeName + " implementing NetBeans' TokenId class from the Lexer API.")
                .field("id").withModifier(PRIVATE).withModifier(FINAL).ofType("int")
                .implementing(tokenTypeName).constructor().addArgument("int", "id").body()
                .statement("this.id = id").endBlock()
                .method("ordinal").annotatedWith("Override").closeAnnotation().withModifier(PUBLIC).returning("int").body().returning("this.id").endBlock()
                .method("symbolicName").annotatedWith("Override").closeAnnotation().withModifier(PUBLIC).returning("String").body().returning(toks.className() + "." + "stripQuotes(" + proxy.lexerClassSimple() + ".VOCABULARY.getSymbolicName(id))").endBlock()
                .method("literalName").annotatedWith("Override").closeAnnotation().withModifier(PUBLIC).returning("String").body().returning(proxy.lexerClassSimple() + ".VOCABULARY.getLiteralName(id)").endBlock()
                .method("displayName").annotatedWith("Override").closeAnnotation().withModifier(PUBLIC).returning("String").body().returning(proxy.lexerClassSimple() + ".VOCABULARY.getDisplayName(id)").endBlock()
                .method("primaryCategory").annotatedWith("Override").closeAnnotation().withModifier(PUBLIC).returning("String").body().returning(toks.className() + "." + "CATEGORIZER.categoryFor(ordinal(), displayName(), symbolicName(), literalName())").endBlock()
                .method("toString").withModifier(PUBLIC).annotatedWith("Override").closeAnnotation().returning("String").body().returning("ordinal() + \"/\" + displayName()").endBlock()
                .method("equals").withModifier(PUBLIC).annotatedWith("Override").closeAnnotation().addArgument("Object", "o")
                .returning("boolean").body().returning("o == this || (o instanceof " + tokensImplName + " && ((" + tokensImplName + ") o).ordinal() == ordinal())").endBlock()
                .method("hashCode").withModifier(PUBLIC).annotatedWith("Override").closeAnnotation().returning("int").body().returning("id * 51").endBlock();

        writeOne(tokensImpl);

        // Now build a giant switch statement for lookup by id
        List<Integer> keys = proxy.allTypesSortedByName();
        ClassBuilder.SwitchBuilder<ClassBuilder.BlockBuilder<ClassBuilder<String>>> idSwitch
                = toks.method("forId")
                        .docComment("Look up a NetBeans token by its ANTLR token ID.")
                        .withModifier(PUBLIC)
                        .withModifier(STATIC).returning(tokenTypeName)
                        .addArgument("int", "id").body().switchingOn("id");

        toks.method("all")
                .docComment("Returns the set of all tokens defined by the grammar.")
                .withModifier(STATIC).withModifier(PUBLIC)
                .returning(tokenTypeName + "[]").body()
                .returning("Arrays.copyOf(ALL, ALL.length)").endBlock();

        ClassBuilder.ArrayLiteralBuilder<ClassBuilder<String>> allArray = toks.field("ALL").withModifier(STATIC).withModifier(PRIVATE).withModifier(FINAL).assignedTo().toArrayLiteral(tokenTypeName);
        // Loop and build cases in the switch statement for each token id, plus EOF,
        // and also populate our static array field which contains all of the token types
        for (Integer k : keys) {
            String fieldName = proxy.toFieldName(k);
            toks.field(fieldName).withModifier(STATIC).withModifier(PUBLIC).withModifier(FINAL)
                    .docComment(k.equals(proxy.erroneousTokenId())
                            ? "Placeholder token used by the NetBeans lexer for content which the lexer parses as erroneous or unparseable."
                            : "Constant for NetBeans token corresponding to token "
                            + proxy.tokenName(k) + " in " + proxy.lexerClassSimple() + ".VOCABULARY.")
                    .initializedTo("new " + tokensImplName + "(" + k + ")")
                    .ofType(tokenTypeName);
            idSwitch.inCase(k).returning(fieldName).endBlock();
            if (k != -1) {
                allArray.add(fieldName);
            }
        }
        idSwitch.inDefaultCase().statement("throw new IllegalArgumentException(\"No such id \" + id)").endBlock();
        idSwitch.build().endBlock();
        allArray.closeArrayLiteral();

        // Create our array and map fields here (so they are placed below the
        // constants in the source file - these are populated in the static block
        // defined above)
        toks.field("bySymbolicName").initializedTo("new HashMap<>()").withModifier(FINAL).withModifier(PRIVATE).withModifier(STATIC).ofType("Map<String, " + tokenTypeName + ">");
        toks.field("byLiteralName").initializedTo("new HashMap<>()").withModifier(FINAL).withModifier(PRIVATE).withModifier(STATIC).ofType("Map<String, " + tokenTypeName + ">");
        toks.field("chars").withModifier(FINAL).withModifier(PRIVATE).withModifier(STATIC).ofType("char[]");
        toks.field("tokForChar").withModifier(FINAL).withModifier(PRIVATE).withModifier(STATIC).ofType(tokenTypeName + "[]");

        String adapterClassName = prefix + "LexerAdapter";

        // Build an implementation of LanguageHierarchy here
        String hierName = prefix + "Hierarchy";
        String hierFqn = pkg + "." + hierName;
        String languageMethodName = lc(prefix) + "Language";
        ClassBuilder<String> lh = ClassBuilder.forPackage(pkg).named(hierName)
                .importing("org.netbeans.api.lexer.Language", "org.netbeans.spi.lexer.LanguageHierarchy",
                        "org.netbeans.spi.lexer.Lexer", "org.netbeans.spi.lexer.LexerRestartInfo",
                        "java.util.Collection", "java.util.Arrays", "java.util.Collections",
                        "org.nemesis.antlr.spi.language.NbAntlrUtils", "org.netbeans.spi.lexer.Lexer",
                        "org.antlr.v4.runtime.CharStream", "org.nemesis.antlr.spi.language.NbLexerAdapter",
                        "java.util.Map", "java.util.HashMap", "java.util.ArrayList", "org.antlr.v4.runtime.CharStream",
                        "java.util.function.BiConsumer", "org.nemesis.antlr.spi.language.ParseResultContents",
                        "org.netbeans.modules.parsing.api.Snapshot", "org.nemesis.extraction.Extraction",
                        "org.nemesis.antlr.spi.language.AntlrParseResult", "org.nemesis.antlr.spi.language.IterableTokenSource",
                        "org.netbeans.api.editor.mimelookup.MimeRegistration",
                        "org.antlr.v4.runtime.Vocabulary", proxy.lexerClassFqn()
                )
                .docComment("LanguageHierarchy implementation for ", prefix,
                        ". Generated by ", getClass().getSimpleName(), " from fields on ", proxy.lexerClassSimple(), ".")
                //                .importing("javax.annotation.processing.Generated")
                .makePublic().makeFinal()
                .constructor().setModifier(PUBLIC).body().debugLog("Create a new " + hierName).endBlock()
                //                .annotatedWith("Generated").addArgument("value", getClass().getName()).addArgument("comments", versionString()).closeAnnotation()
                .extending("LanguageHierarchy<" + tokenTypeName + ">")
                .field("LANGUAGE").withModifier(FINAL).withModifier(STATIC).withModifier(PRIVATE)
                /*.initializedTo("new " + hierName + "().language()").*/.ofType("Language<" + tokenTypeName + ">")
                .field("CATEGORIES").withModifier(PRIVATE).withModifier(STATIC).withModifier(FINAL).ofType("Map<String, Collection<" + tokenTypeName + ">>")
                .staticBlock(bb -> {
                    bb.declare("map").initializedWith("new HashMap<>()").as("Map<String, Collection<" + tokenTypeName + ">>")
                            .lineComment("Assign fields here to guarantee initialization order")
                            .statement("IDS = Collections.unmodifiableList(Arrays.asList(" + tokensTypeName + ".all()));")
                            .simpleLoop(tokenTypeName, "tok").over("IDS")
                            .iff().invoke("ordinal").on("tok").equals().literal(-1).endCondition()
                            .lineComment("Antlr has a token type -1 for EOF; editor's cache cannot handle negative ordinals")
                            .statement("continue").endIf()
                            .declare("curr").initializedByInvoking("get").withArgument("tok.primaryCategory()").on("map").as("Collection<" + tokenTypeName + ">")
                            .iff().variable("curr").equals().expression("null").endCondition()
                            .statement("curr = new ArrayList<>()")
                            .invoke("put").withArgument("tok.primaryCategory()").withArgument("curr").on("map")
                            .endIf()
                            .invoke("add").withArgument("tok").on("curr")
                            .endBlock()
                            .statement("CATEGORIES = Collections.unmodifiableMap(map)")
                            .statement("LANGUAGE = new " + hierName + "().language()")
                            .endBlock();
                })
                .field("IDS").withModifier(PRIVATE).withModifier(STATIC).withModifier(FINAL).ofType("Collection<" + tokenTypeName + ">")
                //                .initializedTo("Collections.unmodifiableList(Arrays.asList(" + tokensTypeName + ".all()))").ofType("Collection<" + tokenTypeName + ">")
                .method("mimeType")
                .docComment("Get the MIME type.")
                .returning(STRING)
                .annotatedWith("Override").closeAnnotation()
                .withModifier(PROTECTED).withModifier(FINAL).body()
                .returning(tokenTypeName + ".MIME_TYPE").endBlock()
                .method(languageMethodName).withModifier(PUBLIC).withModifier(STATIC).returning("Language<" + tokenTypeName + ">")
                //                .annotatedWith("MimeRegistration", ab -> {
                //                    ab.addArgument("mimeType", mimeType)
                //                            .addArgument("service", "Language")
                //                            .addExpressionArgument("position", 500).closeAnnotation();
                //                })
                .body().returning("LANGUAGE").endBlock()
                .method("createTokenIds").returning("Collection<" + tokenTypeName + ">").override().withModifier(PROTECTED).withModifier(FINAL).body().returning("IDS").endBlock()
                .method("createLexer").override().returning("Lexer<" + tokenTypeName + ">").addArgument("LexerRestartInfo<" + tokenTypeName + ">", "info").withModifier(PROTECTED).withModifier(FINAL)
                .body(bb -> {
                    bb.debugLog("Create a new " + prefix + " Lexer");
                    bb.returning("NbAntlrUtils.createLexer(info, LEXER_ADAPTER)").endBlock();
                })
                .method("isRetainTokenText")
                .docComment("Returns true for tokens which are entirely defined within the ANTLR grammar (keywords, symbols).")
                .override().withModifier(PROTECTED).withModifier(FINAL).returning("boolean").addArgument(tokenTypeName, "tok")
                .body().returning("tok.literalName() == null").endBlock()
                .method("createTokenCategories").override().withModifier(PROTECTED).withModifier(FINAL)
                .returning("Map<String, Collection<" + tokenTypeName + ">>").body().returning("CATEGORIES").endBlock()
                .method("createAntlrLexer")
                .returning(proxy.lexerClassSimple()).withModifier(PUBLIC).withModifier(STATIC)
                .addArgument("CharStream", "stream")
                .body().returning("LEXER_ADAPTER.createLexer(stream)").endBlock()
                .method("newParseResult", mth -> {
                    mth.docComment("Parse a document and pass it to the BiConsumer.");
                    mth.withModifier(STATIC)
                            .addArgument("Snapshot", "snapshot")
                            .addArgument("Extraction", "extraction")
                            .addArgument("BiConsumer<AntlrParseResult, ParseResultContents>", "receiver")
                            .body(bb -> {
                                bb.debugLog("new " + prefix + " parse result");
                                bb.log("New {0} parse result: {1}", Level.FINE,
                                        LinesBuilder.stringLiteral(prefix),
                                        "extraction.logString()");
                                bb.invoke("newParseResult")
                                        .withArgument("snapshot")
                                        .withArgument("extraction")
                                        .withArgument("receiver").on("LEXER_ADAPTER").endBlock();
                            });
                })
                .method("createWrappedTokenSource", mb -> {
                    mb.docComment("Creates a token source which wraps the lexer contents and can "
                            + "have its position wound forward and backwards, for processing tools "
                            + "to process it individually without a reparse.");
                    mb.addArgument(proxy.lexerClassSimple(), "lexer")
                            .withModifier(STATIC)
                            .returning("IterableTokenSource")
                            .body(body -> {
                                body.returningInvocationOf("createWrappedTokenSource")
                                        .withArgument("lexer").on("LEXER_ADAPTER").endBlock();
                            });
                });

        // Inner implementation of NbLexerAdapter, which allows us toExpression use a generic
        // Lexer class and takes care of creating the Antlr lexer and calling methods
        // that will exist on the lexer implementation class but not on the parent class
        lh.innerClass(adapterClassName).withModifier(PRIVATE).withModifier(STATIC).withModifier(FINAL)
                .extending("NbLexerAdapter<" + tokenTypeName + ", " + proxy.lexerClassSimple() + ">")
                .method("createLexer").override().withModifier(PUBLIC).returning(proxy.lexerClassSimple()).addArgument("CharStream", "stream")
                .docComment("Creates a NetBeans lexer wrapper for an ANTLR lexer.")
                .body(bb -> {
                    bb.declare("result").initializedWith("new " + proxy.lexerClassSimple() + "(stream)").as(proxy.lexerClassSimple());
                    bb.invoke("removeErrorListeners").on("result");
                    bb.returning("result").endBlock();
                })
                .method("createWrappedTokenSource", mb -> {
                    mb.docComment("Creates a token source which wraps the lexer contents and can "
                            + "have its position wound forward and backwards, for processing tools "
                            + "to process it individually without a reparse.");
                    mb.addArgument(proxy.lexerClassSimple(), "lexer").returning("IterableTokenSource")
                            .body().returningInvocationOf("wrapLexer")
                            .withArgument("lexer").on("super").endBlock();
                })
                .override("vocabulary").withModifier(PROTECTED).returning("Vocabulary")
                .body().returning(proxy.lexerClassSimple() + ".VOCABULARY").endBlock()
                .method("tokenId").withModifier(PUBLIC).override().addArgument("int", "ordinal").returning(tokenTypeName)
                .body().returning(tokensTypeName + ".forId(ordinal)").endBlock()
                .method("setInitialStackedModeNumber").override().withModifier(PUBLIC).addArgument(proxy.lexerClassSimple(), "lexer")
                .addArgument("int", "modeNumber").body()
                .lineComment("This method will be exposed on the implementation type")
                .lineComment("but is not exposed in the parent class Lexer")
                .statement("lexer.setInitialStackedModeNumber(modeNumber)").endBlock()
                .overridePublic("getInitialStackedModeNumber").returning("int").addArgument(proxy.lexerClassSimple(), "lexer")
                .body()
                .lineComment("This method will be exposed on the implementation type")
                .lineComment("but is not exposed in the parent class Lexer")
                .returning("lexer.getInitialStackedModeNumber()").endBlock()
                .method("newParseResult", mth -> {
                    mth.docComment("Invokes ANTLR and creates a parse result.");
                    mth.addArgument("Snapshot", "snapshot")
                            .addArgument("Extraction", "extraction")
                            .addArgument("BiConsumer<AntlrParseResult, ParseResultContents>", "receiver")
                            .body(bb -> {
                                bb.invoke("createParseResult")
                                        .withArgument("snapshot")
                                        .withArgument("extraction")
                                        .withArgument("receiver").on("super").endBlock();
                            });
                })
                .build();

        lh.field("LEXER_ADAPTER")
                .docComment("Adapter which lets us talk to ANTLR.")
                .initializedTo("new " + adapterClassName + "()").withModifier(PRIVATE).withModifier(STATIC).withModifier(FINAL)
                .ofType(adapterClassName);
        //.ofType("NbLexerAdapter<" + tokenTypeName + ", " + lexerClass + ">");

        // Save generated source files toExpression disk
        writeOne(cb);
        writeOne(toks);
        writeOne(lh);

        if (categories != null) {
            handleCategories(categories, proxy, type, mirror, tokenCategorizerClass, tokenCatName, pkg, prefix);
            maybeGenerateParserRuleExtractors(mimeType, categories, proxy, type, mirror, tokenCategorizerClass, hierName, pkg, prefix, parser);
        }
        String bundle = utils().annotationValue(mirror, "localizingBundle", String.class);

        String layerFileName = hierFqn.replace('.', '-');
        String languageRegistrationPath = "Editors/" + mimeType + "/" + layerFileName + ".instance";

        String genBundleName = prefix + "LanguageBundle";
        String genBundleInfo = pkg + "." + genBundleName;
        Filer filer = processingEnv.getFiler();
        String bundlePath = genBundleInfo.replace('.', '/') + ".properties";
        FileObject altBundle = filer.createResource(StandardLocation.CLASS_OUTPUT, "", bundlePath, type);
        Properties props = new Properties();
        props.setProperty("prefix", prefix);
        props.setProperty(mimeType, prefix);
        props.setProperty("Editors/" + mimeType, prefix);
        props.setProperty(languageRegistrationPath, prefix);

        try (OutputStream out = altBundle.openOutputStream()) {
            props.store(out, getClass().getName());
        }

        addLayerTask(lb -> {
            log("Run layer task to create bundle info");
            // For some reason, the generated @MimeRegistration annotation is resulting
            // in an attempt toExpression load a *class* named with the fqn + method name.
            // So, do it the old fashioned way
            try {
                lb.folder("Editors/" + mimeType).bundlevalue("displayName",
                        bundle + "#" + mimeType).write();

                lb.file(languageRegistrationPath)
                        //            LayerBuilder.File reg = lb.instanceFile("Editors/" + mimeType, layerFileName)
                        .methodvalue("instanceCreate", hierFqn, languageMethodName)
                        .stringvalue("instanceOf", "org.netbeans.api.lexer.Language")
                        .intvalue("position", 1000)
                        .bundlevalue("displayName", bundle + "#" + mimeType)
                        .write();
            } catch (LayerGenerationException ex) {
                logException(ex, true);
                utils().fail("No bundle key found for the language name in the"
                        + " specified bundle. Try specifying '"
                        + mimeType + "=" + prefix
                        + "' in the properties file " + bundle, type);
            }
        }, type);
    }

    static String willHandleCategories(List<AnnotationMirror> categories, TypeMirror specifiedClass, String prefix, AnnotationUtils utils) {
        if (categories == null || categories.isEmpty() || (categories.size() == 1 && "_".equals(utils.annotationValue(categories.get(0), "name", String.class)))) {
            return specifiedClass == null ? null : specifiedClass.toString();
        }
        return prefix + "TokenCategorizer";
    }

    private void maybeGenerateParserRuleExtractors(String mimeType, List<AnnotationMirror> categories, LexerProxy lexer, TypeElement type, AnnotationMirror mirror, TypeMirror tokenCategorizerClass, String catName, Name pkg, String prefix, ParserProxy parser) throws Exception {
        System.out.println("HAVE " + categories.size() + " CATEGORIES");
        List<AnnotationMirror> relevant = new ArrayList<>(categories.size() / 2);
        Map<Integer, Set<String>> ownership = CollectionUtils.supplierMap(TreeSet::new);
        for (AnnotationMirror am : categories) {
            List<Integer> parserRuleIds = utils().annotationValues(am, "parserRuleIds", Integer.class);
            if (!parserRuleIds.isEmpty()) {
                relevant.add(am);
                Set<Integer> overlap = new HashSet<>(ownership.keySet());
                overlap.retainAll(parserRuleIds);
                String categoryName = utils().annotationValue(am, "name", String.class, "<unnamed>");
                if (!overlap.isEmpty()) {
                    for (Integer i : overlap) {
                        String name = parser.nameForRule(i);
                        Set<String> others = ownership.get(i);
                        StringBuilder sb = new StringBuilder(64).append(name)
                                .append(" is used by multiple token categories: ");
                        for (Iterator<String> it = others.iterator(); it.hasNext();) {
                            sb.append(it.next());
                            if (it.hasNext()) {
                                sb.append(", ");
                            }
                        }
                        sb.append(" and ").append(categoryName).append(". ")
                                .append("One will win and others will lose.");
                        utils().warn(sb.toString(), type, am);
                    }
                }
            }
        }
        System.out.println("FOUND " + relevant.size() + " relevant categories");
        for (AnnotationMirror am : relevant) {
            Set<Integer> parserRuleIds = new TreeSet<>(utils().annotationValues(am, "parserRuleIds", Integer.class));
            StringBuilder genClassName = new StringBuilder(prefix).append('_');
            StringBuilder keyFieldName = new StringBuilder("KEY");
            String categoryName = utils().annotationValue(am, "name", String.class, "<unnamed>");
            genClassName.append(toUsableFieldName(categoryName));
            List<String> fieldNames = new ArrayList<>(parserRuleIds.size());
            System.out.println("  CATEGORY " + categoryName + " maps");
            for (int val : parserRuleIds) {
                String nm = parser.nameForRule(val);
                System.out.println("     " + nm);
                genClassName.append('_').append(nm);
                keyFieldName.append('_').append(nm.toUpperCase());
                fieldNames.add(parser.ruleFieldForRuleId(val));
            }
            genClassName.append("RuleHighlighting");
            /*
.extractingRegionsUnder(AntlrKeys.STUFF)
                .whenRuleIdIn(ANTLRv4Parser.RULE_fragmentRuleDeclaration, ANTLRv4Parser.RULE_labeledParserRuleElement)
                .extractingKeyWith(rule -> {
                    String name = ANTLRv4Parser.ruleNames[rule.getRuleIndex()];
                    System.out.println("ID KEY " + name + ": " + rule.getText());
                    return name;
                }).finishRegionExtractor()

public static final RegionsKey<String> STUFF = RegionsKey.create(String.class, "stuff");

    @HighlighterKeyRegistration(mimeType = MIME_TYPE, positionInZOrder = 10, order = 11,
            colors = @ColoringCategory(name = "stuff",
                    colors = @Coloration(bg = {100, 255, 100})))

org.nemesis.antlr.spi.language.highlighting.semantic.HighlighterKeyRegistration

             */
            ClassBuilder<String> cb = ClassBuilder.forPackage(utils().packageName(type)).named(genClassName.toString())
                    .makePublic().makeFinal()
                    .importing("org.nemesis.extraction.ExtractionRegistration",
                            "org.nemesis.extraction.ExtractorBuilder",
                            "org.nemesis.extraction.key.RegionsKey",
                            "org.nemesis.antlr.spi.language.highlighting.semantic.HighlighterKeyRegistration",
                            parser.parserEntryPointReturnTypeFqn(),
                            parser.parserClassFqn())
                    .field(keyFieldName.toString(), fb -> {
                        fb.withModifier(PUBLIC, STATIC, FINAL)
                                .annotatedWith("HighlighterKeyRegistration", ab -> {
                                    ab.addArgument("mimeType", mimeType)
                                            .addArgument("coloringName", categoryName);
                                })
                                .initializedFromInvocationOf("create")
                                .withArgument("String.class")
                                .withStringLiteral(keyFieldName.toString())
                                .on("RegionsKey")
                                .ofType("RegionsKey<String>");
                    })
                    .method("extract", mb -> {
                        mb.annotatedWith("ExtractionRegistration", ab -> {
                            ab.addArgument("mimeType", mimeType)
                                    .addClassArgument("entryPoint", parser.parserEntryPointReturnTypeSimple());
                        }).withModifier(PUBLIC, STATIC)
                                .addArgument("ExtractorBuilder<? super "
                                        + parser.parserEntryPointReturnTypeSimple() + ">", "bldr")
                                .body(bb -> {
                                    bb.invoke("finishRegionExtractor")
                                            .onInvocationOf("extractingKeyWith")
                                            .withLambdaArgument(lb -> {
                                                lb.withArgument("rule")
                                                        .body(lbb -> {
                                                            lbb.returning(parser.parserClassSimple()
                                                                    + ".ruleNames[rule.getRuleIndex()]");
                                                        });
                                            }).onInvocationOf("whenRuleIdIn")
                                            .withNewArrayArgument("int", avb -> {
                                                fieldNames.forEach((fn) -> {
                                                    avb.expression(parser.parserClassSimple() + "." + fn);
                                                });
                                            }).onInvocationOf("extractingRegionsUnder")
                                            .withArgument(keyFieldName.toString())
                                            .on("bldr");
                                });

                    });
            ;

            System.out.println("GENERATE\n" + cb);

            writeOne(cb);

        }
    }

    static String toUsableFieldName(String s) {
        StringBuilder sb = new StringBuilder();
        int max = s.length();
        for (int i = 0; i < max; i++) {
            char c = s.charAt(i);
            boolean valid;
            switch (i) {
                case 0:
                    valid = Character.isJavaIdentifierStart(c);
                    if (valid) {
                        c = Character.toLowerCase(c);
                    }
                    break;
                default:
                    valid = Character.isJavaIdentifierPart(c);
            }
            if (!valid) {
                continue;
            }
            sb.append(c);
        }
        return sb.toString();
    }

    private void handleCategories(List<AnnotationMirror> categories, LexerProxy lexer, TypeElement type, AnnotationMirror mirror, TypeMirror tokenCategorizerClass, String catName, Name pkg, String prefix) throws Exception {
        // Create an implementation of TokenCategorizer based on the annotation values
        ClassBuilder<String> cb = ClassBuilder.forPackage(pkg).named(catName).withModifier(FINAL)
                .importing("org.nemesis.antlr.spi.language.highlighting.TokenCategorizer")
                .implementing("TokenCategorizer")
                .conditionally(tokenCategorizerClass != null, cbb -> {
                    cbb.field("DEFAULT_CATEGORIZER").initializedTo("new " + tokenCategorizerClass + "()").ofType("TokenCategorizer");
                });

        // categoryFor(ordinal(), displayName(), symbolicName(), literalName())
        cb.publicMethod("categoryFor", catFor -> {
            catFor.docComment("Find the category for a token, either by returning the value "
                    + "specified in an annotation, or using a heuristic.");
            catFor.addArgument("int", "id")
                    .addArgument(STRING, "displayName").addArgument(STRING, "symbolicName")
                    .addArgument(STRING, "literalName").returning(STRING)
                    .body(meth -> {
                        Set<Integer> usedTokenTypes = new HashSet<>();
                        meth.conditionally(tokenCategorizerClass != null, bb -> {
                            bb.declare("result").initializedByInvoking("categoryFor")
                                    .withArgument("id")
                                    .withArgument("displayName")
                                    .withArgument("symbolicName")
                                    .withArgument("literalName")
                                    .on("DEFAULT_CATEGORIZER");
                            bb.iff().variable("result").notEquals().expression("null").endCondition()
                                    .returning("result").endIf();
                        }).switchingOn("id", (ClassBuilder.SwitchBuilder<?> sw) -> {
                            for (AnnotationMirror cat : categories) {
                                String name = utils().annotationValue(cat, "name", String.class);
                                List<Integer> values = utils().annotationValues(cat, "tokenIds", Integer.class);
                                Collections.sort(values);
                                for (Iterator<Integer> it = values.iterator(); it.hasNext();) {
                                    Integer val = it.next();
                                    if (usedTokenTypes.contains(val)) {
                                        utils().fail("More than one token category specifies the token type " + val
                                                + " - " + lexer.tokenName(val) + " - in category '" + name + "' of " + cat, type, cat);
                                        return;
                                    }
                                    if (!lexer.typeExists(val)) {
                                        Map<String, Integer> consts = lexerClassConstants();
                                        if (consts.values().contains(val)) {
                                            List<String> all = new ArrayList<>();
                                            for (Map.Entry<String, Integer> e : consts.entrySet()) {
                                                if (val.equals(e.getValue())) {
                                                    all.add(e.getKey());
                                                }
                                            }
                                            if (all.size() == 1) {
                                                utils().fail(val + " is not a token type on your lexer; it is likely the constant "
                                                        + all.get(0) + ", declared on its parent class, Lexer"
                                                        + " and accidentally included.", type, cat);
                                            } else {
                                                utils().fail(val + " is not a token type on your lexer; it is likely a constant "
                                                        + "declared on its parent class Lexer, one of " + all, type, cat);
                                            }
                                            return;
                                        }
                                        utils().fail("No token with type " + val + " exists on as a field on " + lexer.lexerClassFqn()
                                                + " specified in category '" + name + "' of " + cat, type, cat);
                                        return;
                                    }
                                    sw.inCase(val, caseBody -> {
                                        String tokenName = lexer.tokenName(val);
                                        String fieldName = lexer.toFieldName(val);
                                        caseBody.lineComment(prefix + "Tokens." + fieldName);
                                        caseBody.lineComment(lexer.lexerClassSimple() + "." + tokenName);
                                        if (!it.hasNext()) {
                                            caseBody.returningStringLiteral(name);
                                        }
//                                        caseBody.endBlock();
                                    });
                                    usedTokenTypes.add(val);
                                }
                            }
                            sw.inCase(lexer.erroneousTokenId(), caseBody -> {
                                caseBody.lineComment(prefix + ".TOK_$ERRONEOUS");
                                caseBody.lineComment("This token does not exist in \n"
                                        + lexer.lexerClassSimple()
                                        + ", but is \nused by the generated NetBeans lexer to "
                                        + "handle trailing tokens\nif the Antlr lexer aborts.");
                                caseBody.returningStringLiteral("error");
                            });
                            sw.inDefaultCase().returningStringLiteral("default").endBlock();
                        });
                        Set<Integer> unhandled = lexer.tokenTypes();
                        unhandled.remove(lexer.erroneousTokenId());
                        unhandled.remove(-1);
                        unhandled.removeAll(usedTokenTypes);
                        if (!unhandled.isEmpty()) {
                            StringBuilder sb = new StringBuilder("The following tokens "
                                    + "were not given a category in the annotation that "
                                    + "generated this class:\n");
                            int ix = 0;
                            for (Iterator<Integer> it = unhandled.iterator(); it.hasNext();) {
                                Integer unh = it.next();
                                String name = lexer.tokenName(unh);
                                sb.append(name).append('(').append(unh).append(')');
                                if (it.hasNext()) {
                                    if (++ix % 3 == 0) {
                                        sb.append(",\n");
                                    } else {
                                        sb.append(", ");
                                    }
                                }
                            }
                            meth.lineComment(sb.toString());
                            processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING,
                                    sb, type, mirror);
                        } else {
                            meth.lineComment("All token types defined in "
                                    + lexer.lexerClassSimple() + " have categories");;
                        }
                    });
        });
        writeOne(cb);

    }
    private static final String STRING = "String";

    private static String lc(String s) {
        char[] c = s.toCharArray();
        c[0] = Character.toLowerCase(c[0]);
        return new String(c);
    }

    private static final String DEFAULT_ACTIONS[] = {
        "-System/org.openide.actions.OpenAction",
        "Edit/org.openide.actions.CutAction",
        "Edit/org.openide.actions.CopyAction",
        "Edit/org.openide.actions.DeleteAction",
        "-System/org.openide.actions.RenameAction",
        "-System/org.openide.actions.SaveAsTemplateAction",
        "System/org.openide.actions.FileSystemAction",
        "System/org.openide.actions.ToolsAction",
        "System/org.openide.actions.PropertiesAction"
    };

}

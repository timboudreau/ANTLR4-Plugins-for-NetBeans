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

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.logging.Level;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import static org.nemesis.registration.GotoDeclarationProcessor.GOTO_ANNOTATION;
import static org.nemesis.registration.LanguageRegistrationProcessor.REGISTRATION_ANNO;
import com.mastfrog.java.vogon.ClassBuilder;
import com.mastfrog.annotation.AnnotationUtils;
import com.mastfrog.annotation.processor.LayerGeneratingDelegate;
import com.mastfrog.util.preconditions.Exceptions;
import static org.nemesis.registration.LocalizeAnnotationProcessor.inIDE;
import static org.nemesis.registration.NameAndMimeTypeUtils.cleanMimeType;
import static org.nemesis.registration.typenames.JdkTypes.ACTION;
import static org.nemesis.registration.typenames.JdkTypes.TEXT_ACTION;
import org.nemesis.registration.typenames.KnownTypes;
import static org.nemesis.registration.typenames.KnownTypes.ABSTRACT_EDITOR_ACTION;
import static org.nemesis.registration.typenames.KnownTypes.BASE_DOCUMENT;
import static org.nemesis.registration.typenames.KnownTypes.DECLARATION_TOKEN_PROCESSOR;
import static org.nemesis.registration.typenames.KnownTypes.EDITOR_ACTION_REGISTRATION;
import static org.nemesis.registration.typenames.KnownTypes.EXTRACTION;
import static org.nemesis.registration.typenames.KnownTypes.NAMED_REGION_REFERENCE_SETS;
import static org.nemesis.registration.typenames.KnownTypes.NAMED_SEMANTIC_REGION_REFERENCE;
import static org.nemesis.registration.typenames.KnownTypes.NAME_REFERENCE_SET_KEY;
import static org.nemesis.registration.typenames.KnownTypes.NB_ANTLR_UTILS;
import static org.nemesis.registration.typenames.KnownTypes.TOKEN_CONTEXT_PATH;
import static org.nemesis.registration.typenames.KnownTypes.TOKEN_ID;
import static org.nemesis.registration.typenames.KnownTypes.UTIL_EXCEPTIONS;
import org.openide.filesystems.annotations.LayerBuilder;

/**
 *
 * @author Tim Boudreau
 */
//@ServiceProvider(service = Processor.class)
@SupportedAnnotationTypes({GOTO_ANNOTATION, REGISTRATION_ANNO})
public class GotoDeclarationProcessor extends LayerGeneratingDelegate {

    static final String GOTO_ANNOTATION = "org.nemesis.antlr.spi.language.Goto";
    private Predicate<? super Element> fieldTest;
    private Predicate<? super AnnotationMirror> annoTest;

    @Override
    protected void onInit(ProcessingEnvironment env, AnnotationUtils utils) {
        fieldTest = utils.testsBuilder().mustBeFullyReifiedType()
                .isKind(ElementKind.FIELD)
                .isSubTypeOf(NAME_REFERENCE_SET_KEY.qnameNotouch())
                .hasModifier(STATIC)
                .doesNotHaveModifier(PRIVATE)
                .testContainingClass().doesNotHaveModifier(PRIVATE)
                .build().build();
        ;
        annoTest = utils().testMirror()
                .testMember("mimeType")
                .addPredicate("Mime type", mir -> {
                    Predicate<String> mimeTest = NameAndMimeTypeUtils.complexMimeTypeValidator(true, utils(), null, mir);
                    String value = utils().annotationValue(mir, "mimeType", String.class);
                    return mimeTest.test(value);
                })
                .build().build();
    }

    @Override
    protected boolean validateAnnotationMirror(AnnotationMirror mirror, ElementKind kind, Element el) {
        return annoTest.test(mirror);
    }

    private final Map<String, Set<String>> all = new HashMap<>();
    private final Map<String, Set<Element>> variablesForItem = new HashMap<>();
    private final Map<String, String> targetPackageForMimeType = new HashMap<>();
    private final Map<String, LexerProxy> lexerForMimeType = new HashMap<>();

    private Set<Element> setFor(String keyField) {
        return variablesForItem.getOrDefault(keyField, Collections.emptySet());
    }

    @Override
    protected boolean processTypeAnnotation(TypeElement type, AnnotationMirror mirror, RoundEnvironment roundEnv) throws Exception {
        // We need to consume this annotation to accurately get the package the
        // data object type will be generated into, in order to generate the
        // declaration token processor next to it
        if (REGISTRATION_ANNO.equals(mirror.getAnnotationType().toString())) {
            String mimeType = cleanMimeType(utils().annotationValue(mirror, "mimeType", String.class));
            if (mimeType != null) {
                AnnotationMirror file = utils().annotationValue(mirror, "file", AnnotationMirror.class);
                if (file != null) {
                    String pkg = utils().packageName(type);
                    targetPackageForMimeType.put(mimeType, pkg);
                }
                LexerProxy lexerProxy = LexerProxy.create(mirror, type, utils());
                if (lexerProxy != null) {
                    lexerForMimeType.put(mimeType, lexerProxy);
                }
            }
        }
        return false;
    }

    private void addField(String mimeType, String targetType, VariableElement on) {
        if (!targetPackageForMimeType.containsKey(mimeType)) {
            targetPackageForMimeType.put(mimeType, processingEnv.getElementUtils().getPackageOf(AnnotationUtils.enclosingType(on)).getQualifiedName().toString());
        }
        Set<String> fields = all.get(mimeType);
        if (fields == null) {
            fields = new HashSet<>();
            all.put(mimeType, fields);
        }
        fields.add(targetType);
        Set<Element> vars = variablesForItem.get(targetType);
        if (vars == null) {
            vars = new HashSet<>();
            variablesForItem.put(targetType, vars);
        }
        vars.add(on);
    }

    @Override
    protected boolean processFieldAnnotation(VariableElement var, AnnotationMirror mirror, RoundEnvironment roundEnv) throws Exception {
        if (!fieldTest.test(var)) {
            return false;
        }
        String mimeType = cleanMimeType(utils().annotationValue(mirror, "mimeType", String.class));
        TypeElement targetType = AnnotationUtils.enclosingType(var);
        addField(mimeType, targetType.getQualifiedName() + "." + var.getSimpleName(), var);
        return true;
    }

    private final Set<String> builtMimeTypes = new HashSet<>();

    @Override
    protected boolean onRoundCompleted(Map<AnnotationMirror, Element> processed, RoundEnvironment env) throws Exception {
        if (!all.isEmpty()) {
            for (Map.Entry<String, Set<String>> e : all.entrySet()) {
                if (!builtMimeTypes.contains(e.getKey())) {
                    ClassBuilder<String> bldr = generateCustomGotoDeclarationAction(e.getKey(), e.getValue());
                    if (bldr != null) {
                        writeOne(bldr);
                    }
                    ClassBuilder<String> declBuilder = declarationFinder(e.getKey(), e.getValue());
                    writeOne(declBuilder);
                }
                builtMimeTypes.add(e.getKey());
            }
            if (env.processingOver() && !env.errorRaised() && !inIDE) {
//            processingEnv.getMessager().printMessage(Diagnostic.Kind.MANDATORY_WARNING, KnownTypes.touchedMessage());
                System.out.println(KnownTypes.touchedMessage(this));
            }
        }
        return false;
    }

    private Set<Element> setsFor(Set<String> keyFqns) {
        Set<Element> result = new HashSet<>();
        for (String fqn : keyFqns) {
            result.addAll(setFor(fqn));
        }
        return result;
    }

    private String targetPackage(String mimeType) {
        String result = targetPackageForMimeType.get(mimeType);
        if (result == null) {
            throw new IllegalArgumentException("No target package for " + mimeType);
        }
        return result;
    }

    private String dataObjectPackageForMimeType(String mimeType) {
        return targetPackage(mimeType) + ".file";
    }

    private ClassBuilder<String> declarationFinder(String mimeType, Set<String> fieldFqns) {
        String targetPackage = dataObjectPackageForMimeType(mimeType);
        String lexerFqn = lexerForMimeType.containsKey(mimeType)
                ? lexerForMimeType.get(mimeType).lexerClassFqn() : null;
        String base = NameAndMimeTypeUtils.prefixFromMimeTypeOrLexerName(mimeType, lexerFqn);
        String generatedClassName = base + "DeclarationTokenProcessor";
        ClassBuilder<String> cb = ClassBuilder.forPackage(targetPackage)
                .named(generatedClassName)
                .importing(
                        DECLARATION_TOKEN_PROCESSOR.qname(),
                        BASE_DOCUMENT.qname(),
                        TOKEN_ID.qname(),
                        UTIL_EXCEPTIONS.qname(),
                        NB_ANTLR_UTILS.qname(),
                        NAMED_REGION_REFERENCE_SETS.qname(),
                        NAME_REFERENCE_SET_KEY.qname(),
                        TOKEN_CONTEXT_PATH.qname(),
                        NAMED_SEMANTIC_REGION_REFERENCE.qname(),
                        EXTRACTION.qname())
                .implementing(DECLARATION_TOKEN_PROCESSOR.simpleName())
                .withModifier(PUBLIC, FINAL)
                .field("KEYS", fb -> {
                    fb.withModifier(PRIVATE, STATIC, FINAL)
                            .initializedAsArrayLiteral(NAME_REFERENCE_SET_KEY.simpleName() + "<?>", ab -> {
                                fieldFqns.forEach(ab::add);
                                ab.closeArrayLiteral();
                            });
                })
                .field("varName").withModifier(PRIVATE, FINAL).ofType("String")
                .field("startPos").withModifier(PRIVATE, FINAL).ofType("int")
                .field("endPos").withModifier(PRIVATE, FINAL).ofType("int")
                .field("doc").withModifier(PRIVATE, FINAL).ofType(BASE_DOCUMENT.simpleName())
                .constructor(con -> {
                    con.setModifier(PUBLIC)
                            .addArgument("String", "varName")
                            .addArgument("int", "startPos")
                            .addArgument("int", "endPos")
                            .addArgument(BASE_DOCUMENT.simpleName(), "doc")
                            .body(bb -> {
                                bb.statement("this.varName = varName")
                                        .statement("this.startPos = startPos")
                                        .statement("this.endPos = endPos")
                                        .statement("this.doc = doc");
                                bb.log(Level.FINEST).argument("varName").argument("startPos").argument("endPos").argument("doc")
                                        .logging("Create " + generatedClassName + " for {0} at {1}:{2} in {3}")
                                        .endBlock();
                            });
                })
                .override("getDeclarationPosition", mb -> {
                    mb.withModifier(PUBLIC).returning("int")
                            .body(bb -> {
                                bb.declare("result").initializedAsNewArray("int").literal(-1).closeArrayLiteral();
//                                bb.declare("result").initializedWith("new int[] {-1}").as("int[]");
                                bb.invoke("parseImmediately").withArgument("doc")
                                        .withLambdaArgument(lb -> {
                                            lb.withArgument(EXTRACTION.simpleName(), "extraction").withArgument("Exception", "thrown")
                                                    .body(lbb -> {
                                                        lbb.ifNotNull("thrown", cbb -> {
                                                            cbb.log("Thrown in extracting", Level.FINER)
                                                                    .statement(UTIL_EXCEPTIONS.simpleName() + ".printStackTrace(thrown)")
                                                                    .statement("return");
                                                        });
                                                        lbb.simpleLoop(NAME_REFERENCE_SET_KEY.parametrizedName("?"), "key")
                                                                .over("KEYS", loopBody -> {

                                                                    loopBody.declare("regions").initializedByInvoking("references")
                                                                            .withArgument("key")
                                                                            .on("extraction").as("NamedRegionReferenceSets<?>");

                                                                    // NamedSemanticRegionReference<?> set = x.at( startPos );
                                                                    loopBody.declare("set").initializedByInvoking("at")
                                                                            .withArgument("startPos")
                                                                            .on("regions").as(NAMED_SEMANTIC_REGION_REFERENCE.parametrizedName("?"));

                                                                    loopBody.log(Level.FINE)
                                                                            .argument("startPos")
                                                                            .argument("key")
                                                                            .argument("set")
                                                                            .logging("Start {0} for {1} gets {2}");

                                                                    loopBody.ifNotNull("set")
                                                                            .log(Level.FINER)
                                                                            .argument("set")
                                                                            .argument("set.referencing()")
                                                                            .argument("set.referencing().start()")
                                                                            .logging("Found ref {0} navigating to {1} at {2}")
                                                                            .statement("result[0] = set.referencing().start()");
                                                                }).endBlock();

                                                    });
                                        })
                                        .on(NB_ANTLR_UTILS.simpleName())
                                        .returning("result[0]")
                                        .endBlock();

                            });
                }).override("token", mb -> {
            mb.withModifier(PUBLIC).returning("boolean")
                    .addArgument(TOKEN_ID.simpleName(), "tokenId")
                    .addArgument(TOKEN_CONTEXT_PATH.simpleName(), "path")
                    .addArgument("int", "tokenBufferOffset")
                    .addArgument("int", "tokenLength")
                    .body().returning("true").endBlock();
            ;

        }).override("nextBuffer", mb -> {
            mb.withModifier(PUBLIC)
                    .addArgument("char[]", "buffer")
                    .addArgument("int", "offset")
                    .addArgument("int", "length")
                    .addArgument("int", "startPos")
                    .addArgument("int", "preScan")
                    .addArgument("boolean", "lastBuffer")
                    .emptyBody();
        }).override("eot").withModifier(PUBLIC)
                .addArgument("int", "offset").returning("int").body().returning(0).endBlock();
        return cb;
    }

    private ClassBuilder<String> generateCustomGotoDeclarationAction(String mimeType, Set<String> value) {
        log("Generate one: {0} with {1}", mimeType, value);
        String lexerFqn = lexerForMimeType.containsKey(mimeType)
                ? lexerForMimeType.get(mimeType).lexerClassFqn() : null;
        String prefix = NameAndMimeTypeUtils.prefixFromMimeTypeOrLexerName(mimeType, lexerFqn);
        String gotoDeclarationActionClassName = prefix
                + "GotoDeclarationAction";
        ClassBuilder<String> cb = ClassBuilder.forPackage(dataObjectPackageForMimeType(mimeType))
                .named(gotoDeclarationActionClassName)
                .withModifier(PUBLIC, FINAL)
                .importing(ABSTRACT_EDITOR_ACTION.qname(),
                        NAME_REFERENCE_SET_KEY.qname(),
                        NAMED_SEMANTIC_REGION_REFERENCE.qname(),
                        NAMED_REGION_REFERENCE_SETS.qname(),
                        ACTION.qname(),
                        EDITOR_ACTION_REGISTRATION.qname(),
                        NB_ANTLR_UTILS.qname()
                )
//                .staticBlock(lb -> {
//                    lb.statement("LOGGER.setLevel(Level.ALL)").endBlock();
//                })
                .field("KEYS", fb -> {
                    fb.withModifier(PRIVATE, STATIC, FINAL)
                            .initializedAsArrayLiteral(NAME_REFERENCE_SET_KEY.parametrizedName("?"), ab -> {
                                value.forEach(ab::add);
                                ab.closeArrayLiteral();
                            });
                })
                /*.field("ACTION", fb -> {
            fb.withModifier(PUBLIC, STATIC, FINAL)
                    .initializedFromInvocationOf("createGotoDeclarationAction", ib -> {
                        ib.withArgument("KEYS").on(NB_ANTLR_UTILS.simpleName());
                    }).ofType(ABSTRACT_EDITOR_ACTION.simpleName());
        })*/.method("action", mb -> {
                    mb
                            /*
                    .annotatedWith(EDITOR_ACTION_REGISTRATION.simpleName(), ab -> {
                ab.addArgument("name", "goto-declaration")
                        .addArgument("mimeType", mimeType)
                        .addArgument("category", "Refactoring")
                        .addArgument("menuPath", "Menu/Refactoring")
                        .addArgument("menuPosition", 10)
                        .addArgument("noIconInMenu", true)
                        .addArgument("noKeyBinding", false)
                        .addArgument("popupPosition", 10);
            }).annotatedWith(MESSAGES.qname(), ab -> {
                ab.addArgument("value", "goto-declaration=&Go to Declaration");
            })
                             */
                            .withModifier(PUBLIC, STATIC).returning(ACTION.simpleName())
                            .body(bb -> {
                                bb.log("Create a " + mimeType + " inplace editor action", Level.FINEST);
                                bb.debugLog("Create " + mimeType + " inplace editor action");
                                bb.declare("result").initializedByInvoking("createGotoDeclarationAction")
                                        .withStringLiteral(mimeType)
                                        .withArgument("KEYS").on(NB_ANTLR_UTILS.simpleName())
                                        .as(ABSTRACT_EDITOR_ACTION.simpleName());
                                bb.log("Returning {0}", Level.FINEST, "result");
                                bb.returning("result");
                            });
//                    .bodyReturning("ACTION");
                });
        /*
    @EditorActionRegistration(name = "goto-declaration",
        mimeType = "text/x-g4", popupPosition = 10,
            category = "Antlr",
        menuPath="Menu/Refactoring", menuPosition = 10)

         */

        Element[] els = setsFor(value).toArray(new Element[0]);
        try {
            LayerBuilder layer = layer(els);
            String layerPath = "Editors/" + mimeType + "/Actions/"
                    + "goto-declaration.instance";
            layer.file(layerPath)
                    .methodvalue("instanceCreate", cb.fqn(), "action")
                    .stringvalue("instanceClass", "org.nemesis.antlr.spi.language.AntlrGotoDeclarationAction")
                    .stringvalue("instanceOf", ACTION.qname())
                    .stringvalue("instanceOf", ABSTRACT_EDITOR_ACTION.qname())
                    .stringvalue("instanceOf", TEXT_ACTION.qname())
                    .stringvalue("Name", "goto-declaration")
                    .intvalue("position", gotoCount++)
                    .write();
            return cb;
        } catch (Exception ex) {
            ex.printStackTrace(System.err);
            return Exceptions.chuck(ex);
        }
    }
    static volatile int gotoCount = 1;
}

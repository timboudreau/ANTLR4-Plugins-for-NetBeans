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

import com.mastfrog.annotation.AnnotationUtils;
import static com.mastfrog.annotation.AnnotationUtils.capitalize;
import static com.mastfrog.annotation.AnnotationUtils.simpleName;
import com.mastfrog.annotation.processor.Delegate;
import com.mastfrog.java.vogon.ClassBuilder;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import static javax.lang.model.element.Modifier.PUBLIC;
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import static org.nemesis.registration.NameAndMimeTypeUtils.cleanMimeType;
import static org.nemesis.registration.NameAndMimeTypeUtils.displayNameAsVarName;
import static org.nemesis.registration.NameAndMimeTypeUtils.prefixFromMimeTypeOrLexerName;
import static org.nemesis.registration.typenames.JdkTypes.CARET;
import static org.nemesis.registration.typenames.JdkTypes.DOCUMENT;
import static org.nemesis.registration.typenames.JdkTypes.POSITION;
import static org.nemesis.registration.typenames.KnownTypes.ABSTRACT_REFACTORING;
import static org.nemesis.registration.typenames.KnownTypes.ACTION_ID;
import static org.nemesis.registration.typenames.KnownTypes.ACTION_REFERENCE;
import static org.nemesis.registration.typenames.KnownTypes.ACTION_REFERENCES;
import static org.nemesis.registration.typenames.KnownTypes.ACTION_REGISTRATION;
import static org.nemesis.registration.typenames.KnownTypes.ACTION_STATE;
import static org.nemesis.registration.typenames.KnownTypes.CLONEABLE_EDITOR_SUPPORT;
import static org.nemesis.registration.typenames.KnownTypes.CUSTOM_REFACTORING;
import static org.nemesis.registration.typenames.KnownTypes.DATA_OBJECT;
import static org.nemesis.registration.typenames.KnownTypes.EDITOR_REGISTRY;
import static org.nemesis.registration.typenames.KnownTypes.EXTRACTION;
import static org.nemesis.registration.typenames.KnownTypes.GENERIC_REFACTORING_CONTEXT_ACTION;
import static org.nemesis.registration.typenames.KnownTypes.LEXER_TOKEN;
import static org.nemesis.registration.typenames.KnownTypes.LOOKUP;
import static org.nemesis.registration.typenames.KnownTypes.LOOKUPS;
import static org.nemesis.registration.typenames.KnownTypes.MIME_REGISTRATION;
import static org.nemesis.registration.typenames.KnownTypes.NB_BUNDLE;
import static org.nemesis.registration.typenames.KnownTypes.NB_EDITOR_UTILITIES;
import static org.nemesis.registration.typenames.KnownTypes.POSITION_BOUNDS;
import static org.nemesis.registration.typenames.KnownTypes.POSITION_REF;
import static org.nemesis.registration.typenames.KnownTypes.PROXY_LOOKUP;
import static org.nemesis.registration.typenames.KnownTypes.REFACTORING_PLUGIN;
import static org.nemesis.registration.typenames.KnownTypes.REFACTORING_UI;
import static org.nemesis.registration.typenames.KnownTypes.TOKEN_SEQUENCE;
import static org.nemesis.registration.typenames.KnownTypes.UI;

/**
 *
 * @author Tim Boudreau
 */
public class CustomRefactoringDelegate extends Delegate {

    public static final String CUSTOM_REFACTORING_ANNO = "org.nemesis.antlr.refactoring.CustomRefactoringRegistration";
    private BiPredicate<? super AnnotationMirror, ? super Element> test;

    @Override
    protected void onInit(ProcessingEnvironment env, AnnotationUtils utils) {
        test = utils.multiAnnotations().whereAnnotationType(CUSTOM_REFACTORING_ANNO, mb -> {
            mb.testMember("enabledOnTokens", mtb -> {
                mtb.arrayValueMayNotBeEmpty().intValueMustBeGreaterThan(0);
            }).testMember("mimeType").addPredicate("Mime type", mir -> {
                Predicate<String> mimeTest = NameAndMimeTypeUtils.complexMimeTypeValidator(true, utils(), null, mir);
                String value = utils().annotationValue(mir, "mimeType", String.class);
                return mimeTest.test(value);
            })
                    .build().testMember("name").stringValueMustNotBeEmpty().build()
                    .testMember("plugin", plug -> {
                        plug.asTypeSpecifier(tetb -> {
                            tetb.mustBeFullyReifiedType()
                                    .isSubTypeOf(REFACTORING_PLUGIN.qnameNotouch())
                                    .nestingKindMayNotBe(NestingKind.LOCAL)
                                    .addPredicate("Constructor arguments of plugin incorrect", type -> {
                                        List<TypeMirror> types = constructorArguments(type, false);
                                        if (types == null) {
                                            return false;
                                        }
                                        if (types.size() < 2) {
                                            return false;
                                        }

                                        return true;
                                    });
                        });
                    });
        }).build();
    }

    @Override
    protected boolean validateAnnotationMirror(AnnotationMirror mirror, ElementKind kind, Element element) {
        return test.test(mirror, element);
    }

    @Override
    protected boolean processFieldAnnotation(VariableElement var, AnnotationMirror mirror, RoundEnvironment roundEnv) throws Exception {
        String mimeType = utils().annotationValue(mirror, "mimeType", String.class);
        LexerProxy lexer = LexerProxy.create(mirror, var, utils());
        String prefix = prefixFromMimeTypeOrLexerName(mimeType, lexer.lexerClassFqn());
        mimeType = cleanMimeType(mimeType);

        String myPackage = utils().packageName(var);
        String hierPkg = utils().annotationValue(mirror, "languageHierarchyPackage", String.class, myPackage);

        generate(mimeType, var, mirror, lexer, prefix, myPackage, processingEnv, hierPkg);
        return false;
    }

    private List<TypeMirror> constructorArguments(TypeElement what, boolean mustBePublic) {
        for (Element enc : what.getEnclosedElements()) {
            switch (enc.getKind()) {
                case CONSTRUCTOR:
                    ExecutableElement con = (ExecutableElement) enc;
                    boolean isPublic = con.getModifiers().contains(Modifier.PUBLIC);
                    boolean testIt = mustBePublic ? isPublic : true;
                    if (testIt) {
                        List<? extends VariableElement> params = con.getParameters();
                        List<TypeMirror> result = new ArrayList<>(params.size());
                        for (VariableElement va  : params) {
                            result.add(va.asType());
                        }
                        return result;
                    }
            }
        }
        return null;
    }

    private void generate(String mimeType, VariableElement var, AnnotationMirror mirror,
            LexerProxy lexer, String prefix, String targetPackage, ProcessingEnvironment processingEnv,
            String languageSupportPackage) throws IOException {
        Set<Integer> tokens = new TreeSet<>(utils().annotationValues(mirror, "enabledOnTokens", Integer.class));
        if (tokens.isEmpty()) {
            utils().fail("Tokens list to activate refactoring is empty", var, mirror);
            return;
        }
        boolean publicPlugin = utils().annotationValue(mirror, "publicRefactoringPluginClass", Boolean.class, Boolean.FALSE);
        String refactoringDisplayName = utils().annotationValue(mirror, "name", String.class);

        String displayNameAsVarName = displayNameAsVarName(refactoringDisplayName);
        String refactoringDescription = utils().annotationValue(mirror, "description", String.class, refactoringDisplayName);

        TypeMirror refactoringTypeName = utils().typeForSingleClassAnnotationMember(mirror, "refactoring");
        TypeMirror refactoringPluginTypeName = utils().typeForSingleClassAnnotationMember(mirror, "plugin");
        TypeMirror refactoringUITypeName = utils().typeForSingleClassAnnotationMember(mirror, "ui");

        TypeElement refactoringPluginTypeElement = processingEnv.getElementUtils().getTypeElement(refactoringPluginTypeName.toString());

        String refactoringClassName = refactoringTypeName == null
                ? capitalize(prefix) + displayNameAsVarName + "Refactoring"
                : refactoringTypeName.toString();

        String refactoringConstructorArgumentTypeName = refactoringTypeName == null
                ? ABSTRACT_REFACTORING.qname() : refactoringClassName;

        String refactoringClassNameSimple = simpleName(refactoringClassName);

        String refactoringPackage = utils().packageName(refactoringPluginTypeElement);
        boolean constructorMustBePublic = !refactoringPackage.equals(targetPackage);

        List<TypeMirror> pluginArguments = constructorArguments(refactoringPluginTypeElement, constructorMustBePublic);
        if (pluginArguments == null) {
            utils().fail("Could not find a public constructor to use on " + refactoringPluginTypeName
                    + ". There should be one that takes AbstractRefactoring (or the type you specified), "
                    + " Extraction, and PositionBounds in some order", var, mirror);
        }

        String uiClassName = refactoringUITypeName == null ? (targetPackage + "." + displayNameAsVarName + "UI")
                : refactoringUITypeName.toString();

        String uiClassNameSimple = simpleName(uiClassName);

        String customRefactoringFactoryTypeName = prefix + displayNameAsVarName + "CustomRefactoring";

        ClassBuilder<String> customRefactoringFactory = ClassBuilder.forPackage(targetPackage)
                .named(customRefactoringFactoryTypeName).withModifier(Modifier.FINAL, Modifier.PUBLIC)
                .importing(MIME_REGISTRATION.qname(), CUSTOM_REFACTORING.qname(),
                        EXTRACTION.qname(), POSITION_BOUNDS.qname()).conditionally(publicPlugin,
                cbb -> cbb.withModifier(PUBLIC))
                .conditionally(refactoringTypeName != null, cbb -> cbb.importing(refactoringClassName))
                .conditionally(constructorMustBePublic, cbb -> cbb.importing(refactoringPluginTypeName.toString()))
                .conditionally(refactoringConstructorArgumentTypeName.equals(ABSTRACT_REFACTORING.qname()),
                        cbb -> cbb.importing(ABSTRACT_REFACTORING.qname()))
                .extending(CUSTOM_REFACTORING.parametrizedName(refactoringClassNameSimple))
                .annotatedWith(MIME_REGISTRATION.simpleName(), ab -> {
                    ab.addArgument("mimeType", mimeType)
                            .addArgument("position", Math.abs(customRefactoringFactoryTypeName.hashCode()))
                            .addClassArgument("service", CUSTOM_REFACTORING.simpleName());
                })
                .constructor(con -> {
                    con.setModifier(PUBLIC)
                            .body().invoke("super").withClassArgument(refactoringClassNameSimple)
                            .inScope().endBlock();
                })
                .overridePublic("apply", mb -> {
                    mb.returning(refactoringPluginTypeName.toString())
                            .addArgument(refactoringClassNameSimple, "refactoring")
                            .addArgument(EXTRACTION.simpleName(), "extraction")
                            .addArgument(POSITION_BOUNDS.simpleName(), "bounds")
                            .body(bb -> {
                                bb.returningNew(nb -> {
                                    List<TypeMirror> remaining = new ArrayList<>(pluginArguments);
                                    for (TypeMirror mir : pluginArguments) {
                                        String typeName = mir.toString();
                                        boolean found = false;
                                        if (typeName.equals(refactoringConstructorArgumentTypeName)) {
                                            nb.withArgument("refactoring");
                                            found = true;
                                        } else if (typeName.equals(ABSTRACT_REFACTORING.qname())) {
                                            nb.withArgument("refactoring");
                                            found = true;
                                        } else if (typeName.equals(refactoringClassNameSimple)) {
                                            nb.withArgument("refactoring");
                                            found = true;
                                        } else if (EXTRACTION.qname().equals(typeName)) {
                                            nb.withArgument("extraction");
                                            found = true;
                                        } else if (POSITION_BOUNDS.qname().equals(typeName)) {
                                            nb.withArgument("bounds");
                                            found = true;
                                        }
                                        if (found) {
                                            remaining.remove(mir);
                                        }
                                    }
                                    if (!remaining.isEmpty()) {
                                        utils().fail("Don't know how to find an instance to "
                                                + "pass to the constructor of " + refactoringPluginTypeName
                                                + " of the following types: " + remaining, var, mirror);
                                    }
                                    nb.ofType(refactoringPluginTypeName.toString());
                                });
                            });
                });
        ClassBuilder<String> generatedRefactoring = null;
        String varQn = AnnotationUtils.enclosingType(var).getQualifiedName()
                + "." + var.getSimpleName();
        if (refactoringTypeName == null) {
            generatedRefactoring = ClassBuilder.forPackage(targetPackage)
                    .named(refactoringClassName)
                    .importing(ABSTRACT_REFACTORING.qname(), LOOKUP.qname())
                    .staticImport(varQn)
//                    .importing("static " + varQn)
                    .extending(ABSTRACT_REFACTORING.simpleName())
                    .constructor(cb -> {
                        cb.setModifier(Modifier.PUBLIC).addArgument(LOOKUP.simpleName(), "lookup")
                                .body().invoke("super").withArgument("lookup").inScope()
                                .invoke("add").withArgument(simpleName(varQn))
                                .onInvocationOf("getContext").inScope().endBlock();
                    });
        }
        String refactoringActionName = customRefactoringFactoryTypeName + "Action";
        String nameBundleKey = "CTL_" + displayNameAsVarName;
        String descBundleKey = "DESC_" + displayNameAsVarName;
        int actionPos = utils().annotationValue(mirror, "actionPosition", Integer.class, varQn.hashCode());
        String keybinding = utils().annotationValue(mirror, "keybinding", String.class);
        ClassBuilder<String> refactoringAction = ClassBuilder.forPackage(targetPackage)
                .named(refactoringActionName)
                .importing(GENERIC_REFACTORING_CONTEXT_ACTION.qname(),
                        ACTION_ID.qname(), ACTION_REGISTRATION.qname(),
                        ACTION_STATE.qname(), ACTION_REFERENCES.qname(),
                        ACTION_REFERENCE.qname(), NB_BUNDLE.qname(),
                        CARET.qname(), CLONEABLE_EDITOR_SUPPORT.qname(),
                        TOKEN_SEQUENCE.qname(), LEXER_TOKEN.qname(),
                        DOCUMENT.qname(), DATA_OBJECT.qname(), POSITION.qname(),
                        POSITION_REF.qname(), POSITION_BOUNDS.qname(),
                        NB_EDITOR_UTILITIES.qname(), LOOKUP.qname(), LOOKUPS.qname(),
                        PROXY_LOOKUP.qname(), REFACTORING_UI.qname(),
                        UI.qname(), EDITOR_REGISTRY.qname(), CUSTOM_REFACTORING.qname(),
                        lexer.lexerClassFqn())
                .conditionally(refactoringTypeName != null, cbb -> {
                    cbb.importing(refactoringTypeName.toString());
                })
                .withModifier(Modifier.FINAL, Modifier.PUBLIC)
                .extending(GENERIC_REFACTORING_CONTEXT_ACTION.parametrizedName(prefix + "Token"))
                .annotatedWith(ACTION_ID.simpleName(), ab -> {
                    ab.addArgument("category", "Refactoring")
                            .addArgument("id", targetPackage + "." + refactoringActionName);
                }).annotatedWith(ACTION_REGISTRATION.simpleName(), ab -> {
            ab.addArgument("displayName", "#" + nameBundleKey)
                    .addAnnotationArgument("enabledOn", ACTION_STATE.simpleName(), aab -> {
                        aab.addArgument("useActionInstance", true);
                    });
        }).annotatedWith(ACTION_REFERENCES.simpleName(), ab -> {
            ab.addArrayArgument("value", avb -> {
                avb.annotation(ACTION_REFERENCE.simpleName())
                        .addArgument("path", "Editors/" + mimeType + "/RefactoringActions")
                        .addArgument("name", displayNameAsVarName)
                        .addArgument("position", actionPos)
                        .closeAnnotation();
                avb.annotation(ACTION_REFERENCE.simpleName())
                        .addArgument("path", "Editors/" + mimeType + "/Popup")
                        .addArgument("name", displayNameAsVarName)
                        .addArgument("position", actionPos)
                        .closeAnnotation();
                if (keybinding != null) {
                    avb.annotation(ACTION_REFERENCE.simpleName())
                            .addArgument("path", "Shortcuts")
                            .addArgument("name", keybinding)
                            .addArgument("position", actionPos)
                            .closeAnnotation();
                }
            });
        }).annotatedWith("NbBundle.Messages", ab -> {
            ab.addArgument("value", nameBundleKey + "=" + refactoringDisplayName);
        }).constructor(con -> {
            con.setModifier(PUBLIC).body(bb -> {
                bb.invoke("super").withArgument(prefix + "Hierarchy::" + prefix.toLowerCase() + "Language").inScope();
                bb.invoke("putValue").withArgument("NAME").withArgument("Bundle." + nameBundleKey + "()").inScope();
            });
        }).conditionally(refactoringUITypeName != null, cbb -> {
            cbb.importing(refactoringUITypeName.toString());
        }).conditionally(!languageSupportPackage.equals(targetPackage), cbb -> {
            cbb.importing(languageSupportPackage + "." + prefix + "Hierarchy");
        }).overrideProtected("isEnabled", mb -> {
            mb.returning("boolean")
                    .addArgument(CLONEABLE_EDITOR_SUPPORT.simpleName(), "doc")
                    .addArgument(CARET.simpleName(), "caret")
                    .addArgument(TOKEN_SEQUENCE.parametrizedName(prefix + "Token"), "seq")
                    .addArgument("int", "caretPosition")
                    .addArgument(LEXER_TOKEN.parametrizedName(prefix + "Token"), "tok")
                    .body().switchingOn("tok.id().ordinal()", sw -> {
                        for (Integer tok : tokens) {
                            String field = lexer.tokenFieldReference(tok);
                            sw.inCase(field).returning("true").endBlock();
                        }
                        sw.inDefaultCase().returning("false").endBlock();
                    }).endBlock();
        }).overrideProtected("perform", mb -> {
            mb.annotatedWith("NbBundle.Messages", ab -> {
                ab.addArgument("value", descBundleKey + "=" + refactoringDescription);
            });
            mb.addArgument(CLONEABLE_EDITOR_SUPPORT.simpleName(), "context")
                    .addArgument(CARET.simpleName(), "caret")
                    .addArgument(TOKEN_SEQUENCE.parametrizedName(prefix + "Token"), "seq")
                    .addArgument("int", "caretPosition")
                    .addArgument(LEXER_TOKEN.parametrizedName(prefix + "Token"), "tok")
                    .body(bb -> {
                        bb.declare("doc").initializedByInvoking("getDocument").on("context")
                                .as(DOCUMENT.simpleName());
                        bb.declare("dot").initializedByInvoking("getDot").on("caret").as("int");
                        bb.declare("mark").initializedByInvoking("getMark").on("caret").as("int");
                        bb.declare("start").initializedByInvoking("createPositionRef")
                                .withArgumentFromInvoking("min").withArgument("dot")
                                .withArgument("mark").on("Math")
                                .withArgumentFromField("Forward").of("Position.Bias")
                                .on("context").as(POSITION_REF.simpleName());
                        bb.declare("end").initializedByInvoking("createPositionRef")
                                .withArgumentFromInvoking("max").withArgument("dot")
                                .withArgument("mark").on("Math").withArgumentFromField("Backward")
                                .of("Position.Bias")
                                .on("context").as(POSITION_REF.simpleName());

                        bb.declare("bounds")
                                .initializedWithNew(nb -> {
                                    nb.withArgument("start").withArgument("end")
                                            .ofType(POSITION_BOUNDS.simpleName());
                                }).as(POSITION_BOUNDS.simpleName());
                        bb.declare("dob").initializedByInvoking("getDataObject")
                                .withArgument("doc").on(NB_EDITOR_UTILITIES.simpleName())
                                .as(DATA_OBJECT.simpleName());

                        bb.declare("lookup").initializedWithNew(nb -> {
                            nb.withArgumentFromInvoking("getLookup").on("dob")
                                    .withArgumentFromInvoking("fixed", ib -> {
                                        ib.withArgumentFromInvoking("getPrimaryFile").on("dob")
                                                .withArgument("context")
                                                .withArgument("bounds")
                                                .on(LOOKUPS.simpleName());
                                    })
                                    .ofType(PROXY_LOOKUP.simpleName());
                        }).as(LOOKUP.simpleName());

                        bb.declare("refactoring")
                                .initializedWithNew(nb -> {
                                    nb.withArgument("lookup")
                                            .ofType(refactoringClassNameSimple);
                                }).as(refactoringClassNameSimple);
                        if (refactoringUITypeName != null) {
                            bb.declare("ui")
                                    .initializedWithNew(nb -> {
                                        nb.withArgument("refactoring")
                                                .ofType(uiClassNameSimple);
                                    }).as(REFACTORING_UI.simpleName());
                        } else {
                            bb.declare("desc").initializedByInvoking(descBundleKey).on("Bundle").as("String");
                            bb.declare("rname").initializedByInvoking(nameBundleKey).on("Bundle").as("String");
                            bb.declare("ui").initializedByInvoking("createDummyRefactoringUI")
                                    .withArgument("refactoring")
                                    .withArgument("rname")
                                    .withArgument("desc")
                                    .on(CUSTOM_REFACTORING.simpleName())
                                    .as(REFACTORING_UI.simpleName());
                        }
                        bb.invoke("openRefactoringUI")
                                .withArgument("ui")
                                .withArgumentFromInvoking("getOuterTopComponent")
                                .withArgumentFromInvoking("findComponent").withArgument("doc").on(EDITOR_REGISTRY.simpleName())
                                .on(NB_EDITOR_UTILITIES.simpleName())
                                .on(UI.simpleName());
                    });
        });
        writeOne(customRefactoringFactory);
        if (generatedRefactoring != null) {
            writeOne(generatedRefactoring);
        }
        writeOne(refactoringAction);
    }

//    class TaskImpl implements LexerParserTasks.LexerParserTask {
//
//        private final String mimeType;
//        private final VariableElement var;
//        private final AnnotationMirror mirror;
//        private final ProcessingEnvironment env;
//
//        public TaskImpl(String mimeType, VariableElement var, AnnotationMirror mirror, ProcessingEnvironment env) {
//            this.mimeType = mimeType;
//            this.var = var;
//            this.mirror = mirror;
//            this.env = env;
//        }
//
//        @Override
//        public void invoke(LexerProxy lexer, String prefix, String targetPackage) {
//            try {
//                CustomRefactoringDelegate.this.generate(mimeType, var, mirror, lexer, prefix, targetPackage, env);
//            } catch (Exception ex) {
//                ex.printStackTrace(System.out);
//                ex.printStackTrace(System.err);
//                utils().fail(ex.toString(), var, mirror);
//            }
//        }
//    }
}

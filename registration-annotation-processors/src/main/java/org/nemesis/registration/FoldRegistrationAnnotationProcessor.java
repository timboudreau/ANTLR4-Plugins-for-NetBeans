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
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import static javax.lang.model.type.TypeKind.DECLARED;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import com.mastfrog.java.vogon.ClassBuilder;
import com.mastfrog.annotation.processor.LayerGeneratingDelegate;
import static org.nemesis.registration.FoldRegistrationAnnotationProcessor.FOLD_REGISTRATION_ANNO;
import static org.nemesis.registration.NameAndMimeTypeUtils.cleanMimeType;
import static org.nemesis.registration.typenames.JdkTypes.COLLECTION;
import static org.nemesis.registration.typenames.JdkTypes.COLLECTIONS;
import static org.nemesis.registration.typenames.KnownTypes.FOLD_MANAGER_FACTORY;
import static org.nemesis.registration.typenames.KnownTypes.FOLD_TEMPLATE;
import static org.nemesis.registration.typenames.KnownTypes.FOLD_TYPE;
import static org.nemesis.registration.typenames.KnownTypes.FOLD_TYPE_PROVIDER;
import static org.nemesis.registration.typenames.KnownTypes.KEY_TO_FOLD_CONVERTER;
import static org.nemesis.registration.typenames.KnownTypes.MIME_REGISTRATION;
import static org.nemesis.registration.typenames.KnownTypes.NAMED_REGION_KEY;
import static org.nemesis.registration.typenames.KnownTypes.NAMED_SEMANTIC_REGION;
import static org.nemesis.registration.typenames.KnownTypes.NAMED_SEMANTIC_REGION_REFERENCE;
import static org.nemesis.registration.typenames.KnownTypes.REGIONS_KEY;
import static org.nemesis.registration.typenames.KnownTypes.SEMANTIC_REGION;
import static org.nemesis.registration.typenames.KnownTypes.TASK_FACTORY;

/**
 *
 * @author Tim Boudreau
 */
//@ServiceProvider(service = Processor.class)
@SupportedAnnotationTypes(FOLD_REGISTRATION_ANNO)
//@SupportedOptions(AU_LOG)
public class FoldRegistrationAnnotationProcessor extends LayerGeneratingDelegate {

    public static final String PKG = "org.nemesis.antlr.fold";
    public static final String FOLD_REGISTRATION_ANNO = PKG + ".AntlrFoldsRegistration";

    private Predicate<? super Element> variableElementTest;
    private Predicate<? super AnnotationMirror> annotationMirrorTest;

    @Override
    protected void onInit(ProcessingEnvironment env, AnnotationUtils utils) {
        variableElementTest = utils().testsBuilder()
                .isSubTypeOf(REGIONS_KEY.qnameNotouch(), NAMED_REGION_KEY.qnameNotouch())
                .doesNotHaveModifier(Modifier.PRIVATE)
                .hasModifier(Modifier.STATIC)
                .testElementAsType().typeKindMustBe(DECLARED)
                .build()
                //                .addPredicate(this::typeHasTypeParameter)
                .testContainingClass().doesNotHaveModifier(Modifier.PRIVATE)
                .build()
                .build();

        annotationMirrorTest = utils().testMirror().testMember("mimeType")
                .addPredicate("Mime type", mir -> {
                    Predicate<String> mimeTest = NameAndMimeTypeUtils.complexMimeTypeValidator(true, utils(), null, mir);
                    String value = utils().annotationValue(mir, "mimeType", String.class);
                    return mimeTest.test(value);
                })
                .build().build();
    }

    @Override
    protected boolean validateAnnotationMirror(AnnotationMirror mirror, ElementKind kind, Element el) {
        assert annotationMirrorTest != null;
        boolean result = annotationMirrorTest.test(mirror);
        return result;
    }

    boolean typeHasTypeParameter(VariableElement el) {
        TypeMirror type = el.asType();
        if (type.getKind() != TypeKind.DECLARED) {
            utils().fail("Must be a decleared type", el);
            return false;
        }

        List<? extends TypeMirror> args;
        switch (type.getKind()) {
            case DECLARED:
                DeclaredType tp = (DeclaredType) type;
                args = tp.getTypeArguments();
                if (args == null || args.isEmpty()) {
                    utils().fail("Must have a type parameter, not a raw type", el);
                    return false;
                }
                break;

            default:
                utils().fail("Wrong type kind for type parameter: " + type.getKind() + " for " + type);
                return false;
        }

        TypeMirror paramTypeMirror = args.get(0);
        switch (paramTypeMirror.getKind()) {
            case DECLARED:
                break;
            default:
                utils().fail("Type parameters of kind " + type.getKind()
                        + " not supported. Use a concrete, specific type.", el);
                return false;
        }
        return true;
    }

    @Override
    protected boolean processFieldAnnotation(VariableElement field, AnnotationMirror mirror, RoundEnvironment roundEnv) throws Exception {
        boolean valid = variableElementTest.test(field) && checkTypeParameters(field, mirror);
        if (valid) {
            generateCode(mirror, field);
        }
        return true;
    }

    private boolean checkTypeParameters(VariableElement field, AnnotationMirror mirror) {
        // Ensure that if we're annotating a RegionKey<Foo> and the converter is specified,
        // that it is a SemanticRegionToFoldConverter<Foo> or something assingnable to Foo
        TypeMirror fieldType = field.asType();
        if (fieldType.getKind() != DECLARED) {
            utils().fail("Not a declared type " + fieldType, field, mirror);
            return false;
        }
        DeclaredType declaredFieldType = (DeclaredType) fieldType;

        TypeMirror type = utils().typeForSingleClassAnnotationMember(mirror, "converter");
        if (type == null) { // using the default expression
            return true;
        }
        TypeMirror parameterizedOn = null;
        TypeElement semRegionType = processingEnv.getElementUtils().getTypeElement(KEY_TO_FOLD_CONVERTER.qnameNotouch());
        TypeMirror semRegionTypeErased = utils().erasureOf(semRegionType.asType());
        List<? extends TypeMirror> supers = processingEnv.getTypeUtils().directSupertypes(type);
        for (TypeMirror parent : supers) {
            if (parent instanceof DeclaredType) {
                DeclaredType dt = (DeclaredType) parent;
                List<? extends TypeMirror> args = dt.getTypeArguments();
                if (!dt.getTypeArguments().isEmpty()) {
                    TypeMirror eras = processingEnv.getTypeUtils().erasure(dt);
                    if (semRegionTypeErased.equals(eras)) {
                        parameterizedOn = args.get(0);
                        break;
                    }
                }
            }
        }

        List<? extends TypeMirror> fieldTypeArgs = declaredFieldType.getTypeArguments();
        if (!fieldTypeArgs.isEmpty() && parameterizedOn != null) {
            TypeMirror fieldParameterizedOn = fieldTypeArgs.get(0);
            if (!processingEnv.getTypeUtils().isAssignable(parameterizedOn, fieldTypeArgs.get(0))) {
                utils().fail("Incompatible types: " + parameterizedOn + " and " + fieldParameterizedOn, field, mirror);
                return false;
            }
        }
        return true;
    }

    private int pos = 0;

    private void generateCode(AnnotationMirror mirror, VariableElement field) throws IOException {
        TypeElement encType = AnnotationUtils.enclosingType(field);
        PackageElement pkgEl = processingEnv.getElementUtils().getPackageOf(encType);

        String mime = cleanMimeType(utils().annotationValue(mirror, "mimeType", String.class));

        String genClassName = encType.getSimpleName().toString() + "__"
                + field.getSimpleName().toString() + "__" + "FoldRegistration";

        processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, "Generating " + genClassName
                + " in " + pkgEl.getQualifiedName() + " for fold registration annotation", field);

        String fieldFqn = encType.getQualifiedName() + "." + field.getSimpleName();

        Iterator<String> tps = utils().typeList(mirror, "converter").iterator();
        boolean useDefaultConverter = !tps.hasNext();

        String converterType = (!useDefaultConverter ? tps.next()
                : KEY_TO_FOLD_CONVERTER.simpleName())
                .replace('$', '.');

        TypeMirror fieldParamaterType = utils().firstTypeParameter(field);
        String paramType;
        if (fieldParamaterType == null) {
            utils().fail("Could not find a parameter type on " + field);
            return;
        } else {
            String nm = fieldParamaterType.toString().replace('$', '.');
            if (nm.startsWith("java.lang")) {
                int ix = nm.lastIndexOf('.');
                nm = nm.substring(ix + 1);
            }
            paramType = nm;
        }

        String fqn = pkgEl.getQualifiedName() + "." + genClassName;
        int layerPos = utils().annotationValue(mirror, "order", Integer.class, pos);

        TypeMirror fieldRawType = processingEnv.getTypeUtils().erasure(field.asType());
        String itemType;
        switch (fieldRawType.toString()) {
            case "org.nemesis.extraction.key.RegionsKey":
                itemType = SEMANTIC_REGION.qname();
//                itemType = "org.nemesis.data.SemanticRegion";
                break;
            case "org.nemesis.extraction.key.NamedRegionKey":
//                itemType = "org.nemesis.data.named.NamedSemanticRegion";
                itemType = NAMED_SEMANTIC_REGION.qname();
                break;
            case "org.nemesis.extraction.key.NameReferenceSetKey":
//                itemType = "org.nemesis.data.named.NamedSemanticRegionReference";
                itemType = NAMED_SEMANTIC_REGION_REFERENCE.qname();
                break;
            default:
                utils().fail("Unknown field type " + fieldRawType + " - cannot generate code");
                return;
        }

        String itemTypeSimple = simpleName(itemType);

        VariableElement foldType = utils().annotationValue(mirror, "foldType", VariableElement.class);
        String foldTypeName = foldType == null ? null : foldType.getSimpleName().toString();

        AnnotationMirror foldSpecAnno = utils().annotationValue(mirror, "foldSpec", AnnotationMirror.class);
        FoldSpecInfo spec = foldSpecAnno == null ? null : generateFoldTypeSpec(foldSpecAnno, field, mime, layerPos);

        ClassBuilder<String> cb = ClassBuilder.forPackage(pkgEl.getQualifiedName())
                .named(genClassName)
                .importing(
                        //                        "javax.annotation.processing.Generated",
                        TASK_FACTORY.qname(),
                        KEY_TO_FOLD_CONVERTER.qname(),
                        MIME_REGISTRATION.qname(), itemType,
                        FOLD_MANAGER_FACTORY.qname())
                //                .annotatedWith("Generated").addArgument("value", getClass().getName())
                //                .addArgument("comments", versionString())
                //                .closeAnnotation()
                .docComment("Generated from field ", field.getSimpleName(), " on ", encType.getQualifiedName(),
                        " annotated with ", mirror.toString().replaceAll("@", "&#064;"))
                .makePublic().makeFinal()
                .field("CONVERTER", fb -> {
                    fb.withModifier(PRIVATE).withModifier(STATIC).withModifier(FINAL);
                    if (useDefaultConverter) {
                        if (foldTypeName == null) {
                            if (spec == null) {
                                fb.initializedFromInvocationOf("createDefault").on(KEY_TO_FOLD_CONVERTER.simpleName());
                            } else {
//                                fb.withInitializer("new " + spec.className + "Converter");
                                fb.initializedFromInvocationOf("create")
                                        .withArgument(spec.className + "." + spec.fieldName)
                                        .on(KEY_TO_FOLD_CONVERTER.simpleName());
                            }
                        } else {
                            fb.initializedFromInvocationOf("create")
                                    .withArgument("FoldType." + foldTypeName)
                                    .on(KEY_TO_FOLD_CONVERTER.simpleName());
                        }
                    } else {
                        fb.initializedTo("new " + converterType + "()");
                    }
                    fb.ofType(KEY_TO_FOLD_CONVERTER.simpleName() + "<" + itemTypeSimple + "<" + paramType + ">>");
//                    fb.ofType(SEMANTIC_REGION_TO_FOLD_CONVERTER_SIMPLE + "<" + fieldRawType + "<" + field.asType() + ">>");
                })
                .method("createFoldManagerFactory")
                .withModifier(PUBLIC).withModifier(STATIC)
                .returning(FOLD_MANAGER_FACTORY.simpleName())
                //                .annotatedWith("MimeRegistration").addArgument("mimeType", mime).addArgument("service", FOLD_MANAGER_FACTORY_NAME)
                //                .addExpressionArgument("position", "" + (670 + layerPos)).closeAnnotation()
                .body(bb -> {
                    bb.log("Create a " + genClassName, Level.FINE);
                    bb.returningInvocationOf("createFoldManagerFactory")
                            .withArgument(fieldFqn)
                            .withArgument("CONVERTER")
                            .on(KEY_TO_FOLD_CONVERTER.simpleName());
                    bb.endBlock();
                })
                .method("createFoldRefreshTaskFactory")
                .withModifier(PUBLIC).withModifier(STATIC)
                .returning("TaskFactory")
                .annotatedWith(MIME_REGISTRATION.simpleName()).addArgument("mimeType", mime)
                .addClassArgument("service", TASK_FACTORY.simpleName())
                .addArgument("position", (670 + (layerPos))).closeAnnotation()
                .body(bb -> {
                    bb.log(Level.FINE).stringLiteral(mime)
                            .argument(fieldFqn)
                            .stringLiteral(converterType)
                            .logging("Create a {0} fold task factory for {1} with {2}");
                    bb.returningInvocationOf("createTaskFactory")
                            .withStringLiteral(mime)
                            .withArgument(fieldFqn)
                            .on("CONVERTER").endBlock();
                });

        if (foldTypeName != null || spec != null) {
            cb.importing("org.netbeans.api.editor.fold.FoldType");
        }

        writeOne(cb);

        String layerPath = "Editors/" + mime + "/FoldManager/";
        String layerFile = layerPath + fqn.replace('.', '-') + "-" + "createFoldManagerFactory" + ".instance";

        layer(field).file(layerFile)
                .methodvalue("instanceCreate", fqn, "createFoldManagerFactory")
                .stringvalue("instanceOf", FOLD_MANAGER_FACTORY.qnameNotouch())
                .intvalue("position", layerPos)
                .write();
        if (layerPos == pos) {
            pos++;
        }
    }

    private FoldSpecInfo generateFoldTypeSpec(AnnotationMirror typeSpec, VariableElement on, String mimeType, int pos) throws IOException {
        TypeElement encType = AnnotationUtils.enclosingType(on);
        PackageElement pkgEl = processingEnv.getElementUtils().getPackageOf(encType);

        String specName = utils().annotationValue(typeSpec, "name", String.class);
        String displayName = utils().annotationValue(typeSpec, "displayName", String.class, specName);
        String displayText = utils().annotationValue(typeSpec, "displayText", String.class);
        int guardedStart = utils().annotationValue(typeSpec, "guardedStart", Integer.class, 0);
        int guardedEnd = utils().annotationValue(typeSpec, "guardedEnd", Integer.class, 0);

        String regClassName = encType.getSimpleName() + "_" + on.getSimpleName() + "_FoldTypeRegistration";
        String regClassFqn = pkgEl.getQualifiedName() + "." + regClassName;

        String foldFieldName = specName.toUpperCase() + "_FOLD_TYPE";

        ClassBuilder<String> cb = ClassBuilder.forPackage(pkgEl.getQualifiedName())
                .named(regClassName).importing(
                FOLD_TYPE_PROVIDER.qname(),
                FOLD_TYPE.qname(),
                COLLECTIONS.qname(),
                COLLECTION.qname(),
                FOLD_TEMPLATE.qname(),
                MIME_REGISTRATION.qname()
        )
                .implementing("FoldTypeProvider")
                .withModifier(PUBLIC).withModifier(FINAL)
                //                .annotatedWith("Generated").addArgument("value", getClass().getName())
                //                .addArgument("comments", versionString())
                //                .closeAnnotation()
                .annotatedWith(MIME_REGISTRATION.simpleName())
                .addArgument("mimeType", mimeType)
                .addClassArgument("service", FOLD_TYPE_PROVIDER.simpleName())
                .addArgument("position", pos)
                .closeAnnotation()
                .field(foldFieldName, fb -> {
                    fb.withModifier(PUBLIC).withModifier(STATIC).withModifier(FINAL);
                    fb.initializedFromInvocationOf("create")
                            .withStringLiteral(specName)
                            .withStringLiteral(displayName)
                            .withArgumentFromInvoking("new " + FOLD_TEMPLATE.simpleName())
                            .withArgument(guardedStart)
                            .withArgument(guardedEnd)
                            .withStringLiteral(displayText == null ? "..." : displayText)
                            .inScope()
                            .on("FoldType")
                            .ofType("FoldType");
                })
                .field("ALL").withModifier(PRIVATE).withModifier(STATIC).withModifier(FINAL)
                .initializedFromInvocationOf("singleton").withArgument(foldFieldName)
                .on("Collections").ofType("Collection<FoldType>")
                .overridePublic("getValues")
                .annotatedWith("SuppressWarnings").addArrayArgument("value").literal("unchecked")
                .literal("rawTypes").closeArray().closeAnnotation()
                .addArgument("Class", "type")
                .returning("Collection")
                .body().returning("ALL").endBlock()
                .overridePublic("inheritable").returning("boolean").bodyReturning("false");

        writeOne(cb);
        return new FoldSpecInfo(regClassName, regClassFqn, foldFieldName, displayText != null);
    }

    static final class FoldSpecInfo {

        public final String className;
        public final String classFqn;
        public final String fieldName;
        public final boolean hasDisplayText;

        public FoldSpecInfo(String className, String classFqn, String fieldName, boolean hasDisplayText) {
            this.className = className;
            this.classFqn = classFqn;
            this.fieldName = fieldName;
            this.hasDisplayText = hasDisplayText;
        }

    }

    private static String version;

    private static String simpleName(String dotted) {
        int ix = dotted.lastIndexOf('.');
        if (ix > 0 && ix < dotted.length() - 1) {
            return dotted.substring(ix + 1);
        }
        return dotted;
    }

    public static String versionString() {
        if (version != null) {
            return "version=" + version;
        }
        InputStream in = FoldRegistrationAnnotationProcessor.class.getResourceAsStream(
                "/META-INF/maven/org/nemesis/registration-annotation-processors/pom.properties");
        if (in == null) {
            return "unknown";
        }
        try {
            Properties props = new Properties();
            props.load(in);
            String result = props.getProperty("version");
            if (result == null) {
                result = "missing-from-pom.properties";
            }
            return version = "version=" + result;
        } catch (IOException ex) {
            Logger.getLogger(FoldRegistrationAnnotationProcessor.class.getName()).log(Level.SEVERE, null, ex);
            return ex.toString();
        } finally {
            try {
                in.close();
            } catch (IOException ex) {
                Logger.getLogger(FoldRegistrationAnnotationProcessor.class.getName()).log(Level.SEVERE, null, ex);
                return ex.toString();
            }
        }
    }
}

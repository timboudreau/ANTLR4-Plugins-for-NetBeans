package org.nemesis.registration;

import org.nemesis.registration.utils.AnnotationUtils;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedOptions;
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
import static org.nemesis.registration.FoldRegistrationAnnotationProcessor.ANNO;
import org.nemesis.registration.api.AbstractLayerGeneratingRegistrationProcessor;
import org.nemesis.registration.codegen.ClassBuilder;
import static org.nemesis.registration.utils.AnnotationUtils.AU_LOG;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 * @author Tim Boudreau
 */
@ServiceProvider(service = Processor.class)
@SupportedAnnotationTypes(ANNO)
@SupportedOptions(AU_LOG)
public class FoldRegistrationAnnotationProcessor extends AbstractLayerGeneratingRegistrationProcessor {

    public static final String PKG = "org.nemesis.antlr.fold";
    public static final String ANNO = PKG + ".AntlrFoldsRegistration";
    private static final String REGIONS_KEY_TYPE = "org.nemesis.extraction.key.RegionsKey";
    private static final String FOLD_MANAGER_FACTORY_TYPE = "org.netbeans.spi.editor.fold.FoldManagerFactory";
    private static final String FOLD_MANAGER_FACTORY_NAME = "FoldManagerFactory";
    private static final String GF_TYPE = PKG + ".GrammarFoldManagerFactory";
    private static final String MIME_REGISTRATION_ANNOT_TYPE = "org.netbeans.api.editor.mimelookup.MimeRegistration";
    private static final String SEMANTIC_REGION_TO_FOLD_CONVERTER_SIMPLE = "SemanticRegionToFoldConverter";
    private static final String SEMANTIC_REGION_TO_FOLD_CONVERTER_FQN = PKG + "." + SEMANTIC_REGION_TO_FOLD_CONVERTER_SIMPLE;

    private Predicate<Element> variableElementTest;
    private Predicate<AnnotationMirror> annotationMirrorTest;

    @Override
    protected void onInit(ProcessingEnvironment env, AnnotationUtils utils) {
        variableElementTest = utils().testsBuilder()
                .isSubTypeOf(REGIONS_KEY_TYPE)
                .doesNotHaveModifier(Modifier.PRIVATE)
                .hasModifier(Modifier.STATIC)
                .addPredicate(this::typeHasTypeParameter)
                .testContainingClass().doesNotHaveModifier(Modifier.PRIVATE)
                .build()
                .build();

        annotationMirrorTest = utils().testMirror().testMember("mimeType")
                .validateStringValueAsMimeType().build().build();
    }

    @Override
    protected boolean validateAnnotationMirror(AnnotationMirror mirror, ElementKind el) {
        assert annotationMirrorTest != null;
        boolean result = annotationMirrorTest.test(mirror);
        return result;
    }

    boolean typeHasTypeParameter(Element el) {
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
        if (type == null) { // using the default value
            return true;
        }
        TypeMirror parameterizedOn = null;
        TypeElement semRegionType = processingEnv.getElementUtils().getTypeElement(SEMANTIC_REGION_TO_FOLD_CONVERTER_FQN);
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

        String mime = utils().annotationValue(mirror, "mimeType", String.class);

        String genClassName = encType.getSimpleName().toString() + "__"
                + field.getSimpleName().toString() + "__" + "FoldRegistration";

        processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, "Generating " + genClassName
                + " in " + pkgEl.getQualifiedName() + " for fold registration annotation", field);

        String fieldFqn = encType.getQualifiedName() + "." + field.getSimpleName();

        Iterator<String> tps = utils().typeList(mirror, "converter").iterator();
        boolean useDefaultConverter = !tps.hasNext();

        String converterType = (!useDefaultConverter ? tps.next() : SEMANTIC_REGION_TO_FOLD_CONVERTER_SIMPLE)
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

        ClassBuilder<String> cb = ClassBuilder.forPackage(pkgEl.getQualifiedName())
                .named(genClassName)
                .generateDebugLogCode()
                .importing("javax.annotation.processing.Generated", "org.netbeans.modules.parsing.spi.TaskFactory",
                        "org.nemesis.antlr.fold.SemanticRegionToFoldConverter", MIME_REGISTRATION_ANNOT_TYPE,
                        FOLD_MANAGER_FACTORY_TYPE)
                .annotatedWith("Generated").addStringArgument("value", getClass().getName())
                .addStringArgument("comments", versionString())
                .closeAnnotation()
                .docComment("Generated from field ", field.getSimpleName(), " on ", encType.getQualifiedName(),
                        " annotated with ", mirror.toString().replaceAll("@", "&#064;"))
                .makePublic().makeFinal()
                .field("CONVERTER", fb -> {
                    fb.withModifier(PRIVATE).withModifier(STATIC).withModifier(FINAL);
                    if (useDefaultConverter) {
                        fb.initializedFromInvocationOf("createDefault").on(SEMANTIC_REGION_TO_FOLD_CONVERTER_SIMPLE);
                    } else {
                        fb.withInitializer("new " + converterType + "()");
                    }
                    fb.ofType(SEMANTIC_REGION_TO_FOLD_CONVERTER_SIMPLE + "<" + paramType + ">");
                })
                .method("createFoldManagerFactory")
                .withModifier(PUBLIC).withModifier(STATIC)
                .returning(FOLD_MANAGER_FACTORY_NAME)
                //                .annotateWith("MimeRegistration").addStringArgument("mimeType", mime).addClassArgument("service", FOLD_MANAGER_FACTORY_NAME)
                //                .addArgument("position", "" + (670 + layerPos)).closeAnnotation()
                .body(bb -> {
                    bb.log("Create a " + genClassName);
                    bb.returningInvocationOf("create")
                            .withArgument(fieldFqn)
                            .withArgument("CONVERTER")
                            .on(GF_TYPE);
                    bb.endBlock();
                })
                .closeMethod()
                .method("createFoldRefreshTaskFactory")
                .withModifier(PUBLIC).withModifier(STATIC)
                .returning("TaskFactory")
                .annotateWith("MimeRegistration").addStringArgument("mimeType", mime).addClassArgument("service", "TaskFactory")
                .addArgument("position", "" + (670 + (layerPos))).closeAnnotation()
                .body(bb -> {
                    bb.log(Level.FINE).stringLiteral(mime)
                            .argument(fieldFqn)
                            .stringLiteral(converterType)
                            .logging("Create a {0} fold task factory for {1} with {2}");
                    bb.returningInvocationOf("createTaskFactory")
                            .withStringLiteral(mime)
                            .withArgument(fieldFqn)
                            .on("CONVERTER").endBlock();
                })
                .closeMethod();

        writeOne(cb);

        String layerPath = "Editors/" + mime + "/FoldManager/";
        String layerFile = layerPath + fqn.replace('.', '-') + "-" + "createFoldManagerFactory" + ".instance";

        layer(field).file(layerFile)
                .methodvalue("instanceCreate", fqn, "createFoldManagerFactory")
                .stringvalue("instanceOf", FOLD_MANAGER_FACTORY_TYPE)
                .intvalue("position", layerPos)
                .write();

//        if (!isSidebarFileWritten(mime)) {
//            String sideBarPath = "Editors/" + mime + "/SideBar/";
//            String sidebarClass = "org.netbeans.modules.editor.fold.ui.CodeFoldingSideBar$Factory";
//            String factoryClass = "org.netbeans.spi.editor.SideBarFactory";
//            String sideBarFile = sideBarPath + sidebarClass.replace('.', '-') + ".instance";
//            lb.file(sideBarFile)
//                    .stringvalue("instanceClass", sidebarClass)
//                    .stringvalue("instanceOf", factoryClass)
//                    .intvalue("position", layerPos)
//                    .write();
//            sidebarFileWritten(mime);
//        }
        if (layerPos == pos) {
            pos++;
        }
    }

//    private final Set<String> sidebarsWritten = new HashSet<>();
//
//    private boolean isSidebarFileWritten(String mimeType) {
//        return sidebarsWritten.contains(mimeType);
//    }
//
//    private void sidebarFileWritten(String mimeType) {
//        sidebarsWritten.add(mimeType);
//    }

    private static String version;

    public static String versionString() {
        if (version != null) {
            return version;
        }
        InputStream in = FoldRegistrationAnnotationProcessor.class.getResourceAsStream("/META-INF/maven/org/nemesis/registration-annotation-processors/pom.properties");
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

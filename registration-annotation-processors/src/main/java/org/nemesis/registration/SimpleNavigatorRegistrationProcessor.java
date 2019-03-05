package org.nemesis.registration;

import org.nemesis.registration.api.AbstractRegistrationProcessor;
import java.io.IOException;
import java.util.List;
import java.util.function.Predicate;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedOptions;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import static org.nemesis.registration.FoldRegistrationAnnotationProcessor.versionString;
import static org.nemesis.registration.NavigatorPanelRegistrationAnnotationProcessor.*;
import org.nemesis.registration.codegen.ClassBuilder;
import org.nemesis.registration.utils.AnnotationUtils;
import static org.nemesis.registration.utils.AnnotationUtils.AU_LOG;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 * @author Tim Boudreau
 */
@ServiceProvider(service = Processor.class)
@SupportedAnnotationTypes(SimpleNavigatorRegistrationProcessor.SIMPLE_ANNOTATION_CLASS)
@SupportedOptions(AU_LOG)
public class SimpleNavigatorRegistrationProcessor extends AbstractRegistrationProcessor {

    public static final String SIMPLE_ANNOTATION_CLASS = ANTLR_NAVIGATOR_PACKAGE + ".SimpleNavigatorRegistration";
    private Predicate<AnnotationMirror> annoTest;
    private Predicate<Element> typeTest;

    static {
        AnnotationUtils.forceLogging();
    }

    @Override
    protected void onInit(ProcessingEnvironment env, AnnotationUtils utils) {
        annoTest = utils.testMirror().testMember("icon")
                .stringValueMustMatch((val, ut) -> {
                    if (val == null || val.isEmpty()) {
                        return true;
                    }
                    boolean result = val.endsWith(".jpg")
                            || val.endsWith(".gif")
                            || val.endsWith(".png");
                    if (!result) {
                        ut.fail("Icon base must end with .gif, .jpg or .png");
                    }
                    return result;
                })
                .build()
                .testMember("mimeType").validateStringValueAsMimeType().build()
                .build();
        typeTest = utils.testsBuilder().hasModifier(STATIC).doesNotHaveModifier(PRIVATE)
                .isSubTypeOf(NAMED_REGION_KEY_TYPE, SEMANTIC_REGION_KEY_TYPE)
                .testContainingClass().doesNotHaveModifier(PRIVATE).build().build();
    }

    @Override
    protected boolean validateAnnotationMirror(AnnotationMirror mirror, ElementKind kind) {
        boolean result = annoTest.test(mirror);
        return result;
    }

    @Override
    protected boolean processFieldAnnotation(VariableElement var, AnnotationMirror mirror, RoundEnvironment roundEnv) throws Exception {
        if (!annoTest.test(mirror)) {
            return true;
        }
        if (!typeTest.test(var)) {
            return true;
        }
        PackageElement pkg = processingEnv.getElementUtils().getPackageOf(var);
        if (pkg == null || pkg.getQualifiedName().toString().isEmpty()) {
            utils().fail("Class annotated with " + SIMPLE_ANNOTATION_CLASS
                    + " may not be in the default package");
            return true;
        }
        generateNavigatorPanelFactory(var, mirror, pkg.getQualifiedName().toString(), roundEnv, AnnotationUtils.enclosingType(var));
        return true;
    }

    private String classNamePrefix(VariableElement el) {
        StringBuilder sb = new StringBuilder();
        TypeElement parentType = AnnotationUtils.enclosingType(el);
        sb.append(parentType.getSimpleName());
        while (parentType.getEnclosingElement() instanceof TypeElement) {
            parentType = (TypeElement) parentType.getEnclosingElement();
            sb.insert(0, "_");
            sb.insert(0, parentType.getSimpleName());
        }
        return sb.toString();
    }

    private TypeMirror firstTypeParameter(Element var) {
        TypeMirror mir = var.asType();
        if (mir.getKind() != TypeKind.DECLARED) {
            utils().fail("Field must have a fully reified type, but found " + mir, var);
            return null;
        }
        DeclaredType dt = (DeclaredType) mir;
        List<? extends TypeMirror> args = dt.getTypeArguments();

        if (args.isEmpty()) {
            utils().fail("No type parameters - cannot generate code", var);
            return null;
        }
        return args.get(0);
    }

    private void generateNavigatorPanelFactory(VariableElement var, AnnotationMirror mirror, String pkg, RoundEnvironment roundEnv, TypeElement on) throws IOException {
        boolean isSemanticRegion = utils().isSubtypeOf(var, SEMANTIC_REGION_KEY_TYPE).isSubtype();

        final String generatedClassName = classNamePrefix(var) + "_" + var.getSimpleName() + "_NavRegistration";
        final String mimeType = utils().annotationValue(mirror, "mimeType", String.class);
        String ic = utils().annotationValue(mirror, "icon", String.class, "");
        final Integer order = utils().annotationValue(mirror, "order", Integer.class, Integer.MAX_VALUE);

        if (!ic.isEmpty() && ic.indexOf('/') < 0) {
            ic = pkg.replace('.', '/') + '/' + ic;
        }

        final String icon = ic;

        String dn = utils().annotationValue(mirror, "displayName", String.class, "");
        boolean bundleKey = !dn.isEmpty() && dn.charAt(0) == '#';
        if (bundleKey) {
            dn = dn.substring(1);
        }
        if (dn.isEmpty()) {
            utils().warn("displayName not set", on, mirror);
            dn = "<display name not set>";
        }
        final String displayName = dn;
        TypeMirror param = firstTypeParameter(var);
        if (param == null) {
            return;
        }
        final String keyType = param.toString();

        String configType = isSemanticRegion ? SEMANTIC_REGION_PANEL_CONFIG_TYPE
                : NAVIGATOR_PANEL_CONFIG_TYPE;

        String configTypeName = isSemanticRegion ? SEMANTIC_REGION_PANEL_CONFIG_NAME
                : NAVIGATOR_PANEL_CONFIG_NAME;

        ClassBuilder<String> bldr = ClassBuilder.forPackage(pkg).named(generatedClassName)
                .importing(configType,
                        NAVIGATOR_PANEL_REGISTRATION_ANNOTATION,
                        "javax.annotation.processing.Generated"
                )
                .annotatedWith("Generated").addStringArgument("value", getClass().getName()).addStringArgument("comments", versionString()).closeAnnotation()
                .staticImport(on.getQualifiedName() + "." + var.getSimpleName())
                .withModifier(FINAL).constructor().body().statement("throw new AssertionError()").endBlock().endConstructor()
                .method("create", mb -> {
                    mb.withModifier(PUBLIC).withModifier(STATIC)
                            .annotatedWith("AntlrNavigatorPanelRegistration")
                            .addStringArgument("mimeType", mimeType)
                            .addStringArgument("displayName", displayName)
                            .addArgument("order", order).closeAnnotation()
                            .returning(configTypeName)
                            .body(bb -> {

                                ClassBuilder.DeclarationBuilder<?> dnDeclaration = bb.declare("displayName");
                                if (bundleKey) {
                                    dnDeclaration.initializedByInvoking("getMessage").withArgument(generatedClassName + ".class")
                                            .withStringLiteral(displayName).on("NbBundle").as("String");
                                } else {
                                    dnDeclaration.initializedWithStringLiteral(displayName).as("String");
                                }
                                // return NavigatorPanelConfig.builder( NAMES )
                                //.setDisplayName( Bundle.namedTypes() ).sortable().build();
                                bb.declare("result").initializedByInvoking("builder")
                                        .withArgument(var.getSimpleName().toString()).on(configTypeName)
                                        .as(configTypeName + ".Builder<" + keyType + ">");
                                if (!icon.isEmpty()) {
                                    bb.invoke("setSingleIcon").withStringLiteral(icon).on("result");
                                }
                                bb.invoke("setDisplayName").withArgument("displayName").on("result");
                                bb.invoke("sortable").on("result");
                                bb.returning("result.build()").endBlock();
                            }).closeMethod();
                });
        if (bundleKey) {
            bldr.importing("org.openide.util.NbBundle");
        }
        writeOne(bldr);
    }
}

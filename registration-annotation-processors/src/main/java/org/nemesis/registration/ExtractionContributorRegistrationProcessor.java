package org.nemesis.registration;

import com.mastfrog.annotation.processor.AbstractDelegatingProcessor;
import java.util.function.Predicate;
import java.util.logging.Level;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedOptions;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PROTECTED;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import static org.nemesis.registration.FoldRegistrationAnnotationProcessor.versionString;
import com.mastfrog.java.vogon.ClassBuilder;
import com.mastfrog.annotation.AnnotationUtils;
import static com.mastfrog.annotation.AnnotationUtils.AU_LOG;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 * @author Tim Boudreau
 */
@ServiceProvider(service = Processor.class)
@SupportedAnnotationTypes("org.nemesis.extraction.ExtractionRegistration")
@SupportedOptions(AU_LOG)
public class ExtractionContributorRegistrationProcessor extends AbstractDelegatingProcessor {

    private static final String PKG = "org.nemesis.extraction.";
    static final String ANNO = PKG + "ExtractionRegistration";
    private Predicate<? super AnnotationMirror> mirrorCheck;
    private static final String EC_NAME = "ExtractionContributor";
    private static final String EC_TYPE = PKG + EC_NAME;

    @Override
    protected void onInit(ProcessingEnvironment env, AnnotationUtils utils) {
        super.onInit(env, utils);
        mirrorCheck = utils().testMirror().testMember("mimeType").validateStringValueAsMimeType().build().build();
    }

    @Override
    protected boolean validateAnnotationMirror(AnnotationMirror mirror, ElementKind kind, Element element) {
        boolean result = mirrorCheck.test(mirror);
        return result;
    }

    @Override
    protected boolean processMethodAnnotation(ExecutableElement method, AnnotationMirror mirror, RoundEnvironment roundEnv) throws Exception {
        if (!method.getModifiers().contains(STATIC)) {
            utils().fail("Method annotated with " + ANNO + " must be static");
            return true;
        }
        if (method.getModifiers().contains(PROTECTED) || method.getModifiers().contains(PRIVATE)) {
            utils().fail("Method annotated with " + ANNO + " may not be private or protected");
            return true;
        }
        TypeMirror entryPointType = utils().typeForSingleClassAnnotationMember(mirror, "entryPoint");
        if (entryPointType == null) {
            utils().fail("Could not resolve type");
            return true;
        }
        TypeElement owner = AnnotationUtils.enclosingType(method);
        switch (owner.getNestingKind()) {
            case ANONYMOUS:
            case MEMBER:
                utils().fail("Annotated method may not be on an inner or anonymous class");
                return true;
        }
        String ownerTypeSimple = owner.getSimpleName().toString();
        String entrySimple = simpleName(entryPointType);
        String mimeType = utils().annotationValue(mirror, "mimeType", String.class);
        PackageElement pkg = processingEnv.getElementUtils().getPackageOf(method);
        String generatedClassName = owner.getSimpleName() + "_ExtractionContributor" + "_" + method.getSimpleName();
        ClassBuilder<String> cb = ClassBuilder.forPackage(pkg == null ? null : pkg.getQualifiedName())
                .named(generatedClassName).implementing(EC_NAME + "<" + entrySimple + ">")
                .withModifier(PUBLIC).withModifier(FINAL)
                .importing("javax.annotation.processing.Generated", "org.openide.util.lookup.ServiceProvider",
                        EC_TYPE, "org.nemesis.extraction.ExtractorBuilder", entryPointType.toString())
                .docComment("Generated from ", ANNO, " on method ", method.getSimpleName(), " of ", ownerTypeSimple,
                        " with entry point type ", entrySimple)
                .annotatedWith("Generated").addStringArgument("value", getClass().getName()).addStringArgument("comments", versionString()).closeAnnotation()
                .annotatedWith("ServiceProvider").addClassArgument("service", EC_NAME).addStringArgument("path", registrationPath(mimeType, entryPointType.toString())).closeAnnotation()
                .method("accept", mb -> {
                    mb.override().withModifier(PUBLIC).addArgument("ExtractorBuilder<? super " + entrySimple + ">", "builder")
                            .body(bb -> {
                                bb.log(generatedClassName + " populate ExtractionBuilder", Level.FINER);
                                bb.invoke(method.getSimpleName().toString())
                                        .withArgument("builder")
                                        .on(ownerTypeSimple).endBlock();
                            });
                })
                .method("type").override().withModifier(PUBLIC)
                .returning("Class<" + entrySimple + ">").body().returning(entrySimple
                + ".class").endBlock()
                .method("toString").override().withModifier(PUBLIC).returning("String").body()
                .returningStringLiteral(generatedClassName + "<" + entrySimple + "> delegating to " + method.getSimpleName() + " on " + owner.getQualifiedName()).endBlock();
        writeOne(cb);
        return true;
    }

    private String simpleName(TypeMirror mirror) {
        String s = mirror.toString();
        int ix = s.lastIndexOf('.');
        if (ix >= 0 && ix < s.length() - 1) {
            return s.substring(ix + 1);
        }
        return s;
    }

    private static final String BASE_PATH = "antlr/extractors";

    private static String registrationPath(String mimeType, String entryPointType) {
        // Keep BASE_PATH and this method in sync with identical method in
        // Extractors
        return BASE_PATH + "/" + mimeType + "/" + entryPointType.replace('.', '/');
    }

}

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
import com.mastfrog.java.vogon.ClassBuilder;
import com.mastfrog.annotation.AnnotationUtils;
import static com.mastfrog.annotation.AnnotationUtils.AU_LOG;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import org.openide.util.lookup.ServiceProvider;
import static org.nemesis.registration.ExtractionContributorRegistrationProcessor.EXTRACTION_REGISTRATION_ANNO;
import static org.nemesis.registration.NameAndMimeTypeUtils.cleanMimeType;

/**
 *
 * @author Tim Boudreau
 */
@ServiceProvider(service = Processor.class)
@SupportedAnnotationTypes(EXTRACTION_REGISTRATION_ANNO)
@SupportedOptions(AU_LOG)
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class ExtractionContributorRegistrationProcessor extends AbstractDelegatingProcessor {

    private static final String PKG = "org.nemesis.extraction.";
    static final String EXTRACTION_REGISTRATION_ANNO = PKG + "ExtractionRegistration";
    private Predicate<? super AnnotationMirror> mirrorCheck;
    private static final String EC_NAME = "ExtractionContributor";
    private static final String EC_TYPE = PKG + EC_NAME;

    @Override
    protected void onInit(ProcessingEnvironment env, AnnotationUtils utils) {
        super.onInit(env, utils);
        mirrorCheck = utils().testMirror().testMember("mimeType")
                .addPredicate("Mime type", mir -> {
                    Predicate<String> mimeTest = NameAndMimeTypeUtils.complexMimeTypeValidator(true, utils(), null, mir);
                    String value = utils().annotationValue(mir, "mimeType", String.class);
                    return mimeTest.test(value);
                }).build().build();
    }

    @Override
    protected boolean validateAnnotationMirror(AnnotationMirror mirror, ElementKind kind, Element element) {
        boolean result = mirrorCheck.test(mirror);
        return result;
    }

    @Override
    protected boolean processMethodAnnotation(ExecutableElement method, AnnotationMirror mirror, RoundEnvironment roundEnv) throws Exception {
        if (!method.getModifiers().contains(STATIC)) {
            utils().fail("Method annotated with " + EXTRACTION_REGISTRATION_ANNO + " must be static");
            return true;
        }
        if (method.getModifiers().contains(PROTECTED) || method.getModifiers().contains(PRIVATE)) {
            utils().fail("Method annotated with " + EXTRACTION_REGISTRATION_ANNO + " may not be private or protected");
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
        String mimeType = cleanMimeType(utils().annotationValue(mirror, "mimeType", String.class));
        PackageElement pkg = processingEnv.getElementUtils().getPackageOf(method);
        String generatedClassName = owner.getSimpleName() + "_ExtractionContributor" + "_" + method.getSimpleName();

        String registrationFieldName = "REGISTRATION_PATH";

        ClassBuilder<String> cb = ClassBuilder.forPackage(pkg == null ? null : pkg.getQualifiedName())
                .named(generatedClassName);

        cb.docComment("Registers the static method ", method.getSimpleName(), " on ", owner.getQualifiedName(),
                " to be called for extraction whenever a file of type ", mimeType, " is parsed.  Generated from ",
                mirror);
        String registrationFieldCNB = cb.fqn() + "." + registrationFieldName;
        cb.implementing(EC_NAME + "<" + entrySimple + ">")
                .withModifier(PUBLIC).withModifier(FINAL)
                .importing(
                        //                        "javax.annotation.processing.Generated",
                        "org.openide.util.lookup.ServiceProvider",
                        EC_TYPE, "org.nemesis.extraction.ExtractorBuilder", entryPointType.toString())
                .staticImport(registrationFieldCNB)
                //                .annotatedWith("Generated").addArgument("value", getClass().getName()).addArgument("comments", versionString()).closeAnnotation()
                .annotatedWith("ServiceProvider").addClassArgument("service", EC_NAME)
                .addExpressionArgument("path", registrationFieldName)
                //                .addArgument("path", registrationPath(mimeType, entryPointType.toString()))
                .closeAnnotation()
                .field(registrationFieldName, fb -> {
                    fb.docComment("Tests can use this field to place an instance of " + cb.className()
                            + " in a NamedServicesProvider instance programmatically to make it available "
                            + "when running tests");
                    fb.withModifier(PUBLIC, STATIC, FINAL)
                            .initializedWith(registrationPath(mimeType, entryPointType.toString()));
                })
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

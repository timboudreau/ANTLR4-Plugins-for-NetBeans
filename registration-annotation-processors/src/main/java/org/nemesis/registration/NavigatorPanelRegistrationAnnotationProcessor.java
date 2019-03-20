/*
BSD License

Copyright (c) 2016-2018, Frédéric Yvon Vinet, Tim Boudreau
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

* Redistributions of source code must retain the above copyright
  notice, this list of conditions and the following disclaimer.
* Redistributions in binary form must reproduce the above copyright
  notice, this list of conditions and the following disclaimer in the
  documentation and/or other materials provided with the distribution.
* The name of its author may not be used to endorse or promote products
  derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR IMPLIED 
WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF 
MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT
SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, 
EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT
OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS 
INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN 
CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING
IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY 
OF SUCH DAMAGE.
 */
package org.nemesis.registration;

import org.nemesis.registration.api.AbstractRegistrationProcessor;
import org.nemesis.registration.utils.AnnotationUtils;
import java.io.IOException;
import java.io.OutputStream;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;
import javax.annotation.processing.Filer;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedOptions;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.tools.JavaFileObject;
import static org.nemesis.registration.utils.AnnotationUtils.enclosingType;
import static org.nemesis.registration.FoldRegistrationAnnotationProcessor.versionString;
import org.nemesis.registration.codegen.ClassBuilder;
import org.nemesis.registration.codegen.LinesBuilder;
import static org.nemesis.registration.utils.AnnotationUtils.AU_LOG;
import org.openide.util.lookup.ServiceProvider;
import static org.nemesis.registration.NavigatorPanelRegistrationAnnotationProcessor.NAVIGATOR_PANEL_REGISTRATION_ANNOTATION;

/**
 * Generates java code for registered navigator panels which use Extraction to
 * construct their contents.
 *
 * @author Tim Boudreau
 */
@ServiceProvider(service = Processor.class)
@SupportedAnnotationTypes(NAVIGATOR_PANEL_REGISTRATION_ANNOTATION)
@SupportedOptions(AU_LOG)
public class NavigatorPanelRegistrationAnnotationProcessor extends AbstractRegistrationProcessor {

    public static final String ANTLR_NAVIGATOR_PACKAGE = "org.nemesis.antlr.navigator";
    public static final String NAVIGATOR_PANEL_REGISTRATION_ANNOTATION = ANTLR_NAVIGATOR_PACKAGE + ".AntlrNavigatorPanelRegistration";
    public static final String NAVIGATOR_PANEL_CONFIG_NAME = "NavigatorPanelConfig";
    public static final String NAVIGATOR_PANEL_CONFIG_TYPE = ANTLR_NAVIGATOR_PACKAGE + "." + NAVIGATOR_PANEL_CONFIG_NAME;
    public static final String SEMANTIC_REGION_PANEL_CONFIG_NAME = "SemanticRegionPanelConfig";
    public static final String SEMANTIC_REGION_PANEL_CONFIG_TYPE = ANTLR_NAVIGATOR_PACKAGE + "." + SEMANTIC_REGION_PANEL_CONFIG_NAME;
    public static final String NAV_PANEL_REGISTRATION_ANNO = "NavigatorPanel.Registration";
    public static final String NAV_PANEL_TYPE = "org.netbeans.spi.navigator.NavigatorPanel";
    public static final String NAMED_REGION_KEY_TYPE = "org.nemesis.extraction.key.NamedRegionKey";
    public static final String SEMANTIC_REGION_KEY_TYPE = "org.nemesis.extraction.key.RegionsKey";
    private final Set<String> handled = new HashSet<>();
    private Predicate<? super ExecutableElement> factoryMethodChecks;
    private Predicate<? super AnnotationMirror> annotationChecks;

    @Override
    protected boolean processMethodAnnotation(ExecutableElement method, AnnotationMirror mirror, RoundEnvironment roundEnv) throws Exception {
        assert factoryMethodChecks != null;
        if (!factoryMethodChecks.test(method)) {
            return true;
        }
        Elements els = processingEnv.getElementUtils();
        TypeElement owningClass = enclosingType(method);
        String className = owningClass.getSimpleName().toString();
        PackageElement pkg = els.getPackageOf(method);
        String packageName = pkg.getQualifiedName().toString();
        String methodName = method.getSimpleName().toString();
        String methodFqn = packageName + "." + className + "." + methodName;
        if (!handled.contains(methodFqn)) {
            handleOne(className, packageName, methodName, method, owningClass, mirror);
            handled.add(methodFqn);
        }
        return true;
    }

    @Override
    protected void onInit(ProcessingEnvironment env, AnnotationUtils utils) {
        super.onInit(env, utils);
        factoryMethodChecks = utils.methodTestBuilder()
                .hasModifier(Modifier.STATIC)
                .returns(NAVIGATOR_PANEL_CONFIG_TYPE, SEMANTIC_REGION_PANEL_CONFIG_TYPE)
                .doesNotHaveModifier(Modifier.PRIVATE)
                .mustNotTakeArguments()
                .testContainingClass()
                .doesNotHaveModifier(Modifier.PRIVATE)
                .build()
                .build();

        annotationChecks = utils.testMirror().testMember("mimeType")
                .validateStringValueAsMimeType().build().build();
    }

    @Override
    protected boolean validateAnnotationMirror(AnnotationMirror mirror, ElementKind kind, Element element) {
        return annotationChecks.test(mirror);
    }

    private void handleOne(String className, String packageName, String methodName, ExecutableElement method, TypeElement owningClass, AnnotationMirror anno) throws IOException {
        String generatedFqn = packageName + "." + generatedClassName(className);
        if (generatedFqn == null) { // invalid mime type
            return;
        }
        Filer filer = processingEnv.getFiler();
        JavaFileObject jfo = filer.createSourceFile(generatedFqn, method);
        try (OutputStream out = jfo.openOutputStream()) {
            String classBody = generateJavaSource(packageName, className, methodName, anno, method);
            out.write(classBody.getBytes(UTF_8));
        }
    }

    private String generatedClassName(String className) {
        return className + "__Factory";
    }

    private boolean validateMimeType(String mimeType, ExecutableElement origin) {
        if (mimeType.isEmpty()) {
            utils().fail("Mime type is the empty string", origin);
            return false;
        }
        if (mimeType.trim().length() != mimeType.length()) {
            utils().fail("Mime type is the has leading or trailing whitespace: '" + mimeType + "'", origin);
            return false;
        }
        String[] parts = mimeType.split("/");
        if (parts.length != 2) {
            utils().fail("Mime type should contain exactly one / character: '" + mimeType + "'", origin);
            return false;
        }
        return true;
    }

    private String generateJavaSource(String pkg, String className, String method, AnnotationMirror anno, ExecutableElement on) {
//        boolean isSemantic = utils().isSubtypeOf(on, SEMANTIC_REGION_PANEL_CONFIG_TYPE).isSubtype();
        String mimeType = utils().annotationValue(anno, "mimeType", String.class);
        if (!validateMimeType(mimeType, on)) {
            return null;
        }
        Integer order = utils().annotationValue(anno, "order", Integer.class, Integer.MAX_VALUE);
        String displayName = utils().annotationValue(anno, "displayName", String.class, "-");
        String generatedClassName = generatedClassName(className);
        ClassBuilder<String> cb = ClassBuilder.forPackage(pkg).named(generatedClassName)
                .makeFinal().makePublic()
                .importing(NAV_PANEL_TYPE)
                .importing("javax.annotation.processing.Generated")
                .annotatedWith("Generated").addStringArgument("value", getClass().getName()).addStringArgument("comments", versionString())
                .closeAnnotation()
                .method("create", mb -> {
                    mb.withModifier(Modifier.PUBLIC).withModifier(Modifier.STATIC)
                            .returning("NavigatorPanel")
                            .annotatedWith(NAV_PANEL_REGISTRATION_ANNO)
                            .addStringArgument("displayName", displayName)
                            .addStringArgument("mimeType", mimeType)
                            .addArgument("position", order + "")
                            .closeAnnotation()
                            .body()
                            .returning(pkg + "." + className + "." + method + "().toNavigatorPanel(" + LinesBuilder.stringLiteral(mimeType) + ")").endBlock();

                });
        return cb.build();
    }
}

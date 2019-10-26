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
import com.mastfrog.annotation.processor.AbstractLayerGeneratingDelegatingProcessor;
import com.mastfrog.java.vogon.ClassBuilder;
import java.util.function.BiPredicate;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;
import javax.lang.model.element.VariableElement;
import static org.nemesis.registration.ImportsAndResolvableProcessor.NAMED_REGION_REFERENCE_KEY_TYPE;
import static org.nemesis.registration.InplaceRenameProcessor.INPLACE_RENAME_ANNO_TYPE;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 * @author Tim Boudreau
 */
@ServiceProvider(service = Processor.class)
@SupportedAnnotationTypes(INPLACE_RENAME_ANNO_TYPE)
public class InplaceRenameProcessor extends AbstractLayerGeneratingDelegatingProcessor {

    public static final String INPLACE_RENAME_ANNO_TYPE
            = "org.nemesis.antlr.spi.language.InplaceRename";
    public static final String INPLACE_RENAME_ACTION_TYPE
            = "org.nemesis.antlr.refactoring.InstantRenameAction";

    public static final String EDITOR_ACTION_REGISTRATION_ANNO_TYPE
            = "org.netbeans.api.editor.EditorActionRegistration";

    public static final String HIGHLIGHTS_LAYER_FACTORY_TYPE
            = "org.netbeans.spi.editor.highlighting.HighlightsLayerFactory";

    public static final String MIME_REGISTRATION_ANNO_TYPE
            = "org.netbeans.api.editor.mimelookup.MimeRegistration";

    public static final String MESSAGES_ANNO_TYPE
            = "org.openide.util.NbBundle.Messages";

    /*
import org.netbeans.api.editor.mimelookup.MimeRegistration;
import org.netbeans.spi.editor.highlighting.HighlightsLayerFactory;
import org.openide.util.NbBundle.Messages
     */
    private BiPredicate<? super AnnotationMirror, ? super Element> test;

    @Override
    protected void onInit(ProcessingEnvironment env, AnnotationUtils utils) {
        test = utils.multiAnnotations()
                .whereAnnotationType(INPLACE_RENAME_ANNO_TYPE, b -> {
                    b.testMember("mimeType").validateStringValueAsMimeType();
                    b.whereFieldIsAnnotated(eltest -> {
                        eltest.hasModifiers(Modifier.FINAL, Modifier.STATIC)
                                .isSubTypeOf(NAMED_REGION_REFERENCE_KEY_TYPE);
                    });
                }).build();
    }

    @Override
    protected boolean validateAnnotationMirror(AnnotationMirror mirror, ElementKind kind, Element el) {
        return test.test(mirror, el);
    }

    @Override
    protected boolean processFieldAnnotation(VariableElement var, AnnotationMirror mirror, RoundEnvironment roundEnv) throws Exception {
        String mimeType = utils().annotationValue(mirror, "mimeType", String.class);
        String pkg = utils().packageName(var);
        String className = capitalize(AnnotationUtils.stripMimeType(mimeType)) + "InplaceRenameAction_" + var.getSimpleName();
        String varStaticImport = AnnotationUtils.enclosingType(var).getQualifiedName() + "." + var.getSimpleName();
        /*

@Messages("in-place-refactoring=&Rename")
@EditorActionRegistration(mimeType = ANTLR_MIME_TYPE, noIconInMenu = true,
        category = "Refactoring", name = "in-place-refactoring")

    public G4InstantRenameAction() {
        super(AntlrKeys.RULE_NAME_REFERENCES);
    }

    @MimeRegistration(mimeType = ANTLR_MIME_TYPE, service = HighlightsLayerFactory.class)
    public static HighlightsLayerFactory highlights() {
        return G4InstantRenameAction.highlightsFactory();
    }
         */
        ClassBuilder<String> cb = ClassBuilder.forPackage(pkg).named(className)
                .withModifier(PUBLIC, FINAL)
                .importing(
                        INPLACE_RENAME_ACTION_TYPE,
                        HIGHLIGHTS_LAYER_FACTORY_TYPE,
                        EDITOR_ACTION_REGISTRATION_ANNO_TYPE,
                        MIME_REGISTRATION_ANNO_TYPE,
                        MESSAGES_ANNO_TYPE
                )
                .staticImport(varStaticImport)
                .extending(simpleName(INPLACE_RENAME_ACTION_TYPE))
                .annotatedWith(simpleName(MESSAGES_ANNO_TYPE), ab -> {
                    ab.addArgument("value", "in-place-refactoring=&Rename");
                })
                .annotatedWith(simpleName(EDITOR_ACTION_REGISTRATION_ANNO_TYPE), ab -> {
                    ab.addArgument("mimeType", mimeType)
                            .addArgument("noIconInMenu", true)
                            .addArgument("category", "Refactoring")
                            .addArgument("name", "in-place-refactoring");
                })
                .constructor(con -> {
                    con.setModifier(PUBLIC)
                            .body(bb -> {
                                bb.invoke("super").withArgument(var.getSimpleName().toString())
                                        .inScope();
                            });
                })
                .method("highlights", mb -> {
                    mb.annotatedWith(simpleName(MIME_REGISTRATION_ANNO_TYPE), ab -> {
                        ab.addArgument("mimeType", mimeType)
                                .addClassArgument("service", simpleName(HIGHLIGHTS_LAYER_FACTORY_TYPE));
                    });
                    mb.withModifier(PUBLIC, STATIC)
                            .returning(simpleName(HIGHLIGHTS_LAYER_FACTORY_TYPE))
                            .body(bb -> {
                                bb.returningInvocationOf("highlightsFactory").inScope();
                            });
                });
        writeOne(cb);
        return true;
    }

}

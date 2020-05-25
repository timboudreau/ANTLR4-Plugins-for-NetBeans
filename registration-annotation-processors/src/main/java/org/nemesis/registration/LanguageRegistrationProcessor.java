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

import com.mastfrog.annotation.processor.AbstractLayerGeneratingDelegatingProcessor;
import javax.annotation.processing.Processor;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedOptions;
import javax.lang.model.element.ElementKind;
import static org.nemesis.registration.GotoDeclarationProcessor.GOTO_ANNOTATION;
import static org.nemesis.registration.KeybindingsAnnotationProcessor.ANTLR_ACTION_ANNO_TYPE;
import static org.nemesis.registration.KeybindingsAnnotationProcessor.KEYBINDING_ANNO;
import static org.nemesis.registration.LanguageFontsColorsProcessor.GROUP_SEMANTIC_HIGHLIGHTING_ANNO;
import static org.nemesis.registration.LanguageFontsColorsProcessor.SEMANTIC_HIGHLIGHTING_ANNO;
import com.mastfrog.annotation.processor.Delegates;
import static com.mastfrog.annotation.AnnotationUtils.AU_LOG;
import com.mastfrog.java.vogon.ClassBuilder;
import java.util.Map;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import static org.nemesis.registration.EditorFeaturesAnnotationProcessor.EDITOR_FEATURES_ANNO;
import static org.nemesis.registration.FoldRegistrationAnnotationProcessor.FOLD_REGISTRATION_ANNO;
import static org.nemesis.registration.FormattingRegistrationProcessor.FORMATTER_REGISTRATION_ANNO_TYPE;
import static org.nemesis.registration.ImportsAndResolvableProcessor.IMPORTS_ANNOTATION;
import static org.nemesis.registration.ImportsAndResolvableProcessor.RESOLVER_ANNOTATION;
import static org.nemesis.registration.InplaceRenameProcessor.INPLACE_RENAME_ANNO_TYPE;
import static org.nemesis.registration.KeybindingsAnnotationProcessor.ACTION_BINDINGS_ANNO;
import org.openide.util.lookup.ServiceProvider;
import static org.nemesis.registration.LanguageRegistrationProcessor.REGISTRATION_ANNO;
import org.nemesis.registration.typenames.KnownTypes;
import org.nemesis.registration.typenames.TypeName;

/**
 *
 * @author Tim Boudreau
 */
@ServiceProvider(service = Processor.class)
@SupportedAnnotationTypes({ANTLR_ACTION_ANNO_TYPE, REGISTRATION_ANNO, SEMANTIC_HIGHLIGHTING_ANNO,
    GROUP_SEMANTIC_HIGHLIGHTING_ANNO, GOTO_ANNOTATION, EDITOR_FEATURES_ANNO, FOLD_REGISTRATION_ANNO,
    FORMATTER_REGISTRATION_ANNO_TYPE, IMPORTS_ANNOTATION, RESOLVER_ANNOTATION, INPLACE_RENAME_ANNO_TYPE,
    IMPORTS_ANNOTATION, KEYBINDING_ANNO, GOTO_ANNOTATION, ACTION_BINDINGS_ANNO})
@SupportedOptions(AU_LOG)
public class LanguageRegistrationProcessor extends AbstractLayerGeneratingDelegatingProcessor {

    public static final String REGISTRATION_ANNO
            = "org.nemesis.antlr.spi.language.AntlrLanguageRegistration";

    @Override
    protected void installDelegates(Delegates delegates) {
        LanguageRegistrationDelegate main = new LanguageRegistrationDelegate();
        KeybindingsAnnotationProcessor keys = new KeybindingsAnnotationProcessor();
        LanguageFontsColorsProcessor proc = new LanguageFontsColorsProcessor();
        EditorFeaturesAnnotationProcessor features = new EditorFeaturesAnnotationProcessor();
        HighlighterKeyRegistrationProcessor hkProc = new HighlighterKeyRegistrationProcessor();
        FoldRegistrationAnnotationProcessor foldProc = new FoldRegistrationAnnotationProcessor();
        FormattingRegistrationProcessor formatting = new FormattingRegistrationProcessor();
        GotoDeclarationProcessor gotos = new GotoDeclarationProcessor();
        ImportsAndResolvableProcessor imports = new ImportsAndResolvableProcessor();
        InplaceRenameProcessor renames = new InplaceRenameProcessor();
        delegates
                .apply(main).to(ElementKind.CLASS)
                .whenAnnotationTypes(REGISTRATION_ANNO)
                .apply(features)
                .to(ElementKind.CLASS, ElementKind.FIELD, ElementKind.CONSTRUCTOR, ElementKind.METHOD)
                .whenAnnotationTypes(EDITOR_FEATURES_ANNO)
                .apply(foldProc)
                .to(ElementKind.FIELD)
                .whenAnnotationTypes(FOLD_REGISTRATION_ANNO)
                .apply(formatting)
                .to(ElementKind.CLASS)
                .whenAnnotationTypes(FORMATTER_REGISTRATION_ANNO_TYPE, REGISTRATION_ANNO)
                .apply(gotos)
                .to(ElementKind.FIELD, ElementKind.CLASS)
                .whenAnnotationTypes(GOTO_ANNOTATION, REGISTRATION_ANNO)
                .apply(imports)
                .to(ElementKind.FIELD)
                .whenAnnotationTypes(IMPORTS_ANNOTATION, RESOLVER_ANNOTATION)
                .apply(renames)
                .to(ElementKind.FIELD)
                .whenAnnotationTypes(INPLACE_RENAME_ANNO_TYPE, IMPORTS_ANNOTATION)
                .apply(keys).to(ElementKind.CLASS, ElementKind.METHOD, ElementKind.FIELD)
                .whenAnnotationTypes(KEYBINDING_ANNO, GOTO_ANNOTATION, ACTION_BINDINGS_ANNO)
                .apply(proc).to(ElementKind.CLASS, ElementKind.INTERFACE)
                .onAnnotationTypesAnd(REGISTRATION_ANNO)
                .to(ElementKind.FIELD).whenAnnotationTypes(SEMANTIC_HIGHLIGHTING_ANNO)
                .apply(proc).to(ElementKind.CLASS, ElementKind.INTERFACE)
                .onAnnotationTypesAnd(REGISTRATION_ANNO)
                .to(ElementKind.FIELD).whenAnnotationTypes(GROUP_SEMANTIC_HIGHLIGHTING_ANNO)
                .apply(hkProc)
                .to(ElementKind.FIELD)
                .whenAnnotationTypes(SEMANTIC_HIGHLIGHTING_ANNO)
                .apply(hkProc)
                .to(ElementKind.FIELD)
                .whenAnnotationTypes(GROUP_SEMANTIC_HIGHLIGHTING_ANNO);
    }

    static ClassBuilder<String> newClassBuilder(String pkg, String name, TypeName... imports) {
        ClassBuilder<String> result = ClassBuilder.forPackage(pkg).named(name);
        TypeName.addImports(result, imports);
        return result;
    }

    @Override
    protected boolean onRoundCompleted(Map<AnnotationMirror, Element> processed, RoundEnvironment env) throws Exception {
        if (!env.errorRaised() && env.processingOver()) {
            // Maven seems to swallow many diagnostic messages, so use
            // good old println which at least works
            System.out.println(KnownTypes.touchedMessage(this));
//            processingEnv.getMessager().printMessage(Diagnostic.Kind.MANDATORY_WARNING,
//                    KnownTypes.touchedMessage());
        }
        return super.onRoundCompleted(processed, env);
    }
}

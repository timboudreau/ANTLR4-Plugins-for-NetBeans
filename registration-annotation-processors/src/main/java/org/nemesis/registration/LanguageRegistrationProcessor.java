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
    GROUP_SEMANTIC_HIGHLIGHTING_ANNO, GOTO_ANNOTATION})
@SupportedOptions(AU_LOG)
public class LanguageRegistrationProcessor extends AbstractLayerGeneratingDelegatingProcessor {

    public static final String REGISTRATION_ANNO
            = "org.nemesis.antlr.spi.language.AntlrLanguageRegistration";

    @Override
    protected void installDelegates(Delegates delegates) {
        LanguageRegistrationDelegate main = new LanguageRegistrationDelegate();
        KeybindingsAnnotationProcessor keys = new KeybindingsAnnotationProcessor();
        LanguageFontsColorsProcessor proc = new LanguageFontsColorsProcessor();
        HighlighterKeyRegistrationProcessor hkProc = new HighlighterKeyRegistrationProcessor();
        delegates
                .apply(keys).to(ElementKind.CLASS, ElementKind.METHOD, ElementKind.FIELD)
                .whenAnnotationTypes(KEYBINDING_ANNO, GOTO_ANNOTATION)
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
                .whenAnnotationTypes(GROUP_SEMANTIC_HIGHLIGHTING_ANNO)
                .apply(main).to(ElementKind.CLASS)
                .whenAnnotationTypes(REGISTRATION_ANNO);
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

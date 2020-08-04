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

import static com.mastfrog.annotation.AnnotationUtils.AU_LOG;
import com.mastfrog.annotation.processor.AbstractDelegatingProcessor;
import com.mastfrog.annotation.processor.Delegates;
import java.util.Map;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedOptions;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import static org.nemesis.registration.CustomRefactoringDelegate.CUSTOM_REFACTORING_ANNO;
import static org.nemesis.registration.LocalizeAnnotationProcessor.inIDE;
import org.nemesis.registration.typenames.KnownTypes;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 * @author Tim Boudreau
 */
@ServiceProvider(service = Processor.class)
@SupportedAnnotationTypes({CUSTOM_REFACTORING_ANNO})
@SupportedOptions(AU_LOG)
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class CustomRefactoringProcessor extends AbstractDelegatingProcessor {

    @Override
    protected void installDelegates(Delegates delegates) {
        CustomRefactoringDelegate customRefactoring = new CustomRefactoringDelegate();
        delegates.apply(customRefactoring).to(ElementKind.FIELD)
                .whenAnnotationTypes(CUSTOM_REFACTORING_ANNO);
    }

    @Override
    protected boolean onRoundCompleted(Map<AnnotationMirror, Element> processed, RoundEnvironment env) throws Exception {
        if (!env.errorRaised() && env.processingOver() && !inIDE) {
            // Maven seems to swallow many diagnostic messages, so use
            // good old println which at least works
            System.out.println(KnownTypes.touchedMessage(this));
//            processingEnv.getMessager().printMessage(Diagnostic.Kind.MANDATORY_WARNING,
//                    KnownTypes.touchedMessage());
        }
        return super.onRoundCompleted(processed, env);
    }
}

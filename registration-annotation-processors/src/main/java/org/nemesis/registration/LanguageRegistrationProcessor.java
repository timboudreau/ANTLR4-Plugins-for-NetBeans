package org.nemesis.registration;

import org.nemesis.registration.api.AbstractLayerGeneratingRegistrationProcessor;
import javax.annotation.processing.Processor;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedOptions;
import javax.lang.model.element.ElementKind;
import static org.nemesis.registration.GotoDeclarationProcessor.GOTO_ANNOTATION;
import static org.nemesis.registration.KeybindingsAnnotationProcessor.ANTLR_ACTION_ANNO_TYPE;
import static org.nemesis.registration.KeybindingsAnnotationProcessor.KEYBINDING_ANNO;
import static org.nemesis.registration.LanguageFontsColorsProcessor.GROUP_SEMANTIC_HIGHLIGHTING_ANNO;
import static org.nemesis.registration.LanguageFontsColorsProcessor.SEMANTIC_HIGHLIGHTING_ANNO;
import org.nemesis.registration.api.Delegates;
import static org.nemesis.registration.utils.AnnotationUtils.AU_LOG;
import org.openide.util.lookup.ServiceProvider;
import static org.nemesis.registration.LanguageRegistrationProcessor.REGISTRATION_ANNO;

/**
 *
 * @author Tim Boudreau
 */
@ServiceProvider(service = Processor.class)
@SupportedAnnotationTypes({ANTLR_ACTION_ANNO_TYPE, REGISTRATION_ANNO, SEMANTIC_HIGHLIGHTING_ANNO,
    GROUP_SEMANTIC_HIGHLIGHTING_ANNO, GOTO_ANNOTATION})
@SupportedOptions(AU_LOG)
public class LanguageRegistrationProcessor extends AbstractLayerGeneratingRegistrationProcessor {

    public static final String REGISTRATION_ANNO
            = "org.nemesis.antlr.spi.language.AntlrLanguageRegistration";

    @Override
    protected void installDelegates(Delegates delegates) {
        LanguageRegistrationDelegate main = new LanguageRegistrationDelegate();
        KeybindingsAnnotationProcessor keys = new KeybindingsAnnotationProcessor();
        LanguageFontsColorsProcessor proc = new LanguageFontsColorsProcessor();
        HighlighterKeyRegistrationProcessor hkProc = new HighlighterKeyRegistrationProcessor();
        delegates
                .apply(keys).to(ElementKind.CLASS, ElementKind.METHOD)
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

}

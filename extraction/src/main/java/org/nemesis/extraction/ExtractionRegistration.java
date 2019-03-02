package org.nemesis.extraction;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.antlr.v4.runtime.ParserRuleContext;

/**
 * Registers a static method to contribute to the extraction of interesting
 * regions for a particular mime type and entry point rule context.
 * The annotation processor will generate an implementation of ExtractionContributor
 * in this package.
 * The method annotated may be public or package-private, and should take a
 * single argument of ExtractorBuilder&lt;EntryPointType&gt;. The containing
 * class may be public or package-private, but must not be a non-static inner
 * class.
 *
 * @author Tim Boudreau
 */
@Retention(RetentionPolicy.SOURCE)
@Target(value = ElementType.METHOD)
public @interface ExtractionRegistration {
    public static final String BASE_PATH = "antlr/extractors";
    /**
     * The mime type to register for.
     *
     * @return A mime type
     */
    String mimeType();

    /**
     * The class which is the top level element of the grammar.
     *
     * @return The top level element class for the grammar - usually one
     * which represents an entire file in the language.
     */
    Class<? extends ParserRuleContext> entryPoint();

}

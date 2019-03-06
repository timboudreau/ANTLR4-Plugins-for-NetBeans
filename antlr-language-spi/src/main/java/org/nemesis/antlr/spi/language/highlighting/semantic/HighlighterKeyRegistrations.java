package org.nemesis.antlr.spi.language.highlighting.semantic;

import static java.lang.annotation.ElementType.FIELD;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.SOURCE;
import java.lang.annotation.Target;

/**
 * Allows a key to be annotated with multiple highlighter key registrations, for
 * example, if the key is used in both mark-occurrences highlighting and also
 * some style which is applied to it always.
 *
 * @author Tim Boudreau
 */
@Retention(SOURCE)
@Target(FIELD)
public @interface HighlighterKeyRegistrations {

    HighlighterKeyRegistration[] value();
}

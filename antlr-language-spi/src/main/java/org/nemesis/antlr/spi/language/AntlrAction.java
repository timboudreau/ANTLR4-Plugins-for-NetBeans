package org.nemesis.antlr.spi.language;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.SOURCE;
import java.lang.annotation.Target;

/**
 * Annotation to register actions against an antlr file type.
 *
 * @author Tim Boudreau
 */
@Retention(SOURCE)
@Target({TYPE, METHOD})
public @interface AntlrAction {

    String mimeType();

    int order();
}

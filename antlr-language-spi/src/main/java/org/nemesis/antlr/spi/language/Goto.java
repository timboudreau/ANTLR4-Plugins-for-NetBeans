package org.nemesis.antlr.spi.language;

import static java.lang.annotation.ElementType.FIELD;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.SOURCE;
import java.lang.annotation.Target;

/**
 * Annotation which can be placed on a reference key to automatically allow the
 * goto-declaration action to work against source elements that are references
 * stored in the extraction under that key.
 *
 * @author Tim Boudreau
 */
@Retention(SOURCE)
@Target(FIELD)
public @interface Goto {

    String mimeType();
}

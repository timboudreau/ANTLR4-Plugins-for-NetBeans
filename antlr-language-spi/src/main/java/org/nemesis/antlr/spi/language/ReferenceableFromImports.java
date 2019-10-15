package org.nemesis.antlr.spi.language;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 *
 * @author Tim Boudreau
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.FIELD)
public @interface ReferenceableFromImports {

    String mimeType();
}

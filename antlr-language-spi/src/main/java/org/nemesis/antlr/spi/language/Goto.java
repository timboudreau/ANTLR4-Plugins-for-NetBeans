package org.nemesis.antlr.spi.language;

import static java.lang.annotation.ElementType.FIELD;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.SOURCE;
import java.lang.annotation.Target;

/**
 *
 * @author Tim Boudreau
 */
@Retention(SOURCE)
@Target(FIELD)
public @interface Goto {
    String mimeType();
}

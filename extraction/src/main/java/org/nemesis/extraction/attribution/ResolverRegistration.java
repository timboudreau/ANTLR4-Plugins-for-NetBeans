package org.nemesis.extraction.attribution;

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
public @interface ResolverRegistration {

    String mimeType();
}

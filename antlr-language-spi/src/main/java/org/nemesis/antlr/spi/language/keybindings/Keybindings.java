package org.nemesis.antlr.spi.language.keybindings;

import static java.lang.annotation.ElementType.METHOD;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.SOURCE;
import java.lang.annotation.Target;

/**
 *
 * @author Tim Boudreau
 */
@Retention(SOURCE)
@Target(METHOD)
public @interface Keybindings {

    String mimeType();

    String displayName();

    String description() default "";

    String name() default "";

    String menuPath() default "";

    boolean popup() default false;

    Keybinding[] keybindings();
}

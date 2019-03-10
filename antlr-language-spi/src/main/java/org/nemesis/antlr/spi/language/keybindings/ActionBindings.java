package org.nemesis.antlr.spi.language.keybindings;

import static java.lang.annotation.ElementType.*;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.SOURCE;
import java.lang.annotation.Target;

/**
 *
 * @author Tim Boudreau
 */
@Retention(SOURCE)
@Target({FIELD, TYPE, METHOD, CONSTRUCTOR})
public @interface ActionBindings {

}

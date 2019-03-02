package org.nemesis.antlr.spi.language.highlighting;

import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.SOURCE;
import java.lang.annotation.Target;

/**
 * A named categroy for a set of colorings that apply to different themes.
 *
 * @author Tim Boudreau
 */
@Retention(SOURCE)
@Target({})
public @interface ColoringCategory {

    /**
     * The name (will appear in the Tools | Options Fonts & Colors dialog).
     *
     * @return A name
     */
    String name();

    Coloration[] colors();
}

package org.nemesis.antlr.navigator;

import static java.lang.annotation.ElementType.FIELD;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.SOURCE;
import java.lang.annotation.Target;
import org.nemesis.antlr.navigator.NavigatorPanelConfig.Appearance;

/**
 * Apply this annotation to a NamedRegionKey to create a navigator
 * panel for that region.
 *
 * @author Tim Boudreau
 */
@Retention(SOURCE)
@Target(FIELD)
public @interface SimpleNavigatorRegistration {

    /**
     * The mime type this navigator panel is for.
     *
     * @return A mime type
     */
    String mimeType();

    /**
     * The order among all such navigator panels for this one.
     *
     * @return The order
     */
    int order() default Integer.MAX_VALUE;

    /**
     * An icon to use for all items (if you are not going to
     * supply an Appearance using the <code>appearance()</code>
     * method).
     *
     * @return An icon - either just the file name if it is in the same
     * package as the class defining this annotation, or a classpath path.
     */
    String icon() default "";

    /**
     * The display name.  If prefixed with #, will look for a bundle key
     * with the name.
     *
     * @return A display name or bundle key
     */
    String displayName() default "";

    /**
     * Object which customizes the appearance of the items (icon,
     * display name, font properties, etc.).
     *
     * @return An appearance type
     */
    Class<? extends Appearance<?>> appearance() default NavigatorPanelConfig.NoAppearance.class;
}

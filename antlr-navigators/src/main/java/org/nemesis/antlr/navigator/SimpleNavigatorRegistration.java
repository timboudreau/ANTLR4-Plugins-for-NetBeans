/*
 * Copyright 2016-2019 Tim Boudreau, Frédéric Yvon Vinet
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.nemesis.antlr.navigator;

import static java.lang.annotation.ElementType.FIELD;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.SOURCE;
import java.lang.annotation.Target;
import org.nemesis.antlr.navigator.Appearance;

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
    Class<? extends Appearance<?>> appearance() default DefaultAppearance.class;

    /**
     * If true, the panel will have the ability to update the selection
     * and scroll when the editor caret position changes.
     *
     * @return Whether or not to track the caret
     */
    boolean trackCaret() default false;
}

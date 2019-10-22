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
package org.nemesis.antlr.spi.language.highlighting;

import java.lang.annotation.Retention;

import static java.lang.annotation.RetentionPolicy.SOURCE;

import java.lang.annotation.Target;


/**
 * Defines the coloring and font colors to use for a token category,
 * for multiple themes (since several dark and light themes are popular).
 */
@Retention( SOURCE )
@Target( {} )
public @interface Coloration {

    /**
     * Constant which, if listed in the themes array, will be expanded to the
     * list of all popular and preinstalled <i>dark</i> themes for NetBeans.
     */
    public static final String POPULAR_DARK_THEMES = "dark";
    /**
     * Constant which, if listed in the themes array, will be expanded to the
     * list of all popular and preinstalled <i>light</i> themes for NetBeans.
     */
    public static final String POPULAR_BRIGHT_THEMES = "light";
    /**
     * Constant which, if listed in the themes array, will be expanded to the
     * list of all popular and preinstalled <i>light <b>and</b> dark</i>
     * themes for NetBeans - use this for styles which either do not affect
     * font color, or which are <i>truly</i> readable on both light and
     * dark background for a person with reasonable eyesight.
     */
    public static final String ALL_POPULAR_THEMES = "all";

    /**
     * An array of theme names. These are, specifically, the <i>folder name</i> of
     * editor themes in the system filesystem underneath <code>Editors/FontsColors</code>.
     * If the names "dark" or "light" are included, that name will be expanded to
     * <i>all built-in and popular theme names</i> - since generally, you simply need
     * to specify one set of colors for dark themes and another for light themes.
     * <p>
     * Currently they are expanded as follows:
     * </p>
     * <ul>
     * <li><b>light</b> - expands to
     * <code>{ "NetBeans", "NetBeans55", "NetbeansEarth", "Tan", "NetBeans_Solarized_Light"}</code></li>
     * <li><b>dark</b> - expands to
     * <code>{ "BlueTheme", "Darcula", "CityLights", "NetBeans_Solarized_Dark" }</code></li>
     * <li><b>all</b> - expands to the combination of the above too lists, and should
     * be used for things that just affect bold or italic properties, or with care, for colors
     * which are <i>truly</i> readable on both light and dark backgrounds for a
     * person with reasonable eyesight.</li>
     * </ul>
     * <p>
     * These lists may grow or change without notice, but will not affect your module unless
     * you recompile it, since the themes list is used to generate files your module registers
     * in the system filesystem.
     * </p>
     *
     * @return An array of theme names.
     */
    String[] themes() default {};

    /**
     * The foreground color, as a red / green / blue / alpha array
     * of color values from 0 to 255. Alpha may be omitted if not used;
     * full opacity is 255. If unspecified, the editor's inherited or
     * default color is used.
     *
     * @return A RGB or RGBA array
     */
    int[] fg() default {};

    /**
     * The background color, as a red / green / blue / alpha array
     * of color values from 0 to 255. Alpha may be omitted if not used;
     * full opacity is 255. If unspecified, the editor's inherited or
     * default color is used.
     *
     * @return A RGB or RGBA array
     */
    int[] bg() default {};

    /**
     * The underline color (if any), as a red / green / blue / alpha array
     * of color values from 0 to 255. Alpha may be omitted if not used;
     * full opacity is 255. If omitted, no underline will be present.
     *
     * @return A RGB or RGBA array
     */
    int[] underline() default {};

    /**
     * The wave-underline color (if any), as a red / green / blue / alpha array
     * of color values from 0 to 255 - usually used for marking errors,
     * but can be used as you wish. Alpha may be omitted if not used;
     * full opacity is 255. If omitted, no wave underline will be present.
     *
     * @return A RGB or RGBA array
     */
    int[] waveUnderline() default {};

    /**
     * A default coloring name (from the global set of predefined colors)
     * to use in the case no color is specified, or as a fallback. These
     * should be programmatic names of token categories which exist for all
     * languages and are predefined by the editor API.
     *
     * @return A name. The default is "default" = the coloring for unknown files
     *         (by default, black-on-white)
     */
    String derivedFrom() default "default";

    /**
     * If this returns true, use a bold font.
     *
     * @return True if the font is to be bold
     */
    boolean bold() default false;

    /**
     * If this returns true, use a italic font.
     *
     * @return True if the font is to be italic
     */
    boolean italic() default false;

    /*
     // PENDING: Allow nested semantic regions to
     // shift an HSB component
    DepthTransform[] depthTransforms() default {};

    @Retention( SOURCE )
    @Target( TYPE )
    public @interface DepthTransform {
        DepthTransformType[] types() default HUE;

        float value() default 0.0125F;
    }

    public enum DepthTransformType {
        HUE,
        SATURATION,
        BRIGHTNESS
    }
    */
}

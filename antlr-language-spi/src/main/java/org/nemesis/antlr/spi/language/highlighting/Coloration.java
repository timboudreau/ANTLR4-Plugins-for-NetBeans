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
@Retention(SOURCE)
@Target({})
public @interface Coloration {

    /**
     * An array of theme names.  The default is simply NetBeans.
     *
     * @return An array of theme names.
     */
    String[] themes() default "NetBeans";

    /**
     * The foreground color, as a red / green / blue / alpha array
     * of color values from 0 to 255.  Alpha may be omitted if not used;
     * full opacity is 255.  If unspecified, the default is used.
     *
     * @return A RGB or RGBA array
     */
    int[] fg() default {};

    /**
     * The background color, as a red / green / blue / alpha array
     * of color values from 0 to 255.  Alpha may be omitted if not used;
     * full opacity is 255.  If unspecified, the default is used.
     *
     * @return A RGB or RGBA array
     */
    int[] bg() default {};

    /**
     * The underline color (if any), as a red / green / blue / alpha array
     * of color values from 0 to 255.  Alpha may be omitted if not used;
     * full opacity is 255.
     *
     * @return A RGB or RGBA array
     */
    int[] underline() default {};

    /**
     * The wave-underline color (if any), as a red / green / blue / alpha array
     * of color values from 0 to 255.  Alpha may be omitted if not used;
     * full opacity is 255.
     *
     * @return A RGB or RGBA array
     */
    int[] waveUnderline() default {};

    /**
     * A default coloring name (from the global set of predefined colors)
     * to use in the case no color is specified, or as a fallback.  These
     * should be programmatic names of token categories which exist for all
     * languages and are predefined by the editor.
     *
     * @return A name.  The default is "default" = the coloring for unknown files
     * (by default, black-on-white)
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

}

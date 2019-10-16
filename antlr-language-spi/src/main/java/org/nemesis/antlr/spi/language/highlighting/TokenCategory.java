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
 * Defines one category of tokens, used for colorizing tokens in the editor.
 */
@Retention(SOURCE)
@Target({})
public @interface TokenCategory {

    /**
     * The name of this token category.
     *
     * @return A name
     */
    String name();

    /**
     * All tokens which belong to this category (they will be static fields on
     * your generated Antlr lexer - use those here, as numbers can change when
     * the grammar is edited).
     *
     * @return An array of token ids for this category
     */
    int[] tokenIds() default {};

    /**
     * All parser rules which belong to this category (they will be static
     * fields on your generated Antlr <i>parser</i> with variable names prefixed
     * with <code>RULE_</code>, e.g. <code>RULE_methodArguments</code> - use
     * those here, as numbers can change when the grammar is edited).
     *
     * @return An array of token ids for this category
     */
    int[] parserRuleIds() default {};

    /**
     * The colors to use for this token category - each Coloration can specify
     * multiple themes (NetBeans, Darkula, etc.) they apply to.
     *
     * @return An array of coloring configurations for different editor themes
     */
    Coloration[] colors() default {};

}

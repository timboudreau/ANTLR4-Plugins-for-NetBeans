/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
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
     * All tokens which belong to this category (they will be static
     * fields on your generated Antlr lexer - use those here, as
     * numbers can change when the grammar is edited).
     *
     * @return An array of token ids for this category
     */
    int[] tokenIds() default {};

    /**
     * The colors to use for this token category - each Coloration can
     * specify multiple themes (NetBeans, Darkula, etc.) they apply
     * to.
     *
     * @return An array of coloring configurations for different editor
     * themes
     */
    Coloration[] colors() default {};

}

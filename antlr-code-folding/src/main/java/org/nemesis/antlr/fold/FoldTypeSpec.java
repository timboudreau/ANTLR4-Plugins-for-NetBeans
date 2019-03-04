/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nemesis.antlr.fold;

/**
 * Defines a new fold type, with template.
 *
 * @author Tim Boudreau
 */
public @interface FoldTypeSpec {

    /**
     * The programmatic name of this fold type, used to differentiate it.
     *
     * @return A name
     */
    String name();

    /**
     * The display name of this fold type.
     *
     * @return The display name
     */
    String displayName() default "";

    /**
     * Parameter to FoldTemplate constructor.
     *
     * @return zero
     */
    int guardedStart() default 0;

    /**
     * Parameter to FoldTemplate constructor.
     *
     * @return zero
     */
    int guardedEnd() default 0;

    /**
     * Set the display text for this fold type, used if no name can be derived
     * from the object being folded.
     *
     * @return Localized display text
     */
    String displayText() default "";
}

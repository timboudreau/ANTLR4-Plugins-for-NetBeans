/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nemesis.antlr.fold;

/**
 *
 * @author Tim Boudreau
 */
public @interface FoldTypeSpec {

    String name();

    String displayName() default "";

    int guardedStart() default 0;

    int guardedEnd() default 0;

    /**
     * Set the display text to use
     * 
     * @return 
     */
    String displayText() default "";

    /*
    public static final FoldType COMMENT_FOLD_TYPE = FoldType.create
                   ("comment"           ,
                    Bundle.comment()           ,
                    new FoldTemplate
                        (2             , // length of the guarded starting token
                         2             , // length of the guarded end token
                         FOLDED_COMMENT));
    private static final String FOLDED_ACTION = "{...}";
    public static final FoldType ACTION_FOLD_TYPE = FoldType.create
                   ("action"            ,
                    Bundle.action()            ,
                    new FoldTemplate
                        (1             , // length of the guarded starting token
                         1             , // length of the guarded end token
                         FOLDED_ACTION));
    private static final String FOLDED_RULE = "<rule>";
    public static final FoldType RULE_FOLD_TYPE = FoldType.create
                   ("rule"            ,
                    Bundle.rule()            ,
                    new FoldTemplate
                        (0             , // length of the guarded starting token
                         0             , // length of the guarded end token
                         FOLDED_RULE));
     */
}

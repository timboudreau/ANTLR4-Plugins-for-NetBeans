package org.nemesis.antlr.fold;

/**
 * Mirrors the constants on org.netbeans.spi.editor.fold.FoldType, for use in an
 * annotation.
 *
 * @see org.netbeans.api.editor.fold.FoldType
 * @author Tim Boudreau
 */
public enum FoldTypeName {
    /**
     * @see org.netbeans.api.editor.fold.FoldType.CODE_BLOCK
     */
    CODE_BLOCK, 
    /**
     * @see org.netbeans.api.editor.fold.FoldType.DOCUMENTATION
     */
    DOCUMENTATION,
    /**
     * @see org.netbeans.api.editor.fold.FoldType.COMMENT
     */
    COMMENT,
    /**
     * @see org.netbeans.api.editor.fold.FoldType.INITIAL_COMMENT
     */
    INITIAL_COMMENT,
    /**
     * @see org.netbeans.api.editor.fold.FoldType.TAG
     */
    TAG,
    /**
     * @see org.netbeans.api.editor.fold.FoldType.NESTED
     */
    NESTED,
    /**
     * @see org.netbeans.api.editor.fold.FoldType.MEMBER
     */
    MEMBER,
    /**
     * @see org.netbeans.api.editor.fold.FoldType.IMPORT
     */
    IMPORT,
    /**
     * @see org.netbeans.api.editor.fold.FoldType.USER
     */
    USER
}

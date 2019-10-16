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

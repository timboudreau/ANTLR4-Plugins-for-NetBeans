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

import static java.lang.annotation.ElementType.FIELD;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.SOURCE;
import java.lang.annotation.Target;

/**
 * Annotate a static field whose type is RegionKey or NamedRegionKey to register
 * code folding for a mime type. Multiple fields can have this annotation for
 * the same type, and will be combined to provide aggregate code folding.
 * <p>
 * This annotation will result in several layer registration entries being
 * generated, and a fold registration class.
 * </p>
 *
 * @see org.nemesis.antlr.fold.KeyToFoldConverter
 * @author Tim Boudreau
 */
@Retention(SOURCE)
@Target(FIELD)
public @interface AntlrFoldsRegistration {

    /**
     * The mime type to register for.
     *
     * @return The mime type
     */
    String mimeType();

    /**
     * If desired, use a custom fold template which will be returned by the
     * class specified here (note the fold template also must be registered
     * separately, if you are writing one from scratch) - this attribute is
     * <i>mutually exclusive</i> with <code>foldType()</code>.
     *
     * @return
     */
    Class<? extends KeyToFoldConverter<?>> converter() default DefaultKeyToFoldConverter.class;

    /**
     * Specify a stock fold type of the ones included in NetBeans which exists
     * as a named constant on org.netbeans.spi.editor.fold.FoldType - this
     * attribute is <i>mutually exclusive</i> with <code>converter()</code>.
     *
     * @see org.netbeans.spi.editor.fold.FoldType
     * @return A fold type name
     */
    public FoldTypeName foldType() default FoldTypeName.CODE_BLOCK;

    /**
     * Specify a new fold type which will be created and registered - this
     * attribute is <i>mutually exclusive</i> with <code>converter()</code>.
     *
     * @see org.netbeans.spi.editor.fold.FoldType
     * @return A fold type name
     */
    public FoldTypeSpec foldSpec() default @FoldTypeSpec(name = "");

    /**
     * The sort order of this particular folding item, which may determine in
     * the case of overlapping folds, which one wins.
     *
     * @return
     */
    int position() default -1;
}

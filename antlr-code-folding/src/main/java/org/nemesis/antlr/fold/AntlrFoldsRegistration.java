/*
BSD License

Copyright (c) 2016-2018, Frédéric Yvon Vinet, Tim Boudreau
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

* Redistributions of source code must retain the above copyright
  notice, this list of conditions and the following disclaimer.
* Redistributions in binary form must reproduce the above copyright
  notice, this list of conditions and the following disclaimer in the
  documentation and/or other materials provided with the distribution.
* The name of its author may not be used to endorse or promote products
  derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR IMPLIED
WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT
SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT
OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING
IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY
OF SUCH DAMAGE.
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

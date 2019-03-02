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
package org.nemesis.antlr.navigator;

import static java.lang.annotation.ElementType.METHOD;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.SOURCE;
import java.lang.annotation.Target;

/**
 * Register an Antlr navigator panel. This annotation applies only to
 * static methods which return an {@link NavigatorPanelConfig}.
 *
 * @see org.nemesis.antlr.navigator.NavigatorPanelConfig.Builder
 * @author Tim Boudreau
 */
@Retention(SOURCE)
@Target(METHOD)
public @interface AntlrNavigatorPanelRegistration {

    /**
     * The mime type to register against.
     *
     * @return A mime type
     */
    String mimeType();

    /**
     * The ad-hoc sort order of this panel relative to others of the same mime
     * type.
     *
     * @return An ordering integer, or Integer.MAX_VALUE
     */
    int order() default Integer.MAX_VALUE;

    /**
     * A non-localized display name - this is here in order to generate a
     * NavigatorPanel.Registration on generated code.  It is superseded by
     * the displayName() property of the panel config.
     *
     * @return A dummy display name by default
     */
    String displayName() default "-";
}

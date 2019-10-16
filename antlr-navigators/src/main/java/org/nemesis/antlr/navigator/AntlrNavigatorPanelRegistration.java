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

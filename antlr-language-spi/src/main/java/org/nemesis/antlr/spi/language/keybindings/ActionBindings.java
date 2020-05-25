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
package org.nemesis.antlr.spi.language.keybindings;

import static java.lang.annotation.ElementType.*;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.SOURCE;
import java.lang.annotation.Target;

/**
 * Bind an existing action by name to a set of keystrokes in the
 * editor kit for a particular mime type - this annotation is just
 * for tying <i>preexisting actions</i> to a particular MIME type's
 * editor kit. To generate new actions with code you write, use
 * the {@link Keybindings} and {@link KeyBinding} annotations; this
 * annotation may then be used to associate them with a particular
 * editor kit, as opposed to just popup and main menu bindings.
 *
 * @author Tim Boudreau
 */
@Retention( SOURCE )
@Target( { FIELD, TYPE, METHOD, CONSTRUCTOR } )
public @interface ActionBindings {
    /**
     * The mime type these bindings apply to.
     *
     * @return The mime type
     */
    String mimeType();

    /**
     * The set of action bindings you want to bind.
     *
     * @return The bindings
     */
    ActionBinding[] bindings();
}

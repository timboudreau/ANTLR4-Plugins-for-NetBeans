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

/**
 * Allows a custom keybinding to be set up for an existing NetBeans action
 * for a particular mime type and profile. One of <code>
 * actionName()</code> or <code>action()</code> must be specified
 * (but not both or neither) to indicate what action the keybindings
 * should trigger.
 *
 * @author Tim Boudreau
 */
public @interface ActionBinding {

    /**
     * One of the set of predefined actions (mostly defined on ExtKit
     * in the editor api).
     *
     * @return A built in action
     */
    BuiltInAction action() default BuiltInAction.NONE;

    /**
     * The name of an action you have defined or know is defined in
     * the application.
     *
     * @return The action name
     */
    String actionName() default "";

    /**
     * The set of (at least one) keybindings to associate; there may be multiple
     * ones (for example, some keyboards have a Find key in addition
     * to CTRL-F, and both should be bound).
     *
     * @return An array of keybindings
     */
    Keybinding[] bindings();
}

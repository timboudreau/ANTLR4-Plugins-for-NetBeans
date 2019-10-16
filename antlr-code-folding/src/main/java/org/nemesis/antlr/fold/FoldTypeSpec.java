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

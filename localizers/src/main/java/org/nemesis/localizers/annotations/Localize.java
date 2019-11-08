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

package org.nemesis.localizers.annotations;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.TYPE;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.SOURCE;
import java.lang.annotation.Target;

/**
 * Allows Enum constants and ExtractionKeys to have localized names
 * and icons attached via annotations and looked up at runtime.
 *
 *
 * @author Tim Boudreau
 */
@Retention(SOURCE)
@Target({FIELD, TYPE, ANNOTATION_TYPE})
public @interface Localize {
    static final String NO_VALUE = "///";

    String displayName() default NO_VALUE;
    String iconPath() default NO_VALUE;
    KeyValuePair[] hints() default {};

    public @interface KeyValuePair {
        String key();
        String value();
    }
}

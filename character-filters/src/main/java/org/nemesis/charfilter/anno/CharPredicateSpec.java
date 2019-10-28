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
package org.nemesis.charfilter.anno;

import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.CLASS;
import java.lang.annotation.Target;
import org.nemesis.charfilter.CharPredicate;
import org.nemesis.charfilter.CharPredicates;

/**
 * Annotation to flexibly define a character predicates. If multiple are
 * defined, they will be or'd together unless logicallyOr is false, in which
 * case they will be logically and'd.
 *
 * @author Tim Boudreau
 */
@Retention(CLASS)
@Target({})
public @interface CharPredicateSpec {

    char[] including() default {};

    Class<? extends CharPredicate> instantiate() default CharPredicate.class;

    CharPredicates[] include() default {};

    boolean logicallyOr() default true;
}

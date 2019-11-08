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
package org.nemesis.antlr.refactoring.usages;

import org.netbeans.modules.refactoring.api.Problem;

/**
 *
 * @author Tim Boudreau
 */
@FunctionalInterface
public interface PetaFunction<A, B, C, D, E> {

    Problem accept(A a, B b, C c, D d, E e);

//    default PetaConsumer<A, B, C, D, E> andThen(PetaConsumer<A, B, C, D, E> next) {
//        return (a, b, c, d, e) -> {
//            this.accept(a, b, c, d, e);
//            next.accept(a, b, c, d, e);
//        };
//    }
}

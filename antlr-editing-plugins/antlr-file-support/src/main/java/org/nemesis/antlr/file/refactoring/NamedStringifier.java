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
package org.nemesis.antlr.file.refactoring;

import com.mastfrog.abstractions.Named;
import com.mastfrog.abstractions.Stringifier;

/**
 *
 * @author Tim Boudreau
 */
final class NamedStringifier implements Stringifier<Named> {

    static final NamedStringifier INSTANCE = new NamedStringifier();

    @Override
    public String toString(Named obj) {
        return obj.name();
    }

    @Override
    public String toString() {
        return "Stringifier<Named>";
    }

    public static Stringifier<Object> generic() {
        return obj -> {
            if (obj instanceof Named) {
                return ((Named) obj).name();
            }
            return obj.toString();
        };
    }
}

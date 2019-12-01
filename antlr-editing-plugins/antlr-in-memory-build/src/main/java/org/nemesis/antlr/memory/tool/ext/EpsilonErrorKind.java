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
package org.nemesis.antlr.memory.tool.ext;

/**
 * Avoid exposing Antlr tool types externally.
 */
public enum EpsilonErrorKind {
    EPSILON_CLOSURE, EPSILON_LR_FOLLOW, EPSILON_OPTIONAL, EPSILON_TOKEN;

    public int antlrErrorCode() {
        switch (this) {
            case EPSILON_TOKEN :
                return 146;
            case EPSILON_CLOSURE:
                return 153;
            case EPSILON_LR_FOLLOW:
                return 148;
            case EPSILON_OPTIONAL:
                return 154;
            default:
                return -1;
        }
    }

}

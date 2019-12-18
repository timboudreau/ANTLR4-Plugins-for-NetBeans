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
package org.nemesis.antlr.file.editor.ext;

/**
 *
 * @author Tim Boudreau
 */
public enum OpType {
    ON_BEFORE_TYPING_INSERT,
    ON_TYPING_INSERT,
    ON_AFTER_TYPING_INSERT,
    ON_TYPING_CANCELLED,

    ON_BEFORE_REMOVE,
    ON_REMOVE,
    ON_AFTER_REMOVE,
    ON_REMOVE_CANCELLED,

    ON_BEFORE_BREAK_INSERT,
    ON_BREAK_INSERT,
    ON_AFTER_BREAK_INSERT,
    ON_BREAK_INSERT_CANCELLED
}

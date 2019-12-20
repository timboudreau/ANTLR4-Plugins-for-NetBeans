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
package com.mastfrog.editor.features;

/**
 * Unifies the operations the various editor api text processors can call. Not
 * all methods will be called for all types.
 *
 * @author Tim Boudreau
 */
interface EditProcessor {

    default void onInsert(ContextWrapper ctx) {
        // do nothing
    }

    default void onAfterInsert(ContextWrapper ctx) {
        // do nothing
    }

    default void cancelled(ContextWrapper ctx) {
        // do nothing
    }

    default void onRemove(ContextWrapper ctx) {
        // do nothing
    }

    default void onAfterRemove(ContextWrapper ctx) {
        // do nothing
    }

    default void onBeforeInsert(ContextWrapper ctx) {
        // do nothing
    }

    default void onBeforeRemove(ContextWrapper contextWrapper) {
        // do nothing
    }

    /**
     * Only relevant to processor factories that handle BEFORE_* phases and want
     * to entirely take over the edit, causing the original keys stroke not to
     * be processed by anything else.
     *
     * @return True if this factory either ignores the keystroke or handles it
     * entirely itself
     */
    default boolean consumesInitialEvent() {
        return false;
    }
}

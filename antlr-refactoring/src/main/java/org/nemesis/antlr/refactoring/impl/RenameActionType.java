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
package org.nemesis.antlr.refactoring.impl;

public enum RenameActionType {
    NOT_ALLOWED,
    INPLACE,
    INPLACE_AUGMENTED,
    USE_REFACTORING_API,
    POST_PROCESS,
    NOTHING_FOUND;

    public boolean isStandalone() {
        switch (this) {
            case INPLACE:
            case NOT_ALLOWED:
            case INPLACE_AUGMENTED:
            case USE_REFACTORING_API:
            case NOTHING_FOUND:
                return true;
            default:
                return false;
        }
    }

    public boolean isRefactor() {
        return this == USE_REFACTORING_API;
    }

    public boolean isInplaceProceed() {
        switch (this) {
            case INPLACE:
            case INPLACE_AUGMENTED:
            case POST_PROCESS:
                return true;
            default:
                return false;
        }
    }

    public boolean isPassChanges() {
        return this == INPLACE_AUGMENTED;
    }

    public boolean proceedWithInplace() {
        switch (this) {
            case INPLACE:
            case INPLACE_AUGMENTED:
            case POST_PROCESS:
                return true;
            default:
                return false;
        }
    }

    public boolean isVeto() {
        return NOT_ALLOWED == this;
    }

    public String toString() {
        return name().toLowerCase();
    }
}

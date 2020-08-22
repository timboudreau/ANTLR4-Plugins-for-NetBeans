/*
 * Copyright 2016-2020 Tim Boudreau, Frédéric Yvon Vinet
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
package org.nemesis.antlrformatting.api;

import com.mastfrog.function.IntBiPredicate;

/**
 *
 * @author Tim Boudreau
 */
enum OP implements IntBiPredicate {
    GREATER(">"), GREATER_OR_EQUAL(">="), LESS("<"), LESS_OR_EQUAL("<="), EQUAL("=="), UNSET("-unset"), TRUE("==true"), FALSE("==false"), NOT_EQUAL("!=");
    private final String stringValue;

    OP(String stringValue) {
        this.stringValue = stringValue;
    }

    boolean takesArgument() {
        switch (this) {
            case UNSET:
            case FALSE:
            case TRUE:
                return false;
            default:
                return true;
        }
    }

    public String toString() {
        return stringValue;
    }

    @Override
    public boolean test(int a, int b) {
        switch (this) {
            case EQUAL:
                return a == b;
            case FALSE:
                return false;
            case TRUE:
                return true;
            case UNSET:
                return a == -1 && b == -1;
            case GREATER:
                return a > b;
            case GREATER_OR_EQUAL:
                return a >= b;
            case LESS:
                return a < b;
            case LESS_OR_EQUAL:
                return a <= b;
            case NOT_EQUAL:
                return a != b;
            default:
                throw new AssertionError(this);
        }
    }

}

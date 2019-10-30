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
package org.nemesis.charfilter;

/**
 *
 * @author Tim Boudreau
 */
final class AllOrNothing implements CharFilter, CharPredicate {

    private final boolean result;

    public AllOrNothing(boolean result) {
        this.result = result;
    }

    @Override
    public boolean test(boolean isInitial, char typed) {
        return result;
    }

    @Override
    public boolean test(char c) {
        return result;
    }

    @Override
    public String toString() {
        return result ? "<match-everything>" : "<match-nothing>";
    }

}

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
package org.nemesis.antlrformatting.api;

/**
 * Builder for criteria that activate a rule only if a value extracted into the
 * lexing state matches a desired value, or the value of another key from the
 * lexing state.
 *
 * @see org.nemesis.antlrformatting.api.FormattingRule
 * @author Tim Boudreau
 */
public abstract class LexingStateCriteriaBuilder<T extends Enum<T>, R> {

    LexingStateCriteriaBuilder() {

    }

    public abstract R isNotEqualTo(T key);

    public abstract R isGreaterThan(int value);

    public abstract R isGreaterThanOrEqualTo(int value);

    public abstract R isLessThan(int value);

    public abstract R isLessThanOrEqualTo(int value);

    public abstract R isGreaterThan(T key);

    public abstract R isGreaterThanOrEqualTo(T key);

    public abstract R isEqualTo(T key);

    public abstract R isLessThan(T key);

    public abstract R isLessThanOrEqualTo(T key);

    public abstract R isEqualTo(int value);

    public abstract R isUnset();

    public abstract R isTrue();

    public abstract R isFalse();

    public abstract R isSet();
}

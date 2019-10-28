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
public enum CharPredicates implements CharPredicate {

    ALPHABETIC,
    NUMERIC,
    PUNCTUATION,
    WHITESPACE,
    ISO_CONTROL_CHARS,
    JAVA_IDENTIFIER_START,
    JAVA_IDENTIFIER_PART;

    @Override
    public boolean test(char c) {
        switch (this) {
            case ALPHABETIC:
                return Character.isAlphabetic(c);
            case NUMERIC:
                return Character.isDigit(c);
            case PUNCTUATION:
                return !Character.isWhitespace(c)
                        && !Character.isISOControl(c)
                        && !Character.isDigit(c)
                        && !Character.isAlphabetic(c);
            case ISO_CONTROL_CHARS:
                return Character.isISOControl(c);
            case JAVA_IDENTIFIER_PART:
                return Character.isJavaIdentifierPart(c);
            case JAVA_IDENTIFIER_START:
                return Character.isJavaIdentifierStart(c);
            case WHITESPACE:
                return Character.isWhitespace(c);
            default:
                throw new AssertionError(this);
        }
    }
}

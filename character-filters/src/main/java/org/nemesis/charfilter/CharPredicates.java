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
 * Enum of standard, useful character predicates, largely based on static
 * methods of <code>java.lang.Character</code>
 *
 * @see java.lang.Character
 *
 * @author Tim Boudreau
 */
public enum CharPredicates implements CharPredicate {

    /**
     * Wraps Character.isAlphabetic().
     */
    ALPHABETIC,
    /**
     * Wraps Character.isDigit().
     */
    DIGIT,
    /**
     * Returns whether a character is punctuation by process of elimination: If
     * it is
     * <ul>
     * <li>Not whitespace</li>
     * <li>Not an ISO control character</li>
     * <li>Not a digit</li>
     * <li>Not alphabetic</li>
     * <li>Not a letter</li>
     * </ul>
     * then it is considered one.
     */
    PUNCTUATION,
    /**
     * Wraps Character.isWhitespace().
     */
    WHITESPACE,
    /**
     * Wraps Character.isISOControl().
     */
    ISO_CONTROL_CHARS,
    /**
     * Wraps Character.isJavaIdentifierStart().
     */
    JAVA_IDENTIFIER_START,
    /**
     * Wraps Character.isJavaIdentifierPart().
     */
    JAVA_IDENTIFIER_PART,
    /**
     * Wraps Character.isIdentifierIgnorable().
     */
    JAVA_IDENTIFIER_IGNORABLE,
    /**
     * Wraps Character.isUpperCase().
     */
    UPPER_CASE,
    /**
     * Wraps Character.isLowerCase().
     */
    LOWER_CASE,
    /**
     * Wraps Character.isTitleCase().
     */
    TITLE_CASE,
    /**
     * Wraps Character.isLetter().
     */
    LETTER,
    /**
     * Wraps Character.isLetterOrDigit().
     */
    LETTER_OR_DIGIT,
    /**
     * Determines if the tested character is not one which is illegal (or
     * extremely inadvisable) on Windows or Linux.
     */
    FILE_NAME_SAFE;

    @Override
    public boolean test(char c) {
        switch (this) {
            case ALPHABETIC:
                return Character.isAlphabetic(c);
            case DIGIT:
                return Character.isDigit(c);
            case PUNCTUATION:
                return !Character.isWhitespace(c)
                        && !Character.isISOControl(c)
                        && !Character.isDigit(c)
                        && !Character.isAlphabetic(c)
                        && !Character.isLetter(c);
            case ISO_CONTROL_CHARS:
                return Character.isISOControl(c);
            case JAVA_IDENTIFIER_PART:
                return Character.isJavaIdentifierPart(c);
            case JAVA_IDENTIFIER_START:
                return Character.isJavaIdentifierStart(c);
            case WHITESPACE:
                return Character.isWhitespace(c);
            case UPPER_CASE:
                return Character.isUpperCase(c);
            case LOWER_CASE:
                return Character.isLowerCase(c);
            case TITLE_CASE:
                return Character.isTitleCase(c);
            case LETTER:
                return Character.isLetter(c);
            case LETTER_OR_DIGIT:
                return Character.isLetterOrDigit(c);
            case JAVA_IDENTIFIER_IGNORABLE:
                return Character.isIdentifierIgnorable(c);
            case FILE_NAME_SAFE:
                // While fewer characters are illegal in file names on
                // Linux, this includes Windows ones and generally
                // extremely inadvisable ones
                switch (c) {
                    case '/':
                    case ':':
                    case '\\':
                    case ';':
                    case '<':
                    case '>':
                    case '|':
                    case '?':
                    case '*':
                    case '"':
                    case 0:
                        return false;
                }
                if (c < 31) {
                    return false;
                }
                return true;
            default:
                throw new AssertionError(this);
        }
    }
}

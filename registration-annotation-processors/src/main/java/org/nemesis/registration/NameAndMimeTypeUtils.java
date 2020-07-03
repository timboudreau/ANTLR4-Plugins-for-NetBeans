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
package org.nemesis.registration;

import com.mastfrog.annotation.AnnotationUtils;
import static com.mastfrog.annotation.AnnotationUtils.capitalize;
import static com.mastfrog.annotation.AnnotationUtils.simpleName;
import static com.mastfrog.annotation.AnnotationUtils.stripMimeType;
import com.mastfrog.util.strings.Escaper;
import java.nio.charset.CharsetEncoder;
import static java.nio.charset.StandardCharsets.US_ASCII;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.type.TypeMirror;

/**
 *
 * @author Tim Boudreau
 */
final class NameAndMimeTypeUtils {

    private NameAndMimeTypeUtils() {
        throw new AssertionError();
    }

    public static Predicate<String> complexMimeTypeValidator(boolean allowComplex, AnnotationUtils utils, Element el, AnnotationMirror mir) {
        return new MimeTypeValidator(allowComplex, utils, el, mir);
    }

    static final class MimeTypeValidator implements Predicate<String> {

        private final boolean complex;
        private final Consumer<String> fail;
        private final Consumer<String> warn;
        static final Pattern WHITESPACE = Pattern.compile("\\s+");

        public MimeTypeValidator(boolean complex, AnnotationUtils utils, Element el, AnnotationMirror mir) {
            this.complex = complex;
            this.fail = msg -> utils.fail(msg, el, mir);
            this.warn = msg -> utils.warn(msg, el, mir);
        }

        MimeTypeValidator(boolean complex, Consumer<String> fail, Consumer<String> warn) {
            this.complex = complex;
            this.fail = fail;
            this.warn = warn;
        }

        private void fail(String msg) {
            fail.accept(msg);
        }

        private void warn(String msg) {
            warn.accept(msg);
        }

        @Override
        public boolean test(String value) {
            boolean result = true;
            if (value == null || value.length() < 3) {
                fail("Mime type unset or too short to be one: '" + value + "'");
                result = false;
            }
            if (value.length() > 80) {
                int len = value.length();
                int semiIx = value.indexOf(';');
                if (semiIx >= 0) {
                    len = semiIx;
                }
                if (len > 80) {
                    fail("Mime type too long: " + value.length()
                            + " ('" + Escaper.CONTROL_CHARACTERS.escape(value) + "'"
                            + ") for NetBeans' MIMEPath parser");
                    result = false;
                }
            }
            int ix = value.indexOf('/');
            int lix = value.lastIndexOf('/');
            if (ix < 0) {
                fail("No / character in mime type '" + Escaper.CONTROL_CHARACTERS.escape(value) + "'");
                result = false;
            }
            if (lix != ix) {
                fail("More than one / character in mime type '" + Escaper.CONTROL_CHARACTERS.escape(value) + "'");
                result = false;
            }
            if (ix == value.length() - 1) {
                fail("Trailing / in mime type '" + Escaper.CONTROL_CHARACTERS.escape(value) + "'");
                result = false;
            }
            if (ix == 0) {
                fail("No mime type before ; in '" + Escaper.CONTROL_CHARACTERS.escape(value) + "'");
                result = false;
            }
            boolean isComplex = false;

            int failures = 0;
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                if (Character.isWhitespace(c) && (failures & 1) == 0) {
                    fail("Whitespace at " + i + " in mime type '" + value + "'");
                    failures |= 1;
                    result = false;
                }
                if (Character.isUpperCase(c) && (failures & 2) == 0) {
                    fail("Mime type should not contain upper case letters (MIME types are"
                            + "case insensitive, but NetBeans' file paths aren't) but does at " + i);
                    failures |= 2;
                    result = false;
                }
                if (c == 0 && (failures & 4) == 0) {
                    fail("Zero/null character encountered at index " + i);
                    failures |= 4;
                    result = false;
                }
                if (!complex && ';' == value.charAt(i) && i != value.length() - 1 && (failures & 8) == 0) {
                    fail("Complex mime types unsupported");
                    result = false;
                    break;
                } else if (c == ';') {
                    isComplex = true;
                    break;
                }
            }
            if (isComplex) {
                int firstSemi = value.indexOf(';');
                assert firstSemi >= 0 : "Should not get here without a semicolon: " + value;
                String[] sub = value.substring(firstSemi + 1).split(";");
                for (int i = 0; i < sub.length; i++) {
                    String[] keyValue = sub[i].split("=");
                    if (keyValue.length == 0) {
                        if (i == sub.length - 1) {
                            // trailing ; is harmless
                            continue;
                        }
                        warn("Empty parameter section in mime type "
                                + Escaper.CONTROL_CHARACTERS.escape(value) + "'");
                    }
                    if (keyValue.length > 2) {
                        fail("Does not look like a key/value pair: '" + sub[i]);
                        result = false;
                    } else if (keyValue.length == 1) {
                        warn("Non key-value pair '" + sub[i] + " will be "
                                + "stripped out of final mime type, from '"
                                + Escaper.CONTROL_CHARACTERS.escape(value) + "'");
                    }
                    if (!"prefix".equals(keyValue[0])) {
                        warn("Complex mime type encountered: '" + value + "' "
                                + "but the only key/value pair supported is "
                                + "prefix=ClassNamePrefix. '" + sub[i] + " will "
                                + "simply be stripped off in all "
                                + "generated code.");
                    }
                    if (keyValue.length > 0 && !Escaper.CONTROL_CHARACTERS.escape(
                            keyValue[0]).equals(keyValue[0])) {
                        fail("Key part contains control characters: '" + keyValue[0] + "' in '"
                                + Escaper.CONTROL_CHARACTERS.escape(value) + "'");
                        result = false;
                    }
                    if (keyValue.length > 1 && !Escaper.CONTROL_CHARACTERS.escape(
                            keyValue[1]).equals(keyValue[1])) {
                        fail("Value part contains control characters: '"
                                + Escaper.CONTROL_CHARACTERS.escape(keyValue[1]) + "' in '"
                                + Escaper.CONTROL_CHARACTERS.escape(value));
                        result = false;
                    }
                    if (WHITESPACE.matcher(sub[i]).find()) {
                        fail("Key value pair contains whitespace");
                        result = false;
                    }
                }
                CharsetEncoder enc = US_ASCII.newEncoder();
                if (!enc.canEncode(value)) {
                    fail("'" + Escaper.CONTROL_CHARACTERS.escape(value) + "' cannot be encoded"
                            + " in US-ASCII");
                    result = false;
                }
            }
            return result;
        }
    }

    public static String implPackage(String pkg) {
        if (!pkg.endsWith(".impl")) {
            return pkg + ".impl";
        }
        return pkg;
    }

    public static boolean isValidJavaIdentifier(String s) {
        if (s.length() == 0 || !Character.isJavaIdentifierStart(s.charAt(0))) {
            return false;
        }
        for (int i = 1; i < s.length(); i++) {
            if (!Character.isJavaIdentifierPart(s.charAt(0))) {
                return false;
            }
        }
        return true;
    }

    public static String displayNameAsVarName(String displayName) {
        String nm = displayName.replaceAll("\\s+", "");
        if (isValidJavaIdentifier(nm)) {
            return nm;
        }
        return Escaper.JAVA_IDENTIFIER_CAMEL_CASE.escape(displayName.replace("\\s+", "_"));
    }
    private static final Pattern PREFIX_IN_MIME_TYPE = Pattern.compile("^.*prefix=(\\w[\\w\\d_]+)");
    private static final Pattern STRIP_VERSION = Pattern.compile("^(\\w[\\w\\d_]+?)[vV]?[\\d\\.]+");

    static String prefixFromMimeType(String mimeType) {
        return prefixFromMimeTypeOrLexerName(mimeType, (String) null);
    }

    static String prefixFromMimeTypeOrLexerName(String mimeType, TypeMirror lexerClass) {
        return prefixFromMimeTypeOrLexerName(mimeType, lexerClass == null ? null : lexerClass.toString());
    }

    static String prefixFromMimeTypeOrLexerName(String mimeType, String lexerClass) {
        Matcher m = PREFIX_IN_MIME_TYPE.matcher(mimeType);
        if (m.find()) {
            return m.group(1);
        }
        if (lexerClass != null) {
            String name = simpleName(lexerClass);
            // Backward compatibility
            // a few things Antlr generates, like fragment only grammars in the imports dir
            // will NOT end with Lexer
            if (!"Lexer".equals(name) && name.endsWith("Lexer")) {
                String result = name.substring(0, name.length() - "Lexer".length());
                m = STRIP_VERSION.matcher(result);
                if (m.find()) {
                    result = m.group(1);
                }
                return capitalize(result.toLowerCase());
            }
        }
        String result = stripMimeType(mimeType);
        int plus = result.indexOf('+');
        if (plus > 0 && plus < result.length() - 1) {
            String plusOnly = result.substring(plus + 1, result.length());
            if (plusOnly.length() >= 2) {
                result = plusOnly;
            }
        }
        // We may use ;prefix= to redintroduce having a prefix more
        // portably across annotation processors
        if (result.indexOf(';') > 0) {
            result = result.substring(0, result.indexOf(';'));
        }
        return Escaper.JAVA_IDENTIFIER_CAMEL_CASE.escape(result);
    }

    static String cleanMimeType(String result) {
        if (result == null) {
            return null;
        }
        if (result.indexOf(';') > 0) {
            result = result.substring(0, result.indexOf(';'));
        }
        return result;
    }

    /**
     * Generate the name of a character for use in bundle keys.
     *
     * @param c A character
     * @return A string name of the character understandable to a human
     * translator
     */
    static String characterToCharacterName(char c) {
        // The point here isn't accurracy so much as consistency, so
        // for example "Insert )" and "Insert :" aren't converged to
        // the same bundle key "insert".
        CharSequence result = Escaper.JAVA_IDENTIFIER_CAMEL_CASE.escape(c);
        return result == null ? Character.toString(c) : result.toString();
    }
}

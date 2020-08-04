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
package org.nemesis.antlr.error.highlighting.hints.util;

import com.mastfrog.util.strings.Strings;
import static com.mastfrog.util.strings.Strings.capitalize;
import java.util.Optional;
import javax.swing.text.Document;
import org.nemesis.antlr.common.extractiontypes.GrammarType;
import org.nemesis.antlr.common.extractiontypes.RuleTypes;
import org.nemesis.antlr.file.AntlrKeys;
import static org.nemesis.antlr.file.AntlrKeys.GRAMMAR_TYPE;
import org.nemesis.antlr.file.impl.GrammarDeclaration;
import org.nemesis.data.named.NamedSemanticRegion;
import org.nemesis.data.named.NamedSemanticRegions;
import org.nemesis.extraction.Extraction;
import org.nemesis.extraction.SingletonEncounters;

/**
 * Some people use the convention of fragments all uppercase, lexer rules mixed
 * case, and others use the inverse, and parser rules are camel case for some
 * authors, and underscore-delimited for others. So when inventing rule names or
 * converting the types of rule names, try to detect the convention and use
 * that.
 *
 * @author Tim Boudreau
 */
public enum RuleNamingConvention {

    LEXER_RULES_UPPER_CASE_PARSER_RULES_BICAPITALIZED,
    LEXER_RULES_UPPER_CASE_PARSER_RULES_UNDERSCORES,
    LEXER_RULES_MIXED_CASE_PARSER_RULES_BICAPITALIZED,
    LEXER_RULES_MIXED_CASE_PARSER_RULES_UNDERSCORES,
    LEXER_RULES_UPPER_CASE_PARSER_RULES_UNKNOWN,
    LEXER_RULES_MIXED_CASE_PARSER_RULES_UNKNOWN,
    LEXER_RULES_UPPER_CASE,
    LEXER_RULES_MIXED_CASE,
    PARSER_RULES_CAMEL_CASE,
    PARSER_RULES_UNDERSCORES,
    LEXER_RULES_UNKNOWN_PARSER_RULES_BICAPITALIZED,
    LEXER_RULES_UNKNOWN_PARSER_RULES_UNDERSCORES,
    UNKNOWN;

    public boolean parserRulesBicapitalized() {
        switch (this) {
            case LEXER_RULES_UPPER_CASE_PARSER_RULES_BICAPITALIZED:
            case LEXER_RULES_MIXED_CASE_PARSER_RULES_BICAPITALIZED:
            case LEXER_RULES_UNKNOWN_PARSER_RULES_BICAPITALIZED:
            case PARSER_RULES_CAMEL_CASE:
                return true;
            case LEXER_RULES_MIXED_CASE_PARSER_RULES_UNDERSCORES:
            case LEXER_RULES_UPPER_CASE_PARSER_RULES_UNDERSCORES:
            case LEXER_RULES_UNKNOWN_PARSER_RULES_UNDERSCORES:
            case PARSER_RULES_UNDERSCORES:
                return false;
            case LEXER_RULES_MIXED_CASE:
            case LEXER_RULES_MIXED_CASE_PARSER_RULES_UNKNOWN:
            case LEXER_RULES_UPPER_CASE:
            case LEXER_RULES_UPPER_CASE_PARSER_RULES_UNKNOWN:
            case UNKNOWN:
                return false;
            default:
                throw new AssertionError(this);
        }
    }

    public boolean lexerRulesBiCapitalized() {
        switch (this) {
            case LEXER_RULES_MIXED_CASE_PARSER_RULES_BICAPITALIZED:
            case LEXER_RULES_MIXED_CASE_PARSER_RULES_UNDERSCORES:
            case LEXER_RULES_MIXED_CASE_PARSER_RULES_UNKNOWN:
            case LEXER_RULES_MIXED_CASE:
                return true;
            case LEXER_RULES_UPPER_CASE_PARSER_RULES_BICAPITALIZED:
            case LEXER_RULES_UPPER_CASE_PARSER_RULES_UNDERSCORES:
            case LEXER_RULES_UPPER_CASE_PARSER_RULES_UNKNOWN:
            case LEXER_RULES_UPPER_CASE:
                return false;
            case LEXER_RULES_UNKNOWN_PARSER_RULES_BICAPITALIZED:
            case LEXER_RULES_UNKNOWN_PARSER_RULES_UNDERSCORES:
            case PARSER_RULES_CAMEL_CASE:
            case PARSER_RULES_UNDERSCORES:
            case UNKNOWN:
                return false;
            default:
                throw new AssertionError(this);
        }
    }

    public boolean isFullySpecified() {
        switch (this) {
            case LEXER_RULES_MIXED_CASE:
            case LEXER_RULES_MIXED_CASE_PARSER_RULES_BICAPITALIZED:
            case LEXER_RULES_MIXED_CASE_PARSER_RULES_UNDERSCORES:
            case LEXER_RULES_UPPER_CASE_PARSER_RULES_BICAPITALIZED:
            case LEXER_RULES_UPPER_CASE_PARSER_RULES_UNDERSCORES:
            case LEXER_RULES_UPPER_CASE:
            case PARSER_RULES_CAMEL_CASE:
            case PARSER_RULES_UNDERSCORES:
                return true;
            default:
                return false;
        }
    }

    public boolean fragmentRulesUpperCase() {
        return lexerRulesBiCapitalized();
    }

    private String deCapitalize(String s) {
        char[] c = s.toCharArray();
        c[0] = Character.toLowerCase(c[0]);
        return new String(c);
    }

    private String deDelimitParserRuleName(String s) {
        return deCapitalize(Strings.delimitedToCamelCase(s, '_'));
    }

    public String adjustName(String name, RuleTypes type) {
        switch (type) {
            case PARSER:
                name = name.toLowerCase();
                if (this.parserRulesBicapitalized()) {
                    return deDelimitParserRuleName(name);
                }
                return name.toLowerCase();
            case LEXER:
                if (lexerRulesBiCapitalized()) {
                    return capitalize(Strings.delimitedToCamelCase(name, '_'));
                } else {
                    return Strings.camelCaseToDelimited(name, '_').toUpperCase();
                }
            case FRAGMENT:
                if (lexerRulesBiCapitalized()) {
                    return Strings.camelCaseToDelimited(name, '_').toUpperCase();
                } else {
                    return capitalize(Strings.delimitedToCamelCase(name, '_'));
                }
            default:
                return name;
        }
    }

    public static RuleNamingConvention forExtraction(Extraction ext) {
        Optional<Document> doc = ext.source().lookup(Document.class);
        if (doc.isPresent()) {
            Document d = doc.get();
            NamingConventionResult existing = (NamingConventionResult) d.getProperty(NamingConventionResult.class);
            if (existing != null) {
                return existing.convention;
            }
        }
        NamingConventionResult result = find(ext);
        if (doc.isPresent() && result.confident) {
            NamedSemanticRegions<RuleTypes> names = ext.namedRegions(AntlrKeys.RULE_NAMES);
            if (names.size() > 7) {
                doc.get().putProperty(NamingConventionResult.class, result);
            }
        }
        return result.convention;
    }

    public static GrammarType findGrammarType(Extraction ext) {
        SingletonEncounters<GrammarDeclaration> gt = ext.singletons(GRAMMAR_TYPE);
        GrammarType type = GrammarType.UNDEFINED;
        // Do a bunch of null checks in case of broken sources
        if (!gt.isEmpty()) {
            SingletonEncounters.SingletonEncounter<GrammarDeclaration> grammarType = gt.first();
            GrammarDeclaration gg = grammarType.get();
            if (gg.type() != null) {
                type = gg.type();
            }
        }
        return type;
    }

    private static NamingConventionResult find(Extraction ext) {
        GrammarType type = findGrammarType(ext);
        NamedSemanticRegions<RuleTypes> names = ext.namedRegions(AntlrKeys.RULE_NAMES);

        boolean incomplete;
        switch (type) {
            case UNDEFINED:
                incomplete = true;
                break;
            default:
                incomplete = !names.presentKinds().equals(type.legalRuleTypes());
        }

        int totalFragmentNameChars = 0;
        int totalLexerNameChars = 0;
        int totalParserNameChars = 0;

        int fragmentRules = 0;
        int lexerRules = 0;
        int parserRules = 0;

        int fragmentUnderscores = 0;
        int parserUnderscores = 0;
        int parserLowers = 0;
        int parserUppers = 0;
        int parserSymbols = 0;

        int fragmentLowers = 0;
        int fragmentUppers = 0;
        int fragmentSymbols = 0;

        int lexerUnderscores = 0;
        int lexerLowers = 0;
        int lexerUppers = 0;
        int lexerSymbols = 0;
        int[] arr = new int[4];
        for (NamedSemanticRegion<RuleTypes> reg : names) {
            switch (reg.kind()) {
                case NAMED_ALTERNATIVES:
                    continue;
                case PARSER:
                    charTypesCount(reg.name(), arr);
                    parserRules++;
                    totalParserNameChars += reg.name().length();
                    parserUppers += arr[0];
                    parserLowers += arr[1];
                    parserUnderscores += arr[2];
                    parserSymbols += arr[3];

                    continue;
                case FRAGMENT:
                    charTypesCount(reg.name(), arr);
                    fragmentRules++;
                    totalFragmentNameChars += reg.name().length();
                    fragmentUppers += arr[0];
                    fragmentLowers += arr[1];
                    fragmentUnderscores += arr[2];
                    fragmentSymbols += arr[3];
                    break;
                case LEXER:
                    charTypesCount(reg.name(), arr);
                    lexerRules++;
                    totalLexerNameChars += reg.name().length();
                    lexerUppers += arr[0];
                    lexerLowers += arr[1];
                    lexerUnderscores += arr[2];
                    lexerSymbols += arr[3];
                    break;
            }
        }
        switch (type) {
            case LEXER:
                if (lexerRules == 0 && fragmentRules == 0) {
                    return NamingConventionResult.UNK;
                }
                if (lexerLowers == 0 && lexerUnderscores == 0) {
                    incomplete = true;
                }
                if (fragmentRules < 3 || totalFragmentNameChars < 25) {
                    incomplete = true;
                }
                if (lexerRules < 5 || totalLexerNameChars < 25) {
                    incomplete = true;
                }
                break;
            case UNDEFINED:
            case COMBINED:
                if ((lexerRules == 0 && fragmentRules == 0) || parserRules == 0) {
                    return NamingConventionResult.UNK;
                }
                if (lexerLowers == 0 && lexerUnderscores == 0 && fragmentUnderscores == 0) {
                    incomplete = true;
                }
                if (parserUnderscores == 0 && parserUppers == 0) {
                    incomplete = true;
                }
                if (parserRules < 5 || totalParserNameChars < 25 || lexerRules < 4 || totalLexerNameChars < 25) {
                    incomplete = true;
                }
                break;
            case PARSER:
                if (parserRules == 0) {
                    return NamingConventionResult.UNK;
                }
                if (parserUnderscores == 0 && parserUppers == 0) {
                    incomplete = true;
                }
                break;
            default:
                throw new AssertionError(type);
        }
        if (type == GrammarType.LEXER && totalFragmentNameChars == 0 && totalLexerNameChars == 0) {
            return NamingConventionResult.UNK;
        }
        totalFragmentNameChars -= fragmentSymbols + fragmentUnderscores;
        float fragmentFractionUpper = (float) fragmentUppers / (float) totalFragmentNameChars;
        totalLexerNameChars -= lexerSymbols + lexerUnderscores;
        float lexerFractionUpper = (float) lexerUppers / (float) totalLexerNameChars;

        boolean lexerMixed = fragmentFractionUpper > lexerFractionUpper;
        boolean parserMixed = parserUppers > parserUnderscores;

        RuleNamingConvention result;
        switch (type) {
            case PARSER:
                if (parserRules == 0) {
                    return NamingConventionResult.UNK;
                } else {
                    result = parserMixed ? PARSER_RULES_CAMEL_CASE : PARSER_RULES_UNDERSCORES;
                    incomplete = false;
                }
                break;
            case LEXER:
                if (lexerRules == 0 || fragmentRules == 0) {
                    result = UNKNOWN;
                } else if (lexerLowers == 0 && fragmentLowers == 0 && lexerUnderscores == 0 && fragmentUnderscores == 0) {
                    result = UNKNOWN;
                } else {
                    // If the grammar is ALL lexer rules or ALL fragment rules
                    // and they are either all upper case, or contain mixed case,
                    // then we have our answer about both
                    if (totalFragmentNameChars == 0 && totalLexerNameChars != 0) {
                        int sub = totalLexerNameChars - (lexerUnderscores + lexerSymbols);
                        if (sub == lexerUppers) {
                            result = LEXER_RULES_UPPER_CASE;
                            incomplete = false;
                        } else if (lexerLowers > 0) {
                            result = LEXER_RULES_MIXED_CASE;
                            incomplete = false;
                        } else {
                            // punt
                            result = LEXER_RULES_MIXED_CASE;
                        }
                    } else if (totalFragmentNameChars != 0 && totalLexerNameChars == 0) {
                        int sub = totalFragmentNameChars - (fragmentUnderscores + fragmentSymbols);
                        if (sub == fragmentUppers) {
                            result = LEXER_RULES_MIXED_CASE;
                            incomplete = false;
                        } else if (fragmentLowers > 0) {
                            result = LEXER_RULES_UPPER_CASE;
                            incomplete = false;
                        } else {
                            // punt
                            result = LEXER_RULES_MIXED_CASE;
                        }
                    } else {
                        result = lexerMixed ? LEXER_RULES_MIXED_CASE : LEXER_RULES_UPPER_CASE;
                    }
                }
                break;
            case COMBINED:
                if (lexerRules == 0 || lexerLowers == 0 && fragmentLowers == 0 && lexerUnderscores == 0 && fragmentUnderscores == 0) {
                    result = parserMixed ? LEXER_RULES_UNKNOWN_PARSER_RULES_BICAPITALIZED
                            : LEXER_RULES_UNKNOWN_PARSER_RULES_UNDERSCORES;
                } else {
                    result = parserMixed
                            ? lexerMixed ? LEXER_RULES_MIXED_CASE_PARSER_RULES_BICAPITALIZED
                                    : LEXER_RULES_UPPER_CASE_PARSER_RULES_BICAPITALIZED
                            : lexerMixed ? LEXER_RULES_MIXED_CASE_PARSER_RULES_UNDERSCORES
                                    : LEXER_RULES_UPPER_CASE_PARSER_RULES_UNDERSCORES;
                }
                break;
            case UNDEFINED:
                result = UNKNOWN;
                break;
            default:
                throw new AssertionError(type);
        }
        return new NamingConventionResult(result, result == UNKNOWN ? false : incomplete);
    }

    public RuleTypes identify(String name) {
        if (name == null || name.isEmpty()) {
            // something very wrong
            return RuleTypes.PARSER;
        }
        char first = name.charAt(0);
        if (Character.isUpperCase(first)) {
            // must be lexer
            boolean hasLower = false;
            for (int i = 1; i < name.length(); i++) {
                char c = name.charAt(i);
                if (Character.isLetter(c) && Character.isLowerCase(c)) {
                    hasLower = true;
                    break;
                }
            }
            if (fragmentRulesUpperCase()) {
                return hasLower ? RuleTypes.LEXER : RuleTypes.FRAGMENT;
            } else {
                return hasLower ? RuleTypes.FRAGMENT : RuleTypes.LEXER;
            }
        } else {
            return RuleTypes.PARSER;
        }
    }

    static class NamingConventionResult {

        final RuleNamingConvention convention;
        final boolean confident;
        static final NamingConventionResult UNK = new NamingConventionResult(RuleNamingConvention.UNKNOWN, false);

        public NamingConventionResult(RuleNamingConvention convention, boolean confident) {
            this.convention = convention;
            this.confident = confident && convention.isFullySpecified();
            assert convention == RuleNamingConvention.UNKNOWN ? !confident : true : "Unknown naming convention may not be confident";
        }

        @Override
        public String toString() {
            return convention + " " + (confident ? " confident" : "provisional");
        }
    }

    private static void charTypesCount(String name, int[] arr) {
        int underscores = 0;
        int uppers = 0;
        int lowers = 0;
        int symbolsAndNumbers = 0;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            switch (c) {
                case '_':
                    underscores++;
                    break;
                default:
                    if (Character.isLetter(c)) {
                        if (Character.isUpperCase(c)) {
                            uppers++;
                        } else {
                            lowers++;
                        }
                    } else {
                        symbolsAndNumbers++;
                    }
            }
        }
        arr[0] = uppers;
        arr[1] = lowers;
        arr[2] = underscores;
        arr[3] = symbolsAndNumbers;
    }
}

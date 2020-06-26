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
package org.nemesis.antlr.error.highlighting;

import com.mastfrog.util.strings.Strings;
import java.util.Optional;
import javax.swing.text.Document;
import org.nemesis.antlr.common.extractiontypes.RuleTypes;
import org.nemesis.antlr.file.AntlrKeys;
import org.nemesis.data.named.NamedSemanticRegion;
import org.nemesis.data.named.NamedSemanticRegions;
import org.nemesis.extraction.Extraction;

/**
 * Some people use the convention of fragments all uppercase, lexer rules
 * mixed case, and others use the inverse.  So when inventing rule names,
 * try to detect the convention and use that.
 *
 * @author Tim Boudreau
 */
enum RuleNamingConvention {

    LEXER_RULES_UPPER_CASE,
    LEXER_RULES_MIXED_CASE,
    UNKNOWN;

    String adjustName(String name, RuleTypes type) {
        switch(type) {
            case PARSER :
                return name.toLowerCase();
            case LEXER :
                switch(this) {
                    case LEXER_RULES_UPPER_CASE :
                        return Strings.camelCaseToDelimited(name, '_');
                    case LEXER_RULES_MIXED_CASE :
                    default :
                        return name;
                }
            case FRAGMENT :
                switch(this) {
                    case LEXER_RULES_MIXED_CASE :
                        return Strings.camelCaseToDelimited(name, '_');
                    case LEXER_RULES_UPPER_CASE :
                        return name;
                }
            default :
                return name;
        }
    }

    static RuleNamingConvention forExtraction(Extraction ext) {
        Optional<Document> doc = ext.source().lookup(Document.class);
        if (doc.isPresent()) {
            Document d = doc.get();
            RuleNamingConvention existing = (RuleNamingConvention) d.getProperty(RuleNamingConvention.class);
            if (existing != null) {
                return existing;
            }
        }
        RuleNamingConvention result = find(ext);
        if (doc.isPresent() && result != UNKNOWN) {
            doc.get().putProperty(RuleNamingConvention.class, result);
        }
        return result;
    }

    private static RuleNamingConvention find(Extraction ext) {
        NamedSemanticRegions<RuleTypes> names = ext.namedRegions(AntlrKeys.RULE_NAMES);
        int totalFragmentNameChars = 0;
        int totalLexerNameChars = 0;
        int fragmentUnderscores = 0;
        int fragmentLowers = 0;
        int fragmentUppers = 0;
        int fragmentSymbols = 0;

        int lexerUnderscores = 0;
        int lexerLowers = 0;
        int lexerUppers = 0;
        int lexerSymbols = 0;
        int[] arr = new int[4];
        for (NamedSemanticRegion<RuleTypes> reg : names) {
            switch(reg.kind()) {
                case NAMED_ALTERNATIVES :
                case PARSER :
                    continue;
                case FRAGMENT :
                    charTypesCount(reg.name(), arr);
                    totalFragmentNameChars+= reg.name().length();
                    fragmentUppers += arr[0];
                    fragmentLowers += arr[1];
                    fragmentUnderscores += arr[2];
                    fragmentSymbols += arr[3];
                    break;
                case LEXER :
                    charTypesCount(reg.name(), arr);
                    totalLexerNameChars+= reg.name().length();
                    lexerUppers += arr[0];
                    lexerLowers += arr[1];
                    lexerUnderscores += arr[2];
                    lexerSymbols += arr[3];
                    break;
            }
        }
        if (totalFragmentNameChars == 0 || totalLexerNameChars == 0) {
            return RuleNamingConvention.UNKNOWN;
        }
        totalFragmentNameChars -= fragmentSymbols + fragmentUnderscores;
        float fragmentFractionUpper = (float) fragmentUppers / (float) totalFragmentNameChars;
        totalLexerNameChars -= lexerSymbols + lexerUnderscores;
        float lexerFractionUpper = (float) lexerUppers / (float) totalLexerNameChars;
        if (fragmentFractionUpper > lexerFractionUpper) {
            return LEXER_RULES_MIXED_CASE;
        } else {
            return LEXER_RULES_UPPER_CASE;
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

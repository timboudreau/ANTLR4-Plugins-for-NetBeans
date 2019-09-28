/*
BSD License

Copyright (c) 2016-2018, Frédéric Yvon Vinet, Tim Boudreau
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

* Redistributions of source code must retain the above copyright
  notice, this list of conditions and the following disclaimer.
* Redistributions in binary form must reproduce the above copyright
  notice, this list of conditions and the following disclaimer in the
  documentation and/or other materials provided with the distribution.
* The name of its author may not be used to endorse or promote products
  derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR IMPLIED 
WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF 
MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT
SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, 
EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT
OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS 
INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN 
CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING
IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY 
OF SUCH DAMAGE.
 */
package org.nemesis.antlr.language.formatting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import org.nemesis.antlr.ANTLRv4Lexer;
import static org.nemesis.antlr.language.formatting.AbstractFormatter.*;
import org.nemesis.antlr.language.formatting.config.AntlrFormatterConfig;
import org.nemesis.antlrformatting.api.FormattingRules;
import org.nemesis.antlrformatting.api.LexingStateBuilder;
import org.nemesis.antlrformatting.spi.AntlrFormatterRegistration;
import org.nemesis.antlrformatting.spi.AntlrFormatterStub;

/**
 *
 * @author Tim Boudreau
 */
@AntlrFormatterRegistration(mimeType = "text/x-g4")
public class G4FormatterStub implements AntlrFormatterStub<AntlrCounters, ANTLRv4Lexer> {

    private static final String[] EXPECTED_MODE_NAMES = {
        MODE_DEFAULT, MODE_ARGUMENT, MODE_HEADER_PRELUDE, MODE_HEADER_ACTION, MODE_HEADER_PACKAGE,
        MODE_HEADER_IMPORT, MODE_ACTION, MODE_OPTIONS, MODE_TOKENS, MODE_CHANNELS,
        MODE_IMPORT, MODE_IDENTIFIER, MODE_TOKEN_DECLARATION, MODE_FRAGMENT_DECLARATION,
        MODE_PARSER_RULE_DECLARATION, MODE_PARSER_RULE_OPTIONS, MODE_LEXER_COMMANDS,
        MODE_TYPE_LEXER_COMMAND, MODE_LEXER_CHAR_SET
    };

    private static String[] modeNames = {
        MODE_DEFAULT, MODE_ARGUMENT, MODE_HEADER_PRELUDE, MODE_HEADER_ACTION, MODE_HEADER_PACKAGE,
        MODE_HEADER_IMPORT, MODE_ACTION, MODE_OPTIONS, MODE_TOKENS, MODE_CHANNELS, MODE_IMPORT, MODE_IDENTIFIER,
        MODE_TOKEN_DECLARATION, MODE_FRAGMENT_DECLARATION, MODE_PARSER_RULE_DECLARATION, MODE_PARSER_RULE_OPTIONS,
        MODE_LEXER_COMMANDS, MODE_TYPE_LEXER_COMMAND, MODE_LEXER_CHAR_SET
    };

    static void checkExpectedModeNames(BiConsumer<Set<String>, Set<String>> consumer) {
        Set<String> expectedModeNames = new HashSet<>(Arrays.asList(EXPECTED_MODE_NAMES));
        Set<String> actualModeNames = new HashSet<>(Arrays.asList(ANTLRv4Lexer.modeNames));
        if (!expectedModeNames.equals(actualModeNames)) {
            System.err.println("ANTLRv4Lexer mode names have changed.");
            Set<String> missing = new HashSet<>(expectedModeNames);
            missing.removeAll(actualModeNames);
            if (!missing.isEmpty()) {
                System.err.println("Missing modes: " + missing);
            }
            Set<String> added = new HashSet<>(actualModeNames);
            added.removeAll(expectedModeNames);
            if (!added.isEmpty()) {
                System.err.println("Added modes: " + added);
            }
            if (!added.isEmpty() || !missing.isEmpty()) {
                consumer.accept(missing, added);
            }
        }
    }

    static {
        checkExpectedModeNames((missing, added) -> {
            Logger log = Logger.getLogger(G4FormatterStub.class.getName());
            log.log(Level.SEVERE, "Modes now available from Antlr grammar language grammar "
                    + "no longer conform to the list of mode names this formatter "
                    + "was written against.\nMissing:{0}\nAdded:{1}", new Object[]{missing, added});
        });
    }

    List<AbstractFormatter> delegates;

    @Override
    public void configure(LexingStateBuilder<AntlrCounters, ?> stateBuilder, FormattingRules rules, Preferences config) {
        AntlrFormatterConfig cfig = new AntlrFormatterConfig(config);
        delegates = delegates(cfig);
//        System.out.println("CONFIG: " + cfig);
//        System.out.println("DELEGATES: " + delegates);
        delegates.forEach((f) -> {
            f.configure(stateBuilder, rules);
        });
//        System.out.println("RULES: " + rules);
    }

    private List<AbstractFormatter> delegates(AntlrFormatterConfig config) {
        List<AbstractFormatter> l = new ArrayList<>(3);
        l.add(new BasicFormatting(config));
        l.add(new HeaderMatterFormatting(config));
        switch (config.getColonHandling()) {
            case NEWLINE_BEFORE:
            case NEWLINE_AFTER:
            case STANDALONE:
                l.add(new IndentToColonFormatting(config));
                break;
            default:
                l.add(new InlineColonFormatting(config));
                break;

        }
        if (config.isReflowLineComments()) {
            l.add(new LineCommentsFormatting(config));
        }
        if (config.isSpacesInsideParens()) {
            l.add(new ParenthesesSpacing(config));
        }
        return l;
    }

//    @Override
//    public Predicate<Token> debugTokens() {
//        boolean[] lastMatch = new boolean[1];
//        return tok -> {
//            if (lastMatch[0]) {
//                lastMatch[0] = false;
//                return true;
//            }
//            return lastMatch[0] = tok.getText().contains("times by the analyzerDirectiveSpec");
//        };
//    }

}

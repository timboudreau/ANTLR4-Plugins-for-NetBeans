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
package org.nemesis.antlr.memory.output;

import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses ANTLR stderr output lines (requires Antlr be run with -message-format
 * vs2005).
 *
 * @author Tim Boudreau
 */
public final class AntlrErrorParser implements OutputParser<ParsedAntlrError> {

    /*
/grammar/grammar_syntax_checking/ANTLRv4.g4(320,11) : error 124 : rule alt label Ebnf conflicts with rule ebnf
/grammar_syntax_checking/ANTLRv4.g4(321,18) : error 124 : rule alt label ActionBlock conflicts with rule act
     */
    private static final Pattern ERR_PATTERN_VS_2005 = Pattern.compile(
            "^(.*?)\\((\\d+),(\\d+)\\) : ([a-z]+) (\\d+) : (.*)");

    @Override
    public ParsedAntlrError onLine(String line) {
        Matcher m = ERR_PATTERN_VS_2005.matcher(line);
        if (m.find()) {
            String errorOrWarning = m.group(4);
            int errorCode = Integer.parseInt(m.group(5));
            String path = m.group(1);
            int lineNumber = Integer.parseInt(m.group(2));
            int lineOffset = Integer.parseInt(m.group(3));
            String message = m.group(6);
            return new ParsedAntlrError("error".equals(errorOrWarning), errorCode, Paths.get(path), lineNumber, lineOffset, message);
        }
        return null;
    }
}

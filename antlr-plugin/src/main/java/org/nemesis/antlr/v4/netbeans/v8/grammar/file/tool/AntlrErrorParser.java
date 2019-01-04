package org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool;

import org.nemesis.antlr.v4.netbeans.v8.util.isolation.OutputParser;
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

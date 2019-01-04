package org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool;

import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author Tim Boudreau
 */
final class AntlrInvocationResult {

    final List<String> infoMessages = new ArrayList<>();
    final List<ParsedAntlrError> errors = new ArrayList<>();

    public List<String> infoMessages() {
        return infoMessages;
    }

    public List<ParsedAntlrError> errors() {
        return errors;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (String s : infoMessages) {
            sb.append(s);
            sb.append('\n');
        }
        for (ParsedAntlrError e : errors) {
            sb.append(e);
            sb.append('\n');
        }
        return sb.toString();
    }
}

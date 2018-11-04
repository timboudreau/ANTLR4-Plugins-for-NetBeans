package org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool;

import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 *
 * @author Tim Boudreau
 */
public enum AntlrRunOption {
    LOG("XLog"), 
    LONG_MESSAGES("long-messages"),
    GENERATE_VISITOR("visitor"),
    GENERATE_LEXER("listener"),
    GENERATE_ATN("atn"),
    GENERATE_DEPENDENCIES("depend"),
    FORCE_ATN("Xforce-atn");
    private final String stringValue;

    AntlrRunOption(String stringValue) {
        this.stringValue = stringValue;
    }

    public String toString() {
        return "-" + stringValue;
    }

    public static String[] toAntlrArguments(Path sourceFile, Set<AntlrRunOption> options, Charset encoding, Path sourcePath, String pkg, Path importDir) {
        List<String> result = new ArrayList<>(8 + options.size());
        result.add("-encoding");
        result.add("utf-8");
        if (importDir != null) {
            result.add("-lib");
            result.add(importDir.toString());
        }
//        result.add("-o");
//        result.add(outputDir.toString());
        if (pkg != null) {
            result.add("-package");
            result.add(pkg);
        }
        for (AntlrRunOption o : options) {
            result.add(o.toString());
        }
        result.add("-message-format");
        result.add("vs2005");
        result.add(sourceFile.toString());
        return result.toArray(new String[result.size()]);
    }

}

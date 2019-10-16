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

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
package org.nemesis.antlr.live.parsing.extract;

import com.mastfrog.util.path.UnixPath;
import java.io.PrintStream;
import java.nio.file.Path;
import javax.tools.StandardLocation;
import org.nemesis.antlr.live.parsing.impl.GrammarKind;
import org.nemesis.jfs.JFS;
import org.nemesis.jfs.JFSFileObject;

/**
 *
 * @author Tim Boudreau
 */
public final class GenerationInfo {

    public GenerationInfo(GrammarKind kind, Path realSourceFile, JFS fs, String pkg,
            String grammarName, String lexerGrammarName, PrintStream logTo, String tokensHash) {
        
    }

    interface GenerationPolicy {

    }

    private static GenerationPolicy forGrammarKind(GrammarKind kind) {
        switch (kind) {
            case LEXER:
            case PARSER:
                return new NonCombinedGrammarGenerationPolicy();
            case COMBINED:
            default:
                return new CombinedGrammarGenerationPolicy();
        }
    }

    static class CombinedGrammarGenerationPolicy implements GenerationPolicy {

    }

    static class NonCombinedGrammarGenerationPolicy implements GenerationPolicy {

    }

    private static JFSFileObject searchByName(JFS jfs, String pkg, String rawName, String... possibleSuffixen) {
        String pkgPath = pkg.replace('.', '/') + '/';
        for (String suff : possibleSuffixen) {
            for (StandardLocation loc : new StandardLocation[]{StandardLocation.SOURCE_PATH, StandardLocation.SOURCE_OUTPUT}) {
                UnixPath path = UnixPath.get(pkgPath + rawName + suff + ".java");
                JFSFileObject fo = jfs.get(loc, path);
                if (fo != null) {
                    return fo;
                }
            }
        }
        return null;
    }
}

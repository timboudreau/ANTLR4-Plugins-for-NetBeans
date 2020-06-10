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

import java.util.Set;
import java.util.TreeSet;
import org.nemesis.jfs.JFSFileObject;

/**
 *
 * @author Tim Boudreau
 */
public class ExtractionCodeGenerationResult {

    private JFSFileObject generatedFile;
    private final String grammarName;
    private final String pkg;
    private final Set<String> examined = new TreeSet<>();

    ExtractionCodeGenerationResult(String grammarName, String pkg) {
        this.grammarName = grammarName;
        this.pkg = pkg;
    }

    ExtractionCodeGenerationResult setResult(JFSFileObject fo) {
        generatedFile = fo;
        return this;
    }

    public ExtractionCodeGenerationResult examined(String what) {
        examined.add(what);
        return this;
    }

    public boolean isSuccess() {
        return generatedFile != null;
    }

    public JFSFileObject file() {
        return generatedFile;
    }

    @Override
    public String toString() {
        if (generatedFile != null) {
            return grammarName + " in " + pkg + " -> " + generatedFile.getName();
        }
        StringBuilder sb = new StringBuilder();
        examined.forEach(p -> {
            if (sb.length() != 0) {
                sb.append(", ");
            }
            sb.append(p);
        });
        sb.insert(0, " - tried: ");
        sb.insert(0, pkg);
        sb.insert(0, " in ");
        sb.insert(0, grammarName);
        sb.insert(0, "Could not find a generated parser or lexer for ");
        return sb.toString();
    }

}

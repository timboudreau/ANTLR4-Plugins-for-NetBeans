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

import com.mastfrog.function.state.Bool;
import com.mastfrog.util.path.UnixPath;
import java.util.Set;
import java.util.TreeSet;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import org.nemesis.jfs.JFS;
import org.nemesis.jfs.JFSCoordinates;
import org.nemesis.jfs.JFSFileObject;

/**
 *
 * @author Tim Boudreau
 */
public class ExtractionCodeGenerationResult {

    private JFSCoordinates.Resolvable generatedFile;
    private final String grammarName;
    private final String pkg;
    private final Set<String> examined = new TreeSet<>();
    private CharSequence generationInfo;
    private String generatedClassName;

    ExtractionCodeGenerationResult(String grammarName, String pkg) {
        this.grammarName = grammarName;
        this.pkg = pkg;
    }

    ExtractionCodeGenerationResult setResult(JFSFileObject fo) {
        generatedFile = fo.toReference();
        return this;
    }

    void setGeneratedClassName(String generatedClassName) {
        this.generatedClassName = generatedClassName;
    }

    public JFSCoordinates.Resolvable generatedFile() {
        return generatedFile;
    }

    public String generatedClassName() {
        return generatedClassName;
    }

    public CharSequence generationInfo() {
        return generationInfo;
    }

    void setGenerationInfo(CharSequence genInfo) {
        this.generationInfo = genInfo;
    }

    public ExtractionCodeGenerationResult examined(String what) {
        examined.add(what);
        return this;
    }

    public boolean clean(JFS jfs) {
        Bool result = Bool.create();
        JFSFileObject fo = generatedFile.resolve(jfs);
        if (fo != null) {
            UnixPath path = fo.path();
            String rawName = path.rawName();
            result.set(fo.delete());
            UnixPath parentFolder = path.getParent()
                    == null ? UnixPath.empty() : path.getParent();
            UnixPath expectedPathFile = parentFolder.resolve(rawName + ".class");
            String rawPrefix = rawName + "$";
            jfs.list(StandardLocation.CLASS_OUTPUT, (loc, cfo) -> {
                if (cfo instanceof JavaFileObject) {
                    JavaFileObject jfo = (JavaFileObject) cfo;
                    if (jfo.getKind() == JavaFileObject.Kind.CLASS) {
                        UnixPath foPath = cfo.path();
                        if (expectedPathFile.equals(foPath)) {
                            boolean del = cfo.delete();
                            if (del) {
                                result.set();
                            }
                        } else {
                            UnixPath foParent = foPath.getParent();
                            if (foParent == null) {
                                foParent = UnixPath.empty();
                            }
                            if (parentFolder.equals(foParent)) {
                                String raw = foPath.rawName();
                                if (raw.startsWith(rawPrefix)) {
                                    boolean del = cfo.delete();
                                    if (del) {
                                        result.set();
                                    }
                                }
                            }
                        }
                    }
                }
            });
        }
        return result.getAsBoolean();
    }

    public boolean isSuccess() {
        return generatedFile != null;
    }

    public JFSCoordinates.Resolvable file() {
        return generatedFile;
    }

    @Override
    public String toString() {
        if (generatedFile != null) {
            return grammarName + " in " + pkg + " -> " + generatedFile.path();
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

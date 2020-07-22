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
package org.nemesis.antlr.project.spi.addantlr;

import com.mastfrog.util.strings.Strings;
import org.nemesis.antlr.project.impl.FoldersHelperTrampoline;
import org.netbeans.api.project.Project;

/**
 * Used by AntlrSupportAdder to determine the initial configuration to set up in
 * a project that Antlr support is being added to.
 *
 * @author Tim Boudreau
 */
public final class NewAntlrConfigurationInfo {

    private final String antlrDirName;
    private final boolean generateListener;
    private final boolean generateVisitor;
    private final String importDirName;
    private final boolean createImportDir;
    private final String antlrVersion;
    private final SkeletonGrammarType skeletonGrammarType;
    private final String javaPackage;
    private final String generatedGrammarName;

    public NewAntlrConfigurationInfo(
            String antlrDirName,
            boolean generateListener,
            boolean generateVisitor,
            String antlrVersion,
            SkeletonGrammarType skeletonGrammarType,
            String importDirName, boolean createImportDir,
            String javaPackage,
            String generatedGrammarName) {
        this.antlrDirName = antlrDirName;
        this.generateListener = generateListener;
        this.generateVisitor = generateVisitor;
        this.antlrVersion = antlrVersion;
        this.skeletonGrammarType = skeletonGrammarType;
        this.importDirName = importDirName;
        this.createImportDir = createImportDir;
        this.javaPackage = javaPackage;
        this.generatedGrammarName = generatedGrammarName;
    }

    public static String findBestJavaPackageSuggestionForGrammarsWhenAddingAntlr(Project project) {
        return FoldersHelperTrampoline.findBestJavaPackageSuggestionForGrammarsWhenAddingAntlr(project);
    }

    public String generatedGrammarName() {
        return generatedGrammarName;
    }

    public String javaPackage() {
        return javaPackage;
    }

    public boolean hasJavaPackage() {
        return !Strings.isBlank(javaPackage);
    }

    public boolean createImportDir() {
        return createImportDir;
    }

    public String importDirName() {
        return importDirName;
    }

    public SkeletonGrammarType skeletonType() {
        return skeletonGrammarType;
    }

    public String antlrDirName() {
        return antlrDirName;
    }

    public boolean generateVisitor() {
        return generateVisitor;
    }

    public boolean generateListener() {
        return generateListener;
    }

    public String antlrVersion() {
        return antlrVersion;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder("NewAntlrConfigurationInfo(");
        sb.append("antlrDirName=").append(antlrDirName)
                .append(", antlrVersion=").append(antlrVersion)
                .append(", generateListener=").append(generateListener)
                .append(", generateVisitor=").append(generateVisitor);
        return sb.append(')').toString();
    }

}

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

/**
 *
 * @author Tim Boudreau
 */
public final class AddAntlrCapabilities {

    private boolean canChooseAntlrVersion = true;
    private boolean canSetGrammarSourceDir = false;
    private boolean canGenerateSkeletonGrammar = false;
    private boolean canSetGrammarImportDir = false;

    public AddAntlrCapabilities() {

    }

    public boolean canSetGrammarImportDir() {
        return canSetGrammarImportDir;
    }

    public boolean canGenerateSkeletonGrammar() {
        return canGenerateSkeletonGrammar;
    }

    public boolean canChooseAntlrVersion() {
        return canChooseAntlrVersion;
    }

    public boolean canSetGrammarSourceDir() {
        return canSetGrammarSourceDir;
    }

    public AddAntlrCapabilities canSetGrammarImportDir(boolean val) {
        canSetGrammarImportDir = val;
        return this;
    }

    public AddAntlrCapabilities canGenerateSkeletonGrammar(boolean val) {
        this.canGenerateSkeletonGrammar = val;
        return this;
    }

    public AddAntlrCapabilities canChooseAntlrVersion(boolean val) {
        this.canChooseAntlrVersion = val;
        return this;
    }

    public AddAntlrCapabilities canSetGrammarSourceDir(boolean val) {
        this.canSetGrammarSourceDir = val;
        return this;
    }
}

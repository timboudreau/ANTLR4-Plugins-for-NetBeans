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
package org.nemesis.antlr.project.spi;

/**
 * Used by AntlrSupportAdder to determine the initial configuration to set up in
 * a project that Antlr support is being added to.
 *
 * @author Tim Boudreau
 */
public final class NewAntlrConfigurationInfo {

    private final boolean generateListener;
    private final boolean generateVisitor;
    private final String antlrVersion;

    public NewAntlrConfigurationInfo(boolean generateListener,
            boolean generateVisitor, String antlrVersion) {
        this.generateListener = generateListener;
        this.generateVisitor = generateVisitor;
        this.antlrVersion = antlrVersion;
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
}

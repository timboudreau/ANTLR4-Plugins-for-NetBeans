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

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import static org.nemesis.antlr.common.AntlrConstants.WRAPPER_MODULE_CNB;
import org.openide.modules.InstalledFileLocator;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 * @author Tim Boudreau
 */
@ServiceProvider(service=AntlrLibrary.class, position = Integer.MAX_VALUE)
public final class DefaultAntlrLibrary implements AntlrLibrary {

    private static final String[] LIB_FILES = {
        "antlr4-4.7.1.jar",
        "ST4-4.0.8.jar",
        "antlr4-runtime-4.7.1.jar",
        "antlr-runtime-3.5.2.jar"
    };

    @Override
    public URL[] getClasspath() {
        InstalledFileLocator loc = InstalledFileLocator.getDefault();
        URL[] result = new URL[LIB_FILES.length];
        for (int i = 0; i < LIB_FILES.length; i++) {
            String libPath = "libs/" + LIB_FILES[i];
            File file = loc.locate(libPath, WRAPPER_MODULE_CNB, false);
            if (file == null) {
                throw new IllegalStateException("Failed to locate " + libPath
                        + " relative to module with " + loc);
            }
            try {
                result[i] = file.toURI().toURL();
            } catch (MalformedURLException ex) {
                throw new AssertionError(ex);
            }
        }
        return result;
    }
}

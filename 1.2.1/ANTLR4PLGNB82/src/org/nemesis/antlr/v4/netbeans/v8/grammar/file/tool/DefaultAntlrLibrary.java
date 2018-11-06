/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
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
        "antlr4-runtime-4.7.1.jar"
    };

    @Override
    public URL[] getClasspath() {
        InstalledFileLocator loc = InstalledFileLocator.getDefault();
        URL[] result = new URL[LIB_FILES.length];
        for (int i = 0; i < LIB_FILES.length; i++) {
            String libPath = "libs/" + LIB_FILES[i];
            File file = loc.locate(libPath, "org.nemesis.antlr.v4.netbeans.v8", false);
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

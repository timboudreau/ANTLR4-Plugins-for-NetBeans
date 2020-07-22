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
package org.nemesis.antlr.wrapper;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.openide.modules.InstalledFileLocator;

/**
 *
 * @author Tim Boudreau
 */
public final class AntlrVersion {

    private static final String REL_PATH = "antlr/modules/ext/com.mastfrog.antlr-wrapper/";
    private static final String MY_CNB = "com.mastfrog.antlr.wrapper";
    private static final String[] JARS = new String[]{
        "org-antlr/antlr4-runtime.jar",
        "org-antlr/antlr4.jar",
        "org-antlr/antlr-runtime.jar",
        "org-antlr/ST4.jar",
        "org-abego-treelayout/org.abego.treelayout.core.jar",
        "org-glassfish/javax.json.jar",
        "com-ibm-icu/icu4j.jar"
    };
    private static final String ANTLR_PROPS_FILE_NAME = "Antlr.properties";
    private static String version;
    private static String moduleVersion;

    public static String version() {
        if (version == null) {
            load();
        }
        return version;
    }

    public static String version(String defaultValue) {
        if (version == null) {
            load();
        }
        switch (version) {
            case "Missing":
            case "Unknown":
                return defaultValue;
        }
        return version;
    }

    public static String moduleVersion() {
        if (moduleVersion == null) {
            load();
        }
        return moduleVersion;
    }

    private static void load() {
        try (InputStream in = AntlrVersion.class.getResourceAsStream(ANTLR_PROPS_FILE_NAME)) {
            Properties props = new Properties();
            props.load(in);
            version = props.getProperty("version");
            if (version == null) {
                version = "Missing";
            }
            moduleVersion = props.getProperty("myVersion");
            if (moduleVersion == null) {
                moduleVersion = "Missing";
            }
        } catch (IOException ex) {
            Logger.getLogger(AntlrVersion.class.getName()).log(Level.WARNING, null, ex);
            moduleVersion = version = "Unknown";
        }
    }

    public static Set<File> allJars() {
        InstalledFileLocator loc = InstalledFileLocator.getDefault();
        Set<File> result = new HashSet<>();
        for (String jar : JARS) {
            String fullRelativePath = MY_CNB + REL_PATH;
            File foundJar = loc.locate(jar, MY_CNB, false);
            if (foundJar == null) {
                Logger.getLogger(AntlrVersion.class.getName()).log(Level.WARNING,
                        "Did not find {0}. Antlr version is {1}",
                        new Object[]{fullRelativePath, version()});
            }
        }
        return result;
    }
}

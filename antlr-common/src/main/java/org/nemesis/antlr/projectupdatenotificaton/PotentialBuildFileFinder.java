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
package org.nemesis.antlr.projectupdatenotificaton;

import java.nio.file.Path;
import java.util.Set;
import org.openide.util.Lookup;

/**
 * Allows plugins to provide a pointer to project build files.
 *
 * @author Tim Boudreau
 */
public interface PotentialBuildFileFinder {

    /**
     * Find build files, adding to the passed set only those which actually
     * exist on disk. For example, a maven provider would resolve "pom.xml"
     * under the passed directory, test if it exists, and if so, add it to the
     * set.
     *
     * @param path The project directory path
     * @param buildFiles A collection of build filees to add to
     */
    void findExistingPossibleBuildFiles(Path path, Set<? super Path> buildFiles);

    /**
     * Find build files provided by all registered implementations adjacent
     * to the passed project directory.
     *
     * @param path A project directory
     * @param into A collection of build files to check for modifications
     */
    static void findPossibleBuildFiles(Path path, Set<? super Path> into) {
        for (PotentialBuildFileFinder f : Lookup.getDefault().lookupAll(PotentialBuildFileFinder.class)) {
            f.findExistingPossibleBuildFiles(path, into);
        }
    }
}

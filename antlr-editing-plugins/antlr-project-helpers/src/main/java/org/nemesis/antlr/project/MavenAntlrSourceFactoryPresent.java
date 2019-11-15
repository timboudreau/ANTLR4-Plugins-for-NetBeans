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
package org.nemesis.antlr.project;

import org.openide.util.Lookup;

/**
 * Marker to allow the Maven project support module to know if the Antlr source
 * factory is present - it will hide some source folders from Other Sources if
 * it is, but they should be visible in Other Sources if not.
 * <p>
 * Implemented by the antlr-project-helpers-maven module, which must cooperate
 * with the antlr-project-extensions module, since Maven projects do not
 * tolerate a source group that also shows up under the Other Sources node.
 * </p>
 *
 * @author Tim Boudreau
 */
public abstract class MavenAntlrSourceFactoryPresent {

    // XXX put this class somewhere else - it is an implementation detail.

    protected abstract boolean isActive();

    /**
     * If true, then hide the antlr folders from Other Sources in Maven
     * projects.
     *
     * @return
     */
    public static boolean isPresent() {
        return Lookup.getDefault()
                .lookupAll(MavenAntlrSourceFactoryPresent.class)
                .stream()
                .anyMatch(p -> (p.isActive()));
    }
}

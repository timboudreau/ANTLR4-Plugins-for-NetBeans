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
package org.nemesis.antlr.project.extensions;

import org.nemesis.antlr.project.MavenAntlrSourceFactoryPresent;
import org.openide.util.lookup.ServiceProvider;

/**
 * Allows for communication with the project extensions module.
 *
 * @author Tim Boudreau
 */
@ServiceProvider(service = MavenAntlrSourceFactoryPresent.class)
public final class Marker extends MavenAntlrSourceFactoryPresent {

    @Override
    protected boolean isActive() {
        return true;
    }

}

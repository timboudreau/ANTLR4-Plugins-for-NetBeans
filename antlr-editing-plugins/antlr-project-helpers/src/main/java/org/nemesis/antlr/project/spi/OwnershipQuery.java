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

import java.nio.file.Path;
import org.nemesis.antlr.project.Folders;

/**
 * Provided by some heuristic queries which need to use the file being queried
 * for to resolve locations, and so, need to know when they're being asked for
 * the owning Folders of a file because they can't just get it from a
 * configuration file; for the case that the configuration doesn't know a-priori
 * where everything is, and needs to look for it rather than iterate everything
 * it knows about and see what's the folder is a child of.
 *
 * @author Tim Boudreau
 */
public interface OwnershipQuery {

    Folders findOwner(Path file);
}

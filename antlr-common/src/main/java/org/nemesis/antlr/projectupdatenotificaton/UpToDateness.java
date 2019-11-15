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

import static com.mastfrog.util.preconditions.Checks.notNull;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Path;

/**
 * Can tell if a set of files' last-modified date has changed since the last
 * call to isChanged(); used for cache invalidation in several persistent
 * caches, to figure out if the project files need to be reread to determine if
 * a project is an antlr project or any of its parents is. It will dynamically
 * fetch the dependency graph of the module from ProjectUpdates, so that as a
 * project's configuration changes, it will always look at the right project
 * files, and a change in the count of projects it looks at similarly also cause
 * it to report it is changed.
 * <p>
 * The only things an instance caches are:
 * <ul>
 * <li>The greatest file last modified date on any build file of any project
 * that this one depends on or its own build file</li>
 * <li>The path to the project folder</li>
 * <li>The count of dependencies last seen (in theory, though unlikely in
 * practice, the parent project could be changed to incorporate Antlr)</li>
 * </ul>
 *
 * @author Tim Boudreau
 */
public interface UpToDateness {

    static UpToDateness forProject(Path projectDir) {
        return new ProjectUpdateStatus(notNull("projectDir", projectDir));
    }

    /**
     * Will return true if any of the files this object provides status for has
     * changed since the last call to <code>isChanged()</code>, resetting the
     * status.
     *
     * @return true if a file's date has changed since the last call
     */
    boolean isChanged();

    /**
     * Write this object to a channel, returning the number of bytes written.
     *
     * @param <C> The channel type, e.g. FileChannel.
     * @param channel The channel
     * @return The number of bytes written
     * @throws IOException If something goes wrong
     */
    <C extends WritableByteChannel & SeekableByteChannel> int store(C channel) throws IOException;

    /**
     * The path which (along with its dependencies) is monitored.
     *
     * @return A path to a project directory
     */
    Path path();

    static UpToDateness load(ByteBuffer buffer) throws IOException {
        return ProjectUpdateStatus.load(buffer);
    }

    static <C extends ReadableByteChannel & SeekableByteChannel> UpToDateness load(C channel) throws IOException {
        return ProjectUpdateStatus.load(channel);
    }
}

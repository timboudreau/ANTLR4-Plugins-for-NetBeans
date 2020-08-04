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
package org.nemesis.antlr.live.impl;

import com.mastfrog.util.path.UnixPath;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import org.nemesis.antlr.memory.JFSPathHints;
import org.nemesis.jfs.JFSCoordinates;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

/**
 *
 * @author Tim Boudreau
 */
public abstract class GrammarMappings implements JFSPathHints {

    GrammarMappings() {

    }

    public abstract List<JFSCoordinates> forRawName(String rawName, String... exts);

    public abstract JFSCoordinates forFileObject(FileObject fo);

    public abstract FileObject originalFile(UnixPath path);

    public abstract FileObject originalFile(JFSCoordinates path);

    @Override
    public final UnixPath firstPathForRawName(String rawName, String... exts) {
        List<JFSCoordinates> coords = forRawName(rawName, exts);
        if (coords != null && !coords.isEmpty()) {
            return coords.get(0).path();
        }
        return null;
    }

    public Path originalPath(UnixPath path) {
        FileObject fo = originalFile(path);
        if (fo == null) {
            return null;
        }
        File file = FileUtil.toFile(fo);
        if (file == null) {
            return null;
        }
        return file.toPath();
    }

}

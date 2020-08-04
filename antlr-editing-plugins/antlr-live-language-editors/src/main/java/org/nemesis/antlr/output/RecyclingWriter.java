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
package org.nemesis.antlr.output;

import com.mastfrog.util.path.UnixPath;
import static com.mastfrog.util.preconditions.Checks.notNull;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.nio.file.Path;

/**
 *
 * @author Tim Boudreau
 */
class RecyclingWriter extends OutputStreamWriter {

    private final UnixPath path;
    private final FileIndices indices;
    private final int index;
    private final Path originalPath;

    public RecyclingWriter(UnixPath path, FileIndices indices, int index,
            OutputStream out, Charset cs, Path originalPath) {
        super(out, cs);
        this.path = path;
        this.indices = indices;
        this.index = index;
        this.originalPath = notNull("originalPath", originalPath);
        assert !path.equals(originalPath)
                && !UnixPath.get(path).equals(originalPath) : "Original path should not be a path within the JFS: " + originalPath;
    }

    @Override
    public void close() throws IOException {
        try {
            super.close();
        } finally {
            indices.readyForRead(index);
            Output.INSTANCE.onWriterClosed(originalPath);
        }
    }

    public UnixPath outputPath() {
        return path;
    }

    public FileIndices indices() {
        return indices;
    }

    public int index() {
        return index;
    }

    public String toString() {
        return "RecyclingWriter(ix " + index + " for " + path
                + " originalPath " + originalPath + " indices " + indices + ")";
    }

}

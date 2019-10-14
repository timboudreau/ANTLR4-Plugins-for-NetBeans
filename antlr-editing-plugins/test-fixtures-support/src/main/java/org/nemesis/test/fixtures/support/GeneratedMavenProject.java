/*
BSD License

Copyright (c) 2016-2018, Frédéric Yvon Vinet, Tim Boudreau
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

* Redistributions of source code must retain the above copyright
  notice, this list of conditions and the following disclaimer.
* Redistributions in binary form must reproduce the above copyright
  notice, this list of conditions and the following disclaimer in the
  documentation and/or other materials provided with the distribution.
* The name of its author may not be used to endorse or promote products
  derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR IMPLIED 
WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF 
MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT
SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, 
EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT
OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS 
INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN 
CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING
IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY 
OF SUCH DAMAGE.
*/
package org.nemesis.test.fixtures.support;

import com.mastfrog.function.throwing.ThrowingRunnable;
import com.mastfrog.util.file.FileUtils;
import com.mastfrog.util.strings.Strings;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.swing.text.StyledDocument;
import org.netbeans.api.project.Project;
import org.openide.cookies.EditorCookie;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;
import org.openide.loaders.DataObjectNotFoundException;

/**
 *
 * @author Tim Boudreau
 */
public final class GeneratedMavenProject {

    private final Path dir;
    private final String name;
    final Map<String, Path> map = new HashMap<>();
    private volatile boolean preserve;

    public GeneratedMavenProject(Path dir, String name) {
        this.dir = dir;
        this.name = name;
    }

    public GeneratedMavenProject preserve() {
        preserve = true;
        return this;
    }

    public GeneratedMavenProject deletedBy(ThrowingRunnable run) {
        run.andAlways(() -> {
            if (!preserve) {
                delete();
            }
        });
        return this;
    }

    public Project project() throws IOException {
        return ProjectTestHelper.findProject(dir);
    }

    public Map<String,Path> allFiles() {
        return Collections.unmodifiableMap(map);
    }

    public GeneratedMavenProject setText(String filename, String newText) throws IOException {
        // Use the filesystem API to ensure notifcations propagate
        FileObject fo = file(filename);
        try (OutputStream out = fo.getOutputStream()) {
            out.write(newText.getBytes(UTF_8));
        }
        return this;
    }

    public GeneratedMavenProject replaceString(String filename, String from, String to) throws IOException {
        String txt = file(filename).asText();
        String nue = Strings.literalReplaceAll(from, to, txt, false).toString();
        setText(filename, nue);
        return this;
    }

    public FileObject file(String filename) {
        File file = get(filename).toFile();
        return FileUtil.toFileObject(FileUtil.normalizeFile(file));
    }

    public DataObject dataObject(String filename) throws DataObjectNotFoundException {
        return DataObject.find(file(filename));
    }

    public EditorCookie.Observable cookie(String filename) throws DataObjectNotFoundException {
        DataObject dob = dataObject(filename);
        return dob.getLookup().lookup(EditorCookie.Observable.class);
    }

    public StyledDocument document(String filename) throws DataObjectNotFoundException, IOException {
        return cookie(filename).openDocument();
    }

    public Path get(String filename) {
        Path result = map.get(filename);
        MavenProjectBuilder.assertNotNull(result, "No file '" + filename + "' written in " + map);
        return result;
    }

    public Path dir() {
        return dir;
    }

    @Override
    public String toString() {
        return name;
    }

    public void delete() throws IOException {
        if (dir != null && Files.exists(dir)) {
            FileUtils.deltree(dir);
        }
    }

}

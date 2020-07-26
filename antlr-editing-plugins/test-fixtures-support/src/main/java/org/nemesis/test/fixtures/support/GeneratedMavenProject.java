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
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.prefs.Preferences;
import javax.swing.text.StyledDocument;
import static org.nemesis.test.fixtures.support.TestFixtures.assertNotNull;
import org.netbeans.api.project.Project;
import org.openide.cookies.EditorCookie;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;
import org.openide.loaders.DataObjectNotFoundException;
import org.openide.util.NbPreferences;

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

    public FileObject addFile(String relativePath, String content) throws IOException {
        Path pth = add(relativePath, content);
        assertNotNull(pth);
        return FileUtil.toFileObject(pth.toFile());
    }

    public Path add(String relativePath, String content) throws IOException {
        Path pth = dir.resolve(relativePath);
        Files.write(pth, content.getBytes(UTF_8), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        String fn = Paths.get(relativePath).getFileName().toString();
        map.put(fn, pth);
        return pth;
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

    public Map<String, Path> allFiles() {
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

    public GeneratedMavenProject updateText(String filename, Function<String, String> mutator) throws IOException {
        FileObject fo = file(filename);
        String txt = fo.asText();
        String nue = mutator.apply(file(filename).asText());
        if (!Objects.equals(txt, nue)) {
            try (OutputStream out = fo.getOutputStream()) {
                out.write(nue.getBytes(UTF_8));
            }
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
        StyledDocument doc = cookie(filename).openDocument();
        Preferences prefs = NbPreferences.forModule(GeneratedMavenProject.class);
        // Try to prevent the module system from getting initialized by CodeStylePreferences
        doc.putProperty("Tools-Options->Editor->Formatting->Preview - Preferences", prefs);
        return doc;
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

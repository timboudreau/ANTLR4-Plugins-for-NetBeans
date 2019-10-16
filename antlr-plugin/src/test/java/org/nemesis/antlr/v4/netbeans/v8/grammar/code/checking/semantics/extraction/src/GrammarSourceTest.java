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
package org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.extraction.src;

import org.nemesis.source.api.GrammarSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.antlr.v4.runtime.CharStream;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import org.junit.Test;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.TestDir;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

/**
 *
 * @author Tim Boudreau
 */
public class GrammarSourceTest {

    @Test
    public void testSimpleStrings() throws IOException {
        GrammarSource<String> gs = GrammarSource.find("Hello world", "text/x-g4");
        assertNotNull(gs);
        assertEquals("Hello world", gs.source());
        CharStream str = gs.stream();
        StringBuilder sb = new StringBuilder();
        int ch;
        while ((ch = str.LA(1)) != -1) {
            char c = (char) ch;
            sb.append(c);
            str.consume();
        }
        assertEquals("Hello world", sb.toString());
    }

//    @Test
    public void testLookupInProject() throws Throwable {
        // FIxme - need a fake project

        Path dir = Paths.get(System.getProperty("java.io.tmpdir"));
        Path testDir = dir.resolve(GrammarSourceTest.class.getSimpleName() + "-" + System.currentTimeMillis());
        Files.createDirectories(testDir);
        TestDir td = new TestDir(testDir, "testLookupInProject", "Rust-Minimal._g4", "com.foo");
        td.addImportFile("xidcontinue.g4", TestDir.readFile("xidcontinue._g4"));
        td.addImportFile("xidstart.g4", TestDir.readFile("xidstart._g4"));

        System.out.println("TD IS \n" + td);

        FileObject fo = FileUtil.toFileObject(FileUtil.normalizeFile(td.grammar().toFile()));

        GrammarSource<FileObject> gp = GrammarSource.find(fo, "text/x-g4");

        assertNotNull(gp);
        GrammarSource<?> sib1 = gp.resolveImport("xidcontinue");
        GrammarSource<?> sib2 = gp.resolveImport("xidstart");

        assertNotNull(sib1);
        assertNotNull(sib2);

        td.cleanUp();
    }

}

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
package org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.extraction.src;

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

    @Test
    public void testLookupInProject() throws Throwable {
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

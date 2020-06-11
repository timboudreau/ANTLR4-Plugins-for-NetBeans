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
package org.nemesis.jfs;

import com.mastfrog.util.path.UnixPath;
import java.io.InputStream;
import java.io.OutputStream;
import static java.nio.charset.StandardCharsets.UTF_16;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import javax.swing.text.Document;
import javax.tools.StandardLocation;
import static javax.tools.StandardLocation.SOURCE_PATH;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.nemesis.jfs.DocumentBytesStorageWrapper.EMPTY_BYTE_ARRAY_SHA_1;
import org.nemesis.jfs.spi.JFSUtilities;
import org.netbeans.editor.BaseDocument;
import org.netbeans.junit.MockServices;

/**
 *
 * @author Tim Boudreau
 */
public class DocumentMappingTest {

    @Test
    public void testStorageHashes() throws Exception {
        System.out.println("UTL " + JFSUtilities.getDefault());

        assertArrayEquals(EMPTY_BYTE_ARRAY_SHA_1, sha1(new byte[0]));
        JFS jfs = new JFS(UTF_8);
        JFSStorage stor = jfs.storageForLocation(StandardLocation.SOURCE_PATH, true);
        BaseDocument doc = new BaseDocument(false, "text/plain");
        doc.insertString(0, "I have some trees that sneeze.  How about that?", null);
        DocumentBytesStorageWrapper wrap = new DocumentBytesStorageWrapper(stor, doc);

        byte[] hash1 = wrap.hash();
        byte[] bytes1 = wrap.asBytes();
        String body = wrap.asCharBuffer(true).toString();

        assertEquals("I have some trees that sneeze.  How about that?", body);
        assertArrayEquals("I have some trees that sneeze.  How about that?".getBytes(UTF_8), bytes1);
        assertArrayEquals(sha1("I have some trees that sneeze.  How about that?".getBytes(UTF_8)), hash1);

        doc.remove(0, 6);
        assertEquals(" some trees that sneeze.  How about that?", wrap.asCharBuffer(true).toString());

        doc.remove(0, doc.getLength());
        assertArrayEquals(new byte[0], wrap.asBytes());
        assertArrayEquals(EMPTY_BYTE_ARRAY_SHA_1, wrap.hash());
    }

    private static byte[] sha1(byte[] bytes) throws NoSuchAlgorithmException {
        MessageDigest dig = MessageDigest.getInstance("SHA-1");
        return dig.digest(bytes);
    }

    @Test
    public void testMapDocuments() throws Throwable {
        JFS jfs = new JFS(UTF_16);
        Document document = new BaseDocument(false, "text/plain");
        String txt = "This is some text\nWhich shall be given up for you\n";
        document.insertString(0, txt, null);
        UnixPath pth = UnixPath.get("/some/docs/Doc.txt");
        JFSFileObject fo = jfs.masquerade(document, SOURCE_PATH, pth);
        assertNotNull(fo);
        assertEquals(txt, fo.getCharContent(true).toString());
        long lm = fo.getLastModified();
        long len = fo.length();
        String newText = "And now things are different\nin this here document.\n";
        Thread.sleep(100);
        try (OutputStream out = fo.openOutputStream()) {
            out.write(newText.getBytes(UTF_16));
        }
        assertNotEquals(lm, fo.getLastModified());
        assertNotEquals(len, fo.length());
        assertEquals(newText, fo.getCharContent(true).toString());

        try (InputStream in = fo.openInputStream()) {
            byte[] bytes = new byte[fo.length() + 20];
            int count = in.read(bytes);
            String s = new String(bytes, 0, count, UTF_16);
            assertEquals(newText.length(), s.length());
            assertEquals(newText, s);
        }
        jfs.close();
    }

    @BeforeClass
    public static void setup() {
        MockServices.setServices(JFSUrlStreamHandlerFactory.class, JFSTest.FEQImpl.class);
        org.netbeans.ProxyURLStreamHandlerFactory.register();
    }
}

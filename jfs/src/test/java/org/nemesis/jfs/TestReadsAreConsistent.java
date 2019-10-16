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

import com.mastfrog.function.throwing.ThrowingConsumer;
import com.mastfrog.util.path.UnixPath;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Phaser;
import javax.tools.StandardLocation;
import static org.junit.Assert.assertEquals;
import org.junit.Test;
import org.nemesis.jfs.nio.BlockStorageKind;

/**
 *
 * @author Tim Boudreau
 */
public class TestReadsAreConsistent {

    private static final Charset[] CHARSETS
            = new Charset[]{StandardCharsets.UTF_8, StandardCharsets.ISO_8859_1,
                StandardCharsets.UTF_16, StandardCharsets.US_ASCII};

    @Test
    public void testReads() throws Exception {
        forEachType(this::_testReads);
    }

    private void _testReads(JFS jfs) throws Exception {
        UnixPath a = UnixPath.get("com/foo/some-text.txt");
        UnixPath b = UnixPath.get("com/foo/some-more-text.txt");
        String txt = text(30);
        String txt2 = text(120);
        JFSFileObject fo = jfs.create(a, StandardLocation.SOURCE_OUTPUT, txt);
        JFSFileObject fob = jfs.create(a, StandardLocation.SOURCE_OUTPUT, txt2);
        txt = testReadWriteText(fo, txt, a);
        txt2 = testReadWriteText(fob, txt2, b);
        testParallelReadWriteText(fo, txt, a);
        testParallelReadWriteText(fob, txt2, b);
    }

    private String testParallelReadWriteText(JFSFileObject fo, String origText, Path path) throws Exception {
        ExecutorService svc = Executors.newFixedThreadPool(3);
        for (int i = 1; i < 20; i++) {
            Phaser p = new Phaser(3);
            int bufSize = (i * 16) + i;
            Callable<String> a = () -> {
                p.arriveAndAwaitAdvance();
                return readWithReader(fo, bufSize);
            };
            Callable<String> b = () -> {
                p.arriveAndAwaitAdvance();
                return readAsTextContent(fo);
            };
            Callable<String> c = () -> {
                p.arriveAndAwaitAdvance();
                return readAsBytes(fo, bufSize);
            };
            List<Future<String>> fut = svc.invokeAll(Arrays.asList(a, b, c));
            p.arriveAndDeregister();
            String readerText = fut.get(0).get();
            String textContent = fut.get(1).get();
            String byteContent = fut.get(2).get();
            String info = jfsInfo(fo);
            assertEquals(message("Reader text doesn't match w/ buf size " + bufSize + " " + info), origText, readerText);
            assertEquals(message("Text text doesn't match w/ buf size " + bufSize + " " + info), origText, textContent);
            assertEquals(message("Byte text doesn't match w/ buf size " + bufSize + " " + info), origText, byteContent);
        }
        return origText;
    }

    private String testReadWriteText(JFSFileObject fo, String origText, Path path) throws IOException {
        for (int i = 1; i < 20; i++) {
            int bufSize = (i * 8) + i;
//            System.out.println(i + ". BS " + bufSize + " " + message(""));
            readPartially(fo, origText);
            String readerText = readWithReader(fo, bufSize);
            String textContent = readAsTextContent(fo);
            String byteContent = readAsBytes(fo, bufSize);
            String info = jfsInfo(fo);
            assertEquals(message("Reader text doesn't match w/ buf size " + bufSize + " " + info), origText, readerText);
            assertEquals(message("Text text doesn't match w/ buf size " + bufSize + " " + info), origText, textContent);
            assertEquals(message("Byte text doesn't match w/ buf size " + bufSize + " " + info), origText, byteContent);
            origText = text(bufSize);
            switch (i % 3) {
                case 0:
                    fo.setBytes(origText.getBytes(currCharset), System.currentTimeMillis());
                    break;
                case 1:
                    try (OutputStream out = fo.openOutputStream()) {
                        out.write(origText.getBytes(currCharset));
                    }
                    break;
                case 2:
                    try (Writer w = fo.openWriter()) {
                        w.write(origText);
                    }
                    break;
            }
        }
        return origText;
    }

    String jfsInfo(JFSFileObject fo) {
        return fo.getClass().getSimpleName() + "(" + ((JFSFileObjectImpl) fo).storage + ")";
    }

    private static String readWithReader(JFSFileObject fo, int bufSize) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (Reader r = fo.openReader(true)) {
            CharBuffer b = CharBuffer.allocate(bufSize);
//            int count;
            for (int count = r.read(b); count > 0; count = r.read(b)) {
//            while ((count = r.read(b)) > 0) {
                b.flip();
                sb.append(b.toString());
                b.rewind();
            }
        }
        return sb.toString();
    }

    private static String readAsTextContent(JFSFileObject fo) throws IOException {
        return fo.getCharContent(true).toString();
    }

    private String readPartially(JFSFileObject fo, String fullText) throws IOException {
        try (InputStream in = fo.openInputStream()) {
            int avail = in.available();
            assertEquals(fo.length(), avail);
            byte[] b = new byte[avail / 3];
            int read = in.read(b);
            return new String(b, 0, read, currCharset);
        }
    }

    private String readAsBytes(JFSFileObject fo, int blockSize) throws IOException {
        byte[] buffer = new byte[blockSize];
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            try (InputStream in = fo.openInputStream()) {
                for (int count = in.read(buffer); count > 0; count = in.read(buffer)) {
                    out.write(buffer, 0, count);
                }
            }
            return new String(out.toByteArray(), currCharset);
        }
    }

    private String message(String msg) {
        return currKind.name() + ":" + currCharset.name() + ": " + msg;
    }

    private Charset currCharset;
    private BlockStorageKind currKind;

    private void forEachType(ThrowingConsumer<JFS> c) throws IOException, Exception {
//        for (BlockStorageKind k : BlockStorageKind.values()) {
        for (BlockStorageKind k : new BlockStorageKind[]{BlockStorageKind.HEAP, BlockStorageKind.OFF_HEAP, BlockStorageKind.MAPPED_TEMP_FILE}) {
            currKind = k;
            for (Charset ch : CHARSETS) {
                currCharset = ch;
                JFS jfs = JFS.builder()
                        .withCharset(ch)
                        .useBlockStorage(k)
                        .build();
                try {
                    c.accept(jfs);
                } finally {
                    jfs.close();
                }
            }
        }
    }

    private final Random rnd = new Random(1039013);

    private String text(int wc) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < wc; i++) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(WORDS[rnd.nextInt(WORDS.length)]);
        }
        return sb.toString();
    }

    private static String[] WORDS = ("okay here we need a whole much of words "
            + "and I mean a lot so we can generate a bunch of random textual "
            + "things that sound like something or don't because it really doesn't "
            + "matter human language is just easier to debug than random characters "
            + "so it seemed like a good idea at the time but now I have backed myself "
            + "into a corner writing this endless run on sentence because punctuation "
            + "would look screwey and we can't have that or something or other would "
            + "happen if you know what I mean because I do not see under ordinary "
            + "circumstances we would just copy paste some stuff from the web but "
            + "that seems like cheating when we have the opportunity to regale you "
            + "with this marvelous monologue that seems very likely never to end "
            + "yet for some mysterious reason you are still reading aren't you "
            + "well isn't that special you know what is unspecial is having to "
            + "write this unit test just to ensure we keep the investment in code "
            + "we have made but that's the way the cookie crumbles and cookies are "
            + "good unless they have raisins you thought were chocolate chips because "
            + "that is just disappointing on many levels and so deceptive it "
            + "should be illegal nobody wants raisin cookies for heaven's sake "
            + "heh I typed raising but then I fixed it to the word for dried "
            + "grapes speaking of which little champagne grapes are very tasty "
            + "but would probably make pinhead sized snacks when dried perhaps "
            + "that would be good sprinkled in a salad nobody would know what "
            + "they are but it is very probably that most would be swallowed "
            + "untasted but perhaps in an indian dish such as chicken korma "
            + "or malai kofta they would be good since they have a chance to "
            + "rehydrate and absorb some spices you know you can make your own "
            + "garam masala there is no such thing as a curry it is just a blend "
            + "of spices but it is not a thing and indian breakfasts such as dosas "
            + "are very good and spicy but I have never seen an indian restaurant that served "
            + "breakfast outside of india speaking of which tuk-tuks are not real "
            + "taxis in thailand they will just take you wherever the driver wants "
            + "to go and you don't want to learn that the hard way").split("\\s");
}

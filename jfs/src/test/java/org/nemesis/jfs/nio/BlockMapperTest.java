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
package org.nemesis.jfs.nio;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.util.Random;
import java.util.function.IntConsumer;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.Before;
import org.junit.Test;
import org.openide.filesystems.FileUtil;

/**
 *
 * @author Tim Boudreau
 */
public class BlockMapperTest {

    private static final Random RND = new Random(98098233);
    private BlockMapper storage;

    private static final int AMT = 7;
    private final byte[][] bytes = new byte[AMT][];
    private final String[] strings = new String[AMT];
    private final BlockMapper.MappedBytes[] vf = new BlockMapper.MappedBytes[AMT];

    @Test
    public void testReadWithInFlightWrite() throws IOException {
        for (int i = 0; i < AMT; i++) {
            String s = new String(vf[i].getBytes(), US_ASCII);
            assertEquals(strings[i], s);

            String nue = "Moo goo gai pan " + i;
            final BlockMapper.MappedBytes file = vf[i];
            s = readStringSlowly(vf[i], ix -> {
                if (ix == 1) {
                    try (OutputStream out = file.openOutputStream()) {
                        out.write(nue.getBytes(US_ASCII));
                    } catch (IOException ex) {
                        throw new AssertionError(ex);
                    }
                }
            });
            assertEquals(strings[i], s);
            s = readString(vf[i]);
            assertEquals(nue, s);
            System.out.println("\nGOT " + s);
        }

    }

    @Test
    public void test() throws IOException {
        for (int i = 0; i < AMT; i++) {
            String s = new String(vf[i].getBytes(), US_ASCII);
            assertEquals(strings[i], s);
            s = readString(vf[i]);
            assertEquals(strings[i], s);
            s = readStringSlowly(vf[i]); // ensures we fully test SnapshottableInputStream
            assertEquals(strings[i], s);
        }
        for (int i = 0; i < AMT; i++) {
            if (i % 2 == 0) {
                vf[i].delete();
                vf[i] = null;
            }
        }
        System.out.println("MAN NOW " + storage.blockManager());

        for (int i = 0; i < AMT; i++) {
            if (i % 2 == 0) {
                continue;
            }
            String s = new String(vf[i].getBytes(), US_ASCII);
            assertEquals(i + "", strings[i], s);
            s = readString(vf[i]);
            assertEquals(strings[i], s);
            s = readStringSlowly(vf[i]); // ensures we fully test SnapshottableInputStream
            assertEquals(strings[i], s);
        }

        for (int i = 0; i < AMT; i++) {
            if (i % 2 == 0) {
                vf[i] = storage.allocate(("Hello world " + i + "...").getBytes(US_ASCII));
                System.out.println("HELLO WORLD " + i + " @ " + vf[i].blocks());
            }
        }

        System.out.println("AND NOW: " + storage.blockManager());

        storage.blockManager().simpleDefrag();
//
        System.out.println("AFTER DEFRAG: " + storage.blockManager());

        Blocks[] allBlocks = new Blocks[AMT];
        for (int i = 0; i < AMT; i++) {
            allBlocks[i] = (vf[i].blocks().copy());
        }
        for (int i = 0; i < AMT; i++) {
            Blocks b = vf[i].blocks();
            for (int j = 0; j < AMT; j++) {
                if (j != i) {
                    Blocks test = allBlocks[j];
                    if (b.overlaps(test.shrunkBy(1))) {
                        fail("vf[" + i + "] and vf[" + j + "] overlap: " + b + " and " + test);
                    }
                }
            }
        }

        for (int i = 0; i < AMT; i++) {
            if (i % 2 == 0) {
                String expect = "Hello world " + i + "...";
                assertEquals(expect, new String(vf[i].getBytes(), US_ASCII));
            } else {
                String s = new String(vf[i].getBytes(), US_ASCII);
                assertEquals(i + " " + vf[i].blocks(), strings[i], s);
                s = readString(vf[i]);
                assertEquals(strings[i], s);
                s = readStringSlowly(vf[i]); // ensures we fully test SnapshottableInputStream
                assertEquals(strings[i], s);
            }
        }

        byte[] hi = "Hi".getBytes(US_ASCII);
        byte[] bye = "Bye".getBytes(US_ASCII);
        for (int i = 0; i < AMT; i++) {
            if (i % 2 == 0) {
                vf[i].setBytes(hi);
            } else {
                vf[i].setBytes(bye);
            }
        }

        System.out.println("AFTER SHORTEN: " + storage.blockManager());
        for (int i = 0; i < AMT; i++) {
            byte[] b = vf[i].getBytes();
            if (i % 2 == 0) {
                assertArrayEquals(i + ": '" + new String(b, US_ASCII) + "' @ " + vf[i].blocks(), hi, b);
            } else {
                assertArrayEquals(i + ": '" + new String(b, US_ASCII) + "' @ " + vf[i].blocks(), bye, b);
            }
        }

        storage.blockManager().simpleDefrag();
        System.out.println("AFTER DEFRAG 2: " + storage.blockManager());
        for (int i = 0; i < AMT; i++) {
            byte[] b = vf[i].getBytes();
            if (i % 2 == 0) {
                assertArrayEquals(i + ": '" + new String(b, US_ASCII) + "' @ " + vf[i].blocks(), hi, b);
            } else {
                assertArrayEquals(i + ": '" + new String(b, US_ASCII) + "' @ " + vf[i].blocks(), bye, b);
            }
        }
        storage.blockManager().fullDefrag();
        for (int i = 0; i < AMT; i++) {
            byte[] b = vf[i].getBytes();
            if (i % 2 == 0) {
                assertArrayEquals(i + ": '" + new String(b, US_ASCII) + "' @ " + vf[i].blocks(), hi, b);
            } else {
                assertArrayEquals(i + ": '" + new String(b, US_ASCII) + "' @ " + vf[i].blocks(), bye, b);
            }
        }
    }

    private String readString(BlockMapper.MappedBytes vf) throws IOException {
        return new String(readInputStream(vf), UTF_8);
    }

    private String readStringSlowly(BlockMapper.MappedBytes vf) throws IOException {
        return readStringSlowly(vf, null);
    }

    private String readStringSlowly(BlockMapper.MappedBytes vf, IntConsumer action) throws IOException {
        return new String(readBytesSlowly(5, vf.openInputStream(), action), UTF_8);
    }

    private byte[] readInputStream(BlockMapper.MappedBytes vf) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (InputStream in = vf.openInputStream()) {
            FileUtil.copy(in, out);
        }
        return out.toByteArray();
    }

    private byte[] readBytesSlowly(int chunkSize, InputStream in, IntConsumer action) throws IOException {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            int read;
            byte[] buf = new byte[chunkSize];
            int ix = 0;
            while ((read = in.read(buf)) != -1) {
                out.write(buf, 0, read);
                if (action != null) {
                    action.accept(ix++);
                }
            }
            return out.toByteArray();
        } finally {
            in.close();
        }
    }

    @Before
    public void populate() throws IOException {
//        storage = new BlockMapper(10, 2);
        storage = (BlockMapper) BlockStorageKind.MAPPED_TEMP_FILE.create(10, 2);
        int lastEndBlock = -1;
        int lastStartBlock = -1;
        for (int i = 0; i < AMT; i++) {
            int size = Math.max(3, ((AMT - i) + 1) * 12);
            assert size > 0;
            bytes[i] = randomBytes(size);
            strings[i] = new String(bytes[i], US_ASCII);
            System.out.println("LEN " + bytes[i].length);
            vf[i] = storage.allocate(bytes[i]);
            assertArrayEquals(i + "", bytes[i], vf[i].getBytes());
            assertArrayEquals(i + "", bytes[i], vf[i].getBytes());
            assertEquals("Size allocated incorrectly", size, vf[i].size());
            assertTrue(vf[i].allocationSize() >= size);
            Blocks blocks = vf[i].blocks();
            System.out.println("ALLOCATED " + blocks + " for array of " + size);
            assertNotNull(blocks);
            if (lastEndBlock >= 0) {
                assertTrue("Clobbering: " + lastEndBlock + " vs " + blocks, lastEndBlock < blocks.start());
            }
            if (lastStartBlock >= 0) {
                assertFalse(vf[i].blocks().contains(lastStartBlock));
                assertFalse(vf[i - 1].blocks().contains(blocks.start()));
                assertFalse(vf[i - 1].blocks().contains(blocks.stop()));
                assertFalse(blocks.contains(vf[i - 1].blocks().start()));
                assertFalse(blocks.contains(vf[i - 1].blocks().stop()));
            }
            lastEndBlock = blocks.stop();
            lastStartBlock = blocks.start();
            if (i > 0) {
                byte[] expectedPrev = bytes[i - 1];
                byte[] actualPrev = vf[i - 1].getBytes();
                assertArrayEquals("Writing item " + i + " changed the content of item " + (i - 1)
                        + " - got '" + new String(actualPrev, US_ASCII) + "' expected "
                        + "'" + new String(expectedPrev, US_ASCII) + "'", expectedPrev, actualPrev);
            }
        }
    }

    private static byte[] randomBytes(int count) {
        byte[] result = new byte[count];
        for (int i = 0; i < count; i++) {
            if (i != 0 && i % 4 == 0) {
                result[i] = '-';
            } else {
                result[i] = CHARS[RND.nextInt(CHARS.length)];
            }
        }
        return result;
    }

    private static final byte[] CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789".getBytes(UTF_8);

}

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

import static com.mastfrog.util.preconditions.Checks.notNull;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.Segment;
import org.nemesis.jfs.spi.JFSUtilities;

/**
 *
 * @author Tim Boudreau
 */
final class DocumentBytesStorageWrapper implements JFSBytesStorage, DocumentListener, HashingStorage {

    // the sha-1 hash of an empty byte array
    static byte[] EMPTY_BYTE_ARRAY_SHA_1 = new byte[]{-38, 57, -93, -18, 94, 107, 75, 13, 50, 85, -65, -17, -107, 96, 24, -112, -81, -40, 7, 9};
    private final JFSStorage storage;
    private final Document doc;
    private final Segment segment = new Segment();
    static boolean computeHashes = JFSUtilities.documentListenersHavePriority(); // package private for tests
    private AtomicReference<byte[][]> cache = new AtomicReference<>();


    @SuppressWarnings("LeakingThisInConstructor")
    DocumentBytesStorageWrapper(JFSStorage storage, Document doc) {
        this.storage = notNull("storage", storage);
        this.doc = notNull("doc", doc);
        // Ensure initialization of the PriorityTimestamp in NbJFSUtilities
        lastModified();
        recomputeHashes();
        JFSUtilities.attachWeakListener(doc, this);
    }

    private void recomputeHashes() {
        if (computeHashes) {
            doc.render(() -> {
                doc.render(() -> {
                    int length = doc.getLength();
                    if (length == 0) {
                        cache.set(new byte[][]{new byte[0], EMPTY_BYTE_ARRAY_SHA_1});
                        return;
                    }
                    try {
                        doc.getText(0, length, segment);
                        byte[] bytes = new String(segment.array, segment.offset, segment.count).getBytes(storage.encoding());
                        // Ugh. The below should work, if the UTF-8 encoder worked as advertised, and
                        // returned the ByteBuffer's limit set to the number of bytes written; but it
                        // returns the total backing array size of 4096 minus one, instead on JDK 14.
//                        CharsetEncoder enc = storage.encoding().newEncoder();
//                        ByteBuffer buf = enc.encode(CharBuffer.wrap(segment.array));
//                        System.out.println("retbufpos " + buf.position() + " limit " + buf.limit() + " remaining " + buf.remaining() + " hasArray? " + buf.hasArray());
//                        if (buf.hasArray()) {
//                            System.out.println("  array len " + buf.array().length);
//                        }
//                        byte[] bytes = new byte[buf.limit()];;
//                        buf.get(bytes);
                        MessageDigest dig = MessageDigest.getInstance("SHA-1");
                        byte[] hash = dig.digest(bytes);
                        cache.set(new byte[][]{bytes, hash});
                    } catch (BadLocationException | NoSuchAlgorithmException ex) {
                        Logger.getLogger(DocumentBytesStorageWrapper.class.getName()).log(Level.INFO, null, ex);
                    }
                });
            });
        }
    }

    @Override
    public boolean hash(MessageDigest into) throws IOException {
        if (!computeHashes) {
            return false;
        }
        byte[][] cached = cache.get();
        if (cached != null) {
            into.update(cached[0]);
            return true;
        }
        return false;
    }

    @Override
    public byte[] hash() throws IOException {
        if (!computeHashes) {
            return null;
        }
        byte[][] cached = cache.get();
        return cached == null ? null : cached[1];
    }

    @Override
    public CharBuffer asCharBuffer(boolean ignoreEncodingErrors) throws IOException {
        if (computeHashes) {
            byte[][] bts = cache.get();
            if (bts != null) {
                return CharBuffer.wrap(new String(bts[0], storage.encoding()));
            }
        }
        return JFSBytesStorage.super.asCharBuffer(ignoreEncodingErrors);
    }

    @Override
    public CharBuffer asCharBuffer(Charset encoding, boolean ignoreEncodingErrors) throws IOException {
        BadLocationException[] ex = new BadLocationException[1];
        synchronized (this) {
            doc.render(() -> {
                int length = doc.getLength();
                try {
                    doc.getText(0, length, segment);
                } catch (BadLocationException e) {
                    ex[0] = e;
                }
            });
            if (ex[0] != null) {
                throw new JFSException(storage.jfs(), "Exception fetching text from "
                        + doc, ex[0]);
            }
            Segment result = (Segment) segment.clone();
            return CharBuffer.wrap(result, result.offset, result.count);
        }
    }

    @Override
    public JFSStorageKind storageKind() {
        return JFSStorageKind.MASQUERADED_DOCUMENT;
    }

    Document document() {
        return doc;
    }

    @Override
    public String toString() {
        return doc.toString();
    }

    @Override
    public ByteBuffer asByteBuffer() throws IOException {
        if (computeHashes) {
            byte[][] cached = cache.get();
            if (cached != null) {
                return ByteBuffer.wrap(cached[0]);
            }
        }
        Charset encoding = storage.encoding();
        CharsetEncoder enc = encoding.newEncoder();
        return enc.encode(asCharBuffer(true));
    }

    @Override
    public byte[] asBytes() throws IOException {
        if (computeHashes) {
            byte[][] cached = cache.get();
            if (cached != null) {
                return cached[0];
            }
        }
        return asCharBuffer(true).toString().getBytes(storage.encoding());
    }

    @Override
    public OutputStream openOutputStream() throws IOException {
        return new Out();
    }

    @Override
    public long lastModified() {
        return JFSUtilities.lastModifiedFor(doc);
    }

    @Override
    public JFSStorage storage() {
        return storage;
    }

    @Override
    public void discard() {
        // do nothing
    }

    @Override
    public int length() {
        return (int) (doc.getLength() * storage.encoding().newEncoder().averageBytesPerChar());
    }

    @Override
    public void setBytes(byte[] bytes, long lastModified) throws IOException {
        CharsetDecoder dec = storage.encoding().newDecoder();
        CharBuffer cb = dec.decode(ByteBuffer.wrap(bytes));
        int maxTries = 3;
        for (int i = 0; i < maxTries;) {
            try {
                int len = doc.getLength();
                if (len > 0) {
                    doc.remove(0, doc.getLength());
                }
                doc.insertString(0, cb.toString(), null);
                break;
            } catch (BadLocationException ex) {
                if (i == maxTries - 1) {
                    throw new JFSException(storage.jfs(), "Exception setting document bytes in " + doc);
                } else {
                    JFS.LOG.log(Level.FINEST, "Reattempting document update", ex);
                }
            }
        }
    }

    void touch(DocumentEvent e) {
        recomputeHashes();
    }

    @Override
    public void insertUpdate(DocumentEvent e) {
        touch(e);
    }

    @Override
    public void removeUpdate(DocumentEvent e) {
        touch(e);
    }

    @Override
    public void changedUpdate(DocumentEvent e) {
        // do nothing
    }

    private final class Out extends ByteArrayOutputStream {

        @Override
        public void close() throws IOException {
            super.close();
            setBytes(toByteArray(), System.currentTimeMillis());
        }
    }
}

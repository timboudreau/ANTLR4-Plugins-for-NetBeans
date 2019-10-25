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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.util.logging.Level;
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
final class DocumentBytesStorageWrapper implements JFSBytesStorage, DocumentListener {

    private final JFSStorage storage;
    private final Document doc;
    private final Segment segment = new Segment();

    DocumentBytesStorageWrapper(JFSStorage storage, Document doc) {
        this.storage = storage;
        this.doc = doc;
        JFSUtilities.attachWeakListener(doc, this);
        // Ensure initialization of the PriorityTimestamp in NbJFSUtilities
        lastModified();
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
                throw new IOException("Exception fetching text from "
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
        Charset encoding = storage.encoding();
        CharsetEncoder enc = encoding.newEncoder();
        return enc.encode(asCharBuffer(true));
    }

    @Override
    public byte[] asBytes() throws IOException {
        //        ByteBuffer buf = asByteBuffer();
//        byte[] bytes = new byte[buf.limit()];
//        buf.get(bytes);
//        return bytes;
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
                    throw new IOException("Exception setting document bytes in " + doc);
                } else {
                    JFS.LOG.log(Level.FINEST, "Reattempting document update", ex);
                }
            }
        }
    }

    void touch() {
    }

    @Override
    public void insertUpdate(DocumentEvent e) {
        touch();
    }

    @Override
    public void removeUpdate(DocumentEvent e) {
        touch();
    }

    @Override
    public void changedUpdate(DocumentEvent e) {
        touch();
    }

    private final class Out extends ByteArrayOutputStream {

        @Override
        public void close() throws IOException {
            super.close();
            setBytes(toByteArray(), System.currentTimeMillis());
        }
    }
}

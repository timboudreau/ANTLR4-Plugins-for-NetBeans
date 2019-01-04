package org.nemesis.antlr.v4.netbeans.v8.grammar.file.experimental.nio;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;

/**
 * Virtual-filesystem-like storage for bytes, which uses a block-mapping
 * paradigm, where each "file" occupies some number of blocks, and each block is
 * some number of bytes; essentially, a minimal memory manager for wads of
 * bytes. Can use heap memory, direct memory or memory mapped files.
 *
 * <p>
 * To create one, see BlockStorageKind.create().</p>
 *
 * @author Tim Boudreau
 */
public interface BlockStorage extends AutoCloseable {

    /**
     * Allocate a block of storage.
     *
     * @param bytesLength The number of bytes to allocate
     * @return An object to manipulate the bytes with
     * @throws IOException If something goes wrong
     */
    StoredBytes allocate(int bytesLength) throws IOException;

    /**
     * Allocate a block of storage, providing the initial content for it.
     *
     * @param bytes the initial content, which also determines the number of
     * bytes allocated
     * @return An object to manipulate the bytes with
     * @throws IOException If something goes wrong
     */
    StoredBytes allocate(byte[] initialContents) throws IOException;

    @Override
    default void close() throws IOException {

    }

    public interface StoredBytes {

        int size();

        ByteBuffer readBuffer() throws IOException;

        void delete() throws IOException;

        byte[] getBytes() throws IOException;

        OutputStream openOutputStream();

        InputStream openInputStream();

        void setBytes(byte[] bytes) throws IOException;
    }
}

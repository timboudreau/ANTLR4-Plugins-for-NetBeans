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

import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.SPARSE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;
import java.util.EnumSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.nemesis.jfs.JFSException;
import org.nemesis.jfs.spi.JFSUtilities;

/**
 *
 * @author Tim Boudreau
 */
abstract class ByteBufferAllocator implements AutoCloseable {

    public abstract ByteBuffer allocate(int size) throws IOException;

    public abstract ByteBuffer grow(int newSize, int lastUsedByte) throws IOException;

    public abstract ByteBuffer current();

    @SuppressWarnings("LeakingThisInConstructor")
    ByteBufferAllocator() {
        ALL_ALLOCATORS.add(this);
    }

    public int currentBufferSize() {
        try {
            ByteBuffer buf = current();
            return buf == null ? 0 : buf.capacity();
        } catch (Exception ex) {
            Logger.getLogger(ByteBuffer.class.getName()).log(Level.SEVERE,
                    "Error fetching current buffer size", ex);
            return 0;
        }
    }

    int currentAllocation() {
        return currentBufferSize();
    }

    int[] last = new int[3];
    Exception lastCall;

    public synchronized void move(int oldStart, int length, int newStart) throws IOException {
        if (oldStart == last[0] && newStart == last[2]) {
            IOException ex = new JFSException("Duplicate move call in " + this, lastCall);
            ex.printStackTrace(System.out);
            lastCall.printStackTrace(System.out);
            System.out.flush();
            throw ex;
        }
        lastCall = new Exception();
        lastCall.fillInStackTrace();
        last[0] = oldStart;
        last[1] = length;
        last[2] = newStart;

        int newEnd = newStart + length;
        int oldEnd = oldStart + length;
        boolean newStartOverlaps = newStart >= newStart && newStart < oldEnd;
        boolean newEndOverlaps = newEnd >= oldStart && newEnd < oldEnd;
        boolean overlaps = newStartOverlaps || newEndOverlaps;
        ByteBuffer curr = current();
        if (!overlaps) {
            ops().set("alloc-copy-no-overlap {0} bytes from {1} to {2}", length, oldStart, newStart);
            ByteBuffer toCopy = curr.duplicate();
            toCopy.position(oldStart);
            toCopy.limit(oldEnd);
            curr.limit(newStart + length);
            curr.position(newStart);
            curr.put(toCopy.slice());
        } else {
            ops().set("alloc-copy-with-overlap {0} bytes from {1} to {2}", length, oldStart, newStart);
            curr = curr.duplicate();
            byte[] bytes = new byte[length];
            try {
                curr.limit(Math.max(oldStart + length, newStart + length));
                curr.position(oldStart);
                curr.get(bytes);
                curr.position(newStart);
                curr.put(bytes);
            } catch (BufferUnderflowException ex) {
                throw new JFSException("Underflow fetching " + length + " bytes "
                        + " at " + oldStart + " to move to " + newStart + " in buffer pos "
                        + curr.position() + " limit " + curr.limit() + " cap " + curr.capacity(), ex);
            }
        }
    }

    public abstract void ensureSize(int size, int copyBytesCount) throws IOException;

    public void close() throws IOException {

    }

    public ByteBuffer slice(int start, int length) {
        ByteBuffer buf = current().duplicate();
        if (buf == null) {
            throw new IllegalStateException("No current buffer");
        }
        int oldLimit = buf.limit();
        int oldPos = buf.position();
        try {
            buf.position(start);
            buf.limit(start + length);
        } catch (IllegalArgumentException e) {
            throw wrapException(buf, oldLimit, oldPos, start + length, start, e);
        }
        return buf.slice();
    }

    public void withBuffer(int position, int limit, Consumer<ByteBuffer> consumer) {
        ByteBuffer buf = current().duplicate();
        int oldLimit = buf.limit();
        int oldPos = buf.position();
        try {
            buf.limit(limit);
            buf.position(position);
            consumer.accept(buf);
        } catch (IllegalArgumentException e) {
            throw wrapException(buf, oldLimit, oldPos, limit, position, e);
        } finally {
            buf.position(oldPos);
            buf.limit(oldLimit);
        }
    }

    public void withBufferIO(int position, int limit, BufferIOConsumer consumer) throws IOException {
        ByteBuffer buf = current().duplicate();
        int oldLimit = buf.limit();
        int oldPos = buf.position();
        try {
            buf.limit(limit);
            buf.position(position);
            consumer.accept(buf);
        } catch (IllegalArgumentException e) {
            throw wrapException(buf, oldLimit, oldPos, limit, position, e);
        } finally {
            buf.limit(oldLimit);
            buf.position(oldPos);
        }
    }

    public <T> T withBufferIO(int position, int limit, BufferIOFunction<T> consumer) throws IOException {
        ByteBuffer buf = current().duplicate();
        int oldLimit = buf.limit();
        int oldPos = buf.position();
        try {
            buf.limit(limit);
            buf.position(position);
            return consumer.go(buf);
        } catch (IllegalArgumentException e) {
            throw wrapException(buf, oldLimit, oldPos, limit, position, e);
        } finally {
            buf.position(oldPos);
            buf.limit(oldLimit);
        }
    }

    public <T> T withBuffer(int position, int limit, Function<ByteBuffer, T> consumer) {
        assert limit >= position : "Contradictory request to position buffer past requested limit,"
                + " position: " + position + " " + limit + ": " + limit;
        ByteBuffer buf = current().duplicate();
        assert buf.capacity() >= position : "Position > capacity";
        assert buf.capacity() >= limit : "Limit > capacity";
        int oldLimit = buf.limit();
        int oldPos = buf.position();
        IllegalArgumentException thrown = null;
        try {
            if (limit < oldPos) {
                buf.position(Math.max(0, limit - 1));
            }
            buf.limit(limit);
            buf.position(position);
            return consumer.apply(buf);
        } catch (IllegalArgumentException e) {
            thrown = wrapException(buf, oldLimit, oldPos, limit, position, e);
            throw thrown;
        } finally {
            // XXX since we're duplicating the buffer, this block is not really
            // needed, but possibly still somewhat informative when debugging
//            try {
//                buf.limit(oldLimit);
//                buf.position(oldPos);
//            } catch (Exception ex) {
//                IllegalArgumentException ex3 = new IllegalArgumentException("Exception restoring buffer's limit to "
//                        + oldLimit + " when it has been changed to " + limit
//                        + " - current state pos " + buf.position() + " limit " + buf.limit()
//                        + " capacity " + buf.capacity());
//                if (thrown != null) {
//                    ex3.initCause(thrown);
//                }
//                throw ex3;
//            }
        }
    }

    private static IllegalArgumentException wrapException(ByteBuffer buf, int oldLimit, int oldPos, int newLimit, int newPos, IllegalArgumentException e) {
        String msg = "IAE limiting buffer with cap " + buf.capacity() + " to " + newLimit + " and positioning to "
                + newPos + " (previous pos " + oldPos + ", limit " + oldLimit + ")";
        return new IllegalArgumentException(msg, e);
    }

    protected abstract Ops ops();

    interface BufferIOConsumer {

        void accept(ByteBuffer buf) throws IOException;
    }

    interface BufferIOFunction<T> {

        T go(ByteBuffer buf) throws IOException;
    }

    static Set<ByteBufferAllocator> ALL_ALLOCATORS = JFSUtilities.newWeakSet();

    static {
        // Allow monitoring
        if (JFSUtilities.areMetricsSupported()) {
            JFSUtilities.registerMetric("jfs.heap", new AllocCollector(a -> a instanceof DefaultByteBufferAllocator
                    && !((DefaultByteBufferAllocator) a).direct), true);
            JFSUtilities.registerMetric("jfs.direct", new AllocCollector(a -> a instanceof DefaultByteBufferAllocator
                    && ((DefaultByteBufferAllocator) a).direct), true);
            JFSUtilities.registerMetric("jfs.mapped", new AllocCollector(a -> a instanceof MappedBufferAllocator), true);
        }
    }

    static final class AllocCollector implements LongSupplier {

        private final Predicate<? super ByteBufferAllocator> targetType;

        public AllocCollector(Predicate<? super ByteBufferAllocator> targetType) {
            this.targetType = targetType;
        }

        @Override
        public long getAsLong() {
            long result = 0;
            for (ByteBufferAllocator bb : ALL_ALLOCATORS) {
                if (targetType.test(bb)) {
                    result += bb.currentAllocation();
                }
            }
            return result;
        }
    }

    static class DefaultByteBufferAllocator extends ByteBufferAllocator {

        private ByteBuffer current;
        private final boolean direct;
        private final Ops ops;

        DefaultByteBufferAllocator(boolean direct, Ops ops) {
            this.direct = direct;
            this.ops = ops;
        }

        protected Ops ops() {
            return ops;
        }

        @Override
        public synchronized void close() throws IOException {
            current = null;
        }

        @Override
        public ByteBuffer current() {
            if (current != null) {
                current.position(0);
                current.limit(current.capacity());
            }
//            return current == null ? null : current.duplicate();
            return current;
        }

        @Override
        public void ensureSize(int size, int copyThru) {
            ByteBuffer buf = current();
            if (buf != null) {
                if (buf.capacity() < size) {
//                    System.out.println("ENSURE SIZE GROWING AND COPYING " + copyThru);
                    grow(size, copyThru);
                }
            } else {
                current = allocate(size);
            }
        }

        @Override
        public synchronized ByteBuffer allocate(int size) {
//            System.out.println("ALLOCATE BYTES " + size);
            current = direct ? ByteBuffer.allocateDirect(size) : ByteBuffer.allocate(size);
            return current;
        }

        @Override
        public synchronized ByteBuffer grow(int newSize, int copyBytesCount) {
            ByteBuffer old = current;
            current = allocate(newSize);
            ops.set("alloc-grow from {0} to {1} copying {2}", old == null
                    ? 0 : old.capacity(), newSize, copyBytesCount);
            if (old != null && copyBytesCount > 0) {
//                System.out.println("GROW AND COPY 0 - " + copyBytesCount + " to size " + newSize);
//                Thread.dumpStack();
                old.limit(Math.min(copyBytesCount, old.capacity()));
                old.position(0);
                current.put(old);
            }
            return current;
        }
    }

    static class MappedBufferAllocator extends ByteBufferAllocator {

        private final Path file;
        private FileChannel fileChannel;
        private int currentSize;
        private MappedByteBuffer mapping;
        private static final Set<StandardOpenOption> OPEN_OPTIONS
                = EnumSet.of(CREATE, READ,
                        WRITE, SPARSE,
                        TRUNCATE_EXISTING);
        private static final Set<StandardOpenOption> REOPEN_OPTIONS
                = EnumSet.of(CREATE, READ,
                        WRITE, SPARSE);

        static {
            if (Boolean.getBoolean("no.sparse.files")) {
                OPEN_OPTIONS.remove(SPARSE);
                REOPEN_OPTIONS.remove(SPARSE);
            }
        }
        private final Ops ops;

        MappedBufferAllocator(int initialSize, Ops ops) throws IOException {
            this(newTempFile(), initialSize, ops);
        }

        private static Path newTempFile() throws IOException {
            Path tmp = Paths.get(System.getProperty("java.io.tmpdir"));
            StringBuilder sb = new StringBuilder()
                    .append(Long.toString(System.currentTimeMillis(), 36))
                    .append('-')
                    .append(Long.toString(System.nanoTime(), 36))
                    .reverse().insert(0, MappedBufferAllocator.class.getSimpleName() + "-")
                    .append(".vfs");

            return tmp.resolve(sb.toString());
        }

        MappedBufferAllocator(Path file, int initialSize, Ops ops) {
            this.file = file;
            currentSize = initialSize;
            this.ops = ops;
        }

        protected Ops ops() {
            return ops;
        }

        @Override
        int currentAllocation() {
            try {
                FileChannel fc = null;
                synchronized (this) {
                    fc = fileChannel;
                }
                if (fc != null && fc.isOpen()) {
                    return (int) fc.size();
                }
            } catch (Exception ex) {
                Logger.getLogger(ByteBufferAllocator.class.getName())
                        .log(Level.INFO, null, ex);
            }
            return currentSize;
        }

        private boolean hadChannel;

        private synchronized FileChannel channel() throws IOException {
            if (fileChannel != null) {
                if (fileChannel.isOpen()) {
                    return fileChannel;
                }
            }
            FileChannel result = fileChannel = FileChannel.open(file,
                    hadChannel ? REOPEN_OPTIONS : OPEN_OPTIONS);
            hadChannel = true;
            return result;
        }

        private static ByteBuffer zero() {
            ByteBuffer result = ByteBuffer.allocate(1);
            result.put((byte) 0);
            result.flip();
            return result;
        }

        private synchronized MappedByteBuffer mapping() throws IOException {
            if (mapping != null) {
                if (mapping.capacity() >= currentSize) {
                    return mapping;
                }
            }
            FileChannel ch = channel();
            for (int i = 0; i < 5; i++) {
                // closed by interrupt on another thread?
                if (!ch.isOpen()) {
                    ch = channel();
                }
            }
            if (ch.size() < currentSize) {
                ch.write(zero(), currentSize);
            }
            mapping = ch.map(FileChannel.MapMode.READ_WRITE, 0, ch.size());
            return mapping;
        }

        @Override
        public ByteBuffer allocate(int size) throws IOException {
            currentSize = size;
            return mapping();
        }

        @Override
        public ByteBuffer grow(int newSize, int lastUsedByte) throws IOException {
            ops.set("alloc-grow from {0} to {1}", currentSize, newSize);
            currentSize = newSize;
            return mapping();
        }

        @Override
        public ByteBuffer current() {
            try {
                return mapping();
            } catch (IOException ex) {
                throw new IllegalStateException(ex);
            }
        }

        /*

        @Override
        public void move(int oldStart, int length, int newStart) throws IOException {
            if (mapping != null) {
                mapping.force();
            }
            FileChannel ch = channel();
            long pos = ch.position();
            try {
                ch.position(oldStart);
                ch.transferTo(newStart, length, ch);
            } finally {
                ch.position(pos);
            }
        }
         */
        @Override
        public void ensureSize(int size, int copyBytesCount) throws IOException {
            allocate(size);
        }

        @Override
        public synchronized void close() throws IOException {
            mapping = null;
            if (fileChannel != null) {
                fileChannel.close();
                fileChannel = null;
            }
            Files.delete(file);
        }
    }
}

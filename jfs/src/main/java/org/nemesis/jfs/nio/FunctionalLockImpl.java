package org.nemesis.jfs.nio;

import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Wraps a reentrant read-write lock, with the following quirks: 1. Going from
 * read to write is checked, rather than just silently deadlocking
 *
 * @author Tim Boudreau
 */
final class FunctionalLockImpl implements FunctionalLock {

    private final ReentrantReadWriteLock lock;
    private final ReentrantReadWriteLock.ReadLock readLock;
    private final ReentrantReadWriteLock.WriteLock writeLock;
    private final ThreadLocal<Integer> readLocked = new ThreadLocal<>();
    private final ThreadLocal<Exception> readLockedAt = new ThreadLocal<>();
    private final String name;

    FunctionalLockImpl(String name) {
        this(false, name);
    }

    FunctionalLockImpl(boolean fair, String name) {
        lock = new ReentrantReadWriteLock(fair);
        readLock = lock.readLock();
        writeLock = lock.writeLock();
        this.name = name;
    }

    void incrementReadLockCount() {
        Integer val = readLocked.get();
        int v = 0;
        if (val != null) {
            v = val;
        }
        readLocked.set(v + 1);
    }

    void decrementReadLockCount() {
        Integer val = readLocked.get();
        if (val == null) {
            throw new IllegalStateException("readUnlock called asymmetrically");
        }
        readLocked.set(val - 1);
    }

    public String toString() {
        return name + " writers " + writers + " readers " + readers + " readLockCount " + readLocked.get();
    }

    private volatile int readers;
    private volatile int writers;

    public void readLock() {
//        if (isWriteLockedByCurrentThread()) {
//            return;
//        }
//        if (writers > 0) {
//            System.out.println("LOCK read " + this + " on thread " + Thread.currentThread().getId() + " with " + writers);
//        }
        readers++;
        readLock.lock();
        incrementReadLockCount();
        readLockedAt.set(new Exception("read-lock"));
    }

    public void readUnlock() {
//        if (isWriteLockedByCurrentThread()) {
//            return;
//        }
        readers--;
        decrementReadLockCount();
        readLock.unlock();
//        System.out.println("UNLOCK read " + this + " on thread " + Thread.currentThread().getId());
    }

    public void writeLock() {
        checkReadLocked();
        writers++;
        writeLock.lock();
//        System.out.println("LOCK WRITE " + this + " on thread " + Thread.currentThread().getId());
    }

    public void writeUnlock() {
        if (!isWriteLockedByCurrentThread()) {
            throw new IllegalStateException("writeUnlock called asymmetrically");
        }
        writers--;
        writeLock.unlock();
//        System.out.println("UNLOCK WRITE " + this + " on thread " + Thread.currentThread().getId());
    }

    public boolean isWriteLockedByCurrentThread() {
        return writeLock.isHeldByCurrentThread();
    }

    boolean isWriteLocked() {
        return lock.isWriteLocked();
    }

    public int underReadLockInt(IntSupplier supp) {
        if (isWriteLockedByCurrentThread()) {
            return supp.getAsInt();
        }
        readLock();
        try {
            return supp.getAsInt();
        } finally {
            readUnlock();
        }
    }

    private void checkReadLocked() {
        if (isReadLockedInCurrentThread()) {
            IllegalStateException ex = new IllegalStateException("Going from read to write lock not allowed");
            Exception rl = readLockedAt.get();
            if (rl != null) {
                ex.initCause(rl);
            }
            throw ex;
        }
    }

    public int underWriteLockInt(IntSupplier supp) {
        checkReadLocked();
        writeLock();
        try {
            return supp.getAsInt();
        } finally {
            writeUnlock();
        }
    }

    public int underReadLockIntIO(IoIntSupplier supp) throws IOException {
        readLock();
        try {
            return supp.getAsInt();
        } finally {
            readUnlock();
        }
    }

    public int underWriteLockIntIO(IoIntSupplier supp) throws IOException {
        checkReadLocked();
        writeLock();
        try {
            return supp.getAsInt();
        } finally {
            writeUnlock();
        }
    }

    public <T> T getUnderReadLockIO(IoCallable<T> call) throws IOException {
        readLock();
        try {
            return call.call();
        } finally {
            readUnlock();
        }
    }

    public <T> T getUnderWriteLockIO(Callable<T> call) throws Exception {
        writeLock();
        try {
            return call.call();
        } finally {
            writeUnlock();
        }
    }

    public void underReadLockIO(IoRunnable call) throws IOException {
        readLock();
        try {
            call.call();
        } finally {
            readUnlock();
        }
    }

    public void underWriteLockIO(IoRunnable call) throws IOException {
        writeLock();
        try {
            call.call();
        } finally {
            writeUnlock();
        }
    }

    public <T> T getUnderReadLock(Supplier<T> call) {
        readLock();
        try {
            return call.get();
        } finally {
            readUnlock();
        }
    }

    public <T> T getUnderWriteLock(Supplier<T> call) {
        writeLock();
        try {
            return call.get();
        } finally {
            writeUnlock();
        }
    }

    public void underReadLock(Runnable run) {
        readLock();
        try {
            run.run();
        } finally {
            readUnlock();
        }
    }

    public void underWriteLock(Runnable run) {
        writeLock();
        try {
            run.run();
        } finally {
            writeUnlock();
        }
    }

    public boolean isReadLockedInCurrentThread() {
        Integer result = this.readLocked.get();
        return result == null ? false : result > 0;
    }
}

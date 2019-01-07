package org.nemesis.jfs.nio;

import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 *
 * @author Tim Boudreau
 */
interface FunctionalLock {

    <T> T getUnderReadLock(Supplier<T> call);

    <T> T getUnderReadLockIO(IoCallable<T> call) throws IOException;

    <T> T getUnderWriteLock(Supplier<T> call);

    <T> T getUnderWriteLockIO(Callable<T> call) throws Exception;

    boolean isWriteLockedByCurrentThread();

    void readLock();

    void readUnlock();

    void underReadLock(Runnable run);

    void underReadLockIO(IoRunnable call) throws IOException;

    int underReadLockInt(IntSupplier supp);

    int underReadLockIntIO(IoIntSupplier supp) throws IOException;

    void underWriteLock(Runnable run);

    void underWriteLockIO(IoRunnable call) throws IOException;

    int underWriteLockInt(IntSupplier supp);

    int underWriteLockIntIO(IoIntSupplier supp) throws IOException;

    void writeLock();

    void writeUnlock();

    interface IoCallable<T> {

        T call() throws IOException;
    }

    interface IoRunnable {

        void call() throws IOException;
    }

    interface IoIntSupplier {

        int getAsInt() throws IOException;
    }
}

package org.nemesis.antlr.v4.netbeans.v8.grammar.file.experimental.nio;

import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * A do-nothing implementation, until we're ready to implement more granular
 * locking.
 *
 * @author Tim Boudreau
 */
final class DysfunctionalLock implements FunctionalLock {

    @Override
    public <T> T getUnderReadLock(Supplier<T> call) {
        return call.get();
    }

    @Override
    public <T> T getUnderReadLockIO(IoCallable<T> call) throws IOException {
        return call.call();
    }

    @Override
    public <T> T getUnderWriteLock(Supplier<T> call) {
        return call.get();
    }

    @Override
    public <T> T getUnderWriteLockIO(Callable<T> call) throws Exception {
        return call.call();
    }

    @Override
    public boolean isWriteLockedByCurrentThread() {
        return false;
    }

    @Override
    public void readLock() {
        // do nothing
    }

    @Override
    public void readUnlock() {
        // do nothing
    }

    @Override
    public void underReadLock(Runnable run) {
        run.run();
    }

    @Override
    public void underReadLockIO(IoRunnable call) throws IOException {
        call.call();
    }

    @Override
    public int underReadLockInt(IntSupplier supp) {
        return supp.getAsInt();
    }

    @Override
    public int underReadLockIntIO(IoIntSupplier supp) throws IOException {
        return supp.getAsInt();
    }

    @Override
    public void underWriteLock(Runnable run) {
        run.run();
    }

    @Override
    public void underWriteLockIO(IoRunnable call) throws IOException {
        call.call();
    }

    @Override
    public int underWriteLockInt(IntSupplier supp) {
        return supp.getAsInt();
    }

    @Override
    public int underWriteLockIntIO(IoIntSupplier supp) throws IOException {
        return supp.getAsInt();
    }

    @Override
    public void writeLock() {
        // do nothing
    }

    @Override
    public void writeUnlock() {
        // do nothing
    }
}

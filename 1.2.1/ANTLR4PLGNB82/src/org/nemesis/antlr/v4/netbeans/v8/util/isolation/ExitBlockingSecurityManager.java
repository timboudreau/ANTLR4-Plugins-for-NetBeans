package org.nemesis.antlr.v4.netbeans.v8.util.isolation;

import java.io.FileDescriptor;
import java.security.Permission;

/**
 * A security manager installed temporarily to prevent calls to
 * System.exit() from foreign execution from working.
 */
class ExitBlockingSecurityManager extends SecurityManager {

    private final ThreadGroup tg;
    private Thread targetThread;

    ExitBlockingSecurityManager(ThreadGroup tg) {
        this.tg = tg;
    }

    Thread setTargetThread(Thread targetThread) {
        this.targetThread = targetThread;
        return targetThread;
    }

    public boolean isExitBlockingException(Throwable t) {
        while (t != null) {
            if (t instanceof BlockExitException) {
                return true;
            }
            t = t.getCause();
        }
        return false;
    }

    @Override
    public ThreadGroup getThreadGroup() {
        return tg;
    }

    @Override
    public void checkExit(int status) {
        if (targetThread == Thread.currentThread()) {
            throw new BlockExitException("Attempt to exit with " + status, status);
        }
    }

    @Override
    public void checkSecurityAccess(String target) {
    }

    @Override
    public void checkSetFactory() {
    }

    @Override
    public void checkPackageDefinition(String pkg) {
    }

    @Override
    public void checkPackageAccess(String pkg) {
    }

    @Override
    public void checkPropertyAccess(String key) {
    }

    @Override
    public void checkPropertiesAccess() {
    }

    @Override
    public void checkDelete(String file) {
    }

    @Override
    public void checkWrite(String file) {
    }

    @Override
    public void checkWrite(FileDescriptor fd) {
    }

    @Override
    public void checkRead(String file, Object context) {
    }

    @Override
    public void checkRead(String file) {
    }

    @Override
    public void checkRead(FileDescriptor fd) {
    }

    @Override
    public void checkLink(String lib) {
    }

    @Override
    public void checkExec(String cmd) {
    }

    @Override
    public void checkAccess(ThreadGroup g) {
    }

    @Override
    public void checkAccess(Thread t) {
    }

    @Override
    public void checkCreateClassLoader() {
    }

    @Override
    public void checkPermission(Permission perm, Object context) {
    }

    @Override
    public void checkPermission(Permission perm) {
    }

    static class BlockExitException extends SecurityException {

        final int code;

        public BlockExitException(String s, int code) {
            super(s);
            this.code = code;
        }
    }
}

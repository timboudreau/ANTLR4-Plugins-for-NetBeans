/*
 * Copyright 2020 Mastfrog Technologies.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.nemesis.antlr.spi.language;

import com.mastfrog.function.throwing.ThrowingSupplier;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicReference;
import org.netbeans.api.annotations.common.CheckForNull;
import org.netbeans.api.annotations.common.NonNull;
import org.openide.util.Lookup;

/**
 *
 * @author Tim Boudreau
 */
final class ParseLock {

    private final Map<Lookup.Provider, ProjectParseLock> lockForProject
            = new WeakHashMap<>();

    private ProjectParseLock lockFor( Lookup.Provider project, Object projectIdentifier ) {
        synchronized ( lockForProject ) {
            ProjectParseLock lock = lockForProject.get( project );
            if ( lock == null ) {
                lock = new ProjectParseLock( projectIdentifier );
                lockForProject.put( project, lock );
            }
            return lock;
        }
    }

    <T> LockRunResult<T> runLocked( Lookup.Provider project, Object projectIdentifier, ThrowingSupplier<T> toRun )
            throws Exception {
        ProjectParseLock lock = lockFor( project, projectIdentifier );
        return lock.runLocked( toRun );
    }

    <T> T runIfUnlocked( Lookup.Provider project, ThrowingSupplier<T> supp ) throws Exception {
        if ( project == null ) {
            return supp.get();
        }
        ProjectParseLock lock;
        synchronized ( lockForProject ) {
            lock = lockForProject.get( project );
        }
        if ( lock == null ) {
            return supp.get();
        } else {
            return lock.runIfNotLocked( supp );
        }
    }

    private static final class ProjectParseLock {
        private final Object projectIdentifier;
        private final AtomicReference<Thread> currentOwner = new AtomicReference<>();

        public ProjectParseLock( Object projectIdentifier ) {
            this.projectIdentifier = projectIdentifier;
        }

        @CheckForNull
        public <T> T runIfNotLocked( @NonNull ThrowingSupplier<T> supp ) throws Exception {
            if ( currentOwner.get() == null ) {
                return supp.get();
            } else {
                return null;
            }
        }

        @NonNull
        public <T> LockRunResult<T> runLocked( @NonNull ThrowingSupplier<T> supp ) throws Exception {
            // There are two conditions under which we will run the suplier:
            //   1. The owning thread was null and we took ownership
            //   2.  The owning thread is the current thread (reentry0
            //
            //   Otherwise we block the parse - this allows newly created
            //   files not to be parsed prematurely
            Thread currentThread = Thread.currentThread();
            // Poor man's quick'n'dirty atomic locking - the use case
            // for actually locking all documents of a mime type within a 
            // project against reparses is vanishingly rare - this is only
            // done when we are generating new files in a project, and do
            // not want them parsed.  This is essentially a write-lock with
            // zero meaningful handling of the case of two concurrent *write*
            // operations, as it write-locking only happens during mutually
            // contradictory operations (while you are generating some sample
            // grammar files in adding ant support to a project, you are
            // definitely not also instantiating some files from template on
            // some other thread, or something like that.
            //
            // This will either
            // 1.  Set the current owner to the current thread,
            //     returning null;  or it will
            // 2.  Not change the current owner, returning the current thread
            //     which is reentry, so proceed, or
            // 3.  Return some other thread, in which case we don't run the
            //     pased supplier, just return a result indicating it was not run
            Thread previousOwner = currentOwner.compareAndExchange( null, currentThread );
            try {
                // Either we acquired ownership, or the previous (and still contemporary)
                // was already this thread
                if ( previousOwner == null || previousOwner == currentThread ) {
                    return new LockRunResult<>( true, supp.get(), currentThread );
                } else {
                    return new LockRunResult<>( false, null, previousOwner );
                }
            } finally {
                if ( previousOwner == null ) {
                    currentOwner.set( null );
                }
            }
        }

        @Override
        public int hashCode() {
            return 59 * projectIdentifier.hashCode();
        }

        @Override
        public boolean equals( Object obj ) {
            if ( this == obj ) {
                return true;
            } else if ( obj == null || !( obj instanceof ProjectParseLock ) ) {
                return false;
            }
            ProjectParseLock other = ( ProjectParseLock ) obj;
            return projectIdentifier.equals( other.projectIdentifier );
        }

        @Override
        public String toString() {
            return "ParseLock(" + projectIdentifier + ")";
        }
    }

    public static class LockRunResult<T> {
        private final boolean ran;
        private final T value;
        private final Thread owningThread;

        public LockRunResult( boolean ran, T value, Thread owningThread ) {
            this.ran = ran;
            this.value = value;
            this.owningThread = owningThread;
        }

        public T result() {
            return value;
        }

        public boolean wasRun() {
            return ran;
        }

        public Thread owningThread() {
            return owningThread;
        }
    }
}

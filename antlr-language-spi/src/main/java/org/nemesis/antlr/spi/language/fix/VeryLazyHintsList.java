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

package org.nemesis.antlr.spi.language.fix;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import javax.swing.text.StyledDocument;
import org.nemesis.editor.function.DocumentConsumer;
import org.nemesis.extraction.Extraction;
import org.netbeans.spi.editor.hints.Fix;
import org.netbeans.spi.editor.hints.LazyFixList;
import org.openide.util.RequestProcessor;

/**
 *
 * @author Tim Boudreau
 */
final class VeryLazyHintsList implements LazyFixList, Runnable {
    private final PropertyChangeSupport supp = new PropertyChangeSupport( this );
    private static final RequestProcessor THREAD_POOL = new RequestProcessor( "antlr-lazy-hints", 5, true );
    private final DocumentConsumer<FixConsumer> lazyFixes;
    private final Extraction extraction;
    private final AtomicReference<List<Fix>> ref = new AtomicReference<>();
    private volatile boolean computed;
    private volatile Future<?> future;
    private final StyledDocument doc;

    public VeryLazyHintsList( DocumentConsumer<FixConsumer> lazyFixes, Extraction extraction, StyledDocument doc ) {
        this.lazyFixes = lazyFixes;
        this.extraction = extraction;
        this.doc = doc;
    }

    @Override
    public void addPropertyChangeListener( PropertyChangeListener l ) {
        Fixes.LOG.log( Level.FINEST, "Add property change listener to {0}: {1}", new Object[ ]{ this, l } );
        supp.addPropertyChangeListener( l );
    }

    @Override
    public void removePropertyChangeListener( PropertyChangeListener l ) {
        Fixes.LOG.log( Level.FINEST, "Remove property change listener from {0}: {1}", new Object[ ]{ this, l } );
        Future<?> fut = this.future;
        supp.removePropertyChangeListener( l );
        if ( fut != null && supp.getPropertyChangeListeners().length == 0 ) {
            Fixes.LOG.log( Level.FINE, "Cancel hint computation on {0}", this );
            fut.cancel( true );
            ref.set( null );
            future = null;
            computed = false;
        }
    }

    @Override
    public boolean probablyContainsFixes() {
        return !extraction.isSourceProbablyModifiedSinceCreation();
//        return true;
        //            List<Fix> all = ref.get();
        //            return all == null || !computed ? true : !all.isEmpty();
    }

    @Override
    public List<Fix> getFixes() {
        return maybeLaunch();
    }

    @Override
    public boolean isComputed() {
        return computed;
    }

    List<Fix> maybeLaunch() {
        if ( ref.compareAndSet( null, Collections.emptyList() ) ) {
            Fixes.LOG.log( Level.FINER, "Launch computation for {0}", this );
            future = THREAD_POOL.submit( this );
        }
        return ref.get();
    }

    @Override
    public void run() {
        Fixes.LOG.log( Level.FINE, "Begin fix computation for {0}", this );
        List<Fix> fixen = null;
        if ( Thread.interrupted() ) {
            Fixes.LOG.log( Level.FINE, "Skip hint generation for interrupted for {0}", this );
            ref.set( null );
            future = null;
            computed = false;
            return;
        }
        try {
            FixConsumer flb = new FixConsumer( doc );
            lazyFixes.accept( flb );
            fixen = flb.entries();
            Fixes.LOG.log( Level.FINER, "Computed {0} fixes for {1} in {2}", new Object[ ]{ fixen.size(), this, flb } );
            ref.set( fixen );
        } catch ( Throwable t ) {
            Fixes.LOG.log( Level.SEVERE, "Exception computing fixes", t );
            t.printStackTrace( System.out );
        } finally {
            computed = true;
            future = null;
            Fixes.LOG.log( Level.FINEST, "Fix computation completed for {0} with {1}", new Object[ ]{ this, fixen } );
        }
        supp.firePropertyChange( PROP_COMPUTED, false, true );
        if ( fixen.size() > 0 ) {
            Fixes.LOG.log( Level.FINEST, "Firing fix change in {0}", this );
            supp.firePropertyChange( PROP_FIXES, Collections.emptyList(), fixen );
        }
    }
}

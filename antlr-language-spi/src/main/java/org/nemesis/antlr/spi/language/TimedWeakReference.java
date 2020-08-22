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

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CopyOnWriteArrayList;
import org.openide.util.Utilities;

/**
 *
 * @author Tim Boudreau
 */
final class TimedWeakReference<T> extends WeakReference<T> implements Runnable {

    private static final long DELAY = 15000;
    private volatile T strong;
    private volatile long expiry = System.currentTimeMillis() + DELAY;
    private static final List<TimedWeakReference<?>> INSTANCES
            = new CopyOnWriteArrayList<>();

    TimedWeakReference( T referent ) {
        super( referent, Utilities.activeReferenceQueue() );
        strong = referent;
        INSTANCES.add( this );
    }

    void touch() {
        expiry = System.currentTimeMillis() + DELAY;
    }

    boolean isExpired() {
        return System.currentTimeMillis() > expiry;
    }

    @Override
    public T get() {
        T st = strong;
        boolean wasExpired = false;
        if ( st != null ) {
            touch();
            return st;
        } else {
            wasExpired = true;
        }
        T result = super.get();
        if ( result != null ) {
            touch();
            strong = result;
            if ( wasExpired ) {
                INSTANCES.add( this );
            }
        }
        return result;
    }

    void reallyBecomeWeak() {
        strong = null;
    }

    static final Timer timer = new Timer( "timed-weak-refs", true );

    static {
        timer.scheduleAtFixedRate( new CleanupTask(), 15000, DELAY );
    }

    @Override
    public void run() {
        INSTANCES.remove(this);
    }

    static final class CleanupTask extends TimerTask {
        @Override
        public void run() {
            List<TimedWeakReference<?>> items = new ArrayList<>( INSTANCES );
            for ( Iterator<TimedWeakReference<?>> it = items.iterator(); it.hasNext(); ) {
                TimedWeakReference<?> t = it.next();
                if ( t.isExpired() ) {
                    t.reallyBecomeWeak();
                } else {
                    it.remove();
                }
            }
            INSTANCES.removeAll( items );
        }
    }
}

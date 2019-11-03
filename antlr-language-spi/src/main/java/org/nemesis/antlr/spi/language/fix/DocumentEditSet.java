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
package org.nemesis.antlr.spi.language.fix;

import java.util.ArrayList;
import java.util.List;
import javax.swing.text.Position;
import com.mastfrog.function.throwing.ThrowingRunnable;
import com.mastfrog.range.IntRange;
import java.util.Collections;
import java.util.Comparator;
import org.netbeans.editor.BaseDocument;

/**
 *
 * @author Tim Boudreau
 */
public final class DocumentEditSet {

    private final List<ComparableRunnable> all = new ArrayList<>( 3 );
    private final BaseDocument doc;
    private final DocumentEditBag consumer;

    DocumentEditSet( BaseDocument doc, DocumentEditBag consumer ) {
        this.doc = doc;
        this.consumer = consumer;
    }

    public final DocumentEditSet delete( int start, int end ) throws Exception {
        // XXX could use a comparable runnable and keep these sorted by position
        Position startPos = doc.createPosition( start );
        Position endPos = doc.createPosition( end );
        all.add( ComparableRunnable.of(start, end, () -> {
            consumer.delete( doc, startPos.getOffset(), endPos.getOffset() );
        } ));
        return this;
    }

    public final DocumentEditSet insert( int start, String text ) throws Exception {
        Position startPos = doc.createPosition( start );
        all.add( ComparableRunnable.of(start, start + text.length(), () -> {
            consumer.insert( doc, startPos.getOffset(), text );
        } ));
        return this;
    }

    public final DocumentEditSet replace( int start, int end, String text ) throws Exception {
        Position startPos = doc.createPosition( start );
        Position endPos = doc.createPosition( end );
        all.add( ComparableRunnable.of(start, end, () -> {
            consumer.replace( doc, startPos.getOffset(), endPos.getOffset(), text );
        } ));
        return this;
    }

    ThrowingRunnable runner() {
        return () -> {
            // sort from last to first so offsets are valid - Position should take
            // care of that, but we're paranoid
            Collections.sort(all, Comparator.<ComparableRunnable>naturalOrder().reversed());
            for ( ThrowingRunnable r : all ) {
                r.run();
            }
        };
    }

    static class ComparableRunnable implements ThrowingRunnable, IntRange<ComparableRunnable> {
        private final ThrowingRunnable op;
        private final int start;
        private final int end;

        public ComparableRunnable( ThrowingRunnable op, int start, int end ) {
            this.op = op;
            this.start = start;
            this.end = end;
        }

        static ComparableRunnable of(int start, int end, ThrowingRunnable op) {
            return new ComparableRunnable(op, start, end);
        }

        @Override
        public void run() throws Exception {
            op.run();
        }

        @Override
        public int start() {
            return start;
        }

        @Override
        public int size() {
            return end-start;
        }

        @Override
        public ComparableRunnable newRange( int start, int size ) {
            return new ComparableRunnable(op, start, start + size);
        }

        @Override
        public ComparableRunnable newRange( long start, long size ) {
            return new ComparableRunnable(op, (int) start, (int)(start + size));
        }
    }
}

/*
 * Copyright 2019 Mastfrog Technologies.
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

import com.mastfrog.range.IntRange;
import java.util.function.Supplier;
import javax.swing.text.BadLocationException;
import org.nemesis.editor.function.DocumentConsumer;
import org.nemesis.editor.position.PositionFactory;
import org.nemesis.editor.position.PositionRange;
import org.netbeans.spi.editor.hints.Severity;

import static com.mastfrog.util.preconditions.Checks.notNull;

/**
 * A builder for one fix or hint.
 *
 * @author Tim Boudreau
 */
public class FixBuilder {
    final Fixes fixes;
    final Severity severity;
    final PositionFactory positions;

    FixBuilder( Fixes fixes, Severity severity, PositionFactory positions ) {
        this.fixes = fixes;
        this.severity = severity;
        this.positions = positions;
    }

    /**
     * Set the error id; this returns a builder subclass which allows you
     * to set a details message (since NetBeans' ErrorDescriptionFactory
     * has no way to do this unless an ID is supplied). An ID is usually
     * a very good id, so the infrastructure can easily tell when a new error
     * is replacing an ond one.
     *
     * @param errorId
     *
     * @return
     */
    public DescribableFixBuilder withErrorId( String errorId ) {
        return new DescribableFixBuilder( fixes, severity, errorId, positions );
    }

    /**
     * Set the error location to a single character position and return a
     * finishable builder which allows you to provide fixes and the
     * error message.
     *
     * @param location A character position
     *
     * @return a builder
     */
    public FinishableFixBuilder withLocation( int location ) throws BadLocationException {
        return withBounds( location, location );
    }

    /**
     * Set the error bounds to a <i>character range</i> within the document, and return a
     * finishable builder which allows you to provide fixes and the
     * error message. Note that all of the extraction collection element types implement
     * IntRange, so it is possible to pass one directly.
     *
     * @param location A character position
     *
     * @return a builder
     */
    public FinishableFixBuilder withBounds( IntRange<? extends IntRange> bounds ) throws BadLocationException {
        return withBounds( notNull( "bounds", bounds ).start(), bounds.end() );
    }

    /**
     * Set the error bounds to a <i>character range</i> within the document, and return a
     * finishable builder which allows you to provide fixes and the
     * error message. Note that all of the extraction collection element types implement
     * IntRange, so it is possible to pass one directly.
     *
     * @param location A character position
     *
     * @return a builder
     */
    public FinishableFixBuilder withBounds( PositionRange bounds ) {
        return new FinishableFixBuilder( null, fixes, severity, null, bounds );
    }

    /**
     * Set the error bounds to a <i>character range</i> within the document, and return a
     * finishable builder which allows you to provide fixes and the
     * error message.
     *
     * @param location A character position
     *
     * @return a builder
     */
    public FinishableFixBuilder withBounds( int start, int end ) throws BadLocationException {
        if ( end < start ) {
            throw new IllegalArgumentException( "end is < start: " + start + "," + end );
        }
        return new FinishableFixBuilder( null, fixes, severity, null, positions.range( start, end ) );
    }

    public static class DescribableFixBuilder extends FixBuilder {
        final String errorId;
        Supplier<? extends CharSequence> details;

        public DescribableFixBuilder( Fixes fixes, Severity severity, String errorId, PositionFactory positions ) {
            super( fixes, severity, positions );
            this.errorId = notNull( "errorId", errorId );
        }

        /**
         * Set a supplier for the details message popped up with the user hovers
         * over the error or invokes hints on an error which does not have any
         * fixes available.
         *
         * @param details The details message, which will be computed on-demand if the
         *                popup is actually invoked.
         *
         * @return this
         */
        public DescribableFixBuilder withDetails( Supplier<? extends CharSequence> details ) {
            if ( this.details != null ) {
                throw new IllegalStateException( "Details already set to " + this.details.get() );
            }
            this.details = details;
            return this;
        }

        /**
         * Set the details message popped up with the user hovers
         * over the error or invokes hints on an error which does not have any
         * fixes available.
         * <p>
         * If the details message is likely to be large or expensive to compute, prefer the
         * method which takes a Supplier.
         * </p>
         *
         * @param details The details message, which will be computed on-demand if the
         *                popup is actually invoked.
         *
         * @return this
         */
        public DescribableFixBuilder withDetails( String details ) {
            if ( this.details != null ) {
                throw new IllegalStateException( "Details already set to " + this.details.get() );
            }
            this.details = () -> details;
            return this;
        }

        public FinishableFixBuilder withBounds( int start, int end ) throws BadLocationException {
            if ( end < start ) {
                throw new IllegalArgumentException( "end is < start: " + start + "," + end );
            }
            return new FinishableFixBuilder( errorId, fixes, severity, details, positions.range( start, end ) );
        }

        public FinishableFixBuilder withBounds( PositionRange bounds ) {
            return new FinishableFixBuilder( errorId, fixes, severity, details, bounds );
        }

        /**
         * Overridden to throw an exception, since it had to be set to create
         * this builder.
         *
         * @param errorId The error id
         *
         * @return an exception is thrown
         */
        @Override
        public DescribableFixBuilder withErrorId( String errorId ) {
            throw new IllegalStateException( "Error id already set to " + errorId );
        }
    }

    public static final class FinishableFixBuilder {
        private final String id;
        private final Fixes fixes;
        private final Severity severity;
        private final Supplier<? extends CharSequence> details;
        private final PositionRange range;
        private DocumentConsumer<FixConsumer> lazyFixes;

        FinishableFixBuilder( String id, Fixes fixes, Severity severity,
                Supplier<? extends CharSequence> details, PositionRange range ) {
            this.id = id;
            this.fixes = fixes;
            this.severity = severity;
            this.details = details;
            this.range = range;
        }

        /**
         * Provide a consumer which can offer fixes for the user to invoke.
         *
         * @param consumer A consumer
         *
         * @return this
         */
        public FinishableFixBuilder creatingFixesWith( DocumentConsumer<FixConsumer> consumer ) {
            this.lazyFixes = notNull( "consumer", consumer );
            return this;
        }

        /**
         * Set the error message, and add the fix to the Fixes.
         *
         * @param msg
         *
         * @return
         *
         * @throws BadLocationException
         */
        public Fixes withMessage( String msg ) {
            fixes.add( id, severity, msg, range, details, lazyFixes );
            return fixes;
        }
    }
}

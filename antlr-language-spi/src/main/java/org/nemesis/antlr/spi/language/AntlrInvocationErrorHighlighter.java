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
package org.nemesis.antlr.spi.language;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.text.Document;
import org.netbeans.modules.parsing.api.Snapshot;
import org.netbeans.modules.parsing.spi.ParserResultTask;
import org.netbeans.modules.parsing.spi.Scheduler;
import org.netbeans.modules.parsing.spi.SchedulerEvent;
import org.netbeans.modules.parsing.spi.SchedulerTask;
import org.netbeans.modules.parsing.spi.TaskFactory;
import org.netbeans.spi.editor.hints.ErrorDescription;
import org.netbeans.spi.editor.hints.HintsController;
import org.openide.filesystems.FileObject;

/**
 *
 * @author Tim Boudreau
 */
final class AntlrInvocationErrorHighlighter extends ParserResultTask<AntlrParseResult> {

    private static final Logger LOG = Logger.getLogger( AntlrInvocationErrorHighlighter.class.getName() );

    private final AtomicBoolean cancelled = new AtomicBoolean();

    private final String id;

    AntlrInvocationErrorHighlighter( String mimeType ) {
        id = "errors-" + mimeType.replace( '/', '-' );
    }

    @Override
    public String toString() {
        return id;
    }

    static TaskFactory factory( String mimeType ) {
        LOG.log( Level.FINE, "Create error task factory for {0} for "
                             + " with {2}", new Object[]{ mimeType } );
        return new Factory( mimeType );
    }

    private static class Factory extends TaskFactory {

        private final String mimeType;

        Factory( String mimeType ) {
            this.mimeType = mimeType;
        }

        @Override
        public Collection<? extends SchedulerTask> create( Snapshot snapshot ) {
            return Collections.singleton( new AntlrInvocationErrorHighlighter( mimeType ) );
        }
    }

    @Override
    public void run( AntlrParseResult t, SchedulerEvent se ) {
        // Tasks turn out to be frequently cancelled due to reentrancy -
        // attributing a file triggers a parse of another, which causes this
        // parse to be cancelled.  So, though it would make sense, in practice,
        // if we pay attention to the cancelled state, half the time error highlights
        // are never shown when the should be

//        if ( cancelled.getAndSet( false ) ) {
//            LOG.log( Level.FINER, "Skip error highlighting for cancellation for {0} with {1}",
//                     new Object[]{ id, t } );
//            return;
//        }
        List<? extends ErrorDescription> errors = t.getErrorDescriptions();
        LOG.log( Level.FINEST, "Syntax errors in {0}: {1}", new Object[]{ t, errors } );
//        t.fullyProcessed = true;
        setForSnapshot( t.getSnapshot(), errors );
    }

    private void setForSnapshot( Snapshot snapshot, List<? extends ErrorDescription> errors ) {
        Document doc = snapshot.getSource().getDocument( false );
        if ( doc != null ) {
            HintsController.setErrors( doc, id, errors );
        } else {
            FileObject fo = snapshot.getSource().getFileObject();
            if ( fo != null ) {
                HintsController.setErrors( fo, id, errors );
            }
        }
    }

    protected void setErrors( Document document, String layerName, List<? extends ErrorDescription> errors ) {
        if ( document != null ) {
            HintsController.setErrors( document, layerName, errors );
        }
    }

    @Override
    public int getPriority() {
        return 1;
    }

    @Override
    public Class<? extends Scheduler> getSchedulerClass() {
        return Scheduler.EDITOR_SENSITIVE_TASK_SCHEDULER;
    }

    @Override
    public void cancel() {
        cancelled.set( true );
        LOG.log( Level.FINEST, "Cancel {0}", this );
    }
}

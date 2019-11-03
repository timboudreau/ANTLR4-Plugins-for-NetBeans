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
package org.nemesis.editor.utils;

import com.mastfrog.util.strings.Strings;
import java.util.Set;
import java.util.logging.Level;
import javax.swing.text.BadLocationException;
import javax.swing.text.StyledDocument;

/**
 * Performs operations on a single document. Single-use. In general, do not call
 * when holding any foreign locks unless you are very, very sure that nothing that might
 * respond to changes made in the document could acquire them out-of-order. When in doubt,
 * use a new requets processor thread or EventQueue.invokeLater().
 *
 * @param <T> The return type of the operator that will be passed in.
 * @param <E> An exception type thrown by the operation
 */
public final class DocumentOperation<T, E extends Exception> {

    private final DocumentOperator.BleFunction<T, E> runner;
    private String names;

    DocumentOperation( StyledDocument doc, Set<DocumentOperator.Props> props ) {
        this( DocumentOperator.runner( doc, props.toArray( new DocumentOperator.Props[ props.size() ] ) ) );
        names = Strings.join( ":", props );
    }

    private DocumentOperation( DocumentOperator.BleFunction<T, E> runner ) {
        this.runner = runner;
    }

    /**
     * Perform the operation, returning any result.
     *
     * @param documentProcessor
     *
     * @return
     *
     * @throws BadLocationException
     * @throws E
     */
    public T operate( DocumentProcessor<T, E> documentProcessor ) throws BadLocationException, E {
        Thread.currentThread().setName( "Doc processor " + names );
        return runner.apply( documentProcessor );
    }

    /**
     * Run the passed runnable with all the various locks and settings
     * configured; exceptions are logged with level SEVERE which will
     * generate a user notification.
     *
     * @param r A runnable
     */
    public void run( Runnable r ) {
        DocumentProcessor<T, E> wrapped = () -> {
            r.run();
            return null;
        };
        try {
            operate( wrapped );
        } catch ( Exception e ) {
            DocumentOperator.LOG.log( Level.SEVERE, r.toString(), e );
        }
    }

}

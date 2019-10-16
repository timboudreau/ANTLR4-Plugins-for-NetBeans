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

import java.util.function.Consumer;
import java.util.logging.Logger;
import javax.swing.text.BadLocationException;
import org.nemesis.antlr.spi.language.ParseResultContents;
import org.nemesis.data.IndexAddressable;
import org.nemesis.extraction.Extraction;
import org.netbeans.spi.editor.hints.LazyFixList;
import org.netbeans.spi.editor.hints.Severity;

/**
 * Convenience class which makes adding error descriptions with hints to a
 * parsed document much more straightforward.
 *
 * @author Tim Boudreau
 */
public abstract class Fixes {

    static final Logger LOG = Logger.getLogger(Fixes.class.getName());

    /**
     * Utility method to return an empty lazy fix list.
     *
     * @return An empty lazy fix list
     */
    public static LazyFixList none() {
        return NoFixes.NO_FIXES;
    }

    /**
     * Add a hint with Severity.HINT and a callback which will be invoked to
     * collect possible fixes.
     *
     * @param item The item the fix relates to
     * @param message The message to display in the editor margin
     * @param lazyFixes A consumer which can create 0 or more "fixes" which
     * alter the document in some way
     * @return this
     * @throws BadLocationException if the coordinates supplied are outside the
     * document
     */
    public final Fixes addHint(IndexAddressable.IndexAddressableItem item, String message, Consumer<FixConsumer> lazyFixes)
            throws
            BadLocationException {
        add(null, Severity.HINT, message, item.start(), item.end(), lazyFixes);
        return this;
    }

    /**
     * Add a hint with Severity.HINT and a callback which will be invoked to
     * collect possible fixes.
     *
     * @param item The item the fix relates to
     * @param start The start offset in the document
     * @param end The end offset in the document
     * @param lazyFixes A consumer which can create 0 or more "fixes" which
     * alter the document in some way
     * @return this
     * @throws BadLocationException if the coordinates supplied are outside the
     * document
     */
    public final Fixes addHint(int start, int end, String message, Consumer<FixConsumer> lazyFixes)
            throws
            BadLocationException {
        assert start <= end : "start > end";
        add(null, Severity.HINT, message, start, end, lazyFixes);
        return this;
    }

    /**
     * Add a hint with Severity.HINT and a callback which will be invoked to
     * collect possible fixes.
     *
     * @param id The error id
     * @param item The item the fix relates to
     * @param message The message to display in the editor margin
     * @param lazyFixes A consumer which can create 0 or more "fixes" which
     * alter the document in some way
     * @return this
     * @throws BadLocationException if the coordinates supplied are outside the
     * document
     */
    public final Fixes addHint(String errorId, IndexAddressable.IndexAddressableItem item, String message,
            Consumer<FixConsumer> lazyFixes) throws
            BadLocationException {
        add(errorId, Severity.HINT, message, item.start(), item.end(), lazyFixes);
        return this;
    }

    /**
     * Add a hint with Severity.HINT and a callback which will be invoked to
     * collect possible fixes.
     *
     * @param id The error id
     * @param start The start offset in the document
     * @param end The end offset in the document
     * @param message The message to display in the editor margin
     * @param lazyFixes A consumer which can create 0 or more "fixes" which
     * alter the document in some way
     * @return this
     * @throws BadLocationException if the coordinates supplied are outside the
     * document
     */
    public final Fixes addHint(String errorId, int start, int end, String message,
            Consumer<FixConsumer> lazyFixes) throws
            BadLocationException {
        assert start <= end : "start > end";
        add(errorId, Severity.HINT, message, start, end, lazyFixes);
        return this;
    }

    /**
     * Add a hint with Severity.HINT and an id.
     *
     * @param item The item the fix relates to
     * @param message The message to display in the editor margin
     * @return this
     * @throws BadLocationException if the coordinates supplied are outside the
     * document
     */
    public final Fixes addHint(String errorId, IndexAddressable.IndexAddressableItem item, String message) throws
            BadLocationException {
        add(errorId, Severity.HINT, message, item.start(), item.end(), null);
        return this;
    }

    /**
     * Add a hint with Severity.HINT and an id.
     *
     * @param start The start offset in the document
     * @param end The end offset in the document
     * @param message The message to display in the editor margin
     * @return this
     * @throws BadLocationException if the coordinates supplied are outside the
     * document
     */
    public final Fixes addHint(String errorId, int start, int end, String message) throws
            BadLocationException {
        assert start <= end : "start > end";
        add(errorId, Severity.HINT, message, start, end, null);
        return this;
    }

    /**
     * Add a hint with Severity.HINT.
     *
     * @param item The item the fix relates to
     * @param message The message to display in the editor margin
     * @return this
     * @throws BadLocationException if the coordinates supplied are outside the
     * document
     */
    public final Fixes addHint(IndexAddressable.IndexAddressableItem item, String message) throws
            BadLocationException {
        add(null, Severity.HINT, message, item.start(), item.end(), null);
        return this;
    }

    /**
     * Add a hint with Severity.HINT.
     *
     * @param start The start offset in the document
     * @param end The end offset in the document
     * @param message The message to display in the editor margin
     * @return this
     * @throws BadLocationException if the coordinates supplied are outside the
     * document
     */
    public final Fixes addHint(int start, int end, String message) throws
            BadLocationException {
        assert start <= end : "start > end";
        add(null, Severity.HINT, message, start, end, null);
        return this;
    }

    /**
     * Ad a hint with Severity.WARNING and a callback which will be invoked to
     * collect possible fixes.
     *
     * @param item The item the fix relates to
     * @param message The message to display in the editor margin
     * @param lazyFixes A consumer which can create 0 or more "fixes" which
     * alter the document in some way
     * @return this
     * @throws BadLocationException if the coordinates supplied are outside the
     * document
     */
    public final Fixes addWarning(IndexAddressable.IndexAddressableItem item, String message, Consumer<FixConsumer> lazyFixes)
            throws
            BadLocationException {
        add(null, Severity.WARNING, message, item.start(), item.end(), lazyFixes);
        return this;
    }

    /**
     * Ad a hint with Severity.WARNING and a callback which will be invoked to
     * collect possible fixes.
     *
     * @param start The start offset in the document
     * @param end The end offset in the document
     * @param message The message to display in the editor margin
     * @param lazyFixes A consumer which can create 0 or more "fixes" which
     * alter the document in some way
     * @return this
     * @throws BadLocationException if the coordinates supplied are outside the
     * document
     */
    public final Fixes addWarning(int start, int end, String message, Consumer<FixConsumer> lazyFixes)
            throws
            BadLocationException {
        assert start <= end : "start > end";
        add(null, Severity.WARNING, message, start, end, lazyFixes);
        return this;
    }

    /**
     * Add a hint with Severity.WARNING and a callback which will be invoked to
     * collect possible fixes.
     *
     * @param id the error id
     * @param item The item the fix relates to
     * @param message The message to display in the editor margin
     * @param lazyFixes A consumer which can create 0 or more "fixes" which
     * alter the document in some way
     * @return this
     * @throws BadLocationException if the coordinates supplied are outside the
     * document
     */
    public final Fixes addWarning(String errorId, IndexAddressable.IndexAddressableItem item, String message,
            Consumer<FixConsumer> lazyFixes) throws
            BadLocationException {
        add(errorId, Severity.WARNING, message, item.start(), item.end(), lazyFixes);
        return this;
    }

    /**
     * Add a hint with Severity.WARNING and a callback which will be invoked to
     * collect possible fixes.
     *
     * @param id the error id
     * @param start The start offset in the document
     * @param end The end offset in the document
     * @param message The message to display in the editor margin
     * @param lazyFixes A consumer which can create 0 or more "fixes" which
     * alter the document in some way
     * @return this
     * @throws BadLocationException if the coordinates supplied are outside the
     * document
     */
    public final Fixes addWarning(String errorId, int start, int end, String message,
            Consumer<FixConsumer> lazyFixes) throws
            BadLocationException {
        assert start <= end : "start > end";
        add(errorId, Severity.WARNING, message, start, end, lazyFixes);
        return this;
    }

    /**
     * Add a hint with Severity.WARNING.
     *
     * @param id An error id
     * @param item The item the fix relates to
     * @param message The message to display in the editor margin
     * @return this
     * @throws BadLocationException if the coordinates supplied are outside the
     * document
     */
    public final Fixes addWarning(String errorId, IndexAddressable.IndexAddressableItem item, String message) throws
            BadLocationException {
        add(errorId, Severity.WARNING, message, item.start(), item.end(), null);
        return this;
    }

    /**
     * Add a hint with Severity.WARNING.
     *
     * @param id An error id
     * @param start The start offset in the document
     * @param end The end offset in the document
     * @param message The message to display in the editor margin
     * @return this
     * @throws BadLocationException if the coordinates supplied are outside the
     * document
     */
    public final Fixes addWarning(String errorId, int start, int end, String message) throws
            BadLocationException {
        assert start <= end : "start > end";
        add(errorId, Severity.WARNING, message, start, end, null);
        return this;
    }

    /**
     * Add a hint with Severity.WARNING.
     *
     * @param item The item the fix relates to
     * @param message The message to display in the editor margin
     * @return this
     * @throws BadLocationException if the coordinates supplied are outside the
     * document
     */
    public final Fixes addWarning(IndexAddressable.IndexAddressableItem item, String message) throws BadLocationException {
        add(null, Severity.WARNING, message, item.start(), item.end(), null);
        return this;
    }

    /**
     * Add a hint with Severity.WARNING.
     *
     * @param start The start offset in the document
     * @param end The end offset in the document
     * @param message The message to display in the editor margin
     * @return this
     * @throws BadLocationException if the coordinates supplied are outside the
     * document
     */
    public final Fixes addWarning(int start, int end, String message) throws BadLocationException {
        assert start <= end : "start > end";
        add(null, Severity.WARNING, message, start, end, null);
        return this;
    }

    /**
     * Add a hint with Severity.ERROR and a callback which will be invoked to
     * collect possible fixes.
     *
     * @param item The item the fix relates to
     * @param message The message to display in the editor margin
     * @param lazyFixes A consumer which can create 0 or more "fixes" which
     * alter the document in some way
     * @return this
     * @throws BadLocationException if the coordinates supplied are outside the
     * document
     */
    public final Fixes addError(IndexAddressable.IndexAddressableItem item, String message, Consumer<FixConsumer> lazyFixes)
            throws BadLocationException {
        add(null, Severity.ERROR, message, item.start(), item.end(), lazyFixes);
        return this;
    }

    /**
     * Add a hint with Severity.ERROR and a callback which will be invoked to
     * collect possible fixes.
     *
     * @param start The start offset in the document
     * @param end The end offset in the document
     * @param message The message to display in the editor margin
     * @param lazyFixes A consumer which can create 0 or more "fixes" which
     * alter the document in some way
     * @return this
     * @throws BadLocationException if the coordinates supplied are outside the
     * document
     */
    public final Fixes addError(int start, int end, String message, Consumer<FixConsumer> lazyFixes)
            throws BadLocationException {
        assert start <= end : "start > end";
        add(null, Severity.ERROR, message, start, end, lazyFixes);
        return this;
    }

    /**
     * Add a hint with Severity.ERROR and a callback which will be invoked to
     * collect possible fixes.
     *
     * @param id the error id
     * @param item The item the fix relates to
     * @param message The message to display in the editor margin
     * @param lazyFixes A consumer which can create 0 or more "fixes" which
     * alter the document in some way
     * @return this
     * @throws BadLocationException if the coordinates supplied are outside the
     * document
     */
    public final Fixes addError(String id, IndexAddressable.IndexAddressableItem item, String message,
            Consumer<FixConsumer> lazyFixes) throws
            BadLocationException {
        add(id, Severity.ERROR, message, item.start(), item.end(), lazyFixes);
        return this;
    }

    /**
     * Add a hint with Severity.ERROR and a callback which will be invoked to
     * collect possible fixes.
     *
     * @param id the error id
     * @param start The start offset in the document
     * @param end The end offset in the document
     * @param message The message to display in the editor margin
     * @param lazyFixes A consumer which can create 0 or more "fixes" which
     * alter the document in some way
     * @return this
     * @throws BadLocationException if the coordinates supplied are outside the
     * document
     */
    public final Fixes addError(String id, int start, int end, String message,
            Consumer<FixConsumer> lazyFixes) throws
            BadLocationException {
        assert start <= end : "start > end";
        add(id, Severity.ERROR, message, start, end, lazyFixes);
        return this;
    }

    /**
     * Add a hint with Severity.ERROR.
     *
     * @param id the error id
     * @param item The item the fix relates to
     * @param message The message to display in the editor margin
     * @return this
     * @throws BadLocationException if the coordinates supplied are outside the
     * document
     */
    public final Fixes addError(String errorId, IndexAddressable.IndexAddressableItem item, String message) throws
            BadLocationException {
        add(errorId, Severity.ERROR, message, item.start(), item.end(), null);
        return this;
    }

    /**
     * Add a hint with Severity.ERROR.
     *
     * @param id the error id
     * @param start The start offset in the document
     * @param end The end offset in the document
     * @param message The message to display in the editor margin
     * @return this
     * @throws BadLocationException if the coordinates supplied are outside the
     * document
     */
    public final Fixes addError(String errorId, int start, int end, String message) throws
            BadLocationException {
        assert start <= end : "start > end";
        add(errorId, Severity.ERROR, message, start, end, null);
        return this;
    }

    /**
     * Add a hint with Severity.ERROR
     *
     * @param item The item the fix relates to
     * @param message The message to display in the editor margin
     * @return this
     * @throws BadLocationException if the coordinates supplied are outside the
     * document
     */
    public final Fixes addError(IndexAddressable.IndexAddressableItem item, String message) throws
            BadLocationException {
        add(null, Severity.ERROR, message, item.start(), item.end(), null);
        return this;
    }

    /**
     * Add a hint with Severity.ERROR
     *
     * @param start the start
     * @param end the end
     * @param message The message to display in the editor margin
     * @return this
     * @throws BadLocationException if the coordinates supplied are outside the
     * document
     */
    public final Fixes addError(int start, int end, String message) throws
            BadLocationException {
        assert start <= end : "start > end";
        add(null, Severity.ERROR, message, start, end, null);
        return this;
    }

    abstract void add(String id, Severity severity, String description, int start, int end,
            Consumer<FixConsumer> lazyFixes) throws BadLocationException;

    public static Fixes create(Extraction extraction, ParseResultContents contents) {
        return new FixesImpl(extraction, contents);
    }

    /**
     * Needed for ParseResultHooks run initially against a stale parser result.
     *
     * @return A fixes
     */
    public static Fixes empty() {
        return Empty.INSTANCE;
    }

    private static final class Empty extends Fixes {

        static final Empty INSTANCE = new Empty();

        @Override
        void add(String id, Severity severity, String description, int start, int end, Consumer<FixConsumer> lazyFixes) throws BadLocationException {
            // do nothing
        }
    }
}

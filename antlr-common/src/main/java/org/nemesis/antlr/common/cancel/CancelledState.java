/*
 * Copyright 2016-2020 Tim Boudreau, Frédéric Yvon Vinet
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
package org.nemesis.antlr.common.cancel;

/**
 *
 * @author Tim Boudreau
 */
public enum CancelledState {
    /**
     * The canceller has not been cancelled, and is running or in a reusable
     * state.
     */
    NOT_CANCELLED,
    /**
     * The Canceller has been cancelled, but the owning thread may not have yet
     * called <code>getAsBoolean()</code> on it and noticed.
     */
    CANCELLED,
    /**
     * Only returned by the method that attempts to cancel from another thread:
     * The Canceller was already in the cancelled state when the current
     * cancellation attempt was run.
     */
    ALREADY_CANCELLED,
    /**
     * The work was completed without cancellation.
     */
    COMPLETED,
    /**
     * Only returned by the method that attempts to cancel from another thread:
     * There was no canceller associated with the passed thread to cancel.
     */
    NOTHING_TO_CANCEL,
    /**
     * The work was cancelled, <i>and <code>getAsBoolean()</code> was called on
     * the canceller after it was cancelled</i>, so the thread doing the work
     * definitely detected the cancelled state (this does not necessarily mean
     * that thread did anything about it, only that it checked and received the
     * answer that it was cancelled). This state may not be immediately present,
     * as it is set lazily so as not to turn every call into a synchronous write
     * to main memory.
     */
    CANCELLED_AND_CANCELLATION_DETECTED;

    // State values for the atomic state field in
    // Canceller
    static final int STATE_NOT_CANCELLED = 0;
    static final int STATE_CANCELLED = 1;
    static final int STATE_ALREADY_CANCELLED = 2;
    static final int STATE_COMPLETED = 3;
    static final int STATE_CANCELLED_AND_DETECTED = 4;

    static CancelledState forState(int state) {
        switch (state) {
            case STATE_NOT_CANCELLED:
                return NOT_CANCELLED;
            case STATE_CANCELLED:
                return CANCELLED;
            case STATE_ALREADY_CANCELLED:
                return ALREADY_CANCELLED;
            case STATE_COMPLETED:
                return COMPLETED;
            case STATE_CANCELLED_AND_DETECTED:
                return CANCELLED_AND_CANCELLATION_DETECTED;
            default:
                // No integer assigned to NOTHING_TO_CANCEL -
                // it will never be the state of an Canceller, since by
                // definition if one exists, then there *is* something to
                // cancel
                throw new AssertionError("Unknown state " + state);
        }
    }

    /**
     * Is this a state where cancellation has occurred?
     *
     * @return True if this is a cancelled state
     */
    public boolean isCancelledState() {
        switch (this) {
            case CANCELLED:
            case ALREADY_CANCELLED:
            case CANCELLED_AND_CANCELLATION_DETECTED:
                return true;
            default:
                return false;
        }
    }

    /**
     * Is this a state where either the work has completed, or not
     * completed and not been cancelled?
     *
     * @return True if the work was not cancelled (yet)
     */
    public boolean isSuccessfulState() {
        switch (this) {
            case NOT_CANCELLED:
            case COMPLETED:
                return true;
            default:
                return false;
        }
    }

    /**
     * Is this a finished state?
     *
     * @return True if the state is one where the work should be done.
     */
    public boolean isEndState() {
        switch (this) {
            case COMPLETED:
                return true;
            case ALREADY_CANCELLED:
                return true;
            default:
                return false;
        }
    }
}

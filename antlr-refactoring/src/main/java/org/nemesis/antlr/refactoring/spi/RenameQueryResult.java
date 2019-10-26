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
package org.nemesis.antlr.refactoring.spi;

import org.nemesis.antlr.refactoring.impl.RenameActionType;
import static com.mastfrog.util.preconditions.Checks.notNull;
import java.util.Objects;
import javax.swing.text.StyledDocument;
import org.nemesis.antlr.refactoring.impl.RenameQueryResultTrampoline;
import org.nemesis.extraction.Extraction;

/**
 * Result of querying a registerer-provided InstantRenamer to determine if a
 * rename is possible.
 *
 * @author Tim Boudreau
 */
public class RenameQueryResult {

    private final RenameActionType type;
    private final RenameAugmenter augmenter;
    private final RenamePostProcessor postProcessor;
    private final String reason;
    private CharFilter charFilter;

    RenameQueryResult(RenameActionType type) {
        assert type.isStandalone() : "This constructor should not be called "
                + "for this action type: " + type;
        this.type = notNull("type", type);
        augmenter = null;
        postProcessor = null;
        reason = null;
    }

    RenameQueryResult(String reason) {
        this.reason = reason;
        this.type = RenameActionType.NOT_ALLOWED;
        this.postProcessor = null;
        this.augmenter = null;
    }

    RenameQueryResult(RenameAugmenter augmenter, boolean takeover) {
        type = takeover ? RenameActionType.TAKEOVER : RenameActionType.INPLACE_AUGMENTED;
        this.augmenter = notNull("augmenter", augmenter);
        postProcessor = null;
        this.reason = null;
    }

    RenameQueryResult(RenamePostProcessor postProcessor) {
        this.type = RenameActionType.POST_PROCESS;
        this.augmenter = null;
        this.postProcessor = notNull("postProcessor", postProcessor);
        this.reason = null;
    }

    /**
     * Adds a filter to this inplace renamer to cause some typed character
     * to have no effect (if the filter's test method returns false for
     * them).
     *
     * @param filter A filter
     * @return A query result
     */
    public RenameQueryResult withCharFilter(CharFilter filter) {
        if (this.charFilter != null) {
            throw new IllegalStateException("Filter already set to " + filter);
        }
        this.charFilter = notNull("filter", filter);
        return this;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(
                RenameQueryResult.class.getSimpleName()).append('(')
                .append(type.name().toLowerCase());
        switch (type) {
            case USE_REFACTORING_API:
            case INPLACE:
                break;
            case INPLACE_AUGMENTED:
            case TAKEOVER:
                sb.append(" augmenter=").append(augmenter);
                break;
            case NOT_ALLOWED:
                sb.append(" reason=").append(reason);
                break;
            case POST_PROCESS:
                sb.append(" postProcess=").append(postProcessor);
                break;
            default:
                throw new AssertionError(type);
        }
        return sb.append(')').toString();
    }

    void onRenameCompleted(String original, Extraction extraction, String nue, Runnable undo) {
        if (augmenter != null) {
            augmenter.completed();
        }
        if (postProcessor != null) {
            postProcessor.onRenameCompleted(original, extraction, nue, undo);
        }
    }

    void nameUpdated(String orig, String newName, StyledDocument doc, int startOffset, int endOffset) {
        if (augmenter != null) {
            augmenter.nameUpdated(orig, newName, doc, startOffset, endOffset);
        }
    }

    void cancelled() {
        if (augmenter != null) {
            augmenter.cancelled();
        }
        if (postProcessor != null) {
            postProcessor.cancelled();
        }
    }

    RenameActionType type() {
        return type;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 31 * hash + Objects.hashCode(this.type);
        hash = 31 * hash + Objects.hashCode(this.augmenter);
        hash = 31 * hash + Objects.hashCode(this.postProcessor);
        hash = 31 * hash + Objects.hashCode(this.reason);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final RenameQueryResult other = (RenameQueryResult) obj;
        if (!Objects.equals(this.reason, other.reason)) {
            return false;
        }
        if (this.type != other.type) {
            return false;
        }
        if (!Objects.equals(this.augmenter, other.augmenter)) {
            return false;
        }
        return Objects.equals(this.postProcessor, other.postProcessor);
    }

    static class TrampolineImpl extends RenameQueryResultTrampoline {

        private static RenameQueryResult VETO = new RenameQueryResult(RenameActionType.NOT_ALLOWED);
        private static RenameQueryResult PROCEED = new RenameQueryResult(RenameActionType.INPLACE);
        private static RenameQueryResult USE_REFACTORING = new RenameQueryResult(RenameActionType.USE_REFACTORING_API);

        @Override
        protected void _onRename(RenameQueryResult res, String original, Extraction extraction, String nue, Runnable undo) {
            res.onRenameCompleted(original, extraction, nue, undo);
        }

        @Override
        protected void _nameUpdated(RenameQueryResult res, String orig, String newName, StyledDocument doc, int startOffset, int endOffset) {
            res.nameUpdated(orig, newName, doc, startOffset, endOffset);
        }

        @Override
        protected void _cancelled(RenameQueryResult res) {
            res.cancelled();
        }

        @Override
        protected RenameActionType _typeOf(RenameQueryResult res) {
            return res.type();
        }

        protected RenameQueryResult _veto(String reason) {
            return reason == null ? VETO : new RenameQueryResult(reason);
        }

        protected RenameQueryResult _proceed() {
            return PROCEED;
        }

        protected RenameQueryResult _useRefactoring() {
            return USE_REFACTORING;
        }

        protected RenameQueryResult _takeover(RenameAugmenter aug) {
            return new RenameQueryResult(aug, true);
        }

        protected RenameQueryResult _augment(RenameAugmenter aug) {
            return new RenameQueryResult(aug, false);
        }

        protected RenameQueryResult _postProcess(RenamePostProcessor postProcessor) {
            return new RenameQueryResult(postProcessor);
        }
    }

    static {
        RenameQueryResultTrampoline.DEFAULT = new TrampolineImpl();
    }
}

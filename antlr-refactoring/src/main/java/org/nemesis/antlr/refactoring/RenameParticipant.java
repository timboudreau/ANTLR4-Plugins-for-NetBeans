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
package org.nemesis.antlr.refactoring;

import static com.mastfrog.util.preconditions.Checks.notNull;
import org.nemesis.antlr.refactoring.impl.RenameQueryResultTrampoline;
import org.nemesis.antlr.refactoring.spi.RenamePostProcessor;
import org.nemesis.antlr.refactoring.spi.RenameQueryResult;
import org.nemesis.antlr.refactoring.spi.RenameAugmenter;
import org.nemesis.extraction.ExtractionParserResult;
import org.netbeans.api.annotations.common.NonNull;
import org.openide.util.NbBundle.Messages;

/**
 * Allows implementers to participate in an inplace rename operation, delegate
 * that operation to the refactoring API, post process the results, or veto it
 * entirely.
 *
 * @author Tim Boudreau
 */
public abstract class RenameParticipant {

    public static final RenameParticipant DEFAULT = new DefaultParticipant();

    /**
     * Determine if and how an instant rename operation should proceed. The
     * implementation has a chance to
     * <ul>
     * <li><b>Veto inplace renaming entirely</b> &mdash; by returning
     * <code>veto()</code></li>
     * <li><b>Allow inplace renaming to proceed</b> &mdash; by returning
     * <code>proceed()</code></li>
     * <li><b>Transfer invocation to the refactoring API</b> &mdash; by
     * returning <code>useRefactoringAPI()</code> (you will need to have
     * refactoring implementations registered via the NetBeans refactoring API
     * for this to do anything)</li>
     * <li><b>Take over the process of modifying the document</b> &mdash; by
     * returning <code>takeOverModificationsWith(yourRenameAugmenter)</code>
     * &mdash; the inplace rename action will still process keystrokes, but any
     * modifications to the document are up to you.</li>
     * <li><b>Allow inplace renaming to proceed, but also get a chance to
     * further process any modifications</b> &mdash; by returning
     * <code>proceedAndAugmentWith(myAugmenter)</code> - say, to update another
     * file that uses these names in a situation where that is sufficient</li>
     * <li><b>Allow inplace renaming to proceed, and get a callback to perform
     * further modifications once it is completed, including the ability to roll
     * back the operation entirely</b> &mdash; by returning
     * <code>proceedAndPostProcessWith(myPostProcessor)</code></li>
     * </ul>
     *
     * @param info The parser result at the time of initiation
     * @param caretOffset The caret offset in the document at the time of
     * initiation
     * @param identifier The identifier the caret is in at the time of
     * initiation
     * @return A result
     */
    protected abstract RenameQueryResult isRenameAllowed(@NonNull ExtractionParserResult info, int caretOffset, String identifier);

    /**
     * Create a result which will, on completion or cancellation, call the
     * passed post-processor.
     *
     * @param postProcessor A post processor
     * @return a result
     */
    protected final RenameQueryResult proceedAndPostProcessWith(RenamePostProcessor postProcessor) {
        return RenameQueryResultTrampoline.createPostProcessResult(postProcessor);
    }

    /**
     * Create a result which allows inplace renaming to proceed, but which will
     * pass changes to the passed RenameAugmenter.
     *
     * @param augmenter An augmenter
     * @return a result
     */
    protected final RenameQueryResult proceedAndAugmentWith(RenameAugmenter augmenter) {
        return RenameQueryResultTrampoline.createAugmentResult(augmenter);
    }

    /**
     * Create a result which allows inplace <i>typing</i> to proceed, and will
     * pass changes to the passed RenameAugmenter, but makes no actual
     * modifications to the document.
     *
     * @param augmenter An augmenter
     * @return a result
     */
    protected final RenameQueryResult takeOverModificationsWith(RenameAugmenter augmenter) {
        return RenameQueryResultTrampoline.createTakeoverResult(augmenter);
    }

    /**
     * Create a result which vetos renaming with a generic reason message.
     *
     * @return A result
     */
    @Messages("vetoed=Cannot perform rename")
    protected final RenameQueryResult veto() {
        return veto(Bundle.vetoed());
    }

    /**
     * Create a result which vetos renaming, specifying a reason.
     *
     * @param reason The reason
     * @return A result
     */
    protected final RenameQueryResult veto(String reason) {
        return RenameQueryResultTrampoline.createVetoResult(notNull("reason", reason));
    }

    /**
     * Create a result which allows inplace renaming to proceed with the
     * infrastructure taking care of all changes.
     *
     * @return A result
     */
    protected final RenameQueryResult proceed() {
        return RenameQueryResultTrampoline.createProceedResult();
    }

    /**
     * Create a result which does not allow renaming to proceed, but notifies
     * the infrastructure to invoke the refactoring API (you need to have
     * registered refactorings for your document).
     *
     * @return A rename result
     */
    protected final RenameQueryResult useRefactoringAPI() {
        return RenameQueryResultTrampoline.createUseRefactoringResult();
    }

    private static final class DefaultParticipant extends RenameParticipant {

        @Override
        protected RenameQueryResult isRenameAllowed(ExtractionParserResult info, int caretOffset, String identifier) {
            return proceed();
        }
    }
}

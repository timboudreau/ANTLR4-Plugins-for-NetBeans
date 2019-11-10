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
package org.nemesis.antlr.instantrename;

import static com.mastfrog.util.preconditions.Checks.notNull;
import org.nemesis.antlr.instantrename.impl.RenameQueryResultTrampoline;
import org.nemesis.antlr.instantrename.spi.RenamePostProcessor;
import org.nemesis.antlr.instantrename.spi.RenameQueryResult;
import org.nemesis.antlr.instantrename.spi.RenameAugmenter;
import org.nemesis.charfilter.CharFilter;
import org.nemesis.data.IndexAddressable;
import org.nemesis.data.named.NamedRegionReferenceSets;
import org.nemesis.data.named.NamedSemanticRegion;
import org.nemesis.data.named.NamedSemanticRegions;
import org.nemesis.extraction.Extraction;
import org.nemesis.extraction.SingletonEncounters;
import org.nemesis.extraction.SingletonEncounters.SingletonEncounter;
import org.nemesis.extraction.key.ExtractionKey;
import org.nemesis.extraction.key.NameReferenceSetKey;
import org.nemesis.extraction.key.NamedRegionKey;
import org.nemesis.extraction.key.SingletonKey;
import org.openide.util.NbBundle.Messages;

/**
 * Allows implementers to participate in an inplace rename operation, delegate
 * that operation to the refactoring API, post process the results, or veto it
 * entirely.
 *
 * @author Tim Boudreau
 */
public abstract class RenameParticipant<T, K extends ExtractionKey<T>, I extends IndexAddressable.IndexAddressableItem, C extends IndexAddressable<? extends I>> {

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
    protected abstract RenameQueryResult isRenameAllowed(Extraction ext,
            K key, I item, C collection, int caretOffset, String identifier);

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

    public static <T, K extends ExtractionKey<T>, I extends IndexAddressable.IndexAddressableItem, C extends IndexAddressable<I>>
            RenameParticipant<T, K, I, C> filterOnly(CharFilter filter) {
        return new DefaultParticipant<>(filter);
    }

    public static <T, K extends ExtractionKey<T>, I extends IndexAddressable.IndexAddressableItem, C extends IndexAddressable<I>>
            RenameParticipant<T, K, I, C> defaultInstance() {
        return new DefaultParticipant<>();
    }

    @SuppressWarnings("unchecked")
    RenameParticipant<T, K, I, C> wrap(CharFilter filter) {
        return (RenameParticipant<T, K, I, C>) new WrapWithFilter<>(this, filter);
    }

    private static final class DefaultParticipant<T, K extends ExtractionKey<T>, I extends IndexAddressable.IndexAddressableItem, C extends IndexAddressable<I>>
            extends RenameParticipant<T, K, I, C> {

        private final CharFilter filter;

        public DefaultParticipant() {
            this(null);
        }

        public DefaultParticipant(CharFilter filter) {
            this.filter = filter;
        }

        @Override
        protected RenameQueryResult isRenameAllowed(Extraction ext, K key, I item, C collection, int caretOffset, String identifier) {
            return filter == null ? proceed() : proceed().withCharFilter(filter);
        }
    }

    private static class WrapWithFilter<T, K extends ExtractionKey<T>, I extends IndexAddressable.IndexAddressableItem, C extends IndexAddressable<I>> extends RenameParticipant<T, K, I, C> {

        private final RenameParticipant orig;

        private final CharFilter filter;

        public WrapWithFilter(RenameParticipant orig, CharFilter filter) {
            this.orig = orig;
            this.filter = filter;
        }

        @Override
        protected RenameQueryResult isRenameAllowed(Extraction ext, ExtractionKey key,
                IndexAddressable.IndexAddressableItem item, IndexAddressable collection,
                int caretOffset, String identifier) {

            RenameQueryResult res = orig.isRenameAllowed(ext, key, item,
                    collection, caretOffset, identifier);

            switch (RenameQueryResultTrampoline.typeOf(res)) {
                case INPLACE:
                case INPLACE_AUGMENTED:
                case POST_PROCESS:
                    return res.withCharFilter(filter);
                default:
                    return res;
            }
        }
    }

    /**
     * Convenience subclass for <code>NameReferenceSetKey</code>s so
     * implementers don't have to deal with the gargantuan generic signature.
     *
     * @param <T> A type
     */
    public static abstract class NamedReferencesRenameParticipant<T extends Enum<T>> extends RenameParticipant<T, NameReferenceSetKey<T>, NamedSemanticRegion<T>, NamedRegionReferenceSets<T>> {

    }

    /**
     * Convenience subclass for <code>NamedRegionKey</code>s so implementers
     * don't have to deal with the gargantuan generic signature.
     *
     * @param <T> A type
     */
    public static abstract class NamedRegionsRenameParticipant<T extends Enum<T>> extends RenameParticipant<T, NamedRegionKey<T>, NamedSemanticRegion<T>, NamedSemanticRegions<T>> {

    }

    /**
     * Convenience subclass for <code>SingletonKey</code>s so implementers don't
     * have to deal with the gargantuan generic signature.
     *
     * @param <T> A type
     */
    public static abstract class SingletonsRenameParticipant<T> extends RenameParticipant<T, SingletonKey<T>, SingletonEncounter<T>, SingletonEncounters<T>> {

    }

    /**
     * Returns a dummy instance that simply always calls the refactoring API, so
     * rename can be invoked the normal way, but always results in the
     * refactoring API appearing.
     *
     * @param <T>
     * @param <K>
     * @param <I>
     * @param <C>
     * @return A rename participant
     */
    public static <T, K extends ExtractionKey<T>, I extends IndexAddressable.IndexAddressableItem, C extends IndexAddressable<? extends I>>
            RenameParticipant<T, K, I, C> useRefactoringParticipant() {
        return UseRefactoringAPI.INSTANCE;
    }

    private static final class UseRefactoringAPI extends RenameParticipant {

        static final UseRefactoringAPI INSTANCE = new UseRefactoringAPI();

        @Override
        protected RenameQueryResult isRenameAllowed(Extraction ext, ExtractionKey key, IndexAddressable.IndexAddressableItem item, IndexAddressable collection, int caretOffset, String identifier) {
            return useRefactoringAPI();
        }
    }
}

package org.nemesis.antlr.subscription;

import static com.mastfrog.util.preconditions.Checks.notNull;
import java.util.Collection;
import java.util.function.BiConsumer;

/**
 *
 * @author Tim Boudreau
 */
class SubscribableImpl<K, IK, C, E> implements Subscribable<K, C>, SubscribableNotifier<K, E> {

    private final KeyFactory<? super K, ? extends IK> keys;

    private final EventApplier<? super IK, ? super E, ? super C> applier;
    private final SubscribersStore<IK, C> store;
    private final SubscribersStoreController<IK, C> storeModifier;
    private final BiConsumer<? super IK, ? super C> onSubscribe;

    @SuppressWarnings("LeakingThisInConstructor")
    public SubscribableImpl(KeyFactory<? super K, ? extends IK> keys,
            EventApplier<? super IK, ? super E, ? super C> applier,
            SubscribersStore<IK, C> store,
            SubscribersStoreController<IK, C> storeModifier,
            BiConsumer<? super IK, ? super C> onSubscribe,
            DelegatingNotifier<K,E> delegatee) {
        this.keys = notNull("keys", keys);
        this.applier = notNull("applier", applier);
        this.store = notNull("store", store);
        this.storeModifier = notNull("storeModifier", storeModifier);
        this.onSubscribe = onSubscribe;
        delegatee.setDelegate(this);
    }

    @Override
    public void subscribe(K key, C consumer) {
        IK internalKey = keys.constructKey(key);
        storeModifier.add(internalKey, consumer);
        if (onSubscribe != null) {
            onSubscribe.accept(internalKey, consumer);
        }
    }

    @Override
    public void unsubscribe(K type, C consumer) {
        storeModifier.remove(keys.constructKey(type), consumer);
    }

    void internalOnEvent(IK internalKey, E event) {
        Collection<? extends C> consumers = store.subscribersTo(internalKey);
        if (!consumers.isEmpty()) {
            applier.apply(internalKey, event, consumers);
        }
    }

    @Override
    public void onEvent(K key, E event) {
        IK internalKey = keys.constructKey(key);
        internalOnEvent(internalKey, event);
    }

    @Override
    public void destroyed(K type) {
        storeModifier.removeAll(keys.constructKey(type));
    }
}

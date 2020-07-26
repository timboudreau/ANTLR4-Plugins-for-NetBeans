/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nemesis.antlr.subscription;

import com.mastfrog.util.preconditions.Checks;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 *
 * @author Tim Boudreau
 */
public class SubscribableBuilder {

    public static <K> KeyedSubscribableBuilder<K, K> withKeys(Class<K> keyType) {
        return withKeys(keyType, KeyFactory.identity());
    }

    public static <K, IK> KeyedSubscribableBuilder<K, IK> withKeys(Class<K> keyType, KeyFactory<? super K, ? extends IK> keys) {
        return new KeyedSubscribableBuilder<>(keyType, keys);
    }

    public static class KeyedSubscribableBuilder<K, IK> {

        private final KeyFactory<? super K, ? extends IK> keys;
        private final Class<K> keyType;

        KeyedSubscribableBuilder(Class<K> keyType, KeyFactory<? super K, ? extends IK> keys) {
            this.keys = keys;
            this.keyType = keyType;
        }

        @SuppressWarnings("unchecked")
        public <K2> KeyedSubscribableBuilder<Object, IK> andKeys(Class<K2> additionalKeyType, KeyFactory<? super K2, ? extends IK> moreKeys) {
            if (keys instanceof MultiTypeKeyFactory<?>) {
                MultiTypeKeyFactory<IK> mk = (MultiTypeKeyFactory<IK>) keys;
                mk.add(additionalKeyType, moreKeys);
                return (KeyedSubscribableBuilder<Object, IK>) this;
            } else {
                MultiTypeKeyFactory<IK> result = new MultiTypeKeyFactory<>();
                result.add(keyType, keys);
                result.add(additionalKeyType, moreKeys);
                return new KeyedSubscribableBuilder<>(Object.class, result);
            }
        }

        public <E> EventSubscribableBuilder<K, IK, E, Consumer<? super E>> consumers() {
            return withEventApplier(EventApplier.consumers());
        }

        public <E> EventSubscribableBuilder<K, IK, E, BiConsumer<? super IK, ? super E>> biconsumers() {
            EventApplier<IK, E, BiConsumer<? super IK, ? super E>> app = EventApplier.biconsumers();
            return withEventApplier(app);
        }

        public <E, C> EventSubscribableBuilder<K, IK, E, C> withEventApplier(EventApplier<? super IK, ? super E, ? super C> applier) {
            return new EventSubscribableBuilder<>(keyType, keys, applier);
        }

    }

    public static class EventSubscribableBuilder<K, IK, E, C> {

        private final Class<K> keyType;
        private final KeyFactory<? super K, ? extends IK> keys;
        private final EventApplier<? super IK, ? super E, ? super C> applier;

        EventSubscribableBuilder(
                Class<K> keyType,
                KeyFactory<? super K, ? extends IK> keys,
                EventApplier<? super IK, ? super E, ? super C> applier) {
            this.keyType = keyType;
            this.keys = keys;
            this.applier = applier;
        }

        public StoreSubscribableBuilder<K, IK, C, E> storingSubscribers(int targetSize, CacheType type, Supplier<Set<C>> setFactory) {
            SubscribersStoreImpl<IK, C> store = new SubscribersStoreImpl<>(targetSize, type, setFactory);
            return new StoreSubscribableBuilder<>(keyType, keys, applier, store);
        }

        public SubscriberStorageBuilder<K, IK, E, C> storingSubscribersIn(SetSupplier supp) {
            return new SubscriberStorageBuilder<>(this).withSets(supp);
        }

        public SubscriberStorageBuilder<K, IK, E, C> storingSubscribersIn(SetTypes setTypes) {
            return new SubscriberStorageBuilder<>(this).withSets(setTypes);
        }

        public SubscriberStorageBuilder<K, IK, E, C> withInitialMapSize(int mapSize) {
            return new SubscriberStorageBuilder<>(this).withInitialMapSize(mapSize);
        }

        public SubscriberStorageBuilder<K, IK, E, C> withInitialSubscriberSetSize(int setSize) {
            return new SubscriberStorageBuilder<>(this).withInitialSubscriberSetSize(setSize);
        }

        public SubscriberStorageBuilder<K, IK, E, C> withCacheTypes(CacheType types) {
            return new SubscriberStorageBuilder<>(this).withCacheType(types);
        }
    }

    public static final class SubscriberStorageBuilder<K, IK, E, C> {

        private SetSupplier sets;
        private int initialSubscriberSetSize = 16;
        private boolean threadSafe = true;
        private final EventSubscribableBuilder<K, IK, E, C> outer;
        private CacheType mappingType = CacheType.WEAK;
        private int initialKeyMapSize = 16;

        public SubscriberStorageBuilder(EventSubscribableBuilder<K, IK, E, C> outer) {
            this.outer = outer;
        }

        public SubscriberStorageBuilder<K, IK, E, C> withSets(SetTypes setTypes) {
            sets = setTypes;
            return this;
        }

        public SubscriberStorageBuilder<K, IK, E, C> withSets(SetSupplier setTypes) {
            this.sets = setTypes;
            return this;
        }

        public SubscriberStorageBuilder<K, IK, E, C> withInitialMapSize(int initialSize) {
            this.initialKeyMapSize = Checks.greaterThanZero("initialSize", initialSize);
            return this;
        }

        public SubscriberStorageBuilder<K, IK, E, C> withInitialSubscriberSetSize(int initialSize) {
            this.initialSubscriberSetSize = Checks.greaterThanZero("initialSize", initialSize);
            return this;
        }

        public SubscriberStorageBuilder<K, IK, E, C> withCacheType(CacheType type) {
            mappingType = type;
            return this;
        }

        public StoreSubscribableBuilder<K, IK, C, E> threadSafe() {
            return outer.storingSubscribers(initialKeyMapSize, mappingType,
                    sets.setSupplier(initialSubscriberSetSize, threadSafe));
        }
    }

    public static class StoreSubscribableBuilder<K, IK, C, E> {

        private final Class<K> keyType;
        private final KeyFactory<? super K, ? extends IK> keys;
        private final SubscribersStoreImpl<IK, C> store;
        private final DelegatingNotifier<K, E> del = new DelegatingNotifier<>();
        private final EventApplier<? super IK, ? super E, ? super C> applier;

        StoreSubscribableBuilder(
                Class<K> keyType,
                KeyFactory<? super K, ? extends IK> keys,
                EventApplier<? super IK, ? super E, ? super C> applier,
                SubscribersStoreImpl<IK, C> store) {
            this.keys = keys;
            this.keyType = keyType;
            this.applier = applier;
            this.store = store;
        }

        public FinishableSubscribableBuilder<K, IK, E, C> withAsynchronousEventDelivery(Executor executor) {
            return new FinishableSubscribableBuilder<>(keyType, keys, applier, store, del, del.async(executor));
        }

        public FinishableSubscribableBuilder<K, IK, E, C> withAsynchronousEventDelivery() {
            return new FinishableSubscribableBuilder<>(keyType, keys, applier, store, del, del.async(ForkJoinPool.commonPool()));
        }

        public FinishableSubscribableBuilder<K, IK, E, C> withEventDelivery(Function<SubscribableNotifier<K, E>, SubscribableNotifier<? super K, ? super E>> xform) {
            return new FinishableSubscribableBuilder<>(keyType, keys, applier, store, del, xform.apply(del));
        }

        public FinishableSubscribableBuilder<K, IK, E, C> withSynchronousEventDelivery() {
            return new FinishableSubscribableBuilder<>(keyType, keys, applier, store, del, del);
        }

        public FinishableSubscribableBuilder<K, IK, E, C> withCoalescedAsynchronousEventDelivery(ScheduledExecutorService threadPool, int delay, TimeUnit delayUnits) {
            return withCoalescedAsynchronousEventDelivery(threadPool, CacheType.WEAK, delay, delayUnits);
        }

        public FinishableSubscribableBuilder<K, IK, E, C> withCoalescedAsynchronousEventDelivery(int delay, TimeUnit delayUnits) {
            return withCoalescedAsynchronousEventDelivery(Executors.newScheduledThreadPool(3), CacheType.WEAK, delay, delayUnits);
        }

        public FinishableSubscribableBuilder<K, IK, E, C> withCoalescedAsynchronousEventDelivery(ScheduledExecutorService threadPool, CacheType cacheType, int delay, TimeUnit delayUnits) {
            return new FinishableSubscribableBuilder<>(keyType, keys, applier, store, del, del.coalescing(threadPool, cacheType, Checks.greaterThanOne("delay", delay), delayUnits));
        }
    }

    public static class FinishableSubscribableBuilder<K, IK, E, C> {

        private final Class<K> keyType;
        private final KeyFactory<? super K, ? extends IK> keys;
        private final EventApplier<? super IK, ? super E, ? super C> applier;
        private final SubscribersStoreImpl<IK, C> store;
        private final DelegatingNotifier<K, E> del;
        private final SubscribableNotifier<? super K, ? super E> externalNotifier;
        private BiConsumer<? super IK, ? super C> onSubscribe;

        FinishableSubscribableBuilder(Class<K> keyType, KeyFactory<? super K, ? extends IK> keys, EventApplier<? super IK, ? super E, ? super C> applier, SubscribersStoreImpl<IK, C> store, DelegatingNotifier<K, E> del, SubscribableNotifier<? super K, ? super E> externalNotifier) {
            this.keyType = keyType;
            this.keys = keys;
            this.applier = applier;
            this.store = store;
            this.del = del;
            this.externalNotifier = externalNotifier;
        }

        public FinishableSubscribableBuilder<K, IK, E, C> onSubscribe(BiConsumer<? super IK, ? super C> onSubscribe) {
            if (this.onSubscribe == null) {
                this.onSubscribe = onSubscribe;
            } else {
                BiConsumer<? super IK, ? super C> old = this.onSubscribe;
                this.onSubscribe = (ik, c) -> {
                    old.accept(ik, c);
                    onSubscribe.accept(ik, c);
                };
            }
            return this;
        }

        public SubscribableContents<K, C, E> build() {
            SubscribableImpl<K, IK, C, E> result = new SubscribableImpl<>(keys, applier, store, store.mutator(), onSubscribe, del);
            SubscribersStoreController<K, C> mut = store.mutator().converted(keys);
            SubscribableContents<K, C, E> contents
                    = new SubscribableContents<>(result, externalNotifier, mut);
            return contents;
        }
    }

    public static final class SubscribableContents<K, C, E> {

        public final Subscribable<K, C> subscribable;
        public final SubscribableNotifier<? super K, ? super E> eventInput;
//        public final SubscribersStore.SubscribersStoreController<K, C> subscribersManager;

        SubscribableContents(Subscribable<K, C> subscribable, SubscribableNotifier<? super K, ? super E> eventInput, SubscribersStoreController<K, C> subscribersManager) {
            this.subscribable = subscribable;
            this.eventInput = eventInput;
//            this.subscribersManager =s subscribersManager;
        }

    }

}

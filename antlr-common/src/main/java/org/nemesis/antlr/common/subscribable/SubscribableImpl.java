/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nemesis.antlr.common.subscribable;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.WeakHashMap;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 *
 * @author Tim Boudreau
 */
public class SubscribableImpl<T, R> {

    private final Subscribers<T, R> subscribers;

    public SubscribableImpl(Subscribers<T, R> subscribers, Consumer<BiConsumer<T, R>> onEventConsumer) {
        this.subscribers = subscribers;
        onEventConsumer.accept(this::onEvent);
    }

    protected void onEvent(T key, R event) {
        for (BiConsumer<T, R> c : subscribers.subscribers(key)) {
            try {
                c.accept(key, event);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    public void subscribe(T key, BiConsumer<T, R> consumer) {

    }

    protected abstract class Subscribers<T, R> {

        protected abstract Iterable<BiConsumer<T, R>> subscribers(T key);

        protected abstract void add(T key, BiConsumer<T, R> consumer);

        protected abstract MapLike<T, R> map();
    }


    interface MapLike<T, R> {

        void add(T key, R val);

        boolean contains(T key);

        Iterable<R> getItems(T key);

        int size();

        void removeAllItems(T key);

        void removeItem(T key, R item);

        default MapLike<T, R> toSynchronizedMapLike() {
            if (this instanceof SynchronizedMapLike<?, ?>) {
                return this;
            }
            return new SynchronizedMapLike<>(this);
        }
    }

    static class MapLikeConverting<T, R, C> implements MapLike<T, R> {

        private final MapLike<T, C> orig;
        private final Function<R, C> toStoredForm;
        private final Function<C, R> toUsableForm;
        private final BiPredicate<C, R> finder;

        public MapLikeConverting(MapLike<T, C> orig, Function<R, C> toStoredForm, Function<C, R> toUsableForm, BiPredicate<C, R> finder) {
            this.orig = orig;
            this.toStoredForm = toStoredForm;
            this.toUsableForm = toUsableForm;
            this.finder = finder;
        }

        @Override
        public boolean contains(T key) {
            return orig.contains(key);
        }

        @Override
        public Iterable<R> getItems(T key) {
            return new IterableWrapper<C, R>(toUsableForm, orig.getItems(key));
        }

        @Override
        public int size() {
            return orig.size();
        }

        @Override
        public void add(T key, R val) {
            orig.add(key, toStoredForm.apply(val));
        }

        @Override
        public void removeAllItems(T key) {
            orig.removeAllItems(key);
        }

        @Override
        public void removeItem(T key, R item) {
            Iterable<C> it = orig.getItems(key);
            for (C c : it) {
                if (finder.test(c, item)) {
                    orig.removeItem(key, c);
                    break;
                }
            }
        }
    }

    static class IterableWrapper<T, R> implements Iterable<R> {

        private final Function<T, R> converter;
        private final Iterable<T> iterable;

        public IterableWrapper(Function<T, R> converter, Iterable<T> iterable) {
            this.converter = converter;
            this.iterable = iterable;
        }

        @Override
        public Iterator<R> iterator() {
            return new NullSkippingConvertingIterator<>(iterable.iterator(), converter);
        }
    }

    static class NullSkippingConvertingIterator<T, R> implements Iterator<R> {

        private final Iterator<T> orig;
        private final Function<T, R> converter;
        private R next;

        public NullSkippingConvertingIterator(Iterator<T> orig, Function<T, R> converter) {
            this.orig = orig;
            this.converter = converter;
            next = findNext();
        }

        private R findNext() {
            while (orig.hasNext()) {
                T obj = orig.next();
                if (obj != null) {
                    R result = converter.apply(orig.next());
                    if (result != null) {
                        return result;
                    }
                }
            }
            return null;
        }

        @Override
        public boolean hasNext() {
            return next != null;
        }

        @Override
        public R next() {
            R result = next;
            if (result == null) {
                throw new NoSuchElementException();
            }
            next = findNext();
            return result;
        }
    }

    static class StrongMapLike<T, R> extends HashMap<T, Collection<R>> implements MapLike<T, R> {

        private final Supplier<Collection<R>> factory;

        public StrongMapLike(Supplier<Collection<R>> factory) {
            this.factory = factory;
        }

        @Override
        public void add(T key, R val) {
            Collection<R> items = super.get(key);
            if (items == null) {
                items = factory.get();
                super.put(key, items);
            }
            items.add(val);
        }

        @Override
        public boolean contains(T key) {
            return super.containsKey(key);
        }

        @Override
        public Iterable<R> getItems(T key) {
            Collection<R> result = super.get(key);
            return result == null ? Collections.emptyList() : result;
        }

        @Override
        public void removeAllItems(T key) {
            super.remove(key);
        }

        @Override
        public void removeItem(T key, R item) {
            Collection<R> coll = super.get(key);
            if (coll != null) {
                coll.remove(item);
            }
        }
    }

    static class WeakMapLike<T, R> extends WeakHashMap<T, Collection<R>> implements MapLike<T, R> {

        private final Supplier<Collection<R>> factory;

        public WeakMapLike(Supplier<Collection<R>> factory) {
            this.factory = factory;
        }

        @Override
        public void add(T key, R val) {
            Collection<R> items = super.get(key);
            if (items == null) {
                items = factory.get();
                super.put(key, items);
            }
            items.add(val);
        }

        @Override
        public boolean contains(T key) {
            return super.containsKey(key);
        }

        @Override
        public Iterable<R> getItems(T key) {
            Collection<R> result = super.get(key);
            return result == null ? Collections.emptyList() : result;
        }

        @Override
        public void removeAllItems(T key) {
            super.remove(key);
        }

        @Override
        public void removeItem(T key, R item) {
            Collection<R> coll = super.get(key);
            if (coll != null) {
                coll.remove(item);
            }
        }
    }

    static class SynchronizedMapLike<T, R> implements MapLike<T, R> {

        private final MapLike<T, R> orig;

        public SynchronizedMapLike(MapLike<T, R> orig) {
            this.orig = orig;
        }

        @Override
        public synchronized void add(T key, R val) {
            orig.add(key, val);
        }

        @Override
        public synchronized boolean contains(T key) {
            return orig.contains(key);
        }

        @Override
        public synchronized Iterable<R> getItems(T key) {
            return orig.getItems(key);
        }

        @Override
        public synchronized int size() {
            return orig.size();
        }

        @Override
        public synchronized void removeAllItems(T key) {
            orig.removeAllItems(key);
        }

        @Override
        public synchronized void removeItem(T key, R item) {
            orig.removeItem(key, item);
        }
    }
}

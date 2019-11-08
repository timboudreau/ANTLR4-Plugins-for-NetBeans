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
package org.nemesis.localizers.spi;

import com.mastfrog.util.cache.TimedCache;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;
import org.openide.util.lookup.ProxyLookup;

/**
 *
 * @author Tim Boudreau
 */
class LocalizerRegistry {

    private static final long CACHE_TIME = 120000; // 2 minute cache time
    TimedCache<Class<?>, Lookup, RuntimeException> instanceLookupCache
            = TimedCache.<Class<?>, Lookup>create(
                    CACHE_TIME,
                    LocalizerRegistry::instanceLocalizersLookupsForType
            );

    public <T> Localizer<? super T> find(T o) {
        Localizer<? super T> result;
        if (o instanceof Enum<?>) {
            Class<?> enumType = ((Enum<?>) o).getDeclaringClass();
            result = (Localizer<? super T>) createEnumLocalizerFinder(enumType).find(o);
        } else if (o instanceof Class<?>) {
            result = (Localizer<? super T>) findTypeLocalizer((Class<?>) o);
        } else {
            result = findInstanceLocalizer(o);
        }
        return result == null ? FallbackLocalizer.INSTANCE : result;
    }

    private static <T extends Enum<T>> String enumLookupPath(T en) {
        return Localizer.ENUM_LOOKUP_BASE + en.getDeclaringClass().getName().replace('.', '/');
    }

    private static String typeLookupPath(Class<?> type) {
        return Localizer.TYPE_LOOKUP_BASE + type.getName().replace('.', '/');
    }

    private static String instanceLookupPath(Class<?> type) {
        return Localizer.INSTANCE_LOOKUP_BASE + type.getName().replace('.', '/');
    }

    private <T extends Enum<T>> Localizer<? super T> findEnumLocalizer(Class<T> type, T c) {
        String path = enumLookupPath(c);
        Lookup lkp = Lookups.forPath(path);
        Lookup more = instanceLookupCache.get(type);
        if (more != Lookup.EMPTY) {
            lkp = new ProxyLookup(lkp, more);
        }
        return localizerFromLookup(lkp, c);
    }

    private Localizer<? super Class<?>> findTypeLocalizer(Class<?> type) {
        return localizerFromLookup(Lookups.forPath(typeLookupPath(type)), type);
    }

    private <T> Localizer<? super T> findInstanceLocalizer(T obj) {
        return instanceLocalizerByType(obj);
    }

    private <T> Localizer<? super T> instanceLocalizerByType(T o) {
        Lookup lkp = instanceLocalizersLookups(o);
        return localizerFromLookup(lkp, o);
    }

    private static <T> Localizer<? super T> localizerFromLookup(Lookup lkp, T o) {
        List<Localizer<? super T>> localizers = new ArrayList<>();
        Collection<? extends Localizer> all = lkp.lookupAll(Localizer.class);
        for (Localizer<?> loc : all) {
            boolean match = loc.matches(o);
            if (match) {
                localizers.add((Localizer<? super T>) loc);
            }
        }
        localizers.add(FallbackLocalizer.INSTANCE);
        PolyLocalizer<T> result = new PolyLocalizer<>(localizers);
        return result;
    }

    private Lookup instanceLocalizersLookups(Object o) {
        if (o == null) {
            return Lookup.EMPTY;
        }
        Class<?> type = o.getClass();
        return instanceLookupCache.get(type);
    }

    private static Lookup instanceLocalizersLookupsForType(Class<?> type) throws RuntimeException {
        List<Lookup> lookups = new ArrayList<>();
        while (type != null) {
            Lookup lkp = Lookups.forPath(instanceLookupPath(type));
            if (!lkp.equals(Lookup.EMPTY)) {
                lookups.add(lkp);
            }
            for (Class<?> iface : type.getInterfaces()) {
                lkp = Lookups.forPath(instanceLookupPath(iface));
                if (!lkp.equals(Lookup.EMPTY)) {
                    lookups.add(lkp);
                }
            }
            type = type.getSuperclass();
        }
        Lookup result = lookups.isEmpty() ? Lookup.EMPTY : new ProxyLookup(lookups.toArray(new Lookup[lookups.size()]));
        return result;
    }

    private <T extends Enum<T>> LocalizerFinder<T> createEnumLocalizerFinder(Class<?> type) {
        return new LocalizerFinder<>(type);
    }

    private final class LocalizerFinder<T extends Enum<T>> {

        // Generics hack
        private final Class<T> type;

        LocalizerFinder(Class<?> type) {
            this.type = (Class<T>) type;
        }

        Localizer<? super T> find(Object o) {
            if (type.isInstance(o)) {
                T t = type.cast(o);
                return findEnumLocalizer(type, t);
            }
            return null;
        }
    }
}

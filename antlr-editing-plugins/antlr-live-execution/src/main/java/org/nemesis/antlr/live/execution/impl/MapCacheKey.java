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

package org.nemesis.antlr.live.execution.impl;

import com.mastfrog.util.cache.MapCache;

/**
 * Cache key to look up a cache in a cache - for each InvocationRunner type,
 * we have a Subscribable-managed cache (cache entries are removed if all
 * subscribers unsubscribe or the file is deleted);  used to fetch the
 * cache of individual InvocationEnvironments per InvocationRunner
 * per Java source.
 *
 * @param <T> The invocation runner's return type
 */
class MapCacheKey<T> extends TypedCache.K<MapCache<Object, InvocationEnvironment<T, ?>>> {

    public MapCacheKey(InvocationRunnerLookupKey<T> owner) {
        super(MapCache.class, "enviro-cache-" + owner.invocationResultType().getName());
    }

}

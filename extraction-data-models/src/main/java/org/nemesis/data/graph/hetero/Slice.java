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
package org.nemesis.data.graph.hetero;

import java.util.Set;
import org.nemesis.data.IndexAddressable;

/**
 * A slice of the graph which can return typed information about
 * elements from one side of the graph.
 *
 * @param <T>
 * @param <R>
 */
public interface Slice<T, R> {

    Set<IndexAddressable.IndexAddressableItem> closureOf(T obj);

    Set<IndexAddressable.IndexAddressableItem> reverseClosureOf(T obj);

    Set<R> parents(T obj);

    Set<R> children(T obj);

    int childCount(T obj);

    boolean hasOutboundEdge(T obj, R k);

    boolean hasInboundEdge(T obj, R k);

    int distance(T obj, R k);

    int inboundReferenceCount(T obj);

    int outboundReferenceCount(T obj);

    int closureSize(T obj);

    int reverseClosureSize(T obj);

}

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
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

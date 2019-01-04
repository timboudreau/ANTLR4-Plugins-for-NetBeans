package org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.extraction;

import java.io.IOException;
import java.io.Serializable;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.data.IndexAddressable;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.data.Named;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.data.named.NamedSemanticRegion;

/**
 *
 * @author Tim Boudreau
 */
public interface UnknownNameReference<T extends Enum<T>> extends Named, IndexAddressable.IndexAddressableItem, Serializable {

    T expectedKind();

    public static interface UnknownNameReferenceResolver<R, I extends IndexAddressable.NamedIndexAddressable<N>, N extends NamedSemanticRegion<T>, T extends Enum<T>> {

        <X> X resolve(Extraction extraction, UnknownNameReference<T> ref, ResolutionConsumer<R, I, N, T, X> c) throws IOException;
    }

    public static interface ResolutionConsumer<R, I extends IndexAddressable.NamedIndexAddressable<N>, N extends NamedSemanticRegion<T>, T extends Enum<T>, X> {

        X resolved(UnknownNameReference<T> unknown, R resolutionSource, I in, N element);
    }

    default<R, I extends IndexAddressable.NamedIndexAddressable<N>, N extends NamedSemanticRegion<T>> AttributedForeignNameReference<R, I, N, T> 
        resolve(Extraction extraction, UnknownNameReferenceResolver<R, I, N, T> resolver) throws IOException {
        ResolutionConsumer<R,I,N,T,AttributedForeignNameReference<R, I, N, T>> cons = (UnknownNameReference<T> unknown, R resolutionSource, I in, N element) -> new AttributedForeignNameReference<>(unknown, resolutionSource, in, element, extraction);
        return resolver.resolve(extraction, this, cons);
    }
}

package org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.extraction;

import java.util.Objects;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.data.IndexAddressable;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.data.Named;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.data.named.NamedSemanticRegion;

/**
 *
 * @author Tim Boudreau
 */
public final class AttributedForeignNameReference<R, I extends IndexAddressable.NamedIndexAddressable<N>, N extends NamedSemanticRegion<T>, T extends Enum<T>> implements Named, IndexAddressable.IndexAddressableItem {

    private final UnknownNameReference<T> unk;
    private final R resolutionSource;
    private final I in;
    private final N element;
    private final Extraction extraction;

    AttributedForeignNameReference(UnknownNameReference<T> unk, R resolutionSource, I in, N element, Extraction extraction) {
        this.unk = unk;
        this.resolutionSource = resolutionSource;
        this.in = in;
        this.element = element;
        this.extraction = extraction;
    }

    public Extraction from() {
        return extraction;
    }

    public T expectedKind() {
        return unk.expectedKind();
    }

    public boolean isTypeConflict() {
        T ek = expectedKind();
        T actualKind = element.kind();
        return ek != null && actualKind != null && ek != actualKind;
    }

    public R source() {
        return resolutionSource;
    }

    public I in() {
        return in;
    }

    public N element() {
        return element;
    }

    @Override
    public String name() {
        return unk.name();
    }

    @Override
    public int start() {
        return unk.start();
    }

    @Override
    public int end() {
        return unk.end();
    }

    @Override
    public int index() {
        return unk.index();
    }

    @Override
    public String toString() {
        return "for:" + element + "<<-" + resolutionSource;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 79 * hash + Objects.hashCode(this.unk);
        hash = 79 * hash + Objects.hashCode(this.resolutionSource);
        hash = 79 * hash + Objects.hashCode(this.in);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final AttributedForeignNameReference<?, ?, ?, ?> other = (AttributedForeignNameReference<?, ?, ?, ?>) obj;
        if (!Objects.equals(this.unk, other.unk)) {
            return false;
        }
        if (!Objects.equals(this.resolutionSource, other.resolutionSource)) {
            return false;
        }
        if (!Objects.equals(this.in, other.in)) {
            return false;
        }
        return true;
    }

}

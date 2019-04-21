package org.nemesis.data.graph.bits;

/**
 *
 * @author Tim Boudreau
 */
public abstract class AbstractBits implements Bits {

    private final boolean longNative;

    protected AbstractBits(boolean longNative) {
        this.longNative = longNative;
    }

    public final boolean isLongNative() {
        return longNative;
    }

    @Override
    public boolean equals(Object obj) {
        return obj == this ? true : obj == null ? false
                : obj instanceof Bits ? contentEquals((Bits) obj) : false;
    }

    @Override
    public int hashCode() {
        return bitsHashCode();
    }

    @Override
    public String toString() {
        return stringValue();
    }
}

package org.nemesis.jfs.isolation.pk1.pk1a;

/**
 *
 * @author Tim Boudreau
 */
public class Pk1a {

    private final String val;

    public Pk1a() {
        this("hujus");
    }

    public Pk1a(String val) {
        this.val = val;
    }

    @Override
    public String toString() {
        return val;
    }
}

package org.nemesis.antlr.live.language;

import org.openide.util.NbBundle;

/**
 * Bitmasks in a lightweight coloring attribute as a convenient Java enum.
 *
 * @author Tim Boudreau
 */
@NbBundle.Messages(value = {"ACTIVE=Active", "BACKGROUND=Background", "FOREGROUND=Foreground", "BOLD=Bold", "ITALIC=Italic"})
public enum AttrTypes {
    ACTIVE(AdhocColoring.MASK_ACTIVE), 
    BACKGROUND(AdhocColoring.MASK_BACKGROUND),
    FOREGROUND(AdhocColoring.MASK_FOREGROUND),
    BOLD(AdhocColoring.MASK_BOLD),
    ITALIC(AdhocColoring.MASK_ITALIC);
    private final int maskValue;

    private AttrTypes(int maskValue) {
        this.maskValue = maskValue;
    }

    public String toString() {
        return NbBundle.getMessage(AttrTypes.class, name());
    }

    int maskValue() {
        return maskValue;
    }

}

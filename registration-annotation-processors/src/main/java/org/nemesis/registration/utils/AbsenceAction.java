package org.nemesis.registration.utils;

import java.util.function.BooleanSupplier;

/**
 *
 * @author Tim Boudreau
 */
 enum AbsenceAction implements BooleanSupplier {
    TRUE, FALSE, THROW, PASS_THROUGH;

    @Override
    public boolean getAsBoolean() {
        switch (this) {
            case TRUE:
                return true;
            case FALSE:
                return false;
            case THROW:
                throw new IllegalStateException("Conversion produced null");
            case PASS_THROUGH:
                throw new AssertionError("Should not reach here");
            default:
                throw new AssertionError(this);
        }
    }

}

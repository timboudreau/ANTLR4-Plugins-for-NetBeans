package org.nemesis.registration;

/**
 * A simple color class, so annotation processors don't have a dependency on
 * java.awt.
 *
 * @author Tim Boudreau
 */
public final class ARGBColor {

    static final String LIGHT_GRAY_ALT = "light_gray";
    static final String DARK_GRAY_ALT = "dark_gray";
    static final String WHITE = "white";
    static final String YELLOW = "yellow";
    static final String ORANGE = "orange";
    static final String PINK = "pink";
    static final String MAGENTA = "magenta";
    static final String RED = "red";
    static final String LIGHT_GRAY = "lightGray";
    static final String GRAY = "gray";
    static final String DARK_GRAY = "darkGray";
    static final String CYAN = "cyan";
    static final String GREEN = "green";
    static final String BLUE = "blue";
    static final String BLACK = "black";
    private static final String ALPHA = "alpha";

    private final short a;
    private final short r;
    private final short g;
    private final short b;

    public ARGBColor(int r, int g, int b, int a) {
        this.r = check(r, RED);
        this.g = check(g, GREEN);
        this.b = check(b, BLUE);
        this.a = check(a, ALPHA);
    }

    public ARGBColor(int[] argb) {
        a = check(argb[0], ALPHA);
        r = check(argb[1], RED);
        g = check(argb[2], GREEN);
        b = check(argb[3], BLUE);
    }

    public ARGBColor(String hex) {
        int[] ints = parseConstantString(hex);
        if (ints == null) {
            ints = hexToIntArray(hex);
        }
        assert ints.length == 4;
        a = check(ints[0], ALPHA);
        r = check(ints[1], RED);
        g = check(ints[2], GREEN);
        b = check(ints[3], BLUE);
    }

    public static ARGBColor fromNullableString(String s) {
        return s == null || s.isEmpty() ? null : new ARGBColor(s);
    }

    public static ARGBColor fromNullableRGBA(int[] rgba) {
        return rgba == null || rgba.length == 0 ? null : fromRGBA(rgba);
    }

    public ARGBColor(int r, int g, int b) {
        this(r, g, b, 255);
    }

    public static ARGBColor fromRGBA(int[] arr) {
        assert arr.length == 4;
        return new ARGBColor(arr[0], arr[1], arr[2], arr[3]);
    }

    public int[] toARGB() {
        return new int[]{a, r, g, b};
    }

    public int[] toRGBA() {
        return new int[]{r, g, b, a};
    }

    public int getRed() {
        return r;
    }

    public int getGreen() {
        return g;
    }

    public int getBlue() {
        return b;
    }

    public int getAlpha() {
        return a;
    }

    private String name() {
        if (a == 255) {
            switch (r) {
                case 0:
                    switch (g) {
                        case 0:
                            switch (b) {
                                case 0:
                                    return BLACK;
                                case 255:
                                    return BLUE;
                            }
                            break;
                        case 255:
                            switch (b) {
                                case 0:
                                    return GREEN;
                                case 255:
                                    return CYAN;
                            }
                            break;
                    }
                    break;
                case 64:
                    switch (g) {
                        case 64:
                            switch (b) {
                                case 64:
                                    return DARK_GRAY;
                            }
                            break;
                    }
                    break;
                case 128:
                    switch (g) {
                        case 128:
                            switch (b) {
                                case 128:
                                    return GRAY;

                            }
                            break;
                    }
                    break;
                case 192:
                    switch (g) {
                        case 192:
                            switch (b) {
                                case 192:
                                    return LIGHT_GRAY;
                            }
                            break;
                    }
                    break;
                case 255:
                    switch (g) {
                        case 0:
                            switch (b) {
                                case 0:
                                    return RED;
                                case 255:
                                    return MAGENTA;
                            }
                            break;
                        case 175:
                            switch (b) {
                                case 175:
                                    return PINK;
                            }
                            break;
                        case 200:
                            switch (b) {
                                case 0:
                                    return ORANGE;
                            }
                            break;
                        case 255:
                            switch (b) {
                                case 0:
                                    return YELLOW;
                                case 255:
                                    return WHITE;
                            }
                            break;
                    }
                    break;
            }
        }
        return null;
    }

    private String toHex(int val) {
        String result = Integer.toString(val, 16).toLowerCase();
        if (result.length() == 1) {
            return "0" + result;
        }
        return result;
    }

    public String toHexString() {
        StringBuilder sb = new StringBuilder();
        sb.append(toHex(a));
        sb.append(toHex(r));
        sb.append(toHex(g));
        sb.append(toHex(b));
        return sb.toString();
    }

    @Override
    public String toString() {
        String nm = name();
        if (nm != null) {
            return nm;
        }
        return toHexString();
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 53 * hash + this.a;
        hash = 53 * hash + this.r;
        hash = 53 * hash + this.g;
        hash = 53 * hash + this.b;
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
        final ARGBColor other = (ARGBColor) obj;
        if (this.a != other.a) {
            return false;
        }
        if (this.r != other.r) {
            return false;
        }
        if (this.g != other.g) {
            return false;
        }
        return this.b == other.b;
    }

    private static short check(int val, String name) {
        if (val < 0 || val > 255) {
            throw new IllegalArgumentException("Invalid value " + val + " for " + name);
        }
        return (short) val;
    }

    /**
     * Parses a constant name (as defined on java.awt.Color) to an ARGB array.
     *
     * @param colorName The string
     * @return an array of ints in ARGB order
     */
    private static int[] parseConstantString(String colorName) {
        switch (colorName.toLowerCase()) {
            case BLUE:
                return new int[]{255, 0, 0, 255};
            case CYAN:
                return new int[]{255, 0, 255, 255};
            case MAGENTA:
                return new int[]{255, 255, 0, 255};
            case GREEN:
                return new int[]{255, 0, 255, 0};
            case YELLOW:
                return new int[]{255, 255, 255, 0};
            case ORANGE:
                return new int[]{255, 255, 200, 0};
            case PINK:
                return new int[]{255, 255, 175, 175};
            case RED:
                return new int[]{255, 255, 0, 0};
            case BLACK:
                return new int[]{255, 0, 0, 0};
            case DARK_GRAY:
            case "darkgray":
            case DARK_GRAY_ALT:
                return new int[]{255, 64, 64, 64};
            case GRAY:
                return new int[]{255, 128, 128, 128};
            case LIGHT_GRAY:
            case LIGHT_GRAY_ALT:
            case "lightgray":
                return new int[]{255, 192, 192, 192};
            case WHITE:
                return new int[]{255, 255, 255, 255};
        }
        return null;
    }

    private static int[] hexToIntArray(String argb) {
        int[] result = new int[4];
        if (argb.length() != 8) {
            throw new IllegalArgumentException("Argument length should be 8 but is " + argb.length() + " in '" + argb + "'");
        }
        argb = argb.toLowerCase();
        for (int i = 0; i < argb.length(); i++) {
            char c = argb.charAt(i);
            boolean isHexChar = c >= 'a' && c <= 'f';
            if (!isHexChar) {
                boolean isDigit = c >= '0' && c <= '9';
                if (!isDigit) {
                    throw new IllegalArgumentException("Illegal hexadecimal charcter '" + c + " at " + i + " in '" + argb + "'");
                }
            }
        }
        result[0] = Integer.parseInt(argb.substring(0, 2), 16);
        result[1] = Integer.parseInt(argb.substring(2, 4), 16);
        result[2] = Integer.parseInt(argb.substring(4, 6), 16);
        result[3] = Integer.parseInt(argb.substring(6, 8), 16);
        return result;
    }
}

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
package org.nemesis.antlr.live.language;

import java.awt.Color;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import javax.swing.UIManager;
import javax.swing.text.AttributeSet;
import javax.swing.text.StyleConstants;
import org.netbeans.api.editor.mimelookup.MimeLookup;
import org.netbeans.api.editor.mimelookup.MimePath;
import org.netbeans.api.editor.settings.FontColorNames;
import org.netbeans.api.editor.settings.FontColorSettings;

/**
 *
 * @author Tim Boudreau
 */
public class ColorUtils {

    private final Color background;
    private final Color foreground;

    public ColorUtils(Color background, Color foreground) {
        this.background = background;
        this.foreground = foreground;
    }

    public ColorUtils() {
        this(editorBackground(), editorForeground());
    }

    private static int difference(Color a, Color b) {
        return Math.abs(luminance(a) - luminance(b));
    }

    private static int luminance(Color c) {
        return (299 * c.getRed() + 587 * c.getGreen() + 114 * c.getBlue()) / 1000;
    }

    private static boolean isSufficientlyContrasting(Color a, Color b) {
        if (a.equals(b)) {
            return false;
        }
        int diff = difference(a, b);
        return diff > 80;
    }

    public static Color editorBackground() {
        MimePath mimePath = MimePath.EMPTY;
        FontColorSettings fcs = MimeLookup.getLookup(mimePath).lookup(FontColorSettings.class);
        AttributeSet result = fcs.getFontColors(FontColorNames.DEFAULT_COLORING);
        Color color = (Color) result.getAttribute(StyleConstants.ColorConstants.Background);
        return color == null ? UIManager.getColor("text") : color;
    }

    public static Color editorForeground() {
        MimePath mimePath = MimePath.EMPTY;
        FontColorSettings fcs = MimeLookup.getLookup(mimePath).lookup(FontColorSettings.class);
        AttributeSet result = fcs.getFontColors(FontColorNames.DEFAULT_COLORING);
        Color color = (Color) result.getAttribute(StyleConstants.ColorConstants.Foreground);
        return color == null ? UIManager.getColor("textText") : color;
    }

    private static final class HueRotationColorSupplier implements Supplier<Color> {

        float[] hsb = new float[3];
        Color contrastWith;
        private final float baseSaturation;
        private final float baseLevel;
        private static final int HUE = 0;
        private static final int LEVEL = 1;
        private static final int SAT = 2;
        private static final float HUE_ROTATION_BASE = 0.375F;
        private static final float SATURATION_RANGE = 0.425F;
        private static final float LEVEL_RANGE = 0.25F;

        HueRotationColorSupplier(Color bg, Color fg, boolean targetIsForeground) {
            Color target = targetIsForeground ? fg : bg;
            contrastWith = targetIsForeground ? bg : fg;
            Color.RGBtoHSB(target.getRed(), target.getGreen(), target.getBlue(), hsb);
            this.baseSaturation = targetIsForeground ? 0.625F : 0.425F;
            if (isDark(target)) {
                if (targetIsForeground) {
                    baseLevel = Math.max(hsb[LEVEL], 0.625F);
                } else {
                    baseLevel = Math.max(hsb[LEVEL], 0.875F);
                }
            } else {
                if (targetIsForeground) {
                    baseLevel = Math.min(0.675F, hsb[LEVEL]);
                } else {
                    baseLevel = Math.min(0.875F, hsb[LEVEL]);
                }
            }
        }

        private Color currColor() {
            return new Color(Color.HSBtoRGB(hsb[HUE], hsb[SAT], hsb[LEVEL]));
        }

        private Color next() {
            // We write the hue value back to the original array, so hue
            // keeps moving; the rest should be variations around the baseline
            // so we start from the baseline
            hsb[HUE] = randomlyTweak(hsb[0], HUE_ROTATION_BASE);
            hsb[SAT] = randomlyTweak(baseSaturation, SATURATION_RANGE);
            hsb[LEVEL] = randomlyTweak(baseLevel, LEVEL_RANGE);
            return currColor();
        }

        @Override
        public Color get() {
            Color result = next();
            if (!isSufficientlyContrasting(contrastWith, result)) {
                hsb[LEVEL] = clamp(1F - hsb[1]);
                result = currColor();
                if (!isSufficientlyContrasting(result, contrastWith)) {
                    hsb[SAT] = clamp(1F - baseSaturation);
                    result = currColor();
                }
            }
            return result;
        }
    }

    public Supplier<Color> foregroundColorSupplier() {
        return new HueRotationColorSupplier(background, foreground, true);
    }

    public Supplier<Color> backgroundColorSupplier() {
        return new HueRotationColorSupplier(background, foreground, false);
    }

    private static boolean isMidTone(Color color) {
        return !isDark(color) && !isBright(color);
    }

    private static boolean isBright(Color color) {
        return luminance(color) > 160;
    }

    private static boolean isDark(Color color) {
        return luminance(color) < 96;
    }

    private static float clamp(float f) {
        return Math.max(0.0f, Math.min(f, 1.0f));
    }

    private static float randomlyTweak(float f, float by) {
        float offset = (ThreadLocalRandom.current().nextFloat() * by)
                - (by / 2f);
        if (offset < 0 && offset > -0.1F) {
            offset -= 0.15F;
        } else if (offset > 0 && offset < 0.1F) {
            offset += -.15F;
        }
        f += offset;
        if (f > 1.0f) {
            f -= 1.0f;
        } else if (f < 0f) {
            f = 1.0f + f;
        }
        return clamp(f);
    }
}

/*
 * Copyright 2016-2020 Tim Boudreau, Frédéric Yvon Vinet
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
import java.util.function.Supplier;

/**
 * Provides continuously shifting, non repeating hues for differentiating
 * different ambiguity markers, using an uneven enough interval not to repeat
 * exactly without many iterations..
 */
class RotatingColors implements Supplier<Color> {

    private final float[] hsb = new float[]{0.625F, 0.625F, 0.625F};
    private final int alpha = 87;

    public Color get() {
        float h = hsb[0] + 0.57F;
        if (h > 1) {
            h -= 1F;
        }
        hsb[0] = Math.max(0F, Math.min(1F, h));
        int rgb = Color.HSBtoRGB(hsb[0], hsb[1], hsb[2]);
        Color result = new Color(rgb);
        result = new Color(result.getRed(), result.getGreen(), result.getBlue(), alpha);
        return result;
    }

}

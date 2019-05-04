package org.nemesis.registration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import javax.lang.model.element.AnnotationMirror;
import com.mastfrog.annotation.AnnotationUtils;

/**
 *
 * @author Tim Boudreau
 */
class ColoringProxy implements Iterable<String> {

    List<Integer> fg;
    List<Integer> bg;
    List<Integer> und;
    List<Integer> wave;
    String def;
    boolean bold;
    boolean italic;
    Set<String> themes;
    final String mimeType;
    final String categoryName;

    ColoringProxy(String mimeType, String categoryName, AnnotationMirror coloration, AnnotationUtils utils) {
        this.mimeType = mimeType;
        this.categoryName = categoryName;
        fg = LanguageFontsColorsProcessor.ints(coloration, "fg", utils);
        bg = LanguageFontsColorsProcessor.ints(coloration, "bg", utils);
        und = LanguageFontsColorsProcessor.ints(coloration, "underline", utils);
        wave = LanguageFontsColorsProcessor.ints(coloration, "waveUnderline", utils);
        def = utils.annotationValue(coloration, "derivedFrom", String.class);
        bold = utils.annotationValue(coloration, "bold", Boolean.class, false);
        italic = utils.annotationValue(coloration, "italic", Boolean.class, false);
        themes = new HashSet<>(utils.annotationValues(coloration, "themes", String.class));
        if (themes.isEmpty()) {
            themes = Collections.singleton("NetBeans");
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(categoryName).append(" ").append(mimeType);
        ann("fg", fg(), sb);
        ann("bg", bg(), sb);
        ann("wv", waveUnderline(), sb);
        ann("un", underline(), sb);
        sb.append(" themes=").append(themes);
        if (bold) {
            sb.append(" bold");
        }
        if (italic) {
            sb.append(" italic");
        }
        return sb.toString();
    }

    private void ann(String name, Object o, StringBuilder sb) {
        if (o != null) {
            sb.append(' ').append(name).append('=').append(o);
        }
    }

    @Override
    public Iterator<String> iterator() {
        return themes.iterator();
    }

    private int valueOrDefault(int index, List<Integer> ints, int def) {
        if (ints.size() <= index) {
            return def;
        }
        return ints.get(index);
    }

    public boolean isBold() {
        return bold;
    }

    public boolean isItalic() {
        return italic;
    }

    public ARGBColor fg() {
        return toColor(fg);
    }

    public ARGBColor bg() {
        return toColor(bg);
    }

    public ARGBColor underline() {
        return toColor(und);
    }

    public ARGBColor waveUnderline() {
        return toColor(wave);
    }

    private ARGBColor toColor(List<Integer> ints) {
        if (ints.isEmpty()) {
            return null;
        }
        int r = valueOrDefault(0, ints, 0);
        int g = valueOrDefault(1, ints, 0);
        int b = valueOrDefault(2, ints, 0);
        int a = valueOrDefault(3, ints, 255);
        return new ARGBColor(r, g, b, a);
    }

    public List<String> validate() {
        List<String> errors = new ArrayList<>();
        validateColorArray("fg", fg, errors);
        validateColorArray("bg", bg, errors);
        validateColorArray("underline", und, errors);
        validateColorArray("waveUnderline", wave, errors);
        return errors;
    }

    private String themesString() {
        StringBuilder sb = new StringBuilder();
        for (Iterator<String> it = themes.iterator(); it.hasNext();) {
            sb.append(it.next());
            if (it.hasNext()) {
                sb.append(", ");
            }
        }
        return sb.toString();
    }

    void updateFC(FontsColorsBuilder.Fc fc) {
        fc.setBackColor(bg());
        fc.setForeColor(fg());
        fc.setWaveUnderlineColor(waveUnderline());
        fc.setUnderlineColor(underline());
        fc.setDefault(def);
        if (isBold()) {
            fc.setBold();
        }
        if (isItalic()) {
            fc.setItalic();
        }
    }

    private void validateColorArray(String attr, List<Integer> rgba, List<String> errors) {
        if (rgba.isEmpty()) {
            return;
        }
        if (rgba.size() != 3 && rgba.size() != 4) {
            String err = "Colors for attribute " + attr + " on " + categoryName + " of " + mimeType + " for themes " + themesString() + " mis-specified as " + rgba + ".  Colors must have 0 values (unspecified), " + "or 3 RGB values, or 4 RGBA values, but " + rgba.size() + " are present.";
            errors.add(err);
        }
        loop:
        for (int i = 0; i < rgba.size(); i++) {
            if (i < 0 || i > 255) {
                String name;
                switch (i) {
                    case 0:
                        name = "red";
                        break;
                    case 1:
                        name = "green";
                        break;
                    case 2:
                        name = "blue";
                        break;
                    case 3:
                        name = "alpha";
                        break;
                    default:
                        break loop; // error already recorded
                }
                String err = "Color value for " + name + " of " + attr + " on " + categoryName + " of " + mimeType + " for " + themesString() + " is " + "outside the allowed range of 0 to 255.  Colors " + "must be specified as red,green,blue or " + "red,green,blue,alpha values from 0-255.";
                errors.add(err);
            }
        }
    }

    public boolean isDefaults() {
        return fg.isEmpty() && bg.isEmpty() && und.isEmpty() && wave.isEmpty() && ("default".equals(def) || "".equals(def)) && !bold && !italic && (themes.isEmpty() || Arrays.asList("NetBeans").equals(themes));
    }

}

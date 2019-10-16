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
package org.nemesis.registration;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BiConsumer;
import java.util.function.Function;
import org.nemesis.registration.FontsColorsBuilder.Fc;
import org.openide.xml.XMLUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.EntityResolver;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/**
 *
 * @author Tim Boudreau
 */
final class FontsColorsBuilder implements Iterable<Fc> {

    private static final String XML_DECL = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n";
    private static final String DOCTYPE = "<!DOCTYPE fontscolors PUBLIC "
            + "\"-//NetBeans//DTD Editor Fonts and Colors settings 1.1//EN\" "
            + "\"http://www.netbeans.org/dtds/EditorFontsColors-1_1.dtd\">";
    private static final String HEAD = "<fontscolors>\n";
    private static final String TAIL = "</fontscolors>\n";

    private final Set<FcEntry> all = new HashSet<>();
    private final String theme;

    private static final String LOCAL_DTD_RESOURCE = "/org/nemesis/registration/EditorFontsColors-1_1.dtd";
    private static final String PUBLIC_DTD_ID = "-//NetBeans//DTD Editor Fonts and Colors settings 1.1//EN";
    private static final String NETWORK_DTD_URL = "http://netbeans.org/dtds/EditorFontsColors-1_1.dtd";
    private static final ErrorHandler ERROR_HANDLER = new ErrorHandler() {
        @Override
        public void warning(SAXParseException exception) throws SAXException {
            throw exception;
        }

        @Override
        public void error(SAXParseException exception) throws SAXException {
            throw exception;
        }

        @Override
        public void fatalError(SAXParseException exception) throws SAXException {
            throw exception;
        }
    };

    private static final EntityResolver ENTITY_RESOLVER = new EntityResolver() {
        @Override
        public InputSource resolveEntity(String publicId, String systemId) throws SAXException, IOException {
            if (PUBLIC_DTD_ID.equals(publicId)) {
                return new InputSource(FontsColorsBuilder.class.getResource(LOCAL_DTD_RESOURCE).toString());
//                return new InputSource(new URL(NETWORK_DTD_URL).openStream());
            } else {
                return null;
            }
        }
    };

    public FontsColorsBuilder(String theme) {
        this.theme = theme;
    }

    ReadableFc getExisting(String name) {
        // for tests
        for (ReadableFc fc : all) {
            if (name.equals(fc.name())) {
                return fc;
            }
        }
        return null;
    }

    public Set<String> names() {
        Set<String> result = new HashSet<>();
        for (Fc fc : this) {
            result.add(fc.name());
        }
        return result;
    }

    public void loadExisting(InputStream in) throws IOException, SAXException {
        Document doc = XMLUtil.parse(new InputSource(in), true, true, ERROR_HANDLER, ENTITY_RESOLVER);
        NodeList rootNodes = doc.getChildNodes();
        int rootLength = rootNodes.getLength();
        for (int i = 0; i < rootLength; i++) {
            // fontsColors node
            Node root = rootNodes.item(i);
            NodeList coloringEntries = root.getChildNodes();
            int entryCount = coloringEntries.getLength();
            for (int j = 0; j < entryCount; j++) {
                Node fc = coloringEntries.item(j);
                if (fc instanceof Element) {
                    Element fcEl = (Element) fc;
                    String name = fcEl.getAttribute("name");
                    if (name == null || name.isEmpty()) {
                        continue;
                    }
                    String def = fcEl.getAttribute("default");
                    ARGBColor foreColor = ARGBColor.fromNullableString(fcEl.getAttribute("foreColor"));
                    ARGBColor bgColor = ARGBColor.fromNullableString(fcEl.getAttribute("bgColor"));
                    ARGBColor waveUnderlined = ARGBColor.fromNullableString(fcEl.getAttribute("waveUnderlined"));
                    ARGBColor underline = ARGBColor.fromNullableString(fcEl.getAttribute("underline"));

                    Set<FontStyle> styles = EnumSet.noneOf(FontStyle.class);
                    NodeList fontStyles = fcEl.getChildNodes();
                    if (fontStyles != null) {
                        int childCount = fontStyles.getLength();
                        for (int k = 0; k < childCount; k++) {
                            Node fontStyle = fontStyles.item(k);
                            if (fontStyle instanceof Element) {
                                Element fontStyleEl = (Element) fontStyle;
                                String style = fontStyleEl.getAttribute("style");
                                if (style != null) {
                                    switch (style) {
                                        case "bold":
                                            styles.add(FontStyle.BOLD);
                                            break;
                                        case "italic":
                                            styles.add(FontStyle.ITALIC);
                                            break;
                                        case "bold+italic" :
                                            styles.add(FontStyle.BOLD);
                                            styles.add(FontStyle.ITALIC);
                                            break;
                                        default :
                                            throw new IOException("Could not parse font style '" + style + "'");
                                    }
                                }
                            }
                        }
                    }
                    add(name).setForeColor(foreColor).setBackColor(bgColor)
                            .setWaveUnderlineColor(waveUnderlined).setUnderlineColor(underline)
                            .setStyles(styles).setDefault(def);

                }
            }
        }
    }

    public String theme() {
        return theme;
    }

    public Fc add(String name) {
        FcEntry en = new FcEntry(name);
        all.add(en);
        return en;
    }

    @Override
    public Iterator<Fc> iterator() {
        List<Fc> l = new ArrayList<>(all);
        Collections.sort(l);
        return l.iterator();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(XML_DECL);
        sb.append(DOCTYPE);
        sb.append(HEAD);
        for (Fc en : this) {
            sb.append(en);
        }
        sb.append(TAIL);
        return sb.toString();
    }

    static MultiThemeFontsColorsBuilder multiBuilder() {
        return new MultiThemeFontsColorsBuilder();
    }

    static final class MultiThemeFontsColorsBuilder {

        private final Map<String, FontsColorsBuilder> builderForTheme = new HashMap<>();

        private MultiThemeFontsColorsBuilder() {

        }

        Set<String> names() {
            Set<String> result = new TreeSet<>();
            builderForTheme.forEach((s, fcb) -> {
                result.addAll(fcb.names());
            });
            return result;
        }

        void loadExisting(Function<String, InputStream> inputProvider) throws IOException, SAXException {
            for (Map.Entry<String, FontsColorsBuilder> e : builderForTheme.entrySet()) {
                InputStream in = inputProvider.apply(e.getKey());
                if (in != null) {
                    e.getValue().loadExisting(in);
                }
            }
        }

        boolean isEmpty() {
            return builderForTheme.isEmpty();
        }

        public Fc add(String name, Iterable<String> themes) {
            assert name != null : "null name";
            assert themes != null : "null themes";
            assert themes.iterator().hasNext() : "themes iterator is empty";
            List<Fc> relevant = new ArrayList<>(builderForTheme.size());
            for (String theme : themes) {
                FontsColorsBuilder bldr = builderForTheme.get(theme);
                if (bldr == null) {
                    bldr = new FontsColorsBuilder(theme);
                    builderForTheme.put(theme, bldr);
                }
                relevant.add(bldr.add(name));
            }
            return new MetaFc(relevant);
        }

        public void build(BiConsumer<String, FontsColorsBuilder> c) {
            builderForTheme.forEach(c);
        }

        @Override
        public String toString() {
            List<String> keys = new ArrayList(builderForTheme.keySet());
            Collections.sort(keys);
            StringBuilder sb = new StringBuilder();
            for (String k : keys) {
                FontsColorsBuilder b = builderForTheme.get(k);
                sb.append("\n").append(b).append("\n\n");
            }
            return sb.toString();
        }
    }

    private static final class MetaFc implements Fc {

        private final List<Fc> delegates = new ArrayList<>();

        MetaFc(Collection<Fc> delegates) {
            assert delegates != null && !delegates.isEmpty();
            this.delegates.addAll(delegates);
        }

        public String toString() {
            StringBuilder sb = new StringBuilder();
            for (Iterator<Fc> it = delegates.iterator(); it.hasNext();) {
                Fc fc = it.next();
                sb.append(fc);
                if (it.hasNext()) {
                    sb.append(",");
                }
            }
            return sb.toString();
        }

        public String name() {
            return delegates.get(0).name();
        }

        @Override
        public Fc setForeColor(ARGBColor color) {
            for (Fc fc : delegates) {
                fc.setForeColor(color);
            }
            return this;
        }

        @Override
        public Fc setBackColor(ARGBColor color) {
            for (Fc fc : delegates) {
                fc.setBackColor(color);
            }
            return this;
        }

        @Override
        public Fc setDefault(String def) {
            for (Fc fc : delegates) {
                fc.setDefault(def);
            }
            return this;
        }

        @Override
        public Fc setWaveUnderlineColor(ARGBColor c) {
            for (Fc fc : delegates) {
                fc.setWaveUnderlineColor(c);
            }
            return this;
        }

        @Override
        public Fc setUnderlineColor(ARGBColor c) {
            for (Fc fc : delegates) {
                fc.setUnderlineColor(c);
            }
            return this;
        }

        @Override
        public Fc setBold() {
            for (Fc fc : delegates) {
                fc.setBold();
            }
            return this;
        }

        @Override
        public Fc setItalic() {
            for (Fc fc : delegates) {
                fc.setItalic();
            }
            return this;
        }

        @Override
        public Fc setStyles(Iterable<FontStyle> styles) {
            for (Fc fc : delegates) {
                fc.setStyles(styles);
            }
            return this;
        }
    }

    interface Fc extends Comparable<Fc> {

        public Fc setForeColor(ARGBColor color);

        public Fc setBackColor(ARGBColor color);

        public Fc setDefault(String def);

        public Fc setWaveUnderlineColor(ARGBColor c);

        public Fc setUnderlineColor(ARGBColor c);

        public Fc setBold();

        public Fc setItalic();

        public Fc setStyles(Iterable<FontStyle> styles);

        public String name();

        default int compareTo(Fc other) {
            return name().compareToIgnoreCase(other.name());
        }
    }

    interface ReadableFc extends Fc {
        ARGBColor backColor();
        ARGBColor foreColor();
        ARGBColor waveColor();
        ARGBColor underlineColor();
        String defawlt();
        Set<FontStyle> styles();
    }

    private static final class FcEntry implements ReadableFc {

        private static final String INDENT = "    ";
        private ARGBColor foreColor;
        private ARGBColor backColor;
        private ARGBColor waveUnderlineColor;
        private String defawlt = null;
        private final String name;
        private final Set<FontStyle> styles = EnumSet.noneOf(FontStyle.class);
        private ARGBColor underlineColor;

        private FcEntry(String name) {
            this.name = name;
        }

        String styleString() {
            if (styles.isEmpty()) {
                return null;
            } else if (styles.size() == 1) {
                return styles.iterator().next().toString();
            }
            String head = "<font style=\"";
            StringBuilder sb = new StringBuilder(head);
            for (FontStyle f : styles) {
                if (sb.length() > head.length()) {
                    sb.append("+");
                }
                sb.append(f.name().toLowerCase());
            }
            return sb.append("\"/>").toString();
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public FcEntry setStyles(Iterable<FontStyle> styles) {
            this.styles.clear();
            for (FontStyle f : styles) {
                this.styles.add(f);
            }
            return this;
        }

        @Override
        public FcEntry setBold() {
            styles.add(FontStyle.BOLD);
            return this;
        }

        @Override
        public FcEntry setItalic() {
            styles.add(FontStyle.ITALIC);
            return this;
        }

        @Override
        public FcEntry setForeColor(ARGBColor foreColor) {
            this.foreColor = foreColor;
            return this;
        }

        @Override
        public FcEntry setBackColor(ARGBColor color) {
            this.backColor = color;
            return this;
        }

        @Override
        public FcEntry setDefault(String def) {
            if (def == null || def.isEmpty()) {
                def = null;
            }
            this.defawlt = def;
            return this;
        }

        @Override
        public FcEntry setWaveUnderlineColor(ARGBColor wave) {
            this.waveUnderlineColor = wave;
            return this;
        }

        @Override
        public FcEntry setUnderlineColor(ARGBColor underlineColor) {
            this.underlineColor = underlineColor;
            return this;
        }

        public FcEntry clearDefault() {
            defawlt = null;
            return this;
        }

        private String xmlify(String name) {
            String nm = name.replaceAll("\"", "&quot;").replaceAll("\\&", "&amp;").replaceAll("\\<", "&lt;").replaceAll("\\>", "&gt;");
            return nm;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder().append(INDENT).append("<fontcolor name=\"");
            sb.append(xmlify(name));
            sb.append("\"");
            if (foreColor != null) {
                sb.append(" foreColor=\"");
                sb.append(foreColor);
                sb.append("\"");
            }
            if (backColor != null) {
                sb.append(" bgColor=\"");
                sb.append(backColor);
                sb.append("\"");
            }
            if (waveUnderlineColor != null) {
                sb.append(" waveUnderlined=\"");
                sb.append(waveUnderlineColor);
                sb.append("\"");
            }
            if (underlineColor != null) {
                sb.append(" underline=\"");
                sb.append(underlineColor);
                sb.append("\"");
            }
            if (defawlt != null) {
                sb.append(" default=\"").append(defawlt).append("\"");
            }
            if (!styles.isEmpty()) {
                sb.append(">");
                sb.append('\n').append(INDENT).append(INDENT).append(styleString());
                sb.append("\n").append(INDENT).append("</fontcolor>\n");
            } else {
                sb.append("/>\n");
            }
            return sb.toString();
        }

        @Override
        public int hashCode() {
            int hash = 5;
            hash = 97 * hash + Objects.hashCode(this.name);
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
            final FcEntry other = (FcEntry) obj;
            return Objects.equals(this.name, other.name);
        }

        @Override
        public ARGBColor backColor() {
            return backColor;
        }

        @Override
        public ARGBColor foreColor() {
            return foreColor;
        }

        @Override
        public ARGBColor waveColor() {
            return waveUnderlineColor;
        }

        @Override
        public ARGBColor underlineColor() {
            return underlineColor;
        }

        @Override
        public String defawlt() {
            return defawlt;
        }

        @Override
        public Set<FontStyle> styles() {
            return Collections.unmodifiableSet(styles);
        }
    }
}

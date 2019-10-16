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

import java.io.ByteArrayInputStream;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.util.Set;
import org.nemesis.registration.FontsColorsBuilder.Fc;

/**
 *
 * @author Tim Boudreau
 */
public class Foo {

    public static void main(String[] args) throws Throwable {
        FontsColorsBuilder bldr = new FontsColorsBuilder("x");
        bldr.loadExisting(new ByteArrayInputStream(XML.getBytes(UTF_8)));
        System.out.println(bldr);
        for (Fc fc : bldr) {
            System.out.println(fc.name());
            ARGBColor fg = bldr.getExisting(fc.name()).foreColor();
            if (fg != null) {
                System.out.println("    fg: " + fg.toRGBorRGBAString());
            }
            ARGBColor bg = bldr.getExisting(fc.name()).backColor();
            if (bg != null) {
                System.out.println("    bg: " + bg.toRGBorRGBAString());
            }
            Set<FontStyle> styles = bldr.getExisting(fc.name()).styles();
            if (styles.contains(FontStyle.BOLD)) {
                System.out.println("    bold");
            }
            if (styles.contains(FontStyle.BOLD)) {
                System.out.println("    italic");
            }
        }
    }

    private static final String XML = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<!DOCTYPE fontscolors PUBLIC \"-//NetBeans//DTD Editor Fonts and Colors settings 1.1//EN\" \"http://www.netbeans.org/dtds/EditorFontsColors-1_1.dtd\">\n"
            + "<fontscolors>\n"
            + "    <fontcolor default=\"identifier\" foreColor=\"ff43439c\" name=\"identifier\">\n"
            + "        <font style=\"bold\"/>\n"
            + "    </fontcolor>\n"
            + "    <fontcolor default=\"comment\" foreColor=\"ff913040\" name=\"typeNames\"/>\n"
            + "    <fontcolor bgColor=\"fffffff2\" name=\"types\"/>\n"
            + "    <fontcolor bgColor=\"fffffff2\" name=\"refs\"/>\n"
            + "    <fontcolor default=\"literal\" foreColor=\"ffb5b545\" name=\"description\"/>\n"
            + "    <fontcolor default=\"comment\" foreColor=\"ffb4b4b4\" name=\"comment\"/>\n"
            + "    <fontcolor default=\"keyword\" foreColor=\"ff476191\" name=\"keyword\"/>\n"
            + "    <fontcolor default=\"whitespace\" name=\"whitespace\"/>\n"
            + "    <fontcolor default=\"method\" foreColor=\"ff11936e\" name=\"constraints\"/>\n"
            + "    <fontcolor default=\"operator\" foreColor=\"ffa4a4a4\" name=\"operator\"/>\n"
            + "    <fontcolor default=\"string\" foreColor=\"ff817b1a\" name=\"literal\"/>\n"
            + "</fontscolors>";
}

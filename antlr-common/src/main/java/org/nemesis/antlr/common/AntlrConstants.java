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
package org.nemesis.antlr.common;

import java.awt.Image;
import java.util.EnumMap;
import java.util.Map;
import javax.swing.ImageIcon;
import org.nemesis.antlr.common.extractiontypes.RuleTypes;
import org.openide.util.ImageUtilities;

/**
 *
 * @author Tim Boudreau
 */
public final class AntlrConstants {

    public static final String WRAPPER_MODULE_CNB = "org.nemesis.antlr.wrapper";
    public static final String ANTLR_MIME_TYPE = "text/x-g4";
    public static final String ACTION_PATH = "Loaders/" + ANTLR_MIME_TYPE + "/Actions";
//    public static final String ICON_PATH = "org/nemesis/antlr/v4/netbeans/v8/grammar/file/antlr-g4-file-type.png";

    private static final String ALTERNATIVE_PNG = "alternative.png";
    private static final String LEXER_PNG = "lexer.png";
    private static final String PARSER_PNG = "parser.png";
    private static final String FRAGMENT_PNG = "fragment.png";
    private static final String PACKAGE_IMAGE_PATH = "org/nemesis/antlr/common/";
    public static final String ICON_PATH = PACKAGE_IMAGE_PATH + "antlr-g4-file-type.png";

    private static Map<RuleTypes, ImageIcon> iconForTypeMap;
    private static ImageIcon ANTLR_ICON;
    private static Image ANTLR_IMAGE;

    private AntlrConstants() {
        throw new AssertionError();
    }

    public static ImageIcon antlrIcon() {
        if (ANTLR_ICON == null) {
            ANTLR_ICON = ImageUtilities.loadImageIcon(ICON_PATH, false);
        }
        return ANTLR_ICON;
    }

    public static Image antlrImage() {
        if (ANTLR_IMAGE == null) {
            ANTLR_IMAGE = ImageUtilities.loadImage(ICON_PATH);
        }
        return ANTLR_IMAGE;
    }

    public static Map<? super RuleTypes, ImageIcon> iconForTypeMap() {
        if (iconForTypeMap != null) {
            return iconForTypeMap;
        }
        iconForTypeMap = new EnumMap<>(RuleTypes.class);
        iconForTypeMap.put(RuleTypes.FRAGMENT,
                ImageUtilities.loadImageIcon(PACKAGE_IMAGE_PATH + FRAGMENT_PNG, false));
        iconForTypeMap.put(RuleTypes.PARSER,
                ImageUtilities.loadImageIcon(PACKAGE_IMAGE_PATH + PARSER_PNG, false));
        iconForTypeMap.put(RuleTypes.LEXER,
                ImageUtilities.loadImageIcon(PACKAGE_IMAGE_PATH + LEXER_PNG, false));
        iconForTypeMap.put(RuleTypes.NAMED_ALTERNATIVES,
                ImageUtilities.loadImageIcon(PACKAGE_IMAGE_PATH + ALTERNATIVE_PNG, false));
        return iconForTypeMap;
    }

    public static ImageIcon parserIcon() {
        return iconForTypeMap().get(RuleTypes.PARSER);
    }

    public static ImageIcon lexerIcon() {
        return iconForTypeMap().get(RuleTypes.LEXER);
    }

    public static ImageIcon fragmentIcon() {
        return iconForTypeMap().get(RuleTypes.FRAGMENT);
    }

    public static ImageIcon alternativeIcon() {
        return iconForTypeMap().get(RuleTypes.NAMED_ALTERNATIVES);
    }
}

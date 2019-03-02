/*
BSD License

Copyright (c) 2016-2018, Frédéric Yvon Vinet, Tim Boudreau
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

* Redistributions of source code must retain the above copyright
  notice, this list of conditions and the following disclaimer.
* Redistributions in binary form must reproduce the above copyright
  notice, this list of conditions and the following disclaimer in the
  documentation and/or other materials provided with the distribution.
* The name of its author may not be used to endorse or promote products
  derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR IMPLIED 
WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF 
MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT
SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, 
EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT
OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS 
INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN 
CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING
IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY 
OF SUCH DAMAGE.
 */
package org.nemesis.antlr.common;

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

    private AntlrConstants() {
        throw new AssertionError();
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

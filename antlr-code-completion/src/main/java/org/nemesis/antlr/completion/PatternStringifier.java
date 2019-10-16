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
package org.nemesis.antlr.completion;

import java.text.MessageFormat;
import java.util.EnumMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 * @author Tim Boudreau
 */
final class PatternStringifier implements Stringifier<Object> {

    private final EnumMap<StringKind, Pattern> patternForKind;
    private final EnumMap<StringKind, String> messageFormats;

    PatternStringifier(EnumMap<StringKind, Pattern> patternForKind) {
        this(patternForKind, new EnumMap<StringKind, String>(StringKind.class));
    }

    PatternStringifier(EnumMap<StringKind, Pattern> patternForKind,
            EnumMap<StringKind, String> messageFormats) {
        this.patternForKind = new EnumMap<>(patternForKind);
        this.messageFormats = new EnumMap<>(messageFormats);
    }

    @Override
    public String apply(StringKind kind, Object item) {
        Pattern pattern = patternForKind.get(kind);
        if (pattern == null) {
            switch(kind) {
                case TEXT_TO_INSERT :
                    return item.toString();
                case DISPLAY_DIFFERENTIATOR :
                case DISPLAY_NAME :
                case INSERT_PREFIX :
                case SORT_TEXT :
                    return null;
                default :
                    throw new AssertionError(kind);
            }
        }
        String result = item.toString();
        Matcher m = pattern.matcher(result);
        if (m.lookingAt() && m.groupCount() > 0) {
            result = m.group(1);
        } else {
            if (kind != StringKind.DISPLAY_NAME) {
                result = null;
            }
        }
        if (result != null) {
            String fmt = messageFormats.get(kind);
            if (fmt != null) {
                result = MessageFormat.format(fmt, result);
            }
        }
        return result;
    }

}

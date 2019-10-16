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
package org.nemesis.adhoc.mime.types;

import java.nio.file.Path;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;

/**
 *
 * @author Tim Boudreau
 */
public class InvalidMimeTypeRegistrationException extends Exception {

    private final String ext;
    private final String mimeType;
    private final Reason reason;

    InvalidMimeTypeRegistrationException(String ext, String mimeType, Reason reason) {
        super(reason + ": " + ext + ", " + mimeType);
        this.ext = ext;
        this.mimeType = mimeType;
        this.reason = reason;
    }

    public String getLocalizedMessage() {
        return reason.stringValue(ext, mimeType);
    }

    @Messages({"# {0} - the MIME type",
        "COMMON_MIME_TYPE=Cannot override built-in mime registrations - {0} is a common mime type",
        "# {0} - the file extension",
        "COMMON_EXTENSION=Cannot override file extensions the IDE already supports, such as '{0}'",
        "# {0} - the MIME type",
        "INVALID_MIME_TYPE=Not an Antlr-module generated MIME type: {0}",
        "# {0} - the file extension",
        "INVALID_EXTENSION=Not a usable file extension: '{0}'",
        "# {0} - the MIME type",
        "EMPTY_MIME_TYPE=Empty MIME type: '{0}'",
        "# {0} - the extension",
        "EMPTY_EXTENSION=Empty file extension: '{0}'",
        "# {0} - the file extension",
        "# {1} - the grammar file name",
        "IN_USE={0} is already registered to {1}",
        "# {0} - the file extension",
        "ILLEGAL_EXTENSION_CHARACTERS=Illegal characters in extension: {0}",
        "VALUE_TOO_LONG=Value too long"
    })
    public enum Reason {
        COMMON_MIME_TYPE,
        COMMON_EXTENSION,
        INVALID_MIME_TYPE,
        INVALID_EXTENSION,
        EMPTY_MIME_TYPE,
        EMPTY_EXTENSION,
        IN_USE,
        ILLEGAL_EXTENSION_CHARACTERS,
        VALUE_TOO_LONG
        ;

        public String stringValue(String ext, String mime) {
            String arg = ordinal() % 2 == 0 ? mime : ext;
            switch (this) {
                case IN_USE:
                    Path grammarFile = AdhocMimeTypes.grammarFilePathForMimeType(mime);
                    String raw = AdhocMimeTypes.rawFileName(grammarFile);
                    return Bundle.IN_USE(ext, raw);
                case VALUE_TOO_LONG :
                    return Bundle.VALUE_TOO_LONG();
                default:
                    return NbBundle.getMessage(
                            InvalidMimeTypeRegistrationException.class, name(), arg);
            }
        }
    }
}

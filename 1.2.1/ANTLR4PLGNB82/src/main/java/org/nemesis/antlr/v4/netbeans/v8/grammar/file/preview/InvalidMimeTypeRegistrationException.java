/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nemesis.antlr.v4.netbeans.v8.grammar.file.preview;

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

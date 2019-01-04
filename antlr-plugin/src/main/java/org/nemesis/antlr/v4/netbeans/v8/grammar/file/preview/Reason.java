package org.nemesis.antlr.v4.netbeans.v8.grammar.file.preview;

/**
 * There are a lot of ways we can wind up initializing and registering a
 * grammar; many of them happen during startup, via component load or
 * DataObject initialization or something else.  We don't want to be furiously
 * compiling when the main window is not open yet, so each code path that wants
 * to invoke javac and run Antlr supplies a reason;  some of these result in
 * an "unparsed" ParseTreeProxy and defer doing anything to after the window
 * system has settled down.
 *
 * @author Tim Boudreau
 */
public enum Reason {

    OPENING_PREVIEW,
    DATA_OBJECT_INIT,
    OPEN_DATA_OBJECT,
    CREATE_SAMPLE_FILE,
    ERROR_HIGHLIGHTING,
    UPDATE_LANGUAGE_FACTORY,
    UPDATE_PREVIEW,
    CREATE_LEXER,
    POST_INIT,
    REPARSE,
    UNIT_TEST;

    boolean neverAgoodReason() {
        return this == DATA_OBJECT_INIT;
    }

    boolean shouldParseEvenDuringStartup() {
        switch(this) {
            case CREATE_SAMPLE_FILE :
            case CREATE_LEXER :
                return true;
            default :
                return false;
        }
    }
}

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
package org.nemesis.antlr.language.formatting.config;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.prefs.BackingStoreException;
import java.util.prefs.PreferenceChangeEvent;
import java.util.prefs.PreferenceChangeListener;
import java.util.prefs.Preferences;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.tree.RuleNode;
import org.nemesis.antlr.ANTLRv4Lexer;
import static org.nemesis.antlr.ANTLRv4Lexer.VOCABULARY;
import static org.nemesis.antlr.ANTLRv4Lexer.modeNames;
import org.nemesis.antlr.ANTLRv4Parser;
import org.nemesis.antlr.language.formatting.AntlrCounters;
import org.nemesis.antlr.language.formatting.AntlrCriteria;
import org.nemesis.antlr.language.formatting.G4FormatterStub;
import org.nemesis.antlrformatting.spi.AntlrFormatterProvider;
import org.openide.util.Exceptions;

/**
 *
 * @author Tim Boudreau
 */
public class AntlrFormatterConfig {

    public static final String KEY_COLON_HANDLING = "colonHandling";
    public static final String KEY_FLOATING_INDENT = "floatingIndent";
    public static final String KEY_MAX_LINE = "maxLineLength";
    public static final String KEY_INDENT = "indent";
    public static final String KEY_WRAP = "wrap";
    public static final String KEY_BLANK_LINE_BEFORE_RULES = "blankLineBeforeRules";
    public static final String KEY_REFLOW_LINE_COMMENTS = "reflowLineComments";
    public static final String KEY_SPACES_INSIDE_PARENS = "spacesInsideParens";
    public static final int DEFAULT_MAX_LINE = 60;
    public static final boolean DEFAULT_WRAP = false;
    public static final boolean DEFAULT_REFLOW_LINE_COMMENTS = false;
    public static final boolean DEFAULT_SPACES_INSIDE_PARENS = false;
    public static final boolean DEFAULT_FLOATING_INDENT = false;
    public static final int DEFAULT_INDENT = 4;
    public static final ColonHandling DEFAULT_COLON_HANDLING
            = ColonHandling.INLINE;
    public static final boolean DEFAULT_BLANK_LINE_BEFORE_RULES = false;

    public static String[] BOOLEAN_KEYS = new String[] {
        KEY_WRAP,
        KEY_FLOATING_INDENT,
        KEY_REFLOW_LINE_COMMENTS,
        KEY_BLANK_LINE_BEFORE_RULES,
        KEY_SPACES_INSIDE_PARENS
    };
    private final L l = new L();
    private final Preferences config;
    private PropertyChangeSupport supp;

    public AntlrFormatterConfig(Preferences config) {
        this.config = config;
    }

    public Preferences preferences() {
        return config;
    }

    public void setIndent(int val) {
        if (val != getIndent()) {
            config.putInt(KEY_INDENT, val);
        }
    }

    public int getIndent() {
        return config.getInt(KEY_INDENT, DEFAULT_INDENT);
    }

    public void setMaxLineLength(int val) {
        if (val != getMaxLineLength()) {
            config.putInt(KEY_MAX_LINE, val);
        }
    }

    public int getMaxLineLength() {
        return config.getInt(KEY_MAX_LINE, DEFAULT_MAX_LINE);
    }

    public boolean isSpacesInsideParens() {
        return config.getBoolean(KEY_SPACES_INSIDE_PARENS, DEFAULT_SPACES_INSIDE_PARENS);
    }

    public void setSpacesInsideParens(boolean val) {
        if (val != isSpacesInsideParens()) {
            config.putBoolean(KEY_SPACES_INSIDE_PARENS, val);
        }
    }

    public boolean isBlankLineBeforeRules() {
        return config.getBoolean(KEY_BLANK_LINE_BEFORE_RULES, DEFAULT_BLANK_LINE_BEFORE_RULES);
    }

    public void setBlankLineBeforeRules(boolean val) {
        if (val != isBlankLineBeforeRules()) {
            config.putBoolean(KEY_BLANK_LINE_BEFORE_RULES, val);
        }
    }

    public boolean isWrap() {
        return config.getBoolean(KEY_WRAP, DEFAULT_WRAP);
    }

    public void setWrap(boolean wrap) {
        if (wrap != isWrap()) {
            config.putBoolean(KEY_WRAP, wrap);
        }
    }

    public void setReflowLineComments(boolean val) {
        if (isReflowLineComments() != val) {
            config.putBoolean(KEY_REFLOW_LINE_COMMENTS, val);
        }
    }

    public boolean isReflowLineComments() {
        return config.getBoolean(KEY_REFLOW_LINE_COMMENTS, DEFAULT_REFLOW_LINE_COMMENTS);
    }

    public void setFloatingIndent(boolean val) {
        if (val != isFloatingIndent()) {
            config.putBoolean(KEY_FLOATING_INDENT, val);
        }
    }

    public boolean isFloatingIndent() {
        return config.getBoolean(KEY_FLOATING_INDENT, DEFAULT_FLOATING_INDENT);
    }

    public ColonHandling getColonHandling() {
        int val = config.getInt(KEY_COLON_HANDLING, DEFAULT_COLON_HANDLING.ordinal());
        return ColonHandling.values()[val];
    }

    public void setColonHandling(ColonHandling h) {
        ColonHandling old = getColonHandling();
        if (h != old) {
            config.putInt(KEY_COLON_HANDLING, h.ordinal());
        }
    }

    public String toString() {
        return getClass().getSimpleName() + "("
                + " colonHandling=" + getColonHandling()
                + " wrap=" + isWrap()
                + " blankLineBeforeRules=" + isBlankLineBeforeRules()
                + " floatingIndent=" + isFloatingIndent()
                + " spacesInsideParens=" + isSpacesInsideParens()
                + " reflowLineComments=" + isReflowLineComments()
                + " indent=" + getIndent()
                + " maxLineLength=" + getMaxLineLength()
                + ")";
    }

    class L implements PreferenceChangeListener {

        @Override
        public void preferenceChange(PreferenceChangeEvent evt) {
            if (supp != null) {
                supp.firePropertyChange(evt.getKey(), null, evt.getNewValue());
                try {
                    evt.getNode().flush();
                } catch (BackingStoreException ex) {
                    Exceptions.printStackTrace(ex);
                }
            }
        }
    }

    private synchronized void addNotify() {
        if (supp == null) {
            supp = new PropertyChangeSupport(this);
            config.addPreferenceChangeListener(l);
        }
    }

    private synchronized void removeNotify() {
        if (supp != null) {
            supp = null;
            config.removePreferenceChangeListener(l);
        }
    }

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        if (supp == null) {
            addNotify();
        }
        supp.addPropertyChangeListener(listener);
    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
        supp.removePropertyChangeListener(listener);
        if (supp.getPropertyChangeListeners().length == 0) {
            removeNotify();
        }
    }

    public void addPropertyChangeListener(String propertyName, PropertyChangeListener listener) {
        if (supp == null) {
            addNotify();
        }
        supp.addPropertyChangeListener(propertyName, listener);
    }

    public void removePropertyChangeListener(String propertyName, PropertyChangeListener listener) {
        supp.removePropertyChangeListener(propertyName, listener);
        if (supp.getPropertyChangeListeners().length == 0) {
            removeNotify();
        }
    }

    private static ANTLRv4Lexer lexerFor(CharStream stream) {
        return new ANTLRv4Lexer(stream);
    }

    private static RuleNode rn(Lexer lexer) {
        CommonTokenStream str = new CommonTokenStream(lexer);
        ANTLRv4Parser parser = new ANTLRv4Parser(str);
        return parser.grammarFile();
    }

    public static String formatPreviewText(Preferences prefs, String previewText) {
        G4FormatterStub stub = new G4FormatterStub();
        AntlrFormatterProvider prov = stub.toFormatterProvider("text/x-g4", AntlrCounters.class,
                VOCABULARY, modeNames, AntlrFormatterConfig::lexerFor,
                AntlrCriteria.ALL_WHITESPACE, ANTLRv4Parser.ruleNames, AntlrFormatterConfig::rn);
        return prov.reformat(previewText, 0, 0, prefs).text();
    }
}

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
package org.nemesis.antlrformatting.grammarfile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.Objects;
import java.util.Properties;
import java.util.prefs.Preferences;
import javax.swing.ComboBoxModel;
import javax.swing.DefaultComboBoxModel;
import javax.swing.event.ChangeListener;
import org.nemesis.antlrformatting.grammarfile.AntlrFormatterSettings.NewlineStyle;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.ChangeSupport;
import org.openide.util.Exceptions;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.openide.util.RequestProcessor;

/**
 * Settings for Antlr Formatter.
 *
 * @author Tim Boudreau
 */
public class AntlrFormatterSettings {

    private static final String SFS_PATH = "antlr/formatting.properties";
    private int indentSize = 4;
    private boolean newlineAfterColon = true;
    private int wrapPoint = 80;
    private ChangeSupport supp;
    private NewlineStyle newlineStyle = NewlineStyle.ALWAYS;
    private boolean spacesInsideParentheses = true;
    private boolean wrapLines = true;
    private boolean reflowBlockComments = false;

    private static Reference<AntlrFormatterSettings> INSTANCE;

    public static synchronized AntlrFormatterSettings getDefault() {
        AntlrFormatterSettings settings = INSTANCE == null ? null : INSTANCE.get();
        if (settings == null) {
            settings = load(true);
            INSTANCE = new WeakReference<>(settings);
        }
        return settings;
    }

    public AntlrFormatterSettings() {

    }

    private AntlrFormatterSettings(AntlrFormatterSettings orig) {
        this.indentSize = orig.indentSize;
        this.newlineAfterColon = orig.newlineAfterColon;
        this.wrapPoint = orig.wrapPoint;
        this.newlineStyle = orig.newlineStyle;
        this.wrapLines = orig.wrapLines;
    }

    private AntlrFormatterSettings(Properties props) {
        indentSize = Integer.parseInt(props.getProperty("indentSize", "4"));
        this.wrapPoint = Integer.parseInt(props.getProperty("wrapPoint", "80"));
        this.newlineAfterColon = Boolean.parseBoolean(props.getProperty("newlineAfterColon", "true"));
        this.newlineStyle = NewlineStyle.valueOf(props.getProperty("newlineStyle", NewlineStyle.ALWAYS.name()));
        this.spacesInsideParentheses = Boolean.parseBoolean(props.getProperty("spacesInsideParentheses", "true"));
        this.reflowBlockComments = Boolean.parseBoolean(props.getProperty("reflowBlockComments", "false"));
    }

    AntlrFormatterSettings(Preferences props) {
        indentSize = props.getInt("indentSize", 4);
        this.wrapPoint = props.getInt("wrapPoint", 80);
        this.newlineAfterColon = props.getBoolean("newlineAfterColon", true);
        this.newlineStyle = NewlineStyle.valueOf(props.get("newlineStyle", NewlineStyle.ALWAYS.name()));
        this.spacesInsideParentheses = props.getBoolean("spacesInsideParentheses", true);
        this.reflowBlockComments = props.getBoolean("reflowBlockComments", false);
    }

    public AntlrFormatterSettings copy() {
        return new AntlrFormatterSettings(this);
    }

    Properties toProperties() {
        Properties result = new Properties();
        result.setProperty("indentSize", Integer.toString(indentSize));
        result.setProperty("wrapPoint", Integer.toString(wrapPoint));
        result.setProperty("newlineAfterColon", Boolean.toString(newlineAfterColon));
        result.setProperty("newlineStyle", newlineStyle.name());
        result.setProperty("spacesInsideParentheses", Boolean.toString(spacesInsideParentheses));
        result.setProperty("reflowBlockComments", Boolean.toString(reflowBlockComments));
        return result;
    }

    public boolean isReflowBlockComments() {
        return reflowBlockComments;
    }

    public AntlrFormatterSettings setReflowBlockComments(boolean val) {
        if (val != reflowBlockComments) {
            reflowBlockComments = val;
            fire();
        }
        return this;
    }

    public boolean isWrapLines() {
        return wrapLines;
    }

    public AntlrFormatterSettings setWrapLines(boolean val) {
        if (wrapLines != val) {
            this.wrapLines = val;
            fire();
        }
        return this;
    }

    public int getWrapPoint() {
        return wrapPoint;
    }

    public int getIndentSize() {
        return indentSize;
    }

    public boolean isNewlineAfterColon() {
        return newlineAfterColon;
    }

    public NewlineStyle getNewlineStyle() {
        return newlineStyle;
    }

    public boolean isSpacesInsideParentheses() {
        return spacesInsideParentheses;
    }

    public AntlrFormatterSettings setSpacesInsideParentheses(boolean spaces) {
        if (this.spacesInsideParentheses != spaces) {
            this.spacesInsideParentheses = spaces;
            fire();
        }
        return this;
    }

    public AntlrFormatterSettings setBlankLineAfterRule(NewlineStyle newlineStyle) {
        if (this.newlineStyle != newlineStyle) {
            this.newlineStyle = newlineStyle;
            fire();
        }
        return this;
    }

    public AntlrFormatterSettings setNewlineAfterColon(boolean val) {
        if (val != newlineAfterColon) {
            this.newlineAfterColon = val;
            fire();
        }
        return this;
    }

    public AntlrFormatterSettings setIndentSize(int indentSize) {
        if (indentSize <= 0) {
            throw new IllegalArgumentException("Bad indent size: " + indentSize);
        }
        if (this.indentSize != indentSize) {
            this.indentSize = indentSize;
            fire();
        }
        return this;
    }

    public AntlrFormatterSettings setWrapPoint(int wrapPoint) {
        if (wrapPoint < 0) {
            throw new IllegalArgumentException("Bad wrap point: " + wrapPoint);
        }
        if (this.wrapPoint != wrapPoint) {
            this.wrapPoint = wrapPoint;
            fire();
        }
        return this;
    }

    public void addChangeListener(ChangeListener lis) {
        if (supp == null) {
            supp = new ChangeSupport(this);
        }
        supp.addChangeListener(lis);
    }

    public void removeChangeListener(ChangeListener lis) {
        if (supp == null) {
            return;
        }
        supp.removeChangeListener(lis);
    }

    private void fire() {
        if (supp != null) {
            supp.fireChange();
        }
    }

    void save(OutputStream out) throws IOException {
        Properties props = toProperties();
        props.store(out, null);
    }

    @Override
    public String toString() {
        return "AntlrFormatterSettings{" + "indentSize=" + indentSize
                + ", newlineAfterColon=" + newlineAfterColon + ", wrapPoint="
                + wrapPoint + ", newlineStyle=" + newlineStyle
                + ", spacesInsideParentheses=" + spacesInsideParentheses
                + ", wrapLines=" + wrapLines + '}';
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 79 * hash + this.indentSize;
        hash = 79 * hash + (this.newlineAfterColon ? 1 : 0);
        hash = 79 * hash + (this.reflowBlockComments ? 1 : 0);
        hash = 79 * hash + this.wrapPoint;
        hash = 79 * hash + Objects.hashCode(this.newlineStyle);
        hash = 79 * hash + (this.spacesInsideParentheses ? 1 : 0);
        hash = 79 * hash + (this.wrapLines ? 1 : 0);
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
        final AntlrFormatterSettings other = (AntlrFormatterSettings) obj;
        if (this.indentSize != other.indentSize) {
            return false;
        }
        if (this.newlineAfterColon != other.newlineAfterColon) {
            return false;
        }
        if (this.reflowBlockComments != other.reflowBlockComments) {
            return false;
        }
        if (this.wrapPoint != other.wrapPoint) {
            return false;
        }
        if (this.spacesInsideParentheses != other.spacesInsideParentheses) {
            return false;
        }
        if (this.wrapLines != other.wrapLines) {
            return false;
        }
        if (this.newlineStyle != other.newlineStyle) {
            return false;
        }
        return true;
    }

    public static AntlrFormatterSettings load() {
        return load(false);
    }

    public AntlrFormatterSettings save() {
        FileObject saveTo = FileUtil.getConfigFile(SFS_PATH);
        try {
            if (saveTo == null) {
                saveTo = FileUtil.createData(FileUtil.getConfigRoot(), SFS_PATH);
            }
            try (OutputStream out = saveTo.getOutputStream()) {
                save(out);
            }
        } catch (IOException ioe) {
            Exceptions.printStackTrace(ioe);
        }
        return this;
    }

    public static AntlrFormatterSettings load(boolean listen) {
        AntlrFormatterSettings result;
        FileObject fo = FileUtil.getConfigFile(SFS_PATH);
        if (fo != null) {
            try {
                Properties props = new Properties();
                try (InputStream in = fo.getInputStream()) {
                    props.load(in);
                }
                result = new AntlrFormatterSettings(props);
                return result;
            } catch (Exception ex) {
                Exceptions.printStackTrace(ex);
                result = new AntlrFormatterSettings();
            }
        } else {
            result = new AntlrFormatterSettings();
        }
        if (listen) {
            final AntlrFormatterSettings res = result;
            RequestProcessor.Task task = RequestProcessor.getDefault().create(() -> {
                res.save();
            });
            result.addChangeListener(ce -> {
                task.schedule(400);
            });
        }
        return result;
    }

    @Messages({
        "NEVER=Never",
        "ALWAYS=Always",
        "IF_COMPLEX=If Complex"
    })
    public enum NewlineStyle {
        NEVER,
        ALWAYS,
        IF_COMPLEX;

        public boolean isDoubleNewline() {
            switch (this) {
                case NEVER:
                    return false;
                case ALWAYS:
                case IF_COMPLEX:
                    return true;
                default:
                    throw new AssertionError(this);
            }
        }

        public static ComboBoxModel<NewlineStyle> listModel() {
            DefaultComboBoxModel<NewlineStyle> mdl = new DefaultComboBoxModel<>();
            for (NewlineStyle style : values()) {
                mdl.addElement(style);
            }
            mdl.setSelectedItem(ALWAYS);
            return mdl;
        }

        public String toString() {
            return NbBundle.getMessage(AntlrFormatterSettings.class, name());
        }
    }
}

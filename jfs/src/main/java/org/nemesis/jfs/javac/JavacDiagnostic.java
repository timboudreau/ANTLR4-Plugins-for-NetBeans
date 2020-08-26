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
package org.nemesis.jfs.javac;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import org.nemesis.jfs.JFSFileObject;

/**
 * Wrapper for Javac's Diagnostic class, to avoid holding references to any
 * objects from a compile that could keep the compile trees in memory.
 *
 * @author Tim Boudreau
 */
public final class JavacDiagnostic {

    private final String path;
    private final String sourceCode;
    private final Diagnostic.Kind kind;
    private final long lineNumber;
    private final long columnNumber;
    private final long position;
    private final long endPosition;
    private final String message;
    private final Path relativePath;

    JavacDiagnostic(String path, String sourceCode, Diagnostic.Kind kind, long lineNumber, long columnNumber, long position, long endPosition, String message, Path sourceRoot) {
        this.path = path;
        this.sourceCode = sourceCode;
        this.kind = kind;
        this.lineNumber = lineNumber;
        this.columnNumber = columnNumber;
        this.position = position;
        this.endPosition = endPosition;
        this.message = message;
        this.relativePath = sourceRoot.relativize(Paths.get(path));
    }

    public static JavacDiagnostic create(String sourcePath, Path sourceRoot, Diagnostic<? extends JavaFileObject> diag) {
        return new JavacDiagnostic(sourcePath, diag.getCode(), diag.getKind(), diag.getLineNumber(),
                diag.getColumnNumber(), diag.getPosition(), diag.getEndPosition(),
                diag.getMessage(Locale.getDefault()), sourceRoot);
    }

    public String context(JFSFileObject fo) {
        StringBuilder sb = new StringBuilder();
        try {
            CharSequence txt = fo.getCharContent(true);
            int nlcount = 0;
            for (int start = (int) position; start >= 0 && start < txt.length(); start--) {
                char c = txt.charAt(start);
                if (c == '\n') {
                    if (++nlcount >= 2) {
                        break;
                    }
                }
                sb.insert(0, c);
            }
            int tail;
            for (tail = (int) position + 1; tail >= 0 && tail < txt.length(); tail++) {
                char c = txt.charAt(tail);
                if (c == '\n') {
                    break;
                }
                sb.append(c);
            }
            if (sb.length() > 0) {
                int len = (int) (columnNumber + (endPosition - position));
                if (len > 0) {
                    char[] c = new char[len];
                    Arrays.fill(c, 0, (int) columnNumber, ' ');
                    Arrays.fill(c, (int) columnNumber, c.length, '^');
                    sb.append('\n').append(c);
                }
                for (int i = tail; i >= 0 && i < txt.length(); i++) {
                    sb.append(txt.charAt(i));
                    if (txt.charAt(i) == '\n') {
                        break;
                    }
                }
            }
        } catch (IOException ex) {
            Logger.getLogger(JavacDiagnostic.class.getName()).log(Level.INFO,
                    "File " + fo.path() + " may have been deleted before "
                    + "trying to print diagnostics", ex);
        }
        return sb.toString();
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 71 * hash + Objects.hashCode(this.kind);
        hash = 71 * hash + (int) (this.position ^ (this.position >>> 32));
        hash = 71 * hash + (int) (this.endPosition ^ (this.endPosition >>> 32));
        hash = 71 * hash + Objects.hashCode(this.message);
        return hash;
    }

    public Throwable toThrowable() {
        return new IOException(toString());
    }

    public String message() {
        return message;
    }

    public String sourceCode() {
        return sourceCode;
    }

    public Diagnostic.Kind kind() {
        return kind;
    }

    public long lineNumber() {
        return lineNumber;
    }

    public long columnNumber() {
        return columnNumber;
    }

    public long position() {
        return position;
    }

    public long endPosition() {
        return endPosition;
    }

    public Path sourceRootRelativePath() {
        return relativePath;
    }

    public Path sourcePath() {
        return Paths.get(path);
    }

    @Override
    public String toString() {
        return kind.name().toLowerCase() + ": " + path
                + "(" + lineNumber + ":" + columnNumber + "/" + position + ":"
                + endPosition + ") " + message + "\n"
                + sourceCode;
    }

    public boolean isError() {
        return kind == Diagnostic.Kind.ERROR;
    }

    public String fileName() {
        int ix = path.lastIndexOf(File.separatorChar);
        if (File.separatorChar != '/' && ix < 0) {
            ix = path.lastIndexOf('/');
        }
        if (ix > 0 && ix < path.length() - 1) {
            return path.substring(ix + 1);
        }
        return path;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null) {
            return false;
        } else if (o == this) {
            return true;
        } else if (o instanceof JavacDiagnostic) {
            JavacDiagnostic other = (JavacDiagnostic) o;
            return kind == other.kind && position == other.position
                    && endPosition == other.endPosition && fileName().equals(
                            other.fileName()) && Objects.equals(message, other.message);
        }
        return false;
    }
}

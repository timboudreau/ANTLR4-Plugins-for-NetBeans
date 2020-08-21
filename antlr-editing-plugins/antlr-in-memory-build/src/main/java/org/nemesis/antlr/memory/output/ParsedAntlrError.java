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
package org.nemesis.antlr.memory.output;

import com.mastfrog.util.collections.IntIntMap;
import com.mastfrog.util.path.UnixPath;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Wraps an Antlr emitted error, so that if Antlr classes were loaded in an
 * isolating classloader, we do not leak types that were created by it and
 * inadvertently keep it alive.
 *
 * @author Tim Boudreau
 */
public final class ParsedAntlrError implements Comparable<ParsedAntlrError> {

    private final boolean error;
    private final int errorCode;
    private final Path path;
    private final int lineNumber;
    private final int lineOffset;
    private final String message;
    private Object info;
    int fileOffset = -1;
    int length = -1;

    public ParsedAntlrError(boolean error, int errorCode, Path path, int lineNumber, int lineOffset, String message) {
        this.error = error;
        this.errorCode = errorCode;
        this.path = path;
        this.lineNumber = lineNumber;
        this.lineOffset = lineOffset;
        this.message = message;
    }

    /**
     * An ID which identifies this error such that in the same document, if it
     * has not been edited above the position of this error, the same error will
     * have the same id across multiple parses.
     *
     * @return An error code
     */
    public String id() {
        return fileOffset + ":" + endOffset() + ":" + (message == null ? 0 : message.hashCode());
    }

    public <T> ParsedAntlrError setInfo(T info) {
        if (this.info != null) {
            throw new IllegalStateException("Set info twice");
        }
        this.info = info;
        return this;
    }

    public <T> T info(Class<T> expectedType) {
        return info == null ? null : expectedType.isInstance(info)
                ? expectedType.cast(info) : null;
    }

    /**
     * Antlr syntax errors typically only have a line and line offset; where
     * they can be computed, we add character position info after creation.
     *
     * @param offset The offset
     * @param length The length of the region in error
     */
    public void setFileOffsetAndLength(int offset, int length) {
        this.fileOffset = offset;
        this.length = length;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 73 * hash + (this.error ? 1 : 0);
        hash = 73 * hash + this.errorCode;
        hash = 73 * hash + Objects.hashCode(this.path);
        hash = 73 * hash + this.lineNumber;
        hash = 73 * hash + this.lineOffset;
        hash = 73 * hash + Objects.hashCode(this.message);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (!(obj instanceof ParsedAntlrError)) {
            return false;
        }
        final ParsedAntlrError other = (ParsedAntlrError) obj;
        if (this.error != other.error) {
            return false;
        } else if (this.errorCode != other.errorCode) {
            return false;
        } else if (this.lineNumber != other.lineNumber) {
            return false;
        } else if (this.lineOffset != other.lineOffset) {
            return false;
        } else if (!Objects.equals(this.message, other.message)) {
            return false;
        }
        return Objects.equals(this.path, other.path);
    }

    @Override
    public String toString() {
        boolean hasSaneLineInfo = lineNumber >= 0 && lineOffset >= 0;
        StringBuilder sb = new StringBuilder();
        sb.append(path.toString())
                .append('(');
        // Emulate the behavior of Antlr vs-2005 formatting - negative line
        // info gives you (,) in the message
        if (hasSaneLineInfo) {
            sb.append(lineNumber);
        }
        sb.append(',');
        if (hasSaneLineInfo) {
            sb.append(lineOffset);
        }
        sb.append(')')
                .append(" : ").append(error ? "error" : "warning").append(' ').append(errorCode)
                .append(" : ").append(message);
        if (fileOffset != -1) {
            sb.append(" [").append(fileOffset).append(':').append(length).append(']');
        }
        return sb.toString();
    }

    public boolean hasFileOffset() {
        return fileOffset != -1;
    }

    public int fileOffset() {
        if (fileOffset == -1) {
            throw new IllegalStateException("File offset not yet computed. Call computeFileOffsets()");
        }
        return fileOffset;
    }

    public int endOffset() {
        return fileOffset() + length;
    }

    public int length() {
        return length;
    }

    public boolean isError() {
        return error;
    }

    public int code() {
        return errorCode;
    }

    public int lineNumber() {
        return lineNumber;
    }

    public int lineOffset() {
        return lineOffset;
    }

    public String message() {
        return message;
    }

    public Path path() {
        return path;
    }

    /**
     * Errors that are constructed reflectively using ReflectiveValue will have
     * this set if there was an offendingToken field on the original error.
     * Errors that do not have this set (command-line output does not provide
     * it) will need to read the input file and compute the character offset.
     * This method ensures each file is read only once when doing this for a
     * group of related errors.
     *
     * @param errors
     */
    public static void computeFileOffsets(Iterable<? extends ParsedAntlrError> errors) {
        computeFileOffsets(errors, ParsedAntlrError::readFile);
    }

    private static List<String> readFile(Path file) {
        try {
            if (Files.exists(file)) {
                return Files.readAllLines(file);
            }
        } catch (IOException ex) {
            Logger.getLogger(ParsedAntlrError.class.getName()).log(Level.WARNING,
                    "Failed to read " + file, ex);
        }
        return Collections.emptyList();
    }

    /**
     * Antlr errors by default do not have file offsets, only line number and
     * position in line; this method allows for efficiently bulk updating the
     * file offset information in a collection of errors at once - offsets are
     * needed for error highlighting.
     *
     * @param errors A collection of errors
     * @param fileReader A function that will fetch the lines of a file.
     */
    public static void computeFileOffsets(Iterable<? extends ParsedAntlrError> errors, Function<Path, List<String>> fileReader) {
        // Cache the file info so we only read and split it once
        // A map to cache the start position of each line
        Map<Path, IntIntMap> lineOffsets = new HashMap<>(5);
//        Map<Path, IntIntMap> lineLengths = new HashMap<>(5);
        // A map to cache the lines of the file
        Map<Path, List<String>> linesForPath = new HashMap<>(5);
        for (ParsedAntlrError err : errors) {
            if (err.fileOffset == -1) {
                Path path = err.path();
                List<String> lines = linesForPath.get(path);
                IntIntMap map;
                if (lines == null) {
                    // Build our caches
                    lines = fileReader.apply(UnixPath.get(path));
                    linesForPath.put(path, lines);
                    map = IntIntMap.create(lines.size());
                    lineOffsets.put(path, map);
                    int pos = 0;
                    for (int i = 0; i < lines.size(); i++) {
                        String line = lines.get(i);
                        int end = pos + line.length() + 1;
                        map.put(i, pos);
                        pos += end;
                    }
                } else {
                    map = lineOffsets.get(path);
                }
                // Empty file, something wrong, and we dare not set an error
                // endpoint > 0 in an empty document
                if (lines.isEmpty() || lines.size() == 1 && lines.get(0).isEmpty()) {
                    err.fileOffset = 0;
                    err.length = 0;
                    continue;
                }
                int eof = map.get(map.greatestKey())
                        + lines.get(lines.size() - 1).length();
                if (err.lineNumber < 0 || !map.containsKey(err.lineNumber)) {
                    // A few Antlr errors output garbage line offsets, like left recursion -
                    // finding and dealing with them slowly in MemoryTool
                    err.fileOffset = 0;
                    err.length = Math.min(1, eof);
                } else {
                    // Find the start character index of the line in question
                    int lineStart = map.get(err.lineNumber);

                    err.fileOffset = lineStart + err.lineOffset;
                    String line = lines.get(err.lineNumber);
                    if (err.lineOffset < line.length()) {
                        err.length = Math.max(1, line.length() - lineStart);
                    } else {
                        err.length = 1;
                        if (err.length + err.fileOffset > eof) {
                            err.fileOffset = Math.max(0, eof - 2);
                        }
                    }
                }
            }
        }
    }

    @Override
    public int compareTo(ParsedAntlrError o) {
        if (o == this) {
            return 0;
        }
        int result = lineNumber > o.lineNumber ? 1 : lineNumber == o.lineNumber ? 0 : -1;
        if (result == 0) {
            result = lineOffset > o.lineOffset ? 1 : lineOffset == o.lineOffset ? 0 : -1;
        }
        if (result == 0) {
            result = code() > o.code() ? 1 : code() == o.code() ? 0 : -1;
        }
        return result;
    }
}

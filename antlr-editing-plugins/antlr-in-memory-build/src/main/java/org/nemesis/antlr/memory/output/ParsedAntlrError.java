package org.nemesis.antlr.memory.output;

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
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final ParsedAntlrError other = (ParsedAntlrError) obj;
        if (this.error != other.error) {
            return false;
        }
        if (this.errorCode != other.errorCode) {
            return false;
        }
        if (this.lineNumber != other.lineNumber) {
            return false;
        }
        if (this.lineOffset != other.lineOffset) {
            return false;
        }
        if (!Objects.equals(this.message, other.message)) {
            return false;
        }
        if (!Objects.equals(this.path, other.path)) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(path.toString())
                .append('(').append(lineNumber).append(',').append(lineOffset).append(')')
                .append(" : ").append(error ? "error" : "warning").append(' ').append(errorCode)
                .append(" : ").append(message);
        if (fileOffset != -1) {
            sb.append(" [").append(fileOffset).append(':').append(length).append(']');
        }
        return sb.toString();
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

    public static void computeFileOffsets(Iterable<? extends ParsedAntlrError> errors, Function<Path, List<String>> fileReader) {
        Map<Path, List<String>> linesForPath = new HashMap<>(3);
        for (ParsedAntlrError err : errors) {
            if (err.fileOffset == -1) {
                Path path = err.path();
                List<String> lines = linesForPath.get(path);
                if (lines == null) {
                    lines = fileReader.apply(path);
                    linesForPath.put(path, lines);
                }
                System.out.println("FOUND " + lines.size() + " lines for " + path);
                for (int currLineNumber = 0; currLineNumber < lines.size(); currLineNumber++) {
                    String line = lines.get(currLineNumber);
                    if (currLineNumber == err.lineNumber()) {
                        err.fileOffset += err.lineOffset;
                        err.length = 1;
                        for (int i = err.lineOffset + 1; i < line.length(); i++) {
                            if (!Character.isWhitespace(line.charAt(i))) {
                                err.length++;
                            } else {
                                break;
                            }
                        }
                        break;
                    }
                    err.fileOffset += line.length();
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

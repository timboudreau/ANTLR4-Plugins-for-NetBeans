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
package org.nemesis.antlr.memory.tool;

import com.mastfrog.util.path.UnixPath;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.tools.JavaFileManager.Location;
import javax.tools.StandardLocation;
import static javax.tools.StandardLocation.CLASS_OUTPUT;
import static javax.tools.StandardLocation.SOURCE_OUTPUT;
import org.antlr.v4.codegen.CodeGenerator;
import org.antlr.v4.tool.Grammar;
import org.nemesis.antlr.stdio.ThreadMappedStdIO;
import org.nemesis.jfs.JFS;
import org.nemesis.jfs.JFSFileObject;

/**
 * Antlr does some unfortunate reflection-based stuff, which can wind up with it
 * trying to serialize Path instances which loop back on themselves, an entire
 * JFS, and other sorts of trash. So we have to keep objects MemoryTool needs
 * someplace where it can look them up if need be, but cannot find them via
 * fields on itself.
 *
 * @author Tim Boudreau
 */
final class ToolContext {

    private static final Map<MemoryTool, ToolContext> CONTEXTS
            = Collections.synchronizedMap(new WeakHashMap<>());

    final UnixPath dir;
    final JFS jfs;
    final Location inputLocation;
    final Location outputLocation;
    final PrintStream logStream;
    static final ThreadLocal<UnixPath> currentFile = new ThreadLocal<>();
    static final Logger LOG = Logger.getLogger(MemoryTool.class.getName());

    ToolContext(UnixPath dir, JFS jfs, Location inputLocation, Location outputLocation, PrintStream logStream) {
        if (dir == null) {
            throw new IllegalArgumentException("Null source dir for " + jfs);
        }
        this.dir = dir;
        this.jfs = jfs;
        this.inputLocation = inputLocation;
        this.outputLocation = outputLocation;
        this.logStream = logStream;
    }

    static boolean isLoggable(Level level) {
        return LOG.isLoggable(level);
    }

    void logExternal(Level level, String msg, Object arg) {
        // Ensure if console logger, it can actually access the real console
        ThreadMappedStdIO.bypass(()
                -> LOG.log(level, dir + ":" + jfs.id() + ":" + currentFile.get() + ": " + msg, arg));
    }

    void logExternal(Level level, String msg, Object[] args) {
        ThreadMappedStdIO.bypass(()
                -> LOG.log(level, dir + ":" + jfs.id() + ":" + currentFile.get() + ": " + msg, args));
    }

    void logExternal(Level level, String msg, Throwable thrown) {
        ThreadMappedStdIO.bypass(()
                -> LOG.log(level, dir + ":" + jfs.id() + ":" + currentFile.get() + ": " + msg, thrown));
    }

    void logExternal(Level level, Throwable thrown) {
        ThreadMappedStdIO.bypass(()
                -> LOG.log(level, dir + ":" + jfs.id(), thrown));
    }

    static ToolContext get(MemoryTool tool) {
        ToolContext result = CONTEXTS.get(tool);
        if (result == null) {
            throw new AssertionError("No context.  MemoryTool created directly"
                    + " rather than through ToolContext?");
        }
        return result;
    }

    static MemoryTool create(UnixPath dir, JFS jfs, Location inputLocation, Location outputLocation, PrintStream logStream, String... args) {
        ToolContext ctx = new ToolContext(dir, jfs, inputLocation, outputLocation, logStream);
        for (String a : args) {
            if (a.isEmpty()) {
                throw new IllegalArgumentException("Empty string would cause Tool constructor "
                        + "to throw an exception.  Arguments: " + Arrays.toString(args));
            }
        }
        MemoryTool result = new MemoryTool(ctx, args);
        CONTEXTS.put(result, ctx);
        result.initLog(ctx);
        return result;
    }

    UnixPath importedFilePath(Grammar g, MemoryTool tool) {
        UnixPath result = UnixPath.get(g.getOptionString("tokenVocab") + CodeGenerator.VOCAB_FILE_EXTENSION);
        if (jfs.get(inputLocation, result) != null) {
            return result;
        }
        if (g.tool.libDirectory != null) {
            result = UnixPath.get(g.tool.libDirectory).resolve(result);
            if (jfs.get(inputLocation, result) != null) {
                return result;
            }
        }
        if (result.startsWith(".")) {
            result = tool.resolveRelativePath(result.getFileName());
        }
        return result;
    }

    /**
     * Return a File descriptor for vocab file. Look in library or in -o output
     * path. antlr -o foo T.g4 U.g4 where U needs T.tokens won't work unless we
     * look in foo too. If we do not find the file in the lib directory then
     * must assume that the .tokens file is going to be generated as part of
     * this build and we have defined .tokens files so that they ALWAYS are
     * generated in the base output directory, which means the current directory
     * for the command line tool if there was no output directory specified.
     */
    JFSFileObject getImportedVocabFile(Grammar g, MemoryTool tool) throws FileNotFoundException {
        UnixPath path = importedFilePath(g, tool);
        Set<LoadAttempt> attempts = new HashSet<>(3);
        UnixPath fileNameOnly = path.getFileName();
        JFSFileObject fo = null;
        if (tool.hints != null) {
            UnixPath hintPath = tool.hints.firstPathForRawName(path.rawName(), CodeGenerator.VOCAB_FILE_EXTENSION);
            attempts.add(new LoadAttempt(hintPath, StandardLocation.SOURCE_OUTPUT));
            fo = jfs.get(StandardLocation.SOURCE_OUTPUT, path);
            if (fo == null) {
                attempts.add(new LoadAttempt(hintPath, StandardLocation.SOURCE_PATH));
                fo = jfs.get(StandardLocation.SOURCE_PATH, path);
            }
        }
        if (fo == null) {
            for (UnixPath p : new UnixPath[]{path, fileNameOnly}) {
                ToolContext ctx = ToolContext.get(tool);
                fo = tool.jfs().get(ctx.inputLocation, p);
                if (fo == null) {
                    fo = tool.getFO(ctx.inputLocation, p, attempts, ctx);
                }
                if (fo == null) {
                    fo = tool.getFO(SOURCE_OUTPUT, p, attempts, ctx);
                }
                if (fo == null) {
                    fo = tool.getFO(CLASS_OUTPUT, p, attempts, ctx);
                }
                if (fo != null) {
                    break;
                }
            }
        }
        if (fo == null) {
            throw new FileNotFoundException(attempts.toString());
        }
        return fo;
    }
}

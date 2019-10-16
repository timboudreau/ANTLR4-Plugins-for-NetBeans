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
package org.nemesis.antlr.live;

import com.mastfrog.function.throwing.ThrowingFunction;
import java.io.File;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import javax.swing.text.Document;
import org.netbeans.modules.parsing.api.ParserManager;
import org.netbeans.modules.parsing.api.ResultIterator;
import org.netbeans.modules.parsing.api.Source;
import org.netbeans.modules.parsing.api.UserTask;
import org.netbeans.modules.parsing.spi.Parser;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

/**
 * If you do work from inside the ParserManager UserTask callback, you will hold
 * the parser manager's lock until whatever work is being done completes, no
 * matter how long it takes. This is generally deadlock-prone, so this class
 * returns the parsing result or whatever is derived from it, outside the scope
 * of the UserTask. Also provides convenience methods for Path to file to
 * FileObject to Source to Collection-of-Source conversion, since that is simply
 * annoying.
 *
 * @author Tim Boudreau
 */
public final class ParsingUtils {

    static final ThrowingFunction<Parser.Result, Void> NO_CONVERT = new VoidFunction();

    public static FileObject toFileObject(Path path) {
        File f = FileUtil.normalizeFile(path.toFile());
        return FileUtil.toFileObject(f);
    }

    public static Path toPath(FileObject fo) {
        File f = FileUtil.toFile(fo);
        return f == null ? null : f.toPath();
    }

    public static void parse(Collection<Source> sources) throws Exception {
        parse(sources, NO_CONVERT);
    }

    public static void parse(Source src) throws Exception {
        parse(src, NO_CONVERT);
    }

    public static void parse(FileObject src) throws Exception {
        parse(src, NO_CONVERT);
    }

    public static void parse(Path src) throws Exception {
        parse(src, NO_CONVERT);
    }

    public static <T> T parse(Path src, ThrowingFunction<Parser.Result, T> func) throws Exception {
        FileObject fo = FileUtil.toFileObject(FileUtil.normalizeFile(src.toFile()));
        if (fo == null) {
            return null;
        }
        return parse(fo, func);
    }

    public static <T> T parse(FileObject src, ThrowingFunction<Parser.Result, T> func) throws Exception {
        return parse(Source.create(src), func);
    }

    public static void parse(Document src) throws Exception {
        parse(src, NO_CONVERT);
    }

    public static <T> T parse(Document src, ThrowingFunction<Parser.Result, T> func) throws Exception {
        return parse(Source.create(src), func);
    }

    public static <T> T parse(Source src, ThrowingFunction<Parser.Result, T> func) throws Exception {
        Collection<Source> sources = Collections.singleton(src);
        return parse(sources, func);
    }

    public static <T> T parse(Collection<Source> sources, ThrowingFunction<Parser.Result, T> func) throws Exception {
        UT<T> ut = new UT<>(func);
        ParserManager.parse(sources, ut);
        return ut.result();
    }

    private static final class UT<T> extends UserTask {

        private final ThrowingFunction<Parser.Result, T> convert;
        private T obj;
        private Exception thrown;
        private Error err;

        public UT(ThrowingFunction<Parser.Result, T> convert) {
            this.convert = convert;
        }

        @Override
        public void run(ResultIterator resultIterator) throws Exception {
            try {
                Parser.Result result = resultIterator.getParserResult();
                obj = convert.apply(result);
            } catch (Exception ex) {
                thrown = ex;
            } catch (Error error) {
                err = error;
            }
        }

        public T result() throws Exception {
            if (err != null) {
                throw err;
            } else if (thrown != null) {
                throw thrown;
            } else {
                return obj;
            }
        }
    }

    private ParsingUtils() {
        throw new AssertionError();
    }

    static final class VoidFunction implements ThrowingFunction<Parser.Result, Void> {

        @Override
        public Void apply(Parser.Result arg) throws Exception {
            return null;
        }
    }
}

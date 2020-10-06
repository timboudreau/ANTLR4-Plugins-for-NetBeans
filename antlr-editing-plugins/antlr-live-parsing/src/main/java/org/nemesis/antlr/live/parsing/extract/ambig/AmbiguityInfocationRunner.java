/*
 * Copyright 2016-2020 Tim Boudreau, Frédéric Yvon Vinet
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

package org.nemesis.antlr.live.parsing.extract.ambig;

import com.mastfrog.abstractions.Copyable;
import com.mastfrog.util.collections.IntList;
import com.mastfrog.util.path.UnixPath;
import com.mastfrog.util.strings.Strings;
import java.io.IOException;
import java.util.function.Consumer;
import java.util.function.Supplier;
import jdk.internal.util.Preconditions;
import org.antlr.runtime.misc.IntArray;
import org.antlr.v4.Tool;
import org.antlr.v4.runtime.ANTLRErrorListener;
import org.antlr.v4.runtime.Parser;
import org.nemesis.adhoc.mime.types.AdhocMimeTypes;
import org.nemesis.antlr.ANTLRv4Parser;
import org.nemesis.antlr.live.execution.InvocationRunner;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies;
import org.nemesis.antlr.live.parsing.extract.ambig.AmbiguityInfocationRunner.AmbingExtractorGenerationResult;
import org.nemesis.antlr.memory.AntlrGenerationResult;
import org.nemesis.extraction.Extraction;
import org.nemesis.jfs.JFS;
import org.nemesis.jfs.isolation.IsolationClassLoader;
import org.nemesis.jfs.isolation.IsolationClassLoaderBuilder;
import org.nemesis.jfs.javac.JFSCompileBuilder;
import org.stringtemplate.v4.Interpreter;

/**
 *
 * @author Tim Boudreau
 */
public class AmbiguityInfocationRunner extends InvocationRunner<AmbiguityProcessor, AmbingExtractorGenerationResult> {

    public AmbiguityInfocationRunner() {
        super(AmbiguityProcessor.class);
    }

    @Override
    protected AmbingExtractorGenerationResult onBeforeCompilation(ANTLRv4Parser.GrammarFileContext tree,
            AntlrGenerationResult res, Extraction extraction, JFS jfs, JFSCompileBuilder bldr,
            String grammarPackageName, Consumer<Supplier<ClassLoader>> classloaderSupplierConsumer,
            Consumer<UnixPath> singleJavaSourceConsumer) throws IOException {

        bldr.addOwningLibraryToClasspath(IntList.class);
        bldr.addOwningLibraryToClasspath(Preconditions.class);
        bldr.addOwningLibraryToClasspath(Parser.class);
        bldr.addOwningLibraryToClasspath(Copyable.class);
        bldr.addOwningLibraryToClasspath(Strings.class);

        classloaderSupplierConsumer.accept(isolatedParentClassLoader);
        res.mainGrammar.createGrammarParserInterpreter(null);

        return null;
    }
    private static final IsolationClassLoaderBuilder isolatedParentClassLoader = IsolationClassLoader
            .builder()
            // Mark it uncloseable, or closing the JFSClassLoader will inadvertently
            // close it as well
            //            .uncloseable()
            .includingJarOf(ANTLRErrorListener.class)
            .includingJarOf(Tool.class)
            .includingJarOf(IntArray.class)
            .includingJarOf(Interpreter.class)
            .includingJarOf(IntList.class)
            .includingJarOf(Preconditions.class)
            .includingJarOf(Copyable.class)
            .includingJarOf(Strings.class)


            .loadingFromParent(AntlrProxies.class)
            .loadingFromParent(AntlrProxies.ParseTreeBuilder.class)
            .loadingFromParent(AntlrProxies.Ambiguity.class)
            .loadingFromParent(AntlrProxies.ParseTreeElement.class)
            .loadingFromParent(AntlrProxies.ParseTreeElementKind.class)
            .loadingFromParent(AntlrProxies.ParseTreeProxy.class)
            .loadingFromParent(AntlrProxies.ProxyDetailedSyntaxError.class)
            .loadingFromParent(AntlrProxies.ProxyException.class)
            .loadingFromParent(AntlrProxies.ProxySyntaxError.class)
            .loadingFromParent(AntlrProxies.ProxyToken.class)
            .loadingFromParent(AntlrProxies.ProxyTokenType.class)
            .loadingFromParent(AntlrProxies.Ambiguity.class)
            .loadingFromParent(AntlrProxies.TokenAssociated.class)
            .loadingFromParent(AntlrProxies.TerminalNodeTreeElement.class)
            .loadingFromParent(AntlrProxies.RuleNodeTreeElement.class)
            .loadingFromParent(AntlrProxies.ErrorNodeTreeElement.class)
            //            .loadingFromParent(ProxiesInvocationRunner.class.getName())
            // XXX, we should move the mime type guesswork to something
            // with a smaller footprint and omit this
            .loadingFromParent(AdhocMimeTypes.class);
    @Override
    public AmbiguityProcessor apply(AmbingExtractorGenerationResult arg) throws Exception {
        return null;
    }

    public static class AmbingExtractorGenerationResult {

    }
}

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
package org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool;

import org.nemesis.antlr.v4.netbeans.v8.util.isolation.ForeignInvocationResult;
import org.nemesis.antlr.v4.netbeans.v8.util.isolation.ForeignInvocationEnvironment;
import java.lang.ref.Reference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.TestDir.projectBaseDir;

/**
 *
 * @author Tim Boudreau
 */
public class ForeignInvocationEnvironmentTest {

    private static ForeignInvocationEnvironment env;
    private static Path grammar;
    private static Path output;
    private static Path importdir;
    private static Path lexer;
    private static AntlrInvoker invokerA;
    private static AntlrInvoker invokerB;

    @Test
    public void testLoadAntlrInIsolation() throws Throwable {
        System.out.println("OUTPUT TO " + output);
        String[] args = {"-o", output.toString(),
            "-encoding", "utf-8",
            //            "-depend",
            "-lib", importdir.toString(),
            "-long-messages",
            "-listener",
            "-visitor",
            "-message-format", "vs2005", // antlr, gnu, vs2005
            //            "-Xlog",
            //            "-atn",
            "-package", "com.toolenvironment", lexer.toString()};
        invokerA = new AntlrInvoker(args);
        ForeignInvocationResult<AntlrInvocationResult> res = null;
        try {
            res = env.invoke(invokerA);
        } finally {
            if (res != null) {
                System.out.println("RES MSGS: " + res.invocationResult());
                System.out.println("\n********************************************\nOUTPUT");
                System.out.println(res.output());
                System.out.println("----------");
                System.out.println("\n********************************************\nERR");
                System.out.println(res.errorOutput());
                System.out.println("---------");
            } else {
                System.out.println("no res");
            }
        }
        Reference<ClassLoader> ref = invokerA.loader;
        Reference<Class<?>> antlrRef = invokerA.antlrClass;
        assertNotNull(res);
        res.rethrow();

        args[args.length - 1] = grammar.toString();
        invokerB = new AntlrInvoker(args);
        env = new ForeignInvocationEnvironment(new UnitTestAntlrLibrary());
        ForeignInvocationResult<AntlrInvocationResult> res2 = env.invoke(invokerB);
        System.out.println("RES2 MSGS: " + res2.invocationResult());
        System.out.println("\n********************************************\nOUTPUT-2");
        System.out.println(res2.output());
        System.out.println("-----------");
        System.out.println("\n********************************************\nERR-2");
        System.out.println(res2.errorOutput());
        System.out.println("------------");
        res2.rethrow();

        assertNotSame(invokerA.antlrClass.get(), invokerB.antlrClass.get());

        env = null;
        res = null;
        invokerA = null;
        invokerB = null;
        for (int i = 0; i < 15; i++) {
            System.gc();
            System.runFinalization();
        }
//        assertGC("Classloader is still referenced",ref);
//        assertGC("Antlr Tool class not unloaded", antlrRef);
//        assertNull("Classloader is still referenced", ref.get());

        List<ParsedAntlrError> errs = res2.parseErrorOutput(new AntlrErrorParser());
        System.out.println("ERRORS: " + errs);

        System.out.flush();
//        assertNull("Antlr Tool class not unloaded", antlrRef.get());
    }

    @BeforeClass
    public static void findJar() throws Exception {
        // XXX fixme - project should use embedded version
        env = new ForeignInvocationEnvironment(new UnitTestAntlrLibrary());

        Path baseDir = projectBaseDir();
        importdir = baseDir.resolve("src/main/antlr4/imports");
        lexer = baseDir.resolve("src/main/antlr4/org/nemesis/antlr/v4/netbeans/v8/grammar/code/checking/impl/ANTLRv4Lexer.g4");
        grammar = baseDir.resolve("src/main/antlr4/org/nemesis/antlr/v4/netbeans/v8/grammar/code/checking/impl/ANTLRv4.g4");
        output = Paths.get(System.getProperty("java.io.tmpdir"), ForeignInvocationEnvironmentTest.class.getSimpleName() + "-" + System.currentTimeMillis());
        Files.createDirectories(output);
        Files.createDirectories(output.resolve("com/toolenvironment"));
    }
}

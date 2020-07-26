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
package org.nemesis.test.fixtures.support;

import com.mastfrog.util.collections.ArrayUtils;
import com.mastfrog.util.file.FileUtils;
import com.mastfrog.util.preconditions.Exceptions;
import com.mastfrog.util.streams.Streams;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 *
 * @author Tim Boudreau
 */
public final class MavenProjectBuilder {

    private final Map<Path, Supplier<String>> files = new HashMap<>();
    private final Set<Path> dirs = new LinkedHashSet<>();

    MavenProjectBuilder() {
        add("src/main/java").add("src/main/antlr4/imports")
                .add("src/test/java")
                .add("target/generated-sources/antlr4")
                .add("target/classes").add("target/test-classes");
    }

    public MavenProjectBuilder writeStockTestGrammar(String pkg) {
        if (pkg == null) {
            pkg = "abc.def.ghi";
        }
        pkg = pkg.replace('.', '/');
        return addMainAntlrSource(pkg + "/NestedMaps", () -> {
            return MavenProjectBuilder.TEST_GRAMMAR;
        });
    }

    public MavenProjectBuilder writeStockTestGrammarSplit(String pkg) {
        if (pkg == null) {
            pkg = "abc.def.ghi";
        }
        pkg = pkg.replace('.', '/');
        addMainAntlrSource(pkg + "/NestedMaps", () -> {
            String parserSection = TEST_GRAMMAR.split("//END-PARSER\n")[0];
            parserSection = parserSection.replaceAll("//TOP", "import NMLexer;");
            return parserSection;
        });
        return addImportedAntlrSource("NMLexer", () -> {
            String lexerSection = TEST_GRAMMAR.split("//END-PARSER\n")[1];
            return "lexer grammar NMLexer;\n" + lexerSection;
        });
    }

    public MavenProjectBuilder copyMainAntlrSource(Path orig, String path) {
        assert Files.exists(orig);
        Path relPath = Paths.get(path).resolve(orig.getFileName());
        return addMainAntlrSource(relPath.toString(), () -> {
            try {
                return new String(Files.readAllBytes(orig), UTF_8);
            } catch (IOException ex) {
                return Exceptions.chuck(ex);
            }
        });
    }

    public MavenProjectBuilder addMainAntlrSource(String name, Supplier<String> body) {
        if (!name.endsWith(".g4")) {
            name = name + ".g4";
        }
        Path path = Paths.get("src/main/antlr4/").resolve(name);
        return add(path, body);
    }

    public MavenProjectBuilder addImportedAntlrSource(String name, Supplier<String> body) {
        if (!name.endsWith(".g4")) {
            name = name + ".g4";
        }
        Path path = Paths.get("src/main/antlr4/imports").resolve(name);
        return add(path, body);
    }

    public MavenProjectBuilder addMainAntlrSource(String name, Class<?> relativeTo, String resourceName) {
        InputStream in = relativeTo.getResourceAsStream(resourceName);
        if (in == null) {
            throw new IllegalArgumentException("No resource " + resourceName + " adjacent to " + relativeTo.getName());
        } else {
            try {
                in.close();
            } catch (IOException ex) {
                throw new AssertionError(ex);
            }
        }
        return addMainAntlrSource(name, () -> {
            try {
                return Streams.readResourceAsUTF8(relativeTo, name);
            } catch (IOException ex) {
                return Exceptions.chuck(ex);
            }
        });
    }

    public MavenProjectBuilder addImportedAntlrSource(String name, Class<?> relativeTo, String resourceName) {
        InputStream in = relativeTo.getResourceAsStream(resourceName);
        if (in == null) {
            throw new IllegalArgumentException("No resource " + resourceName + " adjacent to " + relativeTo.getName());
        } else {
            try {
                in.close();
            } catch (IOException ex) {
                throw new AssertionError(ex);
            }
        }
        return addImportedAntlrSource(name, () -> {
            try {
                return Streams.readResourceAsUTF8(relativeTo, name);
            } catch (IOException ex) {
                return Exceptions.chuck(ex);
            }
        });
    }

    public MavenProjectBuilder add(String pth) {
        return add(Paths.get(pth));
    }

    public MavenProjectBuilder add(Path pth) {
        if (pth.toString().startsWith("/")) {
            pth = Paths.get(pth.toString().substring(1));
        }
        dirs.add(pth);
        return this;
    }

    public MavenProjectBuilder add(Path pth, Supplier<String> supp) {
        if (pth.toString().startsWith("/")) {
            pth = Paths.get(pth.toString().substring(1));
        }
        this.files.put(pth, supp);
        return this;
    }

    public MavenProjectBuilder generatePomFile(String name) {
        return add(Paths.get("pom.xml"), () -> {
            return POM_TEMPLATE.replaceAll("__NAME__", name);
        });
    }

    public GeneratedMavenProject build(String name) throws IOException {
        generatePomFile(name);
        Path tmp = FileUtils.newTempDir("maven-gen-");
        Path dir = tmp.resolve(name);
        GeneratedMavenProject result = new GeneratedMavenProject(dir, name);
        Files.createDirectories(dir);
        for (Path d : dirs) {
            Path toCreate = dir.resolve(d);
            Files.createDirectories(toCreate);
            result.map.put(toCreate.getFileName().toString(), toCreate);
        }
        for (Map.Entry<Path, Supplier<String>> e : files.entrySet()) {
            Path p = e.getKey();
            Path d = dir.resolve(p).getParent();
            if (!Files.exists(d)) {
                Files.createDirectories(dir.resolve(d));
            }
            String body = e.getValue().get();
            if (body == null) {
                throw new IllegalArgumentException("Supplier for " + e.getKey()
                        + " returned null text: " + e.getValue());
            }
            Path toWrite = dir.resolve(p);
            Files.write(toWrite, body.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            result.map.put(toWrite.getFileName().toString(), toWrite);
        }
        if (hackModuleSystem) {
            TestFixtures.hackModuleSystemAlreadyStarted(true);
        }
        if (verboseLogging) {
            TestFixtures.excludedLogs.addAll(logExclude);
            TestFixtures.includedLogs.addAll(logInclude);
            TestFixtures.initLogging(insanelyVerboseLogging, initLoggers);
        }
        return result;
    }

    private boolean insanelyVerboseLogging;
    private boolean hackModuleSystem;

    public MavenProjectBuilder insanelyVerboseLogging() {
        this.insanelyVerboseLogging = true;
        return this;
    }

    public MavenProjectBuilder hackModuleSystemNotToStart() {
        this.hackModuleSystem = hackModuleSystem;
        return this;
    }

    static void assertNotNull(Object o, String msg) {
        if (o == null) {
            throw new AssertionError(msg);
        }
    }

    private boolean verboseLogging;

    private Object[] initLoggers = new Object[0];

    public MavenProjectBuilder verboseLogging(Object... init) {
        if (initLoggers.length == 0) {
            initLoggers = init;
        } else {
            initLoggers = ArrayUtils.concatenate(initLoggers, init);
        }
        this.verboseLogging = true;
        return this;
    }

    private final Set<String> logExclude = new HashSet<>();
    private final Set<String> logInclude = new HashSet<>();

    public MavenProjectBuilder logExclude(String s, String... more) {
        logExclude.add(s);
        logExclude.addAll(Arrays.asList(more));
        return this;
    }

    public MavenProjectBuilder logInclude(String s, String... more) {
        logInclude.add(s);
        logInclude.addAll(Arrays.asList(more));
        return this;
    }

    private static String POM_TEMPLATE = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<project xmlns=\"http://maven.apache.org/POM/4.0.0\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">\n"
            + "    <modelVersion>4.0.0</modelVersion>\n"
            + "    <groupId>nm.antlr.generated</groupId>\n"
            + "    <version>" + System.currentTimeMillis() + "</version>\n"
            + "    <artifactId>__NAME__</artifactId>\n"
            + "    <dependencies>\n"
            + "        <dependency>\n"
            + "            <groupId>org.nemesis</groupId>\n"
            + "            <artifactId>antlr-wrapper</artifactId>\n"
            + "            <version>2.0</version>\n"
            + "        </dependency>\n"
            + "    </dependencies>\n"
            + "    <properties>\n"
            + "        <maven.compiler.source>1.8</maven.compiler.source>\n"
            + "        <maven.compiler.target>1.8</maven.compiler.target>\n"
            + "        <antlr.message.format>gnu</antlr.message.format>\n"
            + "    </properties>\n"
            + "    <!--MAIN-BODY-->\n"
            + "    <build>\n"
            + "        <plugins>\n"
            + "            <plugin>\n"
            + "                <groupId>org.antlr</groupId>\n"
            + "                <artifactId>antlr4-maven-plugin</artifactId>\n"
            + "                <executions>\n"
            + "                    <execution>\n"
            + "                        <id>antlr</id>\n"
            + "                        <goals>\n"
            + "                            <goal>antlr4</goal>\n"
            + "                        </goals>\n"
            + "                        <phase>generate-sources</phase>\n"
            + "                        <configuration>\n"
            + "                            <!--CONFIG-->\n"
            + "                            <visitor>true</visitor>\n"
            + "                            <listener>true</listener>\n"
            + "                            <options>\n"
            + "                                <language>Java</language>\n"
            + "                            </options>\n"
            + "                        </configuration>\n"
            + "                    </execution>\n"
            + "                </executions>\n"
            + "            </plugin>\n"
            + "        </plugins>\n"
            + "    </build>\n"
            + "</project>\n";

    public static final String TEST_GRAMMAR = "grammar NestedMaps;\n"
            + "\n"
            + "//TOP\n"
            + "items : (map (Comma map)*) EOF;\n"
            + "\n"
            + "map : OpenBrace (mapItem (Comma mapItem)*)? CloseBrace;\n"
            + "\n"
            + "mapItem : id=Identifier Colon value;\n"
            + "\n"
            + "value : booleanValue #Bool\n"
            + "    | numberValue #Num\n"
            + "    | stringValue #Str\n"
            + ";\n"
            + "\n"
            + "booleanValue : val=(True | False);\n"
            + "stringValue : str=String;\n"
            + "numberValue : num=Number;\n"
            + "//END-PARSER\n"
            + "\n"
            + "Number: Minus? Digits;\n"
            + "\n"
            + "Digits : DIGIT+;\n"
            + "\n"
            + "\n"
            + "String : STRING | STRING2;\n"
            + "\n"
            + "Whitespace:\n"
            + "    WHITESPACE -> channel(1);\n"
            + "\n"
            + "OpenBrace : '{';\n"
            + "Comma : ',';\n"
            + "CloseBrace : '}';\n"
            + "Minus : '-';\n"
            + "Colon : ':';\n"
            + "True : TRUE;\n"
            + "False : FALSE;\n"
            + "\n"
            + "Identifier : ID;\n"
            + "\n"
            + "fragment TRUE : 'true';\n"
            + "fragment FALSE : 'false';\n"
            + "fragment STRING: '\"' (ESC|.)*? '\"';\n"
            + "fragment STRING2: '\\''(ESC2|.)*? '\\'';\n"
            + "fragment DIGIT : [0-9];\n"
            + "fragment WHITESPACE : [ \\t\\r\\n]+;\n"
            + "fragment ID: ('a'..'z'|'A'..'Z' | '_')('a'..'z'|'A'..'Z'|'0'..'9' | '_')+;\n"
            + "fragment ESC : '\\\\\"' | '\\\\\\\\' ;\n"
            + "fragment ESC2 : '\\\\\\'' | '\\\\\\\\' ;\n"
            + "\n"
            + "";
}

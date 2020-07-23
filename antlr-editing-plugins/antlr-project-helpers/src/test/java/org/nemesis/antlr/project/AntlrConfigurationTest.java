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
package org.nemesis.antlr.project;

import com.mastfrog.util.file.FileUtils;
import java.io.IOException;
import java.nio.channels.FileChannel;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.nio.file.Path;
import java.nio.file.Paths;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.netbeans.junit.MockServices;

/**
 *
 * @author Tim Boudreau
 */
public class AntlrConfigurationTest {

    private Path path;

    @Test
    public void testSerialization() throws Exception {
        Path dir = Paths.get(System.getProperty("java.io.tmpdir"));
        AntlrConfiguration config = new AntlrConfiguration(dir.resolve("imports"), dir.resolve("src"),
                dir.resolve("out"), true, false, true, false, null, "abcd", UTF_8,
                dir.resolve("build"), "fooger", false, dir.resolve("bo"), dir.resolve("to"), dir.resolve("sources"),
                dir.resolve("testSources"));

        try (FileChannel ch = FileChannel.open(path, CREATE, WRITE, TRUNCATE_EXISTING)) {
            config.writeTo(ch);
        }

        AntlrConfiguration loaded;
        try (FileChannel ch = FileChannel.open(path, READ)) {
            loaded = AntlrConfiguration.readFrom(ch);
        }
        assertNotNull(loaded);
        assertEquals(config, loaded);
    }

    @BeforeEach
    public void before() throws IOException {
        AntlrConfigurationCache.INSTANCE = new AntlrConfigurationCache(null, false);
        MockServices.setServices(FLSIFImpl.class);
        path = FileUtils.newTempFile("AntlrConfigurationTest-");
    }

    @AfterEach
    public void after() throws IOException {
        FileUtils.deleteIfExists(path);
    }


}

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
import static java.nio.charset.StandardCharsets.UTF_8;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.netbeans.junit.MockServices;

/**
 *
 * @author Tim Boudreau
 */
public class AntlrConfigurationCacheTest {

    private static final Path dir = Paths.get(System.getProperty("java.io.tmpdir"));
    private int ix = 0;
    private Path t1;
    private Path t2;
    private Path path;

    @Test
    public void testConfigCache() throws IOException {
        AntlrConfiguration cfig1 = AntlrConfigurationCache.instance().get(t1, this::config);
        assertNotNull(cfig1);
        assertEquals(1, AntlrConfigurationCache.instance().size());
        AntlrConfiguration cfig2 = AntlrConfigurationCache.instance().get(t2, this::config);
        assertNotNull(cfig2);
        assertEquals(2, AntlrConfigurationCache.instance().size());
        AntlrConfiguration cfig2a = AntlrConfigurationCache.instance().get(t2, this::config);
        assertNotNull(cfig2a);
        assertEquals(2, AntlrConfigurationCache.instance().size());
        assertSame(cfig2, cfig2a);
        AntlrConfigurationCache.INSTANCE.store();
        AntlrConfigurationCache nue = new AntlrConfigurationCache(path, true);
        assertEquals(2, nue.size());

        AntlrConfiguration cfig2b = nue.get(t2, () -> null);
        assertNotNull(cfig2b);
        assertEquals(cfig2, cfig2b);
    }

    private AntlrConfiguration config() {
        String suffix = "-" + ++ix;
        AntlrConfiguration config = new AntlrConfiguration(
                dir.resolve("imports" + suffix), dir.resolve("src" + suffix),
                dir.resolve("out" + suffix), true, false, true,
                false, "abab", null, UTF_8,
                dir.resolve("build" + suffix), "fooger", false,
                dir.resolve("bo" + suffix), dir.resolve("to" + suffix),
                dir.resolve("sources" + suffix),
                dir.resolve("testSources" + suffix));
        return config;
    }

    @BeforeEach
    public void before() throws IOException {
        MockServices.setServices(FLSIFImpl.class);
        path = FileUtils.newTempFile("AntlrConfigurationTest-");
        AntlrConfigurationCache inst = AntlrConfigurationCache.INSTANCE
                = new AntlrConfigurationCache(path, false);
        assertSame(AntlrConfigurationCache.instance(), inst);
        t1 = FileUtils.newTempDir("al-1-");
        t2 = FileUtils.newTempDir("al-2-");
    }

    @AfterEach
    public void after() throws IOException {
        FileUtils.deleteIfExists(path);
        FileUtils.deltree(t1);
        FileUtils.deltree(t2);
    }
}

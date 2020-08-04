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

package org.nemesis.antlr.live.language;

import com.mastfrog.function.throwing.ThrowingRunnable;
import java.io.IOException;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.nemesis.adhoc.mime.types.InvalidMimeTypeRegistrationException;
import static org.nemesis.antlr.live.language.AdhocDataLoaderTest.doSetup;
import org.openide.loaders.DataLoader;
import org.openide.loaders.DataLoaderPool;

/**
 *
 * @author Tim Boudreau
 */
public class DataLoaderPresentTest {

    private static ThrowingRunnable teardown;
    @Test
    public void testLoaderIsPresent() throws Throwable {
        DataLoaderPool pool = DataLoaderPool.getDefault();
        Set<DataLoader> ldrs = new HashSet<>();

        Enumeration<DataLoader> all = pool.allLoaders();
        while (all.hasMoreElements()) {
            DataLoader dl = all.nextElement();
            ldrs.add(dl);
        }
        Set<Class<?>> producers = new HashSet<>();
        Enumeration<DataLoader> prodEnum = pool.producersOf(AdhocDataObject.class);
        while (prodEnum.hasMoreElements()) {
            DataLoader dl = prodEnum.nextElement();
            producers.add(dl.getClass());
        }
        assertTrue(producers.contains(AdhocDataLoader.class));
    }

    @BeforeAll
    public static void setup() throws IOException, InvalidMimeTypeRegistrationException {
        teardown = doSetup();
    }

    @AfterAll
    public static void teardown() throws Throwable {
        if (teardown != null) {
            teardown.run();
        }
    }
}

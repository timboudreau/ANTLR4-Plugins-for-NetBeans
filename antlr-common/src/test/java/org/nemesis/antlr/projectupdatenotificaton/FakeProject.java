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
package org.nemesis.antlr.projectupdatenotificaton;

import com.mastfrog.function.throwing.ThrowingRunnable;
import com.mastfrog.util.file.FileUtils;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.nio.file.Files;
import java.nio.file.Path;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.DSYNC;
import static java.nio.file.StandardOpenOption.SYNC;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 *
 * @author Tim Boudreau
 */
final class FakeProject {

    final Path dir;
    final Path pom;
    final int index;
    private int mods = 0;

    FakeProject(int index, ThrowingRunnable onShutdown) throws Exception {
        this.index = index;
        dir = FileUtils.newTempDir("mp-" + index + "-");
        pom = dir.resolve("pom.xml");
        writePom("fake-pom-" + index);
        onShutdown.andAlways(() -> {
            FileUtils.deleteIfExists(pom);
            FileUtils.deltree(dir);
        });
    }

    void touch() throws IOException, InterruptedException {
        Thread.sleep(1000);
        long oldTime = Files.getLastModifiedTime(pom).toMillis();
//        Files.setLastModifiedTime(pom, FileTime.fromMillis(System.currentTimeMillis()));
        writePom("fake-pom" + index + " mod-" + ++mods);
        long nue = Files.getLastModifiedTime(pom).toMillis();
        assertNotEquals(oldTime, nue);
        System.out.println("Touched " + pom + " lm time now " + nue + " was " + oldTime);
        Thread.sleep(1200);
    }

    private void writePom(String val) throws IOException {
        try (FileChannel ch = FileChannel.open(pom, TRUNCATE_EXISTING, WRITE, SYNC, DSYNC, CREATE)) {
            ch.write(ByteBuffer.wrap(val.getBytes(UTF_8)));
            ch.force(true);
        }
    }
}

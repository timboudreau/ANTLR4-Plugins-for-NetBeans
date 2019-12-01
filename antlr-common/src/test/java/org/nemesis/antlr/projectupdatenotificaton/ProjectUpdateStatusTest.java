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

import com.mastfrog.graph.dynamic.DynamicGraph;
import com.mastfrog.function.throwing.ThrowingRunnable;
import static com.mastfrog.util.collections.CollectionUtils.setOf;
import com.mastfrog.util.file.FileUtils;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.channels.WritableByteChannel;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.netbeans.junit.MockServices;

/**
 *
 * @author Tim Boudreau
 */
public class ProjectUpdateStatusTest {

    private ThrowingRunnable onShutdown = ThrowingRunnable.oneShot(true);

    private FakeProject prj1;
    private FakeProject prj2;
    private FakeProject prj3;
    private FakeProject prj4;
    private FakeProject prj5;
    private FakeProject prj6;
    private FakeProject prj7;
    private Path ser;

    @Test
    public void doTestReadWrite() throws Exception {
        initDeps();

        ProjectUpdateStatus ups1 = new ProjectUpdateStatus(prj1.dir);
        assertTrue(ups1.isChanged());
        assertFalse(ups1.isChanged());

        prj3.touch();
        assertTrue(ups1.isChanged());
        assertFalse(ups1.isChanged());

        try (FileChannel channel = FileChannel.open(ser, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            ups1.store(channel);
        }

        ProjectUpdateStatus deser;
        try (FileChannel channel = FileChannel.open(ser, StandardOpenOption.READ)) {
            deser = ProjectUpdateStatus.load(channel);
        }

        assertNotNull(deser);

        assertTrue(ups1.isEqual(deser));
    }

    @Test
    public void testReadWriteGraph() throws Exception {
        initDeps();
        DynamicGraph<Path> dg = ProjectUpdates.graph();
        System.out.println("sz " + dg.size());
        System.out.println("Serializing " + dg);
        try (FileChannel channel = FileChannel.open(ser, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            dg.store(channel, ProjectUpdateStatusTest::writePath);
        }
        DynamicGraph<Path> deser;
        try (FileChannel channel = FileChannel.open(ser, StandardOpenOption.READ)) {
            deser = DynamicGraph.load(channel, ProjectUpdateStatusTest::readPath);
        }
        assertNotNull(deser);
        System.out.println("Deserialized " + deser);
        assertEquals(dg, deser);
    }

    static <C extends ReadableByteChannel & SeekableByteChannel> Path readPath(ByteBuffer buf) throws IOException {
        int len = buf.getInt();
        System.out.println("path length " + len);
        if (len < 0 || len > 2048) {
            throw new IOException("Absurdly long path entry: " + len);
        }
        byte[] bytes = new byte[len];
        buf.get(bytes);
        Path p = Paths.get(new String(bytes, UTF_8));
        System.out.println("  read path '" + p + "'");
        return p;
    }

    static <C extends WritableByteChannel & SeekableByteChannel> int writePath(Path path, C channel) throws IOException {
        byte[] b = path.toString().getBytes(UTF_8);
        ByteBuffer buf = ByteBuffer.allocate(b.length + Integer.BYTES);
        buf.putInt(b.length);
        buf.put(b);
        buf.flip();
        channel.write(buf);
        return buf.capacity();
    }

    private void initDeps() throws Exception {
        assertNotNull(prj1);
        assertNotNull(prj2);
        assertNotNull(prj3);
        assertNotNull(prj4);
        assertNotNull(prj5);
        assertNotNull(prj6);
        assertNotNull(prj7);
        ProjectUpdates.pathDependencies(prj3.dir, prj1.dir);
        ProjectUpdates.pathDependencies(prj2.dir, prj1.dir);
        ProjectUpdates.pathDependencies(prj4.dir, prj3.dir);
        ProjectUpdates.pathDependencies(prj5.dir, prj3.dir);
        ProjectUpdates.pathDependencies(prj6.dir, prj2.dir);
        ProjectUpdates.pathDependencies(prj7.dir, prj6.dir);

        Set<Path> prj1direct = setOf(prj3.dir, prj2.dir);
        Set<Path> prj1indirect = setOf(prj2.dir, prj3.dir, prj4.dir, prj5.dir, prj6.dir, prj7.dir);

        Set<Path> prj2direct = setOf(prj6.dir);
        Set<Path> prj3direct = setOf(prj4.dir, prj5.dir);

        Set<Path> prj2indirect = setOf(prj6.dir, prj7.dir);

        assertEquals(prj1direct, ProjectUpdates.directDependencies(prj1.dir),
                "wrong direct deps for prj1 " + prj1.dir);
        assertEquals(prj2direct, ProjectUpdates.directDependencies(prj2.dir),
                "wrong direct deps for prj2 " + prj2.dir);
        assertEquals(prj3direct, ProjectUpdates.directDependencies(prj3.dir),
                "wrong direct deps for prj3 " + prj3.dir);
        assertEquals(prj1indirect, ProjectUpdates.dependersOn(prj1.dir),
                "wrong indirect deps for prj1 " + prj1.dir);
        assertEquals(prj2indirect, ProjectUpdates.dependersOn(prj2.dir),
                "wrong direct deps for prj2 " + prj2.dir);
    }

    @BeforeEach
    public void setupFakeProjects() throws Exception {

        ProjectUpdates.instanceSupplier = ProjectUpdates::new;
        prj1 = new FakeProject(1, onShutdown);
        prj2 = new FakeProject(2, onShutdown);
        prj3 = new FakeProject(3, onShutdown);
        prj4 = new FakeProject(4, onShutdown);
        prj5 = new FakeProject(5, onShutdown);
        prj6 = new FakeProject(6, onShutdown);
        prj7 = new FakeProject(7, onShutdown);
        ser = FileUtils.newTempFile("mp-ser-");
        onShutdown.andAlways(() -> {
            FileUtils.deleteIfExists(ser);
        });
        MockServices.setServices(BFF.class);
        assertNotNull(prj1);
        assertNotNull(prj2);
        assertNotNull(prj3);
        assertNotNull(prj4);
        assertNotNull(prj5);
        assertNotNull(prj6);
        assertNotNull(prj7);
    }

    @AfterEach
    public void teardownFakeProjects() throws Exception {
        onShutdown.run();
    }
}

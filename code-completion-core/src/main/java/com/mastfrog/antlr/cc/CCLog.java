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
package com.mastfrog.antlr.cc;

import com.mastfrog.util.collections.CollectionUtils;
import com.mastfrog.util.file.FileUtils;
import java.io.IOException;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.WRITE;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;

/**
 *
 * @author Tim Boudreau
 */
final class CCLog {

    private static final Map<Object, List<String>> lines
            = CollectionUtils.supplierMap(ArrayList::new);
    private static boolean enabled = false;

    static void print() {
        int max = 0;
        List<String> ids = new ArrayList<>();
        List<List<String>> lns = new ArrayList<>();
        for (Map.Entry<Object, List<String>> e : lines.entrySet()) {
            max = Math.max(e.getValue().size(), max);
            Object k = e.getKey();
            ids.add(k.getClass().getSimpleName() + "-" + Integer.toHexString(System.identityHashCode(k)));
            lns.add(e.getValue());
        }
        for (int i = 0; i < max; i++) {
            for (int j = 0; j < ids.size(); j++) {
                List<String> currList = lns.get(j);
                if (i < currList.size()) {
                    String k = ids.get(j);
                    String curr = currList.get(i);
                    System.out.println((i + 1) + ". " + k + "\t" + curr);
                }
            }
        }
    }

    private static Path outFile(String name) {
        String tmp = System.getProperty("java.io.tmpdir");
        return Paths.get(tmp, name);
    }

    static void clear() {
        if (enabled) {
            lines.clear();
            Path aFile = outFile("a.log");
            Path bFile = outFile("b.log");
            Path diffFile = outFile("ab.diff");
            try {
                FileUtils.deleteIfExists(aFile, bFile, diffFile);
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    static void enable(boolean val) {
        enabled = val;
        if (!val) {
            lines.clear();
        }
    }

    public void ifLog(Runnable run) {
        if (enabled) {
            run.run();
        }
    }

    public static void log(Object caller, Object line, Object... more) {
        if (!enabled) {
            return;
        }
        if (more.length > 0) {
            StringBuilder sb = new StringBuilder().append(line);
            for (int i = 0; i < more.length; i++) {
                sb.append(' ');
                sb.append(more[i]);
            }
        }
        lines.get(caller).add(Objects.toString(line));
    }

    public static String mismatch(Object a, Object b) throws IOException, InterruptedException, ExecutionException {
        List<String> al = lines.get(a);
        List<String> bl = lines.get(b);
        if (!al.equals(bl)) {
            Path aFile = Paths.get("/tmp/a.log");
            Path bFile = Paths.get("/tmp/b.log");
            Path diffFile = Paths.get("/tmp/ab.diff");
            Files.write(aFile, al, UTF_8, CREATE, WRITE, TRUNCATE_EXISTING);
            Files.write(bFile, bl, UTF_8, CREATE, WRITE, TRUNCATE_EXISTING);
            Process p = new ProcessBuilder("/usr/bin/diff", "-u", "-w", aFile.toString(), bFile.toString())
                    .directory(aFile.getParent().toFile())
                    .redirectOutput(diffFile.toFile()).start();
            while (p.isAlive()) {
                Thread.sleep(10);
            }
            // JDK 9
//            p.onExit().get();

            return new String(Files.readAllBytes(diffFile), UTF_8);
        }
        return null;
    }

}

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
package org.nemesis.antlr.live.impl;

import com.mastfrog.util.collections.CollectionUtils;
import com.mastfrog.util.path.UnixPath;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.tools.JavaFileManager;
import org.nemesis.jfs.JFSCoordinates;
import org.openide.filesystems.FileObject;

/**
 *
 * @author Tim Boudreau
 */
final class GrammarMappingsImpl extends GrammarMappings {

    private final JavaFileManager.Location loc;
    private final Map<FileObject, UnixPath> jfsPathForFo = new ConcurrentHashMap<>();
    private final Map<UnixPath, FileObject> foForUnixPath = new ConcurrentHashMap<>();
    private final Map<String, Set<UnixPath>> rawNamesForUnixPath = CollectionUtils.concurrentSupplierMap(ConcurrentHashMap::newKeySet);

    GrammarMappingsImpl(JavaFileManager.Location loc) {
        this.loc = loc;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("GrammarMappings(");
        for (Map.Entry<FileObject, UnixPath> e : jfsPathForFo.entrySet()) {
            sb.append('\n').append(e.getKey().getPath()).append(" -> ").append(e.getValue());
        }
        return sb.append(')').toString();
    }

    void clear() {
        jfsPathForFo.clear();
        foForUnixPath.clear();
        rawNamesForUnixPath.clear();
    }

    void remove(FileObject file) {
        UnixPath path = jfsPathForFo.remove(file);
        if (path != null) {
            foForUnixPath.remove(path);
            String raw = path.rawName();
            rawNamesForUnixPath.get(raw).remove(path);
        }
    }

    void add(FileObject file, UnixPath path) {
        jfsPathForFo.put(file, path);
        foForUnixPath.put(path, file);
        rawNamesForUnixPath.get(path.rawName()).add(path);
    }

    @Override
    public FileObject originalFile(JFSCoordinates path) {
        if (path.location() != loc) {
            return null;
        }
        return originalFile(path.path());
    }

    @Override
    public JFSCoordinates forFileObject(FileObject fo) {
        UnixPath result = jfsPathForFo.get(fo);
        return result == null ? null : JFSCoordinates.create(loc, result);
    }

    @Override
    public FileObject originalFile(UnixPath path) {
        return foForUnixPath.get(path);
    }

    private Set<UnixPath> filterExtensions(Set<UnixPath> paths, String... exts) {
        if (exts.length == 0) {
            return paths;
        }
        Set<String> ext = CollectionUtils.setOf(exts);
        Set<UnixPath> result = new LinkedHashSet<>();
        for (UnixPath up : paths) {
            if (up.extension() == null) {
                continue;
            }
            if (ext.contains(up.extension())) {
                result.add(up);
            }
        }
        return result;
    }

    @Override
    public JFSCoordinates forFileName(String name, UnixPath relativeTo, String... exts) {
        UnixPath up = UnixPath.get(name);
        String ext = up.extension();
        String raw;
        if (ext != null) {
            raw = up.rawName();
        } else {
            raw = name;
        }
        Set<UnixPath> found = filterExtensions(rawNamesForUnixPath.get(raw), exts);
        switch (found.size()) {
            case 0:
                return null;
            case 1:
                UnixPath first = found.iterator().next();
                if (Objects.equals(first.extension(), up.extension())) {
                    return JFSCoordinates.create(loc, found.iterator().next());
                } else {
                    return null;
                }
            default:
                UnixPath best = null;
                if (relativeTo != null) {
                    for (UnixPath p : found) {
                        String pExt = p.extension();
                        if (!Objects.equals(ext, pExt)) {
                            continue;
                        }
                        if (Objects.equals(p.getParent(), relativeTo.getParent())) {
                            best = p;
                            break;
                        }
                    }
                }
                return JFSCoordinates.create(loc, best != null ? best : found.iterator().next());
        }
    }

    @Override
    public List<JFSCoordinates> forRawName(String rawName, String... exts) {
        Set<UnixPath> all = rawNamesForUnixPath.get(rawName);
        switch (all.size()) {
            case 0:
                return Collections.emptyList();
            case 1:
                UnixPath p = all.iterator().next();
                boolean match = exts.length == 0 ? true : Arrays.asList(exts).contains(p.extension());
                if (match) {
                    return Arrays.asList(JFSCoordinates.create(loc, p));
                } else {
                    return Collections.emptyList();
                }
            default:
                List<JFSCoordinates> result = new ArrayList<>(all.size());
                Set<String> set = CollectionUtils.setOf(exts);
                for (UnixPath up : all) {
                    String ex = up.extension();
                    boolean oneMatch = ex == null ? set.isEmpty() : set.contains(ex);
                    if (oneMatch) {
                        result.add(JFSCoordinates.create(loc, up));
                    }
                }
                return result;
        }
    }

}

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

import com.mastfrog.util.cache.MapCache;
import com.mastfrog.util.collections.CollectionUtils;
import com.mastfrog.util.collections.MapFactories;
import com.mastfrog.util.path.UnixPath;
import java.io.IOException;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.logging.Logger;
import static org.nemesis.antlr.common.AntlrConstants.ANTLR_MIME_TYPE;
import org.nemesis.antlr.live.BrokenSourceThrottle;
import org.nemesis.antlr.live.Subscriber;
import org.nemesis.antlr.project.Folders;
import org.nemesis.jfs.JFS;
import org.nemesis.misc.utils.CachingSupplier;
import org.netbeans.api.project.FileOwnerQuery;
import org.netbeans.api.project.Project;
import org.openide.filesystems.FileObject;

/**
 *
 * @author Tim Boudreau
 */
public final class AntlrGenerationSubscriptionsImpl {

    private static final Logger LOG = Logger.getLogger(AntlrGenerationSubscriptionsImpl.class.getName());
    static final UnixPath IMPORTS = UnixPath.get("imports");

    final BrokenSourceThrottle throttle = new BrokenSourceThrottle();
    private final JFSManager jfses = new JFSManager();
    private final MapCache<Project, AntlrGenerationSubscriptionsForProject> subscribersByProject
            = new MapCache<>(MapFactories.WEAK.createMap(32, true), project -> {
                return new AntlrGenerationSubscriptionsForProject(project, jfses, () -> {
                    this.subscribersByProject.remove(project);
                });
            });

    private static final Supplier<AntlrGenerationSubscriptionsImpl> INSTANCE_SUPPLIER
            = CachingSupplier.of(AntlrGenerationSubscriptionsImpl::new);

    public static AntlrGenerationSubscriptionsImpl instance() {
        return INSTANCE_SUPPLIER.get();
    }

    public static BrokenSourceThrottle throttle() {
        return instance().throttle;
    }

    public JFS jfsFor(Project project) { // for tests
        return INSTANCE_SUPPLIER.get().jfses.getIfPresent(project);
    }

    public static boolean subscribe(FileObject grammarFile, Subscriber reb) {
        return instance()._subscribe(grammarFile, reb);
    }

    public static void unsubscribe(FileObject grammarFile, Subscriber reb) {
        instance()._unsubscribe(grammarFile, reb);
    }

    public static long mostRecentGrammarLastModifiedInProject(Project project) throws IOException {
        if (project == null || project == FileOwnerQuery.UNOWNED) {
            return System.currentTimeMillis() - 1;
        }
        Optional<AntlrGenerationSubscriptionsForProject> subs = INSTANCE_SUPPLIER.get().subscribersByProject.cachedValue(project);
        if (subs.isPresent()) {
            long result = subs.get().newestGrammarLastModified();
            if (result > 0) {
                return result;
            }
        }
        return mostRecentGrammarLastModifiedInProjectTheHardWay(ANTLR_MIME_TYPE, null, project);
    }

    public static long mostRecentGrammarLastModifiedInProjectOf(FileObject fo) throws IOException {
        Project project = FileOwnerQuery.getOwner(fo);
        if (project == null || project == FileOwnerQuery.UNOWNED) {
            return System.currentTimeMillis() - 1;
        }
        Optional<AntlrGenerationSubscriptionsForProject> subs = INSTANCE_SUPPLIER.get().subscribersByProject.cachedValue(project);
        if (subs.isPresent()) {
            long result = subs.get().newestGrammarLastModified();
            if (result > 0) {
                return result;
            }
        }
        return mostRecentGrammarLastModifiedInProjectTheHardWay(ANTLR_MIME_TYPE, fo, project);
    }

    private static long mostRecentGrammarLastModifiedInProjectTheHardWay(String mime, FileObject fo, Project project) throws IOException {
        Iterable<FileObject> files;
        if (fo != null) {
            files = CollectionUtils.concatenate(
                    Folders.ANTLR_GRAMMAR_SOURCES.findFileObject(project, fo),
                    Folders.ANTLR_IMPORTS.findFileObject(project, fo));
        } else {
            files = CollectionUtils.concatenate(
                    Folders.ANTLR_GRAMMAR_SOURCES.findFileObject(project),
                    Folders.ANTLR_IMPORTS.findFileObject(project));
        }
        long result = -1;
        for (FileObject curr : files) {
            if ("text/x-g4".equals(curr.getMIMEType())) {
                result = Math.max(result, curr.lastModified().getTime());
            }
        }
        return result == -1 ? fo != null ? fo.lastModified().getTime() : result : result;
    }

    boolean _subscribe(FileObject grammarFile, Subscriber reb) {
        Project proj = FileOwnerQuery.getOwner(grammarFile);
        if (proj == null || proj == FileOwnerQuery.UNOWNED) {
            return false;
        }
        AntlrGenerationSubscriptionsForProject subscribers = subscribersByProject.get(proj);
        assert subscribers != null : "Null subscribers from cache";
        subscribers.subscribe(grammarFile, reb);
        return true;
    }

    void _unsubscribe(FileObject grammarFile, Subscriber reb) {
        Project proj = FileOwnerQuery.getOwner(grammarFile);
        if (proj == null || proj == FileOwnerQuery.UNOWNED) {
            return;
        }
        AntlrGenerationSubscriptionsForProject subscribers = subscribersByProject.get(proj);
        subscribers.unsubscribe(grammarFile, reb);
        if (subscribers.hasNoSubscribers()) {
            subscribersByProject.remove(subscribers.die());
        }
    }
}

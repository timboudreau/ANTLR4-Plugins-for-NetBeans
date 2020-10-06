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
package org.nemesis.antlr.live;

import org.nemesis.antlr.live.impl.AntlrGenerationSubscriptionsImpl;
import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Supplier;
import javax.swing.text.Document;
import org.nemesis.antlr.memory.AntlrGenerationResult;
import org.nemesis.extraction.Extraction;
import org.nemesis.misc.utils.CachingSupplier;
import org.netbeans.api.project.FileOwnerQuery;
import org.netbeans.api.project.Project;
import org.openide.filesystems.FileObject;

/**
 * Allows callers to pass a callback which will be triggered whenever a new
 * parser result for an Antlr file is being constructed, and process it (for
 * example, compiling it in-memory and running something against the compiled
 * classes in an isolating classloader, or adding hints, or whatever).
 *
 * @author Tim Boudreau
 */
public final class RebuildSubscriptions {

    // Async is preferable, but still testing it, so leaving this here
    // so tests can alter their expectaions
    static boolean SUBSCRIBE_BUILDS_ASYNC = true;
    public static final long DEFAULT_JFS_TIMEOUT_MILLIS = 1_000 * 60 * 10; // 10 minutes
    public static final long JFS_EXPIRATION;
    public static final String SYSTEM_PROP_JFS_EXPIRATION_MINUTES = "jfs.expiration.minutes";

    static {
        long timeout = DEFAULT_JFS_TIMEOUT_MILLIS;
        String syspropTimeout = System.getProperty(SYSTEM_PROP_JFS_EXPIRATION_MINUTES);
        if (syspropTimeout != null) {
            try {
                long val = Long.parseLong(syspropTimeout);
                if (val <= 0) {
                    System.err.println("Bad value " + val + " for "
                            + SYSTEM_PROP_JFS_EXPIRATION_MINUTES);
                }
                timeout = val * (1000 * 60);
            } catch (NumberFormatException nfe) {
                System.err.println("Unparseable value for "
                        + SYSTEM_PROP_JFS_EXPIRATION_MINUTES + ": '" + syspropTimeout
                        + "'");
            }
        }
        JFS_EXPIRATION = timeout;
    }

    /**
     * Get the most recent generation result for a file, or create one if
     * possible.
     *
     * @param fo A file object for an antlr grammar
     * @return A generation result or null if the file is unparsed or
     * broken
     */
    public static AntlrGenerationResult recentGenerationResult(FileObject fo) {
        return AntlrGenerationSubscriptionsImpl.instance().recentGenerationResult(fo);
    }

    /**
     * Determine if a given file path + grammar tokens hash combination has been
     * temporarily blacklisted from Antlr in-memory generation because the
     * generation results have been unusable (Antlr generation fails
     * significantly enough to be useless).
     *
     * @param filePath A file path
     * @param tokensHash A tokens hash from an Extraction run against the
     * grammar
     * @return true if the combination has been throttled and anything receiving
     * it may want to likewise not use it
     */
    public static boolean isThrottled(Path filePath, String tokensHash) {
        return AntlrGenerationSubscriptionsImpl.throttle().isThrottled(filePath, tokensHash);
    }

    /**
     * Determine if a given file path + grammar tokens hash combination has been
     * temporarily blacklisted from Antlr in-memory generation because the
     * generation results have been unusable (Antlr generation fails
     * significantly enough to be useless).
     *
     * @param extraction An extraction from a file
     * @param tokensHash A tokens hash from an Extraction run against the
     * grammar
     * @return true if the combination has been throttled and anything receiving
     * it may want to likewise not use it
     */
    public static boolean isThrottled(Extraction ext) {
        return AntlrGenerationSubscriptionsImpl.throttle().isThrottled(ext);
    }

    /**
     * Tell the throttle that a bad antlr generation pass has been encountered,
     * and the count of passes before throttling is applied should be
     * incremented.
     *
     * @param extraction An extraction from a file
     * @param tokensHash A tokens hash from an Extraction run against the
     * grammar
     * @return true if the combination has been throttled and anything receiving
     * it may want to likewise not use it
     */
    public static boolean maybeThrottled(Path filePath, String tokensHash) {
        return AntlrGenerationSubscriptionsImpl.throttle().maybeThrottle(filePath, tokensHash);
    }

    static Supplier<RebuildSubscriptions> INSTANCE_SUPPLIER
            = CachingSupplier.of(RebuildSubscriptions::new);

    /**
     * Subscribe the passed subscriber to re-parses of the passed file object.
     * If this is the first call to subscribe for this file, a parse will be
     * triggered.
     *
     * @param fo A fileobject for an Antlr grammar file
     * @param sub A subscriber that will be invoked on reparse
     * @return A runnable which will unsubscribe the subscriber; the surrounding
     * plumbing that enables the subscription is strongly referenced <i>by</i>
     * the unsubscriber - allow that to be garbage collected and you may not get
     * notified of changes.
     */
    public static Runnable subscribe(FileObject fo, Subscriber sub) {
        AntlrGenerationSubscriptionsImpl.subscribe(fo, sub);
        return () -> {
            AntlrGenerationSubscriptionsImpl.unsubscribe(fo, sub);
        };
    }

    /**
     * Get the most recent last modified time for any grammar file in the
     * grammar source folders of the project that owns the passed file,
     * preferring the most recent (saved or not) <i>Document</i> edit times
     * where those are available, since we JFS-masquerade documents when
     * present, and listen and update a timestamp for them. If no subscribers
     * are registered for the project, returns the file timestamps.
     *
     * @param fo A file object
     * @return The most recent last modified time, or -1 if it cannot be
     * determined (grammar file deleted, etc.)
     * @throws IOException If something goes wrong
     */
    public static long mostRecentGrammarLastModifiedInProjectOf(FileObject fo) throws IOException {
        String mime = fo.getMIMEType();
        Project p = FileOwnerQuery.getOwner(fo);
        if (p == null) {
            return -1;
        }
        return AntlrGenerationSubscriptionsImpl.mostRecentGrammarLastModifiedInProjectOf(fo);
    }

    /**
     * Allows the JFS mapping to be used to quickly and efficiently get the most
     * recent last modified time for any grammar within a project - used by some
     * of the adhoc lexer infrastructure to determine if its cached information
     * about sets of tokens, etc., may be out-of-date.
     *
     * @param proj The project
     * @return A timestamp if available
     * @throws IOException if something goes wrong
     */
    public static long mostRecentGrammarLastModifiedInProject(Project proj) throws IOException {
        return AntlrGenerationSubscriptionsImpl.mostRecentGrammarLastModifiedInProject(proj);
    }

    /**
     * Get the global BrokenSourceThrottle used by antlr generation to determine
     * if a source file's current state is too broken to be usable. Throttled
     * state is cleared on a timer after the most recent query or throttling of
     * the file.
     *
     * @return The throttle
     */
    public static BrokenSourceThrottle throttle() {
        return AntlrGenerationSubscriptionsImpl.throttle();
    }

    /**
     * Notify the infrastructure that a document is in use, so its JFS mapping
     * should not be discarded.
     *
     * @param doc A document
     */
    public static boolean touched(Document doc) {
        return AntlrGenerationSubscriptionsImpl.touched(doc);
    }

    /**
     * If the passed generation result is unusable, increment its fail count
     * towards throttling, and return whether or not it is now throttled.
     *
     * @param res A generationn result
     * @return whether or not it is now throttled
     */
    public static boolean isThrottled(AntlrGenerationResult res) {
        if (!res.isUsable()) {
            return throttle().maybeThrottle(res.originalFilePath, res.tokensHash);
        }
        return false;
    }
}

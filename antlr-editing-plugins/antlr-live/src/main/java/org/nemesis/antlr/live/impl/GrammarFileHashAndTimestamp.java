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

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Cache object that encapsulates the last known tokens hash for a file, and
 * its last modified date, so we can test if we have an up-to-date tokens
 * hash for the file and can generate synthetic AntlrGenerationResult
 * instances for it when it is regenerated as a side-effect of regenerating
 * Antlr sources for something that depends on it (for example, a lexer
 * getting regenerated because the parser that uses it was), and skip the
 * step of running Antlr in-memory again for that file. If the timestamp of
 * the file is not &lt;= the one here, we assume our information is not
 * up-to-date and run a full regeneration.
 */
final class GrammarFileHashAndTimestamp {

    final long lastModified;
    final String tokensHash;

    GrammarFileHashAndTimestamp(long lastModified, String tokensHash) {
        this.lastModified = lastModified;
        this.tokensHash = tokensHash;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        } else if (o == null || !(o instanceof GrammarFileHashAndTimestamp)) {
            return false;
        } else {
            GrammarFileHashAndTimestamp info = (GrammarFileHashAndTimestamp) o;
            return info.lastModified == lastModified && tokensHash.equals(info.tokensHash);
        }
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash *= (int) (lastModified ^ (lastModified >>> 32));
        hash += 11 * tokensHash.hashCode();
        return hash;
    }

    @Override
    public String toString() {
        Instant then = Instant.ofEpochMilli(lastModified);
        return tokensHash + ":" + ZonedDateTime.ofInstant(then, ZoneId.systemDefault());
    }

}

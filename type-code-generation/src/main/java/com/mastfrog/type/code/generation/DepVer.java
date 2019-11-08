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
package com.mastfrog.type.code.generation;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 *
 * @author Tim Boudreau
 */
final class DepVer implements Comparable<DepVer> {

    public final String groupId;
    public final String artifactId;
    public final String version;
    public final String scope;

    DepVer(String groupId, String artifactId, String version) {
        this(groupId, artifactId, version, null);
    }

    DepVer(String groupId, String artifactId, String version, String scope) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
        this.scope = scope;
    }

    public DepVer withScope(String scope) {
        return new DepVer(groupId, artifactId, version, scope);
    }

    public DepVer withVersion(String version) {
        return new DepVer(groupId, artifactId, version, scope);
    }

    public DepVer withGroupId(String groupId) {
        return new DepVer(groupId, artifactId, version, scope);
    }

    public String toIdentifier() {
        return groupId + ":" + artifactId;
    }

    public String toCoordinates() {
        return groupId + ":" + artifactId + ":" + version;
    }

    public Path resolveRepoDir(Path root) {
        String gid = groupId.replace('.', '/');
        return root.resolve(Paths.get(gid, artifactId, version));
    }

    public boolean equals(Object o) {
        if (o instanceof DepVer) {
            DepVer dv = (DepVer) o;
            return dv.groupId.equals(groupId) && dv.artifactId.equals(artifactId);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return (groupId + ':' + artifactId).hashCode();
    }

    @Override
    public String toString() {
        return groupId + "/" + artifactId + "-" + version + (scope == null ? "" : "(" + scope + ")");
    }

    @Override
    public int compareTo(DepVer t) {
        return toString().compareToIgnoreCase(t.toString());
    }

}

package org.nemesis.antlr.project.helpers.maven;

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

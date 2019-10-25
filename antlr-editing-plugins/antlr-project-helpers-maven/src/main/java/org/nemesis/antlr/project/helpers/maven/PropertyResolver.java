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
package org.nemesis.antlr.project.helpers.maven;

import com.mastfrog.util.strings.Strings;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.xml.xpath.XPathExpressionException;

/**
 *
 * @author Tim Boudreau
 */
final class PropertyResolver {

    List<Map<String, String>> maps = new ArrayList<>();
    private final PomFileResolver poms;
    private final Set<DepVer> deps = new HashSet<>();
    private final Map<String, String> resolvedProps;

    PropertyResolver(File pomFile, PomFileResolver poms) throws IOException, XPathExpressionException {
        this(pomFile, poms, new PomFileAnalyzer(pomFile));
    }

    void addProperty(String key, String value) {
        maps.add(Collections.singletonMap(key, value));
        if (resolvedProps != null) {
            resolvedProps.put(key, value);
        }
    }

    PropertyResolver(File pomFile, PomFileResolver poms, PomFileAnalyzer ana) throws IOException, XPathExpressionException {
        this.poms = poms;
        ana.findDependencies(deps, true);
        Map<String, String> m = new HashMap<>();
        File basedir = pomFile.getParentFile().getAbsoluteFile();
        m.put("basedir", basedir.getPath());
        m.put("project.basedir", basedir.getPath());
        m.put("project.groupId", ana.getGroupID());
        m.put("project.artifactId", ana.getArtifactId());
        m.put("project.version", ana.getVersion());
        m.put("pom.groupId", ana.getGroupID());
        m.put("pom.artifactId", ana.getArtifactId());
        m.put("pom.version", ana.getVersion());

        String relp = ana.getParentRelativePath();
        if (relp == null) {
            relp = "../";
        }
        File parentDir = new File(basedir, relp).getAbsoluteFile();
        if (parentDir.exists() && parentDir.isFile()) {
            parentDir = parentDir.getParentFile();
        }

        m.put("parent.basedir", parentDir.getPath());
        m.put("parent.groupId", ana.getParentGroupID());
        m.put("parent.artifactId", ana.getParentArtifactID());
        m.put("parent.version", ana.getParentVersion());
        m.put("parent.relativePath", relp);

        m.put("project.parent.groupId", ana.getParentGroupID());
        m.put("project.parent.basedir", parentDir.getPath());
        m.put("project.parent.artifactId", ana.getParentArtifactID());
        m.put("project.parent.version", ana.getParentVersion());
        m.put("project.parent.relativePath", relp);

        maps.add(m);
        ana.findProperties(m, false);
        String lastGroupId = ana.getGroupID();
        String lastArtifactId = ana.getArtifactId();
        Set<DepVer> localDeps = new HashSet<>();
        while (ana != null) {
            if (ana.getParentGroupID() != null && ana.getParentArtifactID() != null && !ana.getParentGroupID().isEmpty() && !ana.getParentArtifactID().isEmpty()) {
                File pom = poms == null 
                    ? null 
                    : poms.resolve(ana.getParentGroupID(), ana.getParentArtifactID(), ana.getParentVersion());
                if (pom != null) {
                    ana = new PomFileAnalyzer(pom);
                    if (lastGroupId.equals(ana.getGroupID()) && lastArtifactId.equals(ana.getArtifactId())) {
                        break;
                    }
                    lastGroupId = ana.getGroupID();
                    lastArtifactId = ana.getArtifactId();
                    Set<DepVer> veryLocalDeps = new HashSet<>();
                    ana.findDependencies(veryLocalDeps, true);
                    mergeBest(veryLocalDeps, localDeps);
                    m = new HashMap<>();
                    ana.findProperties(m, false);
                    if (!m.isEmpty()) {
                        maps.add(m);
                    }
                } else {
                    ana = null;
                }
            } else {
                break;
            }
        }
        for (DepVer d : localDeps) {
            deps.add(resolve(d));
        }

        resolvedProps = resolvedProperties();
    }

    private void mergeBest(Set<DepVer> nue, Set<DepVer> master) {
        Set<DepVer> replacements = new HashSet<>();
        for (DepVer n : nue) {
            for (DepVer old : master) {
                if (old.equals(n)) {
                    if (old.version == null || old.version.isEmpty() || PomFileAnalyzer.NO_VERSION.equals(old.version)) {
                        replacements.add(n);
                        break;
                    }
                }
            }
        }
        master.addAll(nue);
        master.removeAll(replacements);
        master.addAll(replacements);
    }

    public final DepVer resolve(DepVer subs) {
        String groupId = resolveIfEscaped(subs.groupId);
        String artifactId = resolveIfEscaped(subs.artifactId);
        String version = resolveIfEscaped(subs.version);
        String scope = subs.scope;
        return new DepVer(groupId, artifactId, version, scope);
    }

    public String resolveIfEscaped(String key) {
        if (key != null && key.endsWith("}") && key.startsWith("${")) {
            String result = resolve(key);
            if (result != null) {
                key = result;
            }
        }
        return key;
    }

    public Map<String, String> resolvedProperties() {
        Map<String, String> result = new HashMap<>();
        for (Map<String, String> m : maps) {
            for (Map.Entry<String, String> e : m.entrySet()) {
                String v = e.getValue();
                if (v == null) {
                    continue;
                }
                while (v.contains("${")) {
                    String newV = substituteInto(v);
                    if (newV.equals(v)) {
                        break;
                    }
                    v = newV;
                }
                result.put(e.getKey(), v);
            }
        }
        return result;
    }

    public String substituteInto(String propertyValue) {
        if (propertyValue == null) {
            return null;
        }
        if (resolvedProps != null) {
            for (Map.Entry<String, String> e : resolvedProps.entrySet()) {
                String test = "${" + e.getKey() + "}";
                if (propertyValue.contains(test)) {
                    String val = e.getValue();
                    if (!val.equals(test) && val.contains("${")) {
                        val = substituteInto(val);
                    }
                    propertyValue = Strings.literalReplaceAll(test,
                            val, propertyValue, false).toString();
                }
            }
            return propertyValue;
        }
        for (Map<String, String> m : maps) {
            for (Map.Entry<String, String> e : m.entrySet()) {
                String test = "${" + e.getKey() + "}";
                if (propertyValue.contains(test)) {
                    String val = e.getValue();
                    if (!val.equals(test) && val.contains("${")) {
                        val = substituteInto(val);
                    }
                    propertyValue = Strings.literalReplaceAll(test,
                            val, propertyValue, false).toString();
                }
            }
        }
        return propertyValue;
    }

    public String resolve(String key) {
        if (key.endsWith("}")) {
            key = key.substring(0, key.length() - 1);
        }
        if (key.startsWith("${")) {
            key = key.substring(2, key.length());
        }
        for (Map<String, String> m : maps) {
            if (m.containsKey(key)) {
                return m.get(key);
            }
        }
        return null;
    }

    public File resolvePomWithSubstitutions(String artifactId, String groupId, String version) {
        artifactId = resolveIfEscaped(artifactId);
        groupId = resolveIfEscaped(groupId);
        if (version == null || PomFileAnalyzer.NO_VERSION.equals(version)) {
            for (DepVer d : deps) {
                if (d.artifactId.equals(artifactId) && d.groupId.equals(groupId)) {
//                    if (d.version != null && !PomFileAnalyzer.NO_VERSION.equals(d.version)) {
                    version = d.version;
                    break;
//                    }
                }
            }
        }
        if (version == null) {
            throw new IllegalStateException("Could not find any way to resolve " + artifactId + " " + groupId + " " + version);
        }
        version = resolveIfEscaped(version);
        File result = poms == null ? null : poms.resolve(groupId, artifactId, version);
        return result;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (Map<String, String> m : this.maps) {
            List<Map.Entry<String, String>> entries = new ArrayList<>(m.entrySet());
            Collections.sort(entries, new Comparator<Map.Entry<String, String>>() {
                @Override
                public int compare(Map.Entry<String, String> o1, Map.Entry<String, String> o2) {
                    return o1.getKey().compareTo(o2.getKey());
                }
            });
            for (Map.Entry<String, String> e : entries) {
                sb.append(" ").append(e.getKey()).append(" : ").append(e.getValue()).append("\n");
            }
        }
        return sb.toString();
    }
}

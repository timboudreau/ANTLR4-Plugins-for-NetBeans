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

import com.mastfrog.function.throwing.ThrowingConsumer;
import com.mastfrog.function.throwing.ThrowingFunction;
import static com.mastfrog.util.collections.CollectionUtils.immutableSetOf;
import com.mastfrog.util.strings.Strings;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Misc tools for analyzing and refactoring Maven projects without using Maven's
 * unwieldy API.
 *
 * @author Tim Boudreau
 */
final class PomFileAnalyzer {

    private final File pomFile;

    PomFileAnalyzer(File pomFile) {
        this.pomFile = pomFile;
    }

    public Path pomFile() {
        return pomFile.toPath();
    }

    public Path projectFolder() {
        return pomFile().getParent();
    }

    public boolean isPomProject() throws XPathExpressionException, IOException {
        return "pom".equals(getPackaging());
    }

    public PluginConfigurationInfo antlrPluginInfo() throws Exception {
        return pluginConfiguration("org.antlr", "antlr4-maven-plugin");
    }

    public Document addAntlrSupport(String version, boolean generateListener, boolean generateVisitor) throws Exception {
        Document d = getDocument();
        XPathFactory fac = XPathFactory.newInstance();
        XPath xpath = fac.newXPath();
        List<String> nodes = new ArrayList<>(Arrays.asList("project", "build", "plugins"));
        LinkedList<String> toCreate = new LinkedList<>();
        Element parent = findParentNode(nodes, toCreate, d, xpath);
        if (parent == null) {
            throw new IOException("Could not find project/build/plugins or any of its parents in " + pomFile);
        }
        IndentedNodeTreeGenerator top = null;
        IndentedNodeTreeGenerator curr = null;
        for (String c : toCreate) {
            if (top == null) {
                top = curr = new IndentedNodeTreeGenerator(c, 1);
            } else {
                curr = top.then(c);
            }
        }
        IndentedNodeTreeGenerator app;
        if (top == null) {
            app = top = new IndentedNodeTreeGenerator("plugin", 3);
        } else {
            app = curr.then("plugin");
        }
        @SuppressWarnings("null")
        IndentedNodeTreeGenerator configNode = app.addSingletonChild("groupId", "org.antlr")
                .addSingletonChild("artifactId", "antlr4-maven-plugin")
                .addSingletonChild("version", version)
                .then("executions").then("execution")
                .addSingletonChild("id", "antlr")
                .then("goals").addSingletonChild("goal", "antlr4")
                .pop().addSingletonChild("phase", "generate-sources")
                .then("configuration");

        configNode.addSingletonChild("visitor", generateVisitor ? "true" : "false");
        configNode.addSingletonChild("listener", generateListener ? "true" : "false");
        configNode.then("options").addSingletonChild("language", "Java");
        configNode.then("arguments").addSingletonChild("argument", "-message-format")
                .addSingletonChild("argument", "gnu");
        top.go(parent, d, true);
        parent.appendChild(d.createTextNode("\n"));
        if (!hasDependency(d, "antlr4-runtime")) {
            addAntlrDependency(d, version);
        }
        return d;
    }

    private boolean hasDependency(Document doc, String artifactId) throws XPathExpressionException {
        XPathFactory fac = XPathFactory.newInstance();
        XPath xpath = fac.newXPath();
        XPathExpression findDependencyIds = xpath.compile(
                "/project/dependencies/dependency[artifactId='" + artifactId + "']");
        NodeList nl = (NodeList) findDependencyIds.evaluate(doc, XPathConstants.NODESET);
        return nl.getLength() > 0;
    }

    private void addAntlrDependency(Document doc, String version) throws IOException, XPathExpressionException {
        XPathFactory fac = XPathFactory.newInstance();
        XPath xpath = fac.newXPath();
        XPathExpression findDeps = xpath.compile(
                "/project/dependencies");

        Element depNode = doc.createElement("dependency");
        Element groupIdNode = doc.createElement("groupId");
        groupIdNode.setTextContent("org.antlr");
        Element artifactIdNode = doc.createElement("artifactId");
        artifactIdNode.setTextContent("antlr4-runtime");
        Element versionNode = doc.createElement("version");
        versionNode.setTextContent(version);
        depNode.appendChild(indentNode(doc, 3));
        depNode.appendChild(groupIdNode);
        depNode.appendChild(indentNode(doc, 3));
        depNode.appendChild(artifactIdNode);
        depNode.appendChild(indentNode(doc, 3));
        depNode.appendChild(versionNode);
        depNode.appendChild(indentNode(doc, 2));
        Node depsNode = (Node) findDeps.evaluate(doc, XPathConstants.NODE);
        if (depsNode == null) {
            Element deps = doc.createElement("dependencies");
            deps.appendChild(indentNode(doc, 2));
            deps.appendChild(depNode);
            XPathExpression findProject = xpath.compile(
                    "/project");
            Node projectNode = (Node) findProject.evaluate(doc, XPathConstants.NODE);
            projectNode.appendChild(deps);
            deps.appendChild(indentNode(doc, 2));
        } else {
            depsNode.appendChild(doc.createTextNode("    "));
            depsNode.appendChild(depNode);
            depsNode.appendChild(indentNode(doc, 1));
        }
    }

    static String stringify(Document doc) throws TransformerConfigurationException, TransformerException {
        TransformerFactory tranFactory = TransformerFactory.newInstance();
        Transformer aTransformer = tranFactory.newTransformer();
        Source src = new DOMSource(doc);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Result dest = new StreamResult(out);
        aTransformer.transform(src, dest);
        return new String(out.toByteArray(), UTF_8);
    }

    private Element findParentNode(List<String> nodes, LinkedList<String> toCreate, Document doc, XPath xpath) throws XPathExpressionException {
        if (nodes.isEmpty()) {
            return null;
        }
        String query = '/' + Strings.join('/', nodes);
        XPathExpression findNode = xpath.compile(query);
        Node nd = (Node) findNode.evaluate(doc, XPathConstants.NODE);
        if (nd instanceof Element) {
            return (Element) nd;
        }
        String last = nodes.remove(nodes.size() - 1);
        toCreate.push(last);
        return findParentNode(nodes, toCreate, doc, xpath);
    }

    public PluginConfigurationInfo pluginConfiguration(String groupId, String artifactId) throws Exception {
        PluginConfigurationInfo info = new PluginConfigurationInfo();
        Document d = getDocument();
        XPathFactory fac = XPathFactory.newInstance();
        XPath xpath = fac.newXPath();
        String query = isPomProject()
                ? "/project/build/pluginManagement/plugins/plugin/artifactId[text()='" + artifactId + "']"
                : "/project/build/plugins/plugin/artifactId[text()='antlr4-maven-plugin']";

        XPathExpression findMavenPluginArtifactId = xpath.compile(query);
        Node artifactIdNode = (Node) findMavenPluginArtifactId.evaluate(d, XPathConstants.NODE);
        if (artifactIdNode != null) {
            Node pluginNode = artifactIdNode.getParentNode();

            NodeList kids = pluginNode.getChildNodes();
            for (int i = 0; i < kids.getLength(); i++) {
                Node test = kids.item(i);
                if (test instanceof Element) {
                    Element el = (Element) test;
                    if ("groupId".equals(el.getTagName())) {
                        String gid = el.getTextContent().trim();
                        if (!groupId.equals(gid)) {
//                            System.err.println("Found plugin w/ right name " + artifactIdNode
//                                    + "' but wrong groupId: '" + gid + "' not '" + groupId + "'");
                            return info;
                        }
                    }
                }
            }

            visitConfigurationNodes(pluginNode, el -> {
                NodeList nl = el.getChildNodes();
                for (int i = 0; i < nl.getLength(); i++) {
                    Node n = nl.item(i);
                    if (n instanceof Element) {
                        Element e = (Element) n;
                        info.props.put(e.getTagName(), e.getTextContent().trim());
                    }
                }
            });
        }
        return info;
    }

    private void visitConfigurationNodes(Node pluginNode, Consumer<Element> c) {
        NodeList kids = pluginNode.getChildNodes();
        for (int i = 0; i < kids.getLength(); i++) {
            Node n = kids.item(i);
            if (n instanceof Element) {
                Element e = (Element) n;
                if ("configuration".equals(e.getTagName())) {
                    c.accept(e);
                } else if ("executions".equals(e.getTagName())) {
                    NodeList exKids = e.getChildNodes();
                    for (int j = 0; j < exKids.getLength(); j++) {
                        Node n1 = exKids.item(j);
                        if (n1 instanceof Element) {
                            Element e1 = (Element) n1;
                            if ("execution".equals(e1.getTagName())) {
                                NodeList ex2kids = e1.getChildNodes();
                                for (int k = 0; k < ex2kids.getLength(); k++) {
                                    Node n2 = ex2kids.item(k);
                                    if (n2 instanceof Element) {
                                        Element e2 = (Element) n2;
                                        if ("configuration".equals(e2.getTagName())) {
                                            c.accept(e2);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    public static class PluginConfigurationInfo {

        final Map<String, String> props = new HashMap<>();

        @Override
        public String toString() {
            return props.toString();
        }

        public boolean isEmpty() {
            return props.isEmpty();
        }
    }

    public Document setModules(Collection<String> modules) throws IOException, XPathExpressionException {
        List<String> all = new ArrayList<>(modules);
        Collections.sort(all);
        Document d = getDocument();
        XPathFactory fac = XPathFactory.newInstance();
        XPath xpath = fac.newXPath();
        XPathExpression findUrl = xpath.compile(
                "/project/modules");

        Node oldModulesNode = (Node) findUrl.evaluate(d, XPathConstants.NODE);
        NodeList kids = oldModulesNode.getChildNodes();
        for (int i = 0; i < kids.getLength(); i++) {
            oldModulesNode.removeChild(kids.item(i));
        }
        for (String s : all) {
            Element m = d.createElement("module");
            m.setTextContent(s);
            oldModulesNode.appendChild(m);
        }
        return d;
    }

    private static final void logMap(String name, Map<String, ?> m) {
        System.out.println("\n\n" + name + " ***************************\n"); // println ok
        for (Map.Entry<String, ?> e : m.entrySet()) {
            System.out.println(" " + e.getKey() + " = '" + e.getValue() + "'"); // println ok
        }
    }

    public Properties getProperties() throws Exception {
        return getProperties(getDocument());
    }

    Properties getProperties(Document d) throws Exception {
        XPathFactory fac = XPathFactory.newInstance();
        XPath xpath = fac.newXPath();
        XPathExpression findBrokenDependencies = xpath.compile(
                "/project/properties/*");
        NodeList nl = (NodeList) findBrokenDependencies.evaluate(d, XPathConstants.NODESET);
        int max = nl.getLength();
        Properties props = new Properties();
        for (int i = 0; i < max; i++) {
            Node n = nl.item(i);
            if (n instanceof Element) {
                Element el = (Element) n;
                props.setProperty(el.getTagName(), el.getTextContent());
            }
        }
        return props;
    }

    public Document setVersion(String version) throws IOException {
        return setVersion(version, false);
    }

    public Document setVersion(String version, boolean onlyIfVersionNodeExists) throws IOException {
        try {
            Document d = getDocument();
            XPathFactory fac = XPathFactory.newInstance();
            XPath xpath = fac.newXPath();
            XPathExpression findArtifactId = xpath.compile(
                    "/project/version");
            Element n = (Element) findArtifactId.evaluate(d, XPathConstants.NODE);

            Element nue = d.createElement("version");
            nue.setTextContent(version);
            Node parent;
            if (n != null) {
                parent = n.getParentNode();
                parent.replaceChild(nue, n);
            } else if (!onlyIfVersionNodeExists) {
                findArtifactId = xpath.compile(
                        "/project/artifactId");
                Node nn = (Element) findArtifactId.evaluate(d, XPathConstants.NODE);
                parent = nn.getParentNode();
                parent.insertBefore(nue, nn);
            } else {
                return null;
            }
            return d;
        } catch (XPathExpressionException ex) {
            throw new IOException(ex);
        }
    }

    public Document setParentArtifactID(String id) throws IOException {
        try {
            Document d = getDocument();
            XPathFactory fac = XPathFactory.newInstance();
            XPath xpath = fac.newXPath();
            XPathExpression findArtifactId = xpath.compile(
                    "/project/parent/artifactId");
            Element n = (Element) findArtifactId.evaluate(d, XPathConstants.NODE);

            Element nue = d.createElement("artifactId");
            nue.setTextContent(id);
            Node parent;
            if (n != null) {
                parent = n.getParentNode();
                parent.replaceChild(nue, n);
            } else {
                return null;
            }
            return d;
        } catch (XPathExpressionException ex) {
            throw new IOException(ex);
        }
    }

    public Document setParentGroupID(String id, boolean onlyIfVersionNodeExists) throws IOException {
        try {
            Document d = getDocument();
            XPathFactory fac = XPathFactory.newInstance();
            XPath xpath = fac.newXPath();
            XPathExpression findArtifactId = xpath.compile(
                    "/project/parent/groupId");
            Element n = (Element) findArtifactId.evaluate(d, XPathConstants.NODE);

            Element nue = d.createElement("groupId");
            nue.setTextContent(id);
            Node parent;
            if (n != null) {
                parent = n.getParentNode();
                parent.replaceChild(nue, n);
            } else if (!onlyIfVersionNodeExists) {
                findArtifactId = xpath.compile(
                        "/project/parent/artifactId");
                Node nn = (Element) findArtifactId.evaluate(d, XPathConstants.NODE);
                parent = nn.getParentNode();
                parent.insertBefore(nue, nn);
            } else {
                return null;
            }
            return d;
        } catch (XPathExpressionException ex) {
            throw new IOException(ex);
        }
    }

    public String getParentArtifactID() throws IOException {
        try {
            Document d = getDocument();
            XPathFactory fac = XPathFactory.newInstance();
            XPath xpath = fac.newXPath();
            XPathExpression findArtifactId = xpath.compile(
                    "/project/parent/artifactId");
            Element n = (Element) findArtifactId.evaluate(d, XPathConstants.NODE);
            return n == null ? "" : n.getTextContent().trim();
        } catch (XPathExpressionException ex) {
            throw new IOException(ex);
        }
    }

    public String getParentGroupID() throws IOException {
        try {
            Document d = getDocument();
            XPathFactory fac = XPathFactory.newInstance();
            XPath xpath = fac.newXPath();
            XPathExpression findArtifactId = xpath.compile(
                    "/project/parent/groupId");
            Element n = (Element) findArtifactId.evaluate(d, XPathConstants.NODE);
            return n == null ? "" : n.getTextContent().trim();
        } catch (XPathExpressionException ex) {
            throw new IOException(ex);
        }
    }

    private String humanize(String id) {
        char[] c = id.replace("_", " ").replace("-", " ").replace("\\.", " ").toCharArray();
        StringBuilder sb = new StringBuilder();
        boolean lastSpace = true;
        for (char cc : c) {
            boolean isSpace = cc == ' ';
            if (lastSpace && !isSpace) {
                cc = Character.toUpperCase(cc);
            }
            sb.append(cc);
            lastSpace = isSpace;
        }
        return sb.toString();
    }

    public Document setArtifactID(String id) throws IOException {
        try {
            Document d = getDocument();
            XPathFactory fac = XPathFactory.newInstance();
            XPath xpath = fac.newXPath();
            XPathExpression findArtifactId = xpath.compile(
                    "/project/artifactId");
            Element n = (Element) findArtifactId.evaluate(d, XPathConstants.NODE);
            Node parent;
            if (n != null) {
                Element nue = d.createElement("artifactId");
                nue.setTextContent(id);
                parent = n.getParentNode();
                parent.replaceChild(nue, n);

                xpath = fac.newXPath();
                findArtifactId = xpath.compile(
                        "/project/name");
                n = (Element) findArtifactId.evaluate(d, XPathConstants.NODE);
                if (n != null) {
                    nue = d.createElement("name");
                    nue.setTextContent(humanize(id));
                    parent = n.getParentNode();
                    parent.replaceChild(nue, n);
                }
            } else {
                return null;
            }
            return d;
        } catch (XPathExpressionException ex) {
            throw new IOException(ex);
        }
    }

    public Document setGroupID(String id, boolean onlyIfVersionNodeExists) throws IOException {
        try {
            Document d = getDocument();
            XPathFactory fac = XPathFactory.newInstance();
            XPath xpath = fac.newXPath();
            XPathExpression findArtifactId = xpath.compile(
                    "/project/groupId");
            Element n = (Element) findArtifactId.evaluate(d, XPathConstants.NODE);

            Element nue = d.createElement("groupId");
            nue.setTextContent(id);
            Node parent;
            if (n != null) {
                parent = n.getParentNode();
                parent.replaceChild(nue, n);
            } else if (!onlyIfVersionNodeExists) {
                findArtifactId = xpath.compile(
                        "/project/artifactId");
                Node nn = (Element) findArtifactId.evaluate(d, XPathConstants.NODE);
                parent = nn.getParentNode();
                parent.insertBefore(nue, nn);
            } else {
                return null;
            }
            return d;
        } catch (XPathExpressionException ex) {
            throw new IOException(ex);
        }
    }

    public String getGroupID() throws IOException {
        try {
            Document d = getDocument();
            XPathFactory fac = XPathFactory.newInstance();
            XPath xpath = fac.newXPath();
            XPathExpression findArtifactId = xpath.compile(
                    "/project/groupId");
            Element n = (Element) findArtifactId.evaluate(d, XPathConstants.NODE);
            if (n == null) {
                return getParentGroupID().trim();
            }
            return n.getTextContent().trim();
        } catch (XPathExpressionException ex) {
            throw new IOException(ex);
        }
    }

    public Document stripProperties(Map<String, String> result) throws IOException, XPathExpressionException {
        return findProperties(result, true);
    }

    public Document findProperties(Map<String, String> result, boolean strip) throws IOException, XPathExpressionException {
        Document d = getDocument();
        XPathFactory fac = XPathFactory.newInstance();
        XPath xpath = fac.newXPath();
        XPathExpression findArtifactId = xpath.compile(
                "/project/properties");
        Node propertiesNode = (Node) findArtifactId.evaluate(d, XPathConstants.NODE);
        if (propertiesNode == null) {
            return null;
        }
        NodeList nl = propertiesNode.getChildNodes();
        if (nl == null) {
            return null;
        }
        for (int i = 0; i < nl.getLength(); i++) {
            Node n = nl.item(i);
            if (n instanceof Element) {
                if (propertiesNode == null) {
                    propertiesNode = n.getParentNode();
                }
                Element el = (Element) n;
                String content = el.getTextContent();
                if (content != null) {
                    result.put(el.getTagName(), content);
                }
            }
        }
        if (strip && propertiesNode != null) {
            propertiesNode.getParentNode().removeChild(propertiesNode);
        }
        return d;
    }

    public Document writeProperties(Map<String, String> props) throws IOException, XPathExpressionException {
        Document d = getDocument();
        XPathFactory fac = XPathFactory.newInstance();
        XPath xpath = fac.newXPath();
        XPathExpression findArtifactId = xpath.compile(
                "/project/properties");
        Node propertiesNode = (Node) findArtifactId.evaluate(d, XPathConstants.NODE);
        Node project;
        if (propertiesNode != null) {
            project = propertiesNode.getParentNode();
            project.removeChild(propertiesNode);
        } else {
            XPathExpression x = xpath.compile(
                    "/project");
            project = (Node) x.evaluate(d, XPathConstants.NODE);
        }
        List<String> keys = new ArrayList<>(props.keySet());
        Collections.sort(keys);
        Node nue = d.createElement("properties");
        for (String key : keys) {
            String val = props.get(key);
            Element el = d.createElement(key);
            el.setTextContent(val);
            nue.appendChild(el);
        }
        project.appendChild(nue);
        return d;
    }

    public Document updateNetbeansVersion(String newVersion) throws IOException, XPathExpressionException {
        Document d = getDocument();
        XPathFactory fac = XPathFactory.newInstance();
        XPath xpath = fac.newXPath();
        XPathExpression findArtifactId = xpath.compile(
                "/project/dependencies/dependency");
        NodeList nl = (NodeList) findArtifactId.evaluate(d, XPathConstants.NODESET);
        if (nl == null) {
            return null;
        }
        boolean found = false;
        for (int i = 0; i < nl.getLength(); i++) {
            Node n = nl.item(i);
            if (n instanceof Element) {
                Element el = (Element) n;
                NodeList gids = el.getElementsByTagName("groupId");
                NodeList aids = el.getElementsByTagName("artifactId");
                NodeList vers = el.getElementsByTagName("version");
                if (gids != null && aids != null && gids.getLength() == 1 && aids.getLength() == 1 && vers.getLength() == 1) {
                    Element aid = (Element) aids.item(0);
                    Element gid = (Element) gids.item(0);
                    Element ver = (Element) vers.item(0);
                    if (gid.getTextContent().contains("openide") || gid.getTextContent().contains("netbeans")) {
                        Element nue = d.createElement("version");
                        nue.setTextContent(newVersion);
                        ver.getParentNode().removeChild(ver);
                        el.appendChild(nue);
                        found = true;
                    }
                }
            }
        }
        if (found) {
            return d;
        }
        return null;
    }

    public Document sortDependencies(boolean dependencyManagement) throws XPathExpressionException, IOException {
        Set<DepVer> deps = new HashSet<>();
        Document d = findDependencies(deps, dependencyManagement);
        List<DepVer> depList = new ArrayList<>(deps);
        Collections.sort(depList);
        if (dependencyManagement) {
            return rewriteDependencyManagementSection(depList);
        } else {
            return writeDependencies(depList);
        }
    }

    public Document writeDependencies(Collection<DepVer> s) throws IOException, XPathExpressionException {
        if (s.isEmpty()) {
            return null;
        }
        XPathFactory fac = XPathFactory.newInstance();
        XPath xpath = fac.newXPath();

        XPathExpression findDependencies = xpath.compile("/project/dependencies");
        Document d = getDocument();

        Node origDepsNode = (Node) findDependencies.evaluate(d, XPathConstants.NODE);
        if (origDepsNode != null) {
            origDepsNode.getParentNode().removeChild(origDepsNode);
        }

        XPathExpression findArtifactId = xpath.compile("/project");

        Element prj = (Element) findArtifactId.evaluate(d, XPathConstants.NODE);
        List<DepVer> all = new LinkedList<>(s);
        Collections.sort(all);
        Element e1 = d.createElement("dependencies");
        prj.appendChild(e1);
        for (DepVer dv : all) {
            Element dep = d.createElement("dependency");
            e1.appendChild(dep);
            Element gid = d.createElement("groupId");
            gid.setTextContent(dv.groupId);
            dep.appendChild(gid);

            Element aid = d.createElement("artifactId");
            aid.setTextContent(dv.artifactId);
            dep.appendChild(aid);

            Element vid = d.createElement("version");
            vid.setTextContent(dv.version);
            dep.appendChild(vid);
        }
        return d;
    }

    public Document rewriteDependencyManagementSection(Collection<DepVer> s) throws IOException, XPathExpressionException {
        XPathFactory fac = XPathFactory.newInstance();
        XPath xpath = fac.newXPath();
        XPathExpression findDependencies = xpath.compile("/project/dependencyManagement");
        Document d = getDocument();

        Node origDepsNode = (Node) findDependencies.evaluate(d, XPathConstants.NODE);
        if (origDepsNode != null) {
            origDepsNode.getParentNode().removeChild(origDepsNode);
        }

        XPathExpression findArtifactId = xpath.compile("/project");

        Node prj = (Node) findArtifactId.evaluate(d, XPathConstants.NODE);
        List<DepVer> all = new LinkedList<>(s);
        Collections.sort(all);
        Element e = d.createElement("dependencyManagement");
        prj.appendChild(e);
        Element e1 = d.createElement("dependencies");
        e.appendChild(e1);
        for (DepVer dv : all) {
            Element dep = d.createElement("dependency");
            e1.appendChild(dep);
            Element gid = d.createElement("groupId");
            gid.setTextContent(dv.groupId);
            dep.appendChild(gid);

            Element aid = d.createElement("artifactId");
            aid.setTextContent(dv.artifactId);
            dep.appendChild(aid);

            Element vid = d.createElement("version");
            vid.setTextContent(dv.version);
            dep.appendChild(vid);
        }
        return d;
    }

    public Document writeDependencyManagementSection(Collection<DepVer> s) throws IOException, XPathExpressionException {
        Document d = removeDependencyVersions(s, true);
        if (d == null) {
            d = getDocument();
        }
        XPathFactory fac = XPathFactory.newInstance();
        XPath xpath = fac.newXPath();
        XPathExpression findArtifactId = xpath.compile("/project");

        Node prj = (Node) findArtifactId.evaluate(d, XPathConstants.NODE);
        List<DepVer> all = new LinkedList<>(s);
        Collections.sort(all);
        Element e = d.createElement("dependencyManagement");
        prj.appendChild(e);
        Element e1 = d.createElement("dependencies");
        e.appendChild(e1);
        for (DepVer dv : all) {
            Element dep = d.createElement("dependency");
            e1.appendChild(dep);
            Element gid = d.createElement("groupId");
            gid.setTextContent(dv.groupId);
            dep.appendChild(gid);

            Element aid = d.createElement("artifactId");
            aid.setTextContent(dv.artifactId);
            dep.appendChild(aid);

            Element vid = d.createElement("version");
            vid.setTextContent(dv.version);
            dep.appendChild(vid);
        }
        return d;
    }

    public Document renameProject(String groupId, String oldArtifactId, String newArtifactId) throws IOException, XPathExpressionException {
        if (oldArtifactId.equals(getArtifactId()) && groupId.equals(getGroupID())) {
            return setArtifactID(newArtifactId);
        } else {
            return replaceDependency(groupId, oldArtifactId, groupId, newArtifactId);
        }
    }

    private static final String DUMMY_VERSION = "0.0.0-?";

    public Document normalizeDependencies(boolean dependencyManagement) throws XPathExpressionException, IOException {
        Document d = getDocument();
        XPathFactory fac = XPathFactory.newInstance();
        XPath xpath = fac.newXPath();
        XPathExpression findArtifactId = xpath.compile(
                dependencyManagement ? "/project/dependencyManagement/dependencies/dependency" : "/project/dependencies/dependency");
        NodeList nl = (NodeList) findArtifactId.evaluate(d, XPathConstants.NODESET);
        boolean modified = false;
        for (int i = 0; i < nl.getLength(); i++) {
            Node n = nl.item(i);
            if (n instanceof Element) {
                Element el = (Element) n;
                NodeList gids = el.getElementsByTagName("groupId");
                NodeList aids = el.getElementsByTagName("artifactId");
                NodeList vers = el.getElementsByTagName("version");
                if (gids != null && aids != null && gids.getLength() == 1 && aids.getLength() == 1) {
                    Element aid = (Element) aids.item(0);
                    Element gid = (Element) gids.item(0);

                    String aidS = aid.getTextContent().trim();
                    String gidS = gid.getTextContent().trim();
                    boolean updateGroupId = false;
                    boolean updateArtifactId = false;
                    boolean updateVersion = false;
                    if (gidS.equals(getGroupID()) || gidS.equals("${groupId}")) {
                        gidS = "${project.groupId}";
                        updateGroupId = true;
                    }
                    if (aidS.equals(getArtifactId()) || aidS.equals("${artifactId}")) {
                        aidS = "${project.artifactId}";
                        updateArtifactId = true;
                    }
                    String verS = DUMMY_VERSION;
                    if (vers != null && vers.getLength() == 1) {
                        if ("${project.groupId".equals(gidS)) {
                            Element ver = (Element) vers.item(0);
                            verS = ver.getTextContent().trim();
                            if (getVersion().equals(verS) || "${version}".equals(verS)) {
                                verS = "${project.version}";
                            }
                        }
                    }
                    if (updateGroupId) {
                        gid.setTextContent(gidS);
                    }
                    if (updateArtifactId) {
                        aid.setTextContent(aidS);
                    }
                    if (updateVersion) {
                        Element ver = (Element) vers.item(0);
                        ver.setTextContent(verS);
                    }
                    modified |= updateGroupId | updateArtifactId | updateVersion;
                }
            }
        }
        return modified ? d : null;
    }

    public String getPackaging() throws XPathExpressionException, IOException {
        Document d = getDocument();
        XPathFactory fac = XPathFactory.newInstance();
        XPath xpath = fac.newXPath();
        XPathExpression findArtifactId = xpath.compile("/project/packaging");
        NodeList nl = (NodeList) findArtifactId.evaluate(d, XPathConstants.NODESET);
        for (int i = 0; i < nl.getLength(); i++) {
            Node n = nl.item(i);
            return n.getTextContent();
        }
        return null;
    }

    public Document flattenDependencies(PropertyResolver prop) throws XPathExpressionException, IOException {
        Set<DepVer> deps = new HashSet<>();
        findRecursiveDependencies(prop, deps);
        boolean[] sent = new boolean[1];
        if (!deps.isEmpty()) {
            rewriteDependencies((Set<DepVer> t) -> {
                t.clear();
                if (!sent[0]) {
                    t.addAll(deps);
                    sent[0] = true;
                } else {
                    t.clear();
                }
            });
        }
        return null;
    }

    public void visitDependencies(Consumer<DepVer> cons) throws Exception {
        Document d = getDocument();
        XPathFactory fac = XPathFactory.newInstance();
        XPath xpath = fac.newXPath();
        String[] paths = new String[]{"/project/dependencyManagement/dependencies/dependency", "/project/dependencies/dependency"};
        boolean found = false;
        for (String path : paths) {
            XPathExpression findIt = xpath.compile(path);
            NodeList nl = (NodeList) findIt.evaluate(d, XPathConstants.NODESET);
            for (int i = 0; i < nl.getLength(); i++) {
                Node n = nl.item(i);
                if (n instanceof Element) {
                    Element el = (Element) n;
                    NodeList gids = el.getElementsByTagName("groupId");
                    NodeList aids = el.getElementsByTagName("artifactId");
                    NodeList vers = el.getElementsByTagName("version");
                    NodeList scopes = el.getElementsByTagName("scope");
                    if (gids != null && aids != null && gids.getLength() == 1 && aids.getLength() == 1) {
                        Element aid = (Element) aids.item(0);
                        Element gid = (Element) gids.item(0);
                        Element sco = scopes.getLength() > 0 ? (Element) scopes.item(0) : null;
                        String aidS = aid.getTextContent().trim();
                        String gidS = gid.getTextContent().trim();
                        String scope = sco == null ? null : sco.getTextContent();

                        if (gidS.equals("${project.groupId}") || gidS.equals("${groupId}")) {
                            gidS = getGroupID();
                        }
                        if (aidS.equals("${project.artifactId}") || aidS.equals("${artifactId}")) {
                            aidS = getArtifactId();
                        }
                        String verS = NO_VERSION;
                        if (vers != null && vers.getLength() == 1) {
                            Element ver = (Element) vers.item(0);
                            verS = ver.getTextContent().trim();
                            if (verS.equals("${version}") || verS.equals("${project.version}")) {
                                verS = getVersion();
                            }
                        }

                        DepVer dv = new DepVer(gidS, aidS, verS, scope);
                        cons.accept(dv);
                    }
                }
            }
        }
    }

    public void findRecursiveDependencies(PropertyResolver properties, Set<DepVer> all) throws XPathExpressionException, IOException {
        rewriteDependencies(new Consumer<Set<DepVer>>() {
            @Override
            public void accept(Set<DepVer> t) {
                for (DepVer d : t) {
                    try {
                        if (all.contains(d)) {
                            continue;
                        }
                        File pom = properties.resolvePomWithSubstitutions(d.artifactId, d.groupId, d.version);
                        if (pom != null) {
                            PomFileAnalyzer ana = new PomFileAnalyzer(pom);
                            if (!all.contains(ana.toDepVer()) && !toDepVer().equals(ana.toDepVer())) {
                                all.add(d);
                                ana.findRecursiveDependencies(properties, all);
                            }
                        }
                        all.add(d);
                    } catch (IOException | XPathExpressionException ex) {
                        throw new Error(ex);
                    }
                }
            }
        });
    }

    public Document rewriteDependencies(Consumer<Set<DepVer>> consumer) throws XPathExpressionException, IOException {
        Document d = getDocument();
        XPathFactory fac = XPathFactory.newInstance();
        XPath xpath = fac.newXPath();
        String[] paths = new String[]{"/project/dependencyManagement/dependencies/dependency", "/project/dependencies/dependency"};
        boolean found = false;
        for (String path : paths) {
            XPathExpression findIt = xpath.compile(path);
            NodeList nl = (NodeList) findIt.evaluate(d, XPathConstants.NODESET);
            for (int i = 0; i < nl.getLength(); i++) {
                Node n = nl.item(i);
                if (n instanceof Element) {
                    Element el = (Element) n;
                    NodeList gids = el.getElementsByTagName("groupId");
                    NodeList aids = el.getElementsByTagName("artifactId");
                    NodeList vers = el.getElementsByTagName("version");
                    NodeList scopes = el.getElementsByTagName("scope");
                    if (gids != null && aids != null && gids.getLength() == 1 && aids.getLength() == 1) {
                        Element aid = (Element) aids.item(0);
                        Element gid = (Element) gids.item(0);
                        Element sco = scopes.getLength() > 0 ? (Element) scopes.item(0) : null;
                        String aidS = aid.getTextContent().trim();
                        String gidS = gid.getTextContent().trim();
                        String scope = sco == null ? null : sco.getTextContent();

                        if (gidS.equals("${project.groupId}") || gidS.equals("${groupId}")) {
                            gidS = getGroupID();
                        }
                        if (aidS.equals("${project.artifactId}") || aidS.equals("${artifactId}")) {
                            aidS = getArtifactId();
                        }
                        String verS = NO_VERSION;
                        if (vers != null && vers.getLength() == 1) {
                            Element ver = (Element) vers.item(0);
                            verS = ver.getTextContent().trim();
                            if (verS.equals("${version}") || verS.equals("${project.version}")) {
                                verS = getVersion();
                            }
                        }

                        DepVer dv = new DepVer(gidS, aidS, verS, scope);
                        Set<DepVer> s = immutableSetOf(dv);
                        consumer.accept(s);
                        if (s.size() != 1 || s.iterator().next() != dv) {
                            found = true;
                            Node par = n.getParentNode();
                            par.removeChild(n);
                            List<DepVer> l = new ArrayList<>(s);
                            Collections.sort(l);
                            for (DepVer dep : l) {
                                Element newDep = d.createElement("dependency");
                                Element newGroupId = d.createElement("groupId");
                                newGroupId.setTextContent(dep.groupId);
                                newDep.appendChild(newGroupId);
                                Element newArtifactId = d.createElement("artifactId");
                                newArtifactId.setTextContent(dep.artifactId);
                                newDep.appendChild(newArtifactId);
                                if (dep.version != null && !"0.0.0-?".equals(dep.version)) {
                                    Element newVersion = d.createElement("version");
                                    newVersion.setTextContent(dep.version);
                                    newDep.appendChild(newVersion);
                                }
                                if (dep.scope != null) {
                                    Element newScope = d.createElement("scope");
                                    newScope.setTextContent(dep.scope);;
                                    newDep.appendChild(newScope);
                                }
                                par.appendChild(newDep);
                            }
                        }
                    }
                }
            }
        }
        return found ? d : null;
    }
    public static final String NO_VERSION = "0.0.0-?";

    public Document findDependencies(Set<DepVer> deps, boolean dependencyManagement) throws XPathExpressionException, IOException {
        Document d = getDocument();
        XPathFactory fac = XPathFactory.newInstance();
        XPath xpath = fac.newXPath();
        XPathExpression findArtifactId = xpath.compile(
                dependencyManagement ? "/project/dependencyManagement/dependencies/dependency" : "/project/dependencies/dependency");
        NodeList nl = (NodeList) findArtifactId.evaluate(d, XPathConstants.NODESET);
        boolean found = false;
        for (int i = 0; i < nl.getLength(); i++) {
            Node n = nl.item(i);
            if (n instanceof Element) {
                Element el = (Element) n;
                NodeList gids = el.getElementsByTagName("groupId");
                NodeList aids = el.getElementsByTagName("artifactId");
                NodeList vers = el.getElementsByTagName("version");
                if (gids != null && aids != null && gids.getLength() == 1 && aids.getLength() == 1) {
                    Element aid = (Element) aids.item(0);
                    Element gid = (Element) gids.item(0);

                    String aidS = aid.getTextContent().trim();
                    String gidS = gid.getTextContent().trim();

                    if (gidS.equals("${project.groupId}") || gidS.equals("${groupId}")) {
                        gidS = getGroupID();
                    }
                    if (aidS.equals("${project.artifactId}") || aidS.equals("${artifactId}")) {
                        aidS = getArtifactId();
                    }
                    String verS = "0.0.0-?";
                    if (vers != null && vers.getLength() == 1) {
                        Element ver = (Element) vers.item(0);
                        verS = ver.getTextContent().trim();
                        if (verS.equals("${version}") || verS.equals("${project.version}")) {
                            verS = getVersion();
                        }
                        if (!dependencyManagement) {
                            ver.getParentNode().removeChild(ver);
                            found = true;
                        }
                    }

                    DepVer dv = new DepVer(gidS, aidS, verS);
                    deps.add(dv);
                }
            }
        }
        return found ? d : null;
    }

    public Document removeDependencyVersions(Collection<DepVer> deps, boolean dependencyManagement) throws IOException, XPathExpressionException {
        Document d = getDocument();
        XPathFactory fac = XPathFactory.newInstance();
        XPath xpath = fac.newXPath();
        XPathExpression findArtifactId = xpath.compile(
                dependencyManagement ? "/project/dependencyManagement/dependencies/dependency" : "/project/dependencies/dependency");
        NodeList nl = (NodeList) findArtifactId.evaluate(d, XPathConstants.NODESET);
        boolean found = false;
        for (int i = 0; i < nl.getLength(); i++) {
            Node n = nl.item(i);
            if (n instanceof Element) {
                Element el = (Element) n;
                NodeList gids = el.getElementsByTagName("groupId");
                NodeList aids = el.getElementsByTagName("artifactId");
                NodeList vers = el.getElementsByTagName("version");
                if (gids != null && aids != null && gids.getLength() == 1 && aids.getLength() == 1 && vers.getLength() == 1) {
                    Element aid = (Element) aids.item(0);
                    Element gid = (Element) gids.item(0);
                    Element ver = (Element) vers.item(0);
                    DepVer dv = new DepVer(gid.getTextContent(), aid.getTextContent(), ver.getTextContent());
                    deps.add(dv);
                    if (!dependencyManagement) {
                        ver.getParentNode().removeChild(ver);
                        found = true;
                    }
                }
            }
        }
        if (dependencyManagement) {
            findArtifactId = xpath.compile(
                    "/project/dependencyManagement");
            Element e = (Element) findArtifactId.evaluate(d, XPathConstants.NODE);
            if (e != null) {
                e.getParentNode().removeChild(e);
                found = true;
            }

        }
        if (found) {
            return d;
        }
        return null;
    }

    public Document renameModule(String old, String nue) throws IOException, XPathExpressionException {
        Document d = getDocument();
        XPathFactory fac = XPathFactory.newInstance();
        XPath xpath = fac.newXPath();
        XPathExpression findArtifactId = xpath.compile(
                "/project/modules/module");
        NodeList nl = (NodeList) findArtifactId.evaluate(d, XPathConstants.NODESET);
        boolean found = false;
        for (int i = 0; i < nl.getLength(); i++) {
            Element e = (Element) nl.item(i);
            if (old.equals(e.getTextContent())) {
                Element replacement = d.createElement("module");
                replacement.setTextContent(nue);
                e.getParentNode().replaceChild(replacement, e);
                found = true;
            }
        }
        return found ? d : null;
    }

    public Document replaceDependency(String oldGroupId, String oldArtifactID, String newGroupId, String newArtifactId) throws IOException, XPathExpressionException {
        Document d = getDocument();
        XPathFactory fac = XPathFactory.newInstance();
        XPath xpath = fac.newXPath();
        XPathExpression findArtifactId = xpath.compile(
                "/project/dependencies/dependency");
        NodeList nl = (NodeList) findArtifactId.evaluate(d, XPathConstants.NODESET);
        if (nl == null) {
            return null;
        }
        boolean found = false;
        for (int i = 0; i < nl.getLength(); i++) {
            Node n = nl.item(i);
            if (n instanceof Element) {
                Element el = (Element) n;
                NodeList gids = el.getElementsByTagName("groupId");
                NodeList aids = el.getElementsByTagName("artifactId");
                if (gids != null && aids != null && gids.getLength() == 1 && aids.getLength() == 1) {
                    Element aid = (Element) aids.item(0);
                    Element gid = (Element) gids.item(0);
                    if (oldGroupId.equals(gid.getTextContent()) && oldArtifactID.equals(aid.getTextContent())) {
                        Element newAid = d.createElement("artifactId");
                        newAid.setTextContent(newArtifactId);
                        Element newGid = d.createElement("groupId");
                        newGid.setTextContent(newGroupId);
                        el.removeChild(aid);
                        el.removeChild(gid);
                        el.appendChild(newGid);
                        el.appendChild(d.createTextNode("\n"));
                        el.appendChild(newAid);
                        found = true;
                    }
                }
            }
        }
        if (found) {
            return d;
        }
        return null;
    }

    public Document replaceDependencyVersion(Document d, String oldGroupId, String oldArtifactID, String version) throws IOException, XPathExpressionException {
        replaceDependencyVersion(d, oldArtifactID, oldArtifactID, version, false);
        d = replaceDependencyVersion(d, oldArtifactID, oldArtifactID, version, true);
        return d;
    }

    public Document removePackgingIfJar() throws IOException, XPathExpressionException {
        Document d = getDocument();
        XPathFactory fac = XPathFactory.newInstance();
        XPath xpath = fac.newXPath();
        XPathExpression findDependencies = xpath.compile(
                "/project/packaging");
        NodeList nl = (NodeList) findDependencies.evaluate(d, XPathConstants.NODESET);
        if (nl.getLength() > 0) {
            for (int i = 0; i < nl.getLength(); i++) {
                Node n = nl.item(i);
                if ("jar".equals(n.getTextContent())) {
                    n.getParentNode().removeChild(n);
                }
            }
        }
        return d;
    }

    private Node indentNode(Document d, int amt) {
        char[] c = new char[(amt * 4) + 1];
        Arrays.fill(c, ' ');
        c[0] = '\n';
        return d.createTextNode(new String(c));
    }

    private Node createLicensesNode(String name, String url, Document d) {
        Element licenses = d.createElement("licenses");
        Element license = d.createElement("license");
        licenses.appendChild(indentNode(d, 2));
        licenses.appendChild(license);
        Element licenseName = d.createElement("name");
        licenseName.setTextContent(name);
        license.appendChild(indentNode(d, 3));
        license.appendChild(licenseName);
        Element licenseUrl = d.createElement("url");
        licenseUrl.setTextContent(url);
        license.appendChild(indentNode(d, 3));
        license.appendChild(licenseUrl);
        Element licenseDist = d.createElement("distribution");
        licenseDist.setTextContent("repo");
        license.appendChild(indentNode(d, 3));
        license.appendChild(licenseDist);
        license.appendChild(indentNode(d, 2));
        licenses.appendChild(indentNode(d, 1));
        return licenses;
    }

    private File gitFile(File dir) {
        if (dir == null) {
            throw new IllegalStateException(".git not found below " + pomFile);
        }
        File f = new File(dir, ".git");
        if (f.exists()) {
            return f;
        }
        return gitFile(dir.getParentFile());
    }

    private File gitConfigFile() throws IOException {
        File f = gitFile(pomFile.getParentFile());
        if (f.isDirectory()) {
            File config = new File(f, "config");
            if (!config.exists()) {
                throw new IllegalStateException("no git config file " + config);
            }
            return config;
        } else {
            List<String> lines = Files.readAllLines(f.toPath());
            if (!lines.isEmpty()) {
                for (String line : lines) {
                    if (line.startsWith("gitdir: ")) {
                        String path = line.substring("gitdir: ".length()).trim();
                        File dir = new File(f.getParent(), path);
                        if (!dir.exists()) {
                            throw new IllegalStateException("Dir referenced from '" + path + "' in " + f + " does not exist");
                        }
                        if (!dir.isDirectory()) {
                            throw new IllegalStateException("Dir referenced from '" + path + "' in " + f + " exists but is not a directory");
                        }
                        File config = new File(dir, "config");
                        if (!config.exists()) {
                            throw new IllegalStateException("Config file '" + config + "' referenced from '" + path + "' in " + f + " does not exist");
                        }
                        if (!config.isFile()) {
                            throw new IllegalStateException("Config file '" + config + "' referenced from '" + path + "' in " + f + " is not a file");
                        }
                        return config;
                    }
                }
            } else {
                throw new IllegalStateException(f + " is a file but is empty");
            }
        }
        throw new IllegalStateException("Could not find a git config file under " + pomFile);
    }

    static final Pattern URL_PAT = Pattern.compile("^url.*?=\\s*(\\S+)$");

    private String gitUrl() throws IOException {
        File configFile = gitConfigFile();
        List<String> lines = Files.readAllLines(configFile.toPath());
        boolean inOrigin = false;
        for (String line : lines) {
            line = line.trim();
            if (!line.isEmpty() && line.charAt(0) == '[') {
                inOrigin = line.contains("remote \"origin\"");
            } else if (!line.isEmpty() && inOrigin) {
                Matcher m = URL_PAT.matcher(line);
                if (m.find()) {
                    return m.group(1);
                }
            }
        }
        throw new IllegalStateException("Could not find origin url in " + configFile + ": " + lines);
    }

    private String projectUrl() throws IOException {
        String res = gitUrl().trim();
        if (res.endsWith(".git")) {
            res = res.substring(0, res.length() - 4);
        }
        if (res.startsWith("git@")) {
            return "https://" + res.substring(4);
        }
        if (res.startsWith("https://github.com:")) {
            return "https://github.com/" + res.substring("https://github.com:".length());
        }
        return res;
    }

    private String scmUrl() throws IOException {
        String result = gitUrl();
        return result;
    }

    private String connectionScmUrl(String url) {
        if (url.startsWith("git@")) {
            url = url.substring(4);
            url = "https://" + url;
        }
        return "scm:git:" + url;
    }

    private String developerConnectionScmUrl(String url) {
        return Strings.literalReplaceAll("https://", "git@", url);
    }

    private Node createProjectUrlNode(Document d) throws IOException {
        Element result = d.createElement("url");
        result.setTextContent(projectUrl());
        return result;
    }

    private Node createScmNode(Document d) throws IOException {
        String url = scmUrl();
        Element scm = d.createElement("scm");
        Element urlEl = d.createElement("url");
        urlEl.setTextContent(url);
        scm.appendChild(indentNode(d, 2));
        scm.appendChild(urlEl);
        Element connection = d.createElement("connection");
        connection.setTextContent(connectionScmUrl(url));
        scm.appendChild(indentNode(d, 2));
        scm.appendChild(connection);
        Element developerConnection = d.createElement("developerConnection");
        developerConnection.setTextContent(developerConnectionScmUrl(url));
        scm.appendChild(indentNode(d, 2));
        scm.appendChild(developerConnection);
        scm.appendChild(indentNode(d, 1));
        return scm;
    }

    private Node createGithubIssueManagementNode(Document d) throws IOException {
        String url = projectUrl() + "/issues";
        if (url.startsWith("https://github.com:")) {
            url = "https://github.com/" + url.substring("https://github.com:".length());
        }
        Element issueManagement = d.createElement("issueManagement");
        Element urlEl = d.createElement("system");
        urlEl.setTextContent("Github");
        issueManagement.appendChild(indentNode(d, 2));
        issueManagement.appendChild(urlEl);
        Element connection = d.createElement("url");
        connection.setTextContent(url);
        issueManagement.appendChild(indentNode(d, 2));
        issueManagement.appendChild(connection);
        issueManagement.appendChild(indentNode(d, 1));
        return issueManagement;
    }

    private Node createOrganizationNode(Document d, String org, String url) throws IOException {
        Element organization = d.createElement("organization");
        Element nameEl = d.createElement("name");
        nameEl.setTextContent(org);
        organization.appendChild(indentNode(d, 2));
        organization.appendChild(nameEl);
        Element urlEl = d.createElement("url");
        urlEl.setTextContent(url);
        organization.appendChild(indentNode(d, 2));
        organization.appendChild(urlEl);
        organization.appendChild(indentNode(d, 1));
        return organization;
    }

    public Document replaceProjectURL() throws IOException, XPathExpressionException {
        return replaceProjectURL("artifactId");
    }

    public Document removeDuplicates(String... names) throws IOException, XPathExpressionException {
        Document d = getDocument();
        XPathFactory fac = XPathFactory.newInstance();
        XPath xpath = fac.newXPath();
        boolean modified = false;
        for (String name : names) {
            XPathExpression findLicenses = xpath.compile(
                    "/project/" + name);
            NodeList nl = (NodeList) findLicenses.evaluate(d, XPathConstants.NODESET);
            for (int i = 1; i < nl.getLength(); i++) {
                Node n = nl.item(i);
                n.getParentNode().removeChild(n);
                modified = true;
            }
        }
        return !modified ? null : d;
    }

    public Document replaceProjectURL(String insertAfterNode) throws IOException, XPathExpressionException {
        Document d = getDocument();
        XPathFactory fac = XPathFactory.newInstance();
        XPath xpath = fac.newXPath();
        XPathExpression findLicenses = xpath.compile(
                "/project/url");
        NodeList nl = (NodeList) findLicenses.evaluate(d, XPathConstants.NODESET);
        Element parent = null;
        Element replace = null;
        Element insertAfter = null;
        if (nl != null && nl.getLength() > 0) {
            replace = (Element) nl.item(0);
            parent = (Element) replace.getParentNode();
        } else {
            findLicenses = xpath.compile(
                    "/project/" + insertAfterNode);
            nl = (NodeList) findLicenses.evaluate(d, XPathConstants.NODESET);
            insertAfter = (Element) nl.item(0);
            if (insertAfter == null) {
                findLicenses = xpath.compile(
                        "/project/" + "artifactId");
                nl = (NodeList) findLicenses.evaluate(d, XPathConstants.NODESET);
                insertAfter = (Element) nl.item(0);
            }
            parent = (Element) insertAfter.getParentNode();
        }
        if (replace != null) {
            parent.replaceChild(createProjectUrlNode(d), replace);
        } else {
            nl = parent.getChildNodes();
            int max = nl.getLength();
            List<Element> els = new ArrayList<>(max);
            for (int i = 0; i < max; i++) {
                Node n = nl.item(i);
                if (n instanceof Element) {
                    els.add((Element) n);
                }
            }
            int ix = indexOf(insertAfter, els);
            if (ix == -1 || ix == els.size() - 1) {
                parent.appendChild(createProjectUrlNode(d));
            } else {
                Element before = els.get(ix + 1);
                parent.insertBefore(createProjectUrlNode(d), before);
                parent.insertBefore(indentNode(d, 1), before);
            }
        }
        return d;
    }

    public Document replaceIssueManagementURL(String insertAfterNode) throws IOException, XPathExpressionException {
        Document d = getDocument();
        XPathFactory fac = XPathFactory.newInstance();
        XPath xpath = fac.newXPath();
        XPathExpression findLicenses = xpath.compile(
                "/project/issueManagement");
        NodeList nl = (NodeList) findLicenses.evaluate(d, XPathConstants.NODESET);
        Element parent = null;
        Element replace = null;
        Element insertAfter = null;
        if (nl != null && nl.getLength() > 0) {
            replace = (Element) nl.item(0);
            parent = (Element) replace.getParentNode();
        } else {
            findLicenses = xpath.compile(
                    "/project/" + insertAfterNode);
            nl = (NodeList) findLicenses.evaluate(d, XPathConstants.NODESET);
            insertAfter = (Element) nl.item(0);
            if (insertAfter == null) {
                findLicenses = xpath.compile(
                        "/project/" + "artifactId");
                nl = (NodeList) findLicenses.evaluate(d, XPathConstants.NODESET);
                insertAfter = (Element) nl.item(0);
            }
            parent = (Element) insertAfter.getParentNode();
        }
        if (replace != null) {
            parent.replaceChild(createGithubIssueManagementNode(d), replace);
        } else {
            nl = parent.getChildNodes();
            int max = nl.getLength();
            List<Element> els = new ArrayList<>(max);
            for (int i = 0; i < max; i++) {
                Node n = nl.item(i);
                if (n instanceof Element) {
                    els.add((Element) n);
                }
            }
            int ix = indexOf(insertAfter, els);
            if (ix == -1 || ix == els.size() - 1) {
                parent.appendChild(createGithubIssueManagementNode(d));
            } else {
                Element before = els.get(ix + 1);
                parent.insertBefore(createGithubIssueManagementNode(d), before);
                parent.insertBefore(indentNode(d, 1), before);
            }
        }
        return d;
    }

    private int indexOf(Element e, List<Element> in) {
        int ix = 0;
        for (Element i : in) {
            if (e.getNodeName().equals(i.getNodeName())) {
                return ix;
            }
            ix++;
        }
        return -1;
    }

    public Document replaceOrganizationSection(String insertAfterNode, String org, String url) throws IOException, XPathExpressionException {
        Document d = getDocument();
        XPathFactory fac = XPathFactory.newInstance();
        XPath xpath = fac.newXPath();
        XPathExpression findLicenses = xpath.compile(
                "/project/organization");
        NodeList nl = (NodeList) findLicenses.evaluate(d, XPathConstants.NODESET);
        Element parent = null;
        Element replace = null;
        Element insertAfter = null;
        if (nl != null && nl.getLength() > 0) {
            replace = (Element) nl.item(0);
            parent = (Element) replace.getParentNode();
        } else {
            findLicenses = xpath.compile(
                    "/project/" + insertAfterNode);
            nl = (NodeList) findLicenses.evaluate(d, XPathConstants.NODESET);
            insertAfter = (Element) nl.item(0);
            if (insertAfter == null) {
                findLicenses = xpath.compile(
                        "/project/" + "artifactId");
                nl = (NodeList) findLicenses.evaluate(d, XPathConstants.NODESET);
                insertAfter = (Element) nl.item(0);
            }
            parent = (Element) insertAfter.getParentNode();
        }
        if (replace != null) {
            parent.replaceChild(createOrganizationNode(d, org, url), replace);
        } else {
            nl = parent.getChildNodes();
            int max = nl.getLength();
            List<Element> els = new ArrayList<>(max);
            for (int i = 0; i < max; i++) {
                Node n = nl.item(i);
                if (n instanceof Element) {
                    els.add((Element) n);
                }
            }
            int ix = indexOf(insertAfter, els);
            if (ix == -1 || ix == els.size() - 1) {
                parent.appendChild(createOrganizationNode(d, org, url));
            } else {
                Element before = els.get(ix + 1);
                parent.insertBefore(createOrganizationNode(d, org, url), before);
                parent.insertBefore(indentNode(d, 1), before);
            }
        }
        return d;
    }

    public Document replaceScmSection() throws IOException, XPathExpressionException {
        return replaceScmSection("artifactId");
    }

    public Document replaceScmSection(String insertAfterNode) throws IOException, XPathExpressionException {
        Document d = getDocument();
        XPathFactory fac = XPathFactory.newInstance();
        XPath xpath = fac.newXPath();
        XPathExpression findLicenses = xpath.compile(
                "/project/scm");
        NodeList nl = (NodeList) findLicenses.evaluate(d, XPathConstants.NODESET);
        Element parent = null;
        Element replace = null;
        Element insertAfter = null;
        if (nl != null && nl.getLength() > 0) {
            replace = (Element) nl.item(0);
            parent = (Element) replace.getParentNode();
        } else {
            findLicenses = xpath.compile(
                    "/project/" + insertAfterNode);
            nl = (NodeList) findLicenses.evaluate(d, XPathConstants.NODESET);
            insertAfter = (Element) nl.item(0);
            if (insertAfter == null) {
                findLicenses = xpath.compile(
                        "/project/" + "artifactId");
                nl = (NodeList) findLicenses.evaluate(d, XPathConstants.NODESET);
                insertAfter = (Element) nl.item(0);
            }
            parent = (Element) insertAfter.getParentNode();
        }
        if (replace != null) {
            parent.replaceChild(createScmNode(d), replace);
        } else {
            nl = parent.getChildNodes();
            int max = nl.getLength();
            List<Element> els = new ArrayList<>(max);
            for (int i = 0; i < max; i++) {
                Node n = nl.item(i);
                if (n instanceof Element) {
                    els.add((Element) n);
                }
            }
            int ix = indexOf(insertAfter, els);
            if (ix == -1 || ix == els.size() - 1) {
                parent.appendChild(createScmNode(d));
            } else {
                Element before = els.get(ix + 1);
                parent.insertBefore(createScmNode(d), before);
                parent.insertBefore(indentNode(d, 1), before);
            }
        }
        return d;
    }

    public Document replaceLicensesSection(String name, String url) throws IOException, XPathExpressionException {
        return replaceLicensesSection("artifactId", name, url);
    }

    public Document replaceLicensesSection(String afterNode, String name, String url) throws IOException, XPathExpressionException {
        Document d = getDocument();
        XPathFactory fac = XPathFactory.newInstance();
        XPath xpath = fac.newXPath();
        XPathExpression findLicenses = xpath.compile(
                "/project/licenses");
        NodeList nl = (NodeList) findLicenses.evaluate(d, XPathConstants.NODESET);
        Element parent = null;
        Element replace = null;
        Element insertAfter = null;
        if (nl != null && nl.getLength() > 0) {
            replace = (Element) nl.item(0);
            parent = (Element) replace.getParentNode();
        } else {
            findLicenses = xpath.compile(
                    "/project/" + afterNode);
            nl = (NodeList) findLicenses.evaluate(d, XPathConstants.NODESET);
            insertAfter = (Element) nl.item(0);
            if (insertAfter == null) {
                findLicenses = xpath.compile(
                        "/project/" + "artifactId");
                nl = (NodeList) findLicenses.evaluate(d, XPathConstants.NODESET);
                insertAfter = (Element) nl.item(0);
            }
            parent = (Element) insertAfter.getParentNode();
        }
        if (replace != null) {
            parent.replaceChild(createLicensesNode(name, url, d), replace);
        } else {
            nl = parent.getChildNodes();
            int max = nl.getLength();
            List<Element> els = new ArrayList<>(max);
            for (int i = 0; i < max; i++) {
                Node n = nl.item(i);
                if (n instanceof Element) {
                    els.add((Element) n);
                }
            }
            int ix = indexOf(insertAfter, els);
            if (ix == -1 || ix == els.size() - 1) {
                parent.appendChild(createLicensesNode(name, url, d));
            } else {
                Element before = els.get(ix + 1);
                parent.insertBefore(createLicensesNode(name, url, d), before);
                parent.insertBefore(indentNode(d, 1), before);
            }
        }
        return d;
    }

    private Document replaceDependencyVersion(Document dd, String oldGroupId, String oldArtifactID, String version, boolean dependencyManagement) throws IOException, XPathExpressionException {
        Document d = dd == null ? getDocument() : dd;
        XPathFactory fac = XPathFactory.newInstance();
        XPath xpath = fac.newXPath();
        XPathExpression findArtifactId = xpath.compile(
                dependencyManagement ? "/project/dependencyManagement/dependencies/dependency" : "/project/dependencies/dependency");
        NodeList nl = (NodeList) findArtifactId.evaluate(d, XPathConstants.NODESET);
        if (nl == null) {
            return null;
        }
        boolean found = false;
        for (int i = 0; i < nl.getLength(); i++) {
            Node n = nl.item(i);
            if (n instanceof Element) {
                Element el = (Element) n;
                NodeList gids = el.getElementsByTagName("groupId");
                NodeList aids = el.getElementsByTagName("artifactId");
                NodeList vers = el.getElementsByTagName("version");
                if (gids != null && aids != null && gids.getLength() == 1 && aids.getLength() == 1) {
                    Element aid = (Element) aids.item(0);
                    Element gid = (Element) gids.item(0);
                    Element ver = (Element) vers.item(0);
                    if (oldGroupId.equals(gid.getTextContent()) && oldArtifactID.equals(aid.getTextContent())) {
                        if (!version.equals(ver.getTextContent())) {
                            Element newVersion = d.createElement("version");
                            newVersion.setTextContent(version);
                            el.removeChild(ver);
                            el.appendChild(newVersion);
                            found = true;
//                            System.out.println("Replace dependency in "
//                                    + getGroupID() + ":" + getArtifactId() + " -> " + version);
                        }
                    }
                }
            }
        }
        XPathExpression findParentArtifactId = xpath.compile("/project/parent/artifactId");
        XPathExpression findParentGroupId = xpath.compile("/project/parent/groupId");
        XPathExpression findParentVersion = xpath.compile("/project/parent/version");
        Element parentArtifactIdElement = (Element) (NodeList) findParentArtifactId.evaluate(d, XPathConstants.NODE);
        Element parentGroupIdElement = (Element) (NodeList) findParentGroupId.evaluate(d, XPathConstants.NODE);
        Element parentVersionElement = (Element) (NodeList) findParentVersion.evaluate(d, XPathConstants.NODE);

        if (parentArtifactIdElement != null && parentGroupIdElement != null && parentVersionElement != null) {
//            System.out.println("FOUND PARENT " + parentGroupIdElement.getTextContent() + ":" + parentArtifactIdElement.getTextContent() + ":" + parentVersionElement.getTextContent());

            if (oldGroupId.equals(parentGroupIdElement.getTextContent())
                    && oldArtifactID.equals(parentArtifactIdElement.getTextContent())
                    && !version.equals(parentVersionElement.getTextContent())) {
                Node parent = parentArtifactIdElement.getParentNode();
                Element newVersion = d.createElement("version");
                newVersion.setTextContent(version);
                parent.removeChild(parentVersionElement);
                parent.appendChild(newVersion);
                found = true;
            }
        }

        if (found) {
            return d;
        }
        return null;
    }

    public Document removeExplicitNBM() throws IOException, XPathExpressionException {
        Document d = getDocument();
        XPathFactory fac = XPathFactory.newInstance();
        XPath xpath = fac.newXPath();
        XPathExpression findArtifactId = xpath.compile(
                "/project/dependencies/dependency/type");
        NodeList nl = (NodeList) findArtifactId.evaluate(d, XPathConstants.NODESET);
        if (nl == null) {
            return null;
        }
        boolean found = false;
        for (int i = 0; i < nl.getLength(); i++) {
            Node n = nl.item(i);
            String txt = n.getTextContent();
            if ("nbm".equals(txt)) {
                n.getParentNode().removeChild(n);
                found = true;
            }
        }
        if (found) {
            return d;
        }
        return null;
    }

    public String getVersion() throws IOException {
        try {
            Document d = getDocument();
            XPathFactory fac = XPathFactory.newInstance();
            XPath xpath = fac.newXPath();
            XPathExpression findArtifactId = xpath.compile(
                    "/project/version");
            Element n = (Element) findArtifactId.evaluate(d, XPathConstants.NODE);
            if (n == null) {
                return getParentVersion();
            }
            return n.getTextContent();
        } catch (XPathExpressionException ex) {
            throw new IOException(ex);
        }
    }

    public Document setParentVersion(String version) throws IOException {
        try {
            Document d = getDocument();
            XPathFactory fac = XPathFactory.newInstance();
            XPath xpath = fac.newXPath();
            XPathExpression findArtifactId = xpath.compile(
                    "/project/parent/version");
            Element n = (Element) findArtifactId.evaluate(d, XPathConstants.NODE);
            if (n == null) {
//                throw new IOException("No artifactId node in " + pomFile);
                return null;
            }
            Element nue = d.createElement("version");
            nue.setTextContent(version);
            n.getParentNode().replaceChild(nue, n);
            return d;
        } catch (XPathExpressionException ex) {
            throw new IOException(ex);
        }
    }

    public Set<String> getDevelopers() throws IOException {
        try {
            Document d = getDocument();
            XPathFactory fac = XPathFactory.newInstance();
            XPath xpath = fac.newXPath();
            XPathExpression findArtifactId = xpath.compile(
                    "/project/developers/developer");
            NodeList nl = (NodeList) findArtifactId.evaluate(d, XPathConstants.NODESET);
            int max = nl.getLength();
            if (max == 0) {
                return Collections.emptySet();
            }
            Set<String> result = new TreeSet<>();
            for (int i = 0; i < max; i++) {
                Node n = nl.item(i);
                String name = n.getTextContent().trim();
                if (!name.isEmpty()) {
                    result.add(name);
                }
            }
            return result;
        } catch (XPathExpressionException ex) {
            throw new IOException(ex);
        }
    }

    public String getParentVersion() throws IOException {
        try {
            Document d = getDocument();
            XPathFactory fac = XPathFactory.newInstance();
            XPath xpath = fac.newXPath();
            XPathExpression findArtifactId = xpath.compile(
                    "/project/parent/version");
            Element n = (Element) findArtifactId.evaluate(d, XPathConstants.NODE);
            if (n == null) {
                return null;
            }
            return n.getTextContent().trim();
        } catch (XPathExpressionException ex) {
            throw new IOException(ex);
        }
    }

    public String getParentRelativePath() throws IOException {
        try {
            Document d = getDocument();
            XPathFactory fac = XPathFactory.newInstance();
            XPath xpath = fac.newXPath();
            XPathExpression findArtifactId = xpath.compile(
                    "/project/parent/relativePath");
            Element n = (Element) findArtifactId.evaluate(d, XPathConstants.NODE);
            if (n == null) {
                return null;
            }
            return n.getTextContent().trim();
        } catch (XPathExpressionException ex) {
            throw new IOException(ex);
        }
    }

    public String getArtifactId() throws IOException {
        try {
            Document d = getDocument();
            XPathFactory fac = XPathFactory.newInstance();
            XPath xpath = fac.newXPath();
            XPathExpression findArtifactId = xpath.compile(
                    "/project/artifactId");
            Element n = (Element) findArtifactId.evaluate(d, XPathConstants.NODE);
            if (n == null) {
                throw new IOException("No artifactId node in " + pomFile);
            }
            return n.getTextContent().trim();
        } catch (XPathExpressionException ex) {
            throw new IOException(ex);
        }
    }

    public String getGroupId() throws IOException {
        try {
            Document d = getDocument();
            XPathFactory fac = XPathFactory.newInstance();
            XPath xpath = fac.newXPath();
            XPathExpression findArtifactId = xpath.compile(
                    "/project/groupId");
            Element n = (Element) findArtifactId.evaluate(d, XPathConstants.NODE);
            if (n == null) {
                findArtifactId = xpath.compile(
                        "/project/parent/groupId");
                n = (Element) findArtifactId.evaluate(d, XPathConstants.NODE);
                if (n == null) {
                    throw new IOException("No group id anywhere");
                }
            }
            return n.getTextContent();
        } catch (XPathExpressionException ex) {
            throw new IOException(ex);
        }
    }

    public DepVer toDepVer() throws IOException, XPathExpressionException {
        Document d = getDocument();
        XPathFactory fac = XPathFactory.newInstance();
        XPath xpath = fac.newXPath();
        XPathExpression findArtifactId = xpath.compile(
                "/project/groupId");
        Element n = (Element) findArtifactId.evaluate(d, XPathConstants.NODE);
        if (n == null) {
            findArtifactId = xpath.compile(
                    "/project/parent/groupId");
            n = (Element) findArtifactId.evaluate(d, XPathConstants.NODE);
            if (n == null) {
                throw new IOException("No group id anywhere");
            }
        }
        String groupId = n.getTextContent();
        findArtifactId = xpath.compile(
                "/project/artifactId");
        n = (Element) findArtifactId.evaluate(d, XPathConstants.NODE);
        if (n == null) {
            throw new IOException("No artifactId node in " + pomFile);
        }
        String artifactId = n.getTextContent();
        findArtifactId = xpath.compile(
                "/project/version");
        n = (Element) findArtifactId.evaluate(d, XPathConstants.NODE);
        if (n == null) {
            findArtifactId = xpath.compile(
                    "/project/parent/version");
            n = (Element) findArtifactId.evaluate(d, XPathConstants.NODE);
            if (n == null) {
                throw new IOException("No version anywhere");
            }
        }
        String version = n.getTextContent();
        return new DepVer(groupId, artifactId, version);
    }

    public List<String> getModules() throws IOException {
        try {
            Document d = getDocument();
            XPathFactory fac = XPathFactory.newInstance();
            XPath xpath = fac.newXPath();
            XPathExpression findArtifactId = xpath.compile(
                    "project/modules/module");
            NodeList nl = (NodeList) findArtifactId.evaluate(d, XPathConstants.NODESET);
            List<String> result = new ArrayList<String>();
            for (int i = 0; i < nl.getLength(); i++) {
                Node n = nl.item(i);
                result.add(n.getTextContent());
            }
            return result;
        } catch (XPathExpressionException ex) {
            throw new IOException(ex);
        }
    }

    public Document getDocument() throws IOException {
        try {
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            dbFactory.setValidating(false);
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(pomFile);
            doc.getDocumentElement().normalize();
            return doc;
        } catch (SAXException | ParserConfigurationException ex) {
            throw new IOException(ex);
        }
    }

    public <T> T inContext(ThrowingFunction<Document, T> r) throws Exception {
        Document doc = DOC_CTX.get();
        if (doc == null) {
            doc = getDocument();
            Document old = DOC_CTX.get();
            DOC_CTX.set(doc);
            try {
                return r.apply(doc);
            } finally {
                DOC_CTX.set(old);
            }
        } else {
            return r.apply(doc);
        }
    }

    private final ThreadLocal<Document> DOC_CTX = new ThreadLocal<>();

    public void inContext(ThrowingConsumer<Document> r) throws Exception {
        Document doc = DOC_CTX.get();
        if (doc == null) {
            doc = getDocument();
            Document old = DOC_CTX.get();
            DOC_CTX.set(doc);
            try {
                r.accept(doc);
            } finally {
                DOC_CTX.set(old);
            }
        } else {
            r.accept(doc);
        }
    }
}

package org.nemesis.antlr.project.helpers.maven;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

/**
 *
 * @author Tim Boudreau
 */
final class MavenSettingsFile {

    private final Path settingsFile;

    MavenSettingsFile(Path settingsFile) {
        this.settingsFile = settingsFile;
    }

    public Path getLocalRepoLocation() throws IOException, XPathExpressionException {
        Document doc = getDocument();
        XPathFactory fac = XPathFactory.newInstance();
        XPath xpath = fac.newXPath();
        XPathExpression findArtifactId = xpath.compile(
                "/settings/localRepository");
        Node n = (Node) findArtifactId.evaluate(doc, XPathConstants.NODE);
        if (n != null) {
            Path path = Paths.get(n.getTextContent().trim());
            if (Files.exists(path) && Files.isDirectory(path)) {
                return path;
            }
        }
        return null;
    }

    public Document getDocument() throws IOException {
        try {
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            dbFactory.setValidating(false);
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(settingsFile.toFile());
            doc.getDocumentElement().normalize();
            return doc;
        } catch (SAXException | ParserConfigurationException ex) {
            throw new IOException(ex);
        }
    }
}

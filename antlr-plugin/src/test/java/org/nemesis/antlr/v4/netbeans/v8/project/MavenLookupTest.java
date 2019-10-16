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
package org.nemesis.antlr.v4.netbeans.v8.project;

import static java.nio.charset.StandardCharsets.UTF_8;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.AfterClass;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.nemesis.antlr.v4.netbeans.v8.project.ProjectTypeTest.NON_STANDARD_LAYOUT_MAVEN_PROJECT_FILES;
import org.nemesis.antlr.v4.netbeans.v8.project.helper.MavenProjectHelper;
import org.netbeans.api.project.Project;

/**
 *
 * @author Tim Boudreau
 */
public class MavenLookupTest {

    static TestProjectStructure structure;
    private static Project mavenProject;
    private static Path projectPath;

    @Test
    public void testLookupProperty() {
        Optional<String> res = fetchProperty("libDirectory");
        assertNotNull(res);
        assertTrue(res.isPresent());
        assertEquals("other/imports", res.get());

        res = fetchProperty("sourceDirectory");
        assertNotNull(res);
        assertTrue(res.isPresent());
        assertEquals("other", res.get());

        res = fetchProperty("outputDirectory");
        assertNotNull(res);
        assertTrue(res.isPresent());
        assertEquals("target/foo", res.get());
    }

    private Optional<String> fetchProperty(String name) {
        Optional<String> res = MavenProjectHelper.forProjects().pluginParameter(mavenProject.getLookup(),
                "org.antlr", "antlr4-maven-plugin", name, null, (err, prop) -> {
                    if (err.isPresent()) {
                        fail(err.get());
                    }
                    assertNotNull(prop);
                    return Optional.of(prop);
                });
        return res;
    }

    @BeforeClass
    public static void setUpTestProjects() throws Exception {
        structure = new TestProjectStructure(ProjectTypeTest.class,
                "non-standard", ProjectTypeTest::getContent, NON_STANDARD_LAYOUT_MAVEN_PROJECT_FILES);
        projectPath = structure.path();
        mavenProject = structure.project();
        boolean found = false;
        for (String line : Files.readAllLines(projectPath.resolve("pom.xml"), UTF_8)) {
            if (line.contains("libDirectory")) {
                found = true;
                break;
            }
        }
        assertTrue("libDirectory not found in pom - wrong file copied?", found);
    }

    @AfterClass
    public static void deleteProjects() throws Exception {
        System.out.println("FOLDER " + structure.path());
//        structure.delete();
    }

}

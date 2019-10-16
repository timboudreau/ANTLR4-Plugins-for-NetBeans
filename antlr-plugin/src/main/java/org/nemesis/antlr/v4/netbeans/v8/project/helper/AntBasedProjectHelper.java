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
package org.nemesis.antlr.v4.netbeans.v8.project.helper;

import java.io.File;

import org.netbeans.api.project.Project;

import org.netbeans.spi.project.support.ant.PropertyEvaluator;
import org.netbeans.spi.project.support.ant.PropertyProvider;
import org.netbeans.spi.project.support.ant.PropertyUtils;

import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

/**
 * 
 *
 * @author Frédéric Yvon Vinet
 */
public class AntBasedProjectHelper {
    public static String getAntProjectProperty
            (Project project, String propertyName) {
        FileObject projectDirFO = project.getProjectDirectory();
        File projectDir = FileUtil.toFile(projectDirFO);
        File propertiesFile = new File(projectDir, "nbproject/project.properties");
        PropertyProvider propertyProvider = PropertyUtils.
                                 propertiesFilePropertyProvider(propertiesFile);
        PropertyEvaluator propertyEvaluator = PropertyUtils.
                            sequentialPropertyEvaluator(null, propertyProvider);
        return propertyEvaluator.getProperty(propertyName);
    }
}
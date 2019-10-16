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
package org.nemesis.antlr.v4.netbeans.v8.project.helper.java;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 *
 * @author Frédéric Yvon Vinet
 */
public class JavaSourceClass {
    protected final String _package;
    protected final String simpleName;

    public String getPackage() {
        return _package;
    }

    public String getSimpleName() {
        return simpleName;
    }
    
    public String getName() {
        StringBuilder name = new StringBuilder();
        if (_package == null) {
            name.append(simpleName);
        } else {
            name.append(_package);
            name.append(".");
            name.append(simpleName);
        }
        return name.toString();
    }
    
    
    public JavaSourceClass(String _package, String name) {
        assert name != null;
        if (_package != null &&
            _package.equals(""))
            this._package = null;
        else
            this._package = _package;
        this.simpleName = name;
    }
    
    
    public boolean equals(JavaSourceClass otherJavaClass) {
        return this.getName().equals(otherJavaClass.getName());
    }
    
    
    public String getLocalFilePath() {
     // step 1: We extract the file name from name
     // if our Java class is an inner class then name may contain a parent class
     // that is our file name else the file name is the class name contained in 
     // name
        String fileName;
        int index = simpleName.indexOf(".");
        if (index == -1) {
            fileName = simpleName;
        } else {
            fileName = simpleName.substring(0, index);
        }
        
     // step 2: We build thz directory path string corresponding to our package
     // if there is some
        Path path;
        if (_package != null) {
            String packagePath = _package.replace(".", System.getProperty("file.separator"));
            path = Paths.get(packagePath, fileName);
        } else {
            path = Paths.get(fileName);
        }
        return path.toString();
    }
}

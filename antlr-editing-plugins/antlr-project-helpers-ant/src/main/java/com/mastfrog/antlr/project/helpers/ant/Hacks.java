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
package com.mastfrog.antlr.project.helpers.ant;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.netbeans.api.project.Project;
import org.netbeans.spi.project.support.ant.AntProjectHelper;
import org.openide.util.Exceptions;
import org.w3c.dom.Node;

/**
 *
 * @author Tim Boudreau
 */
public class Hacks {

    private static Class<?> j2seProjectType;
    private static Method getHelper;
    private static boolean broken;

    static AntProjectHelper helperFor(Project project) {
        if (broken) {
            return null;
        }
        Project j2seProjectUnwrapped = j2seProject(project);
        if (j2seProjectUnwrapped != null) {
            if (getHelper == null) {
                try {
                    getHelper = j2seProjectType.getDeclaredMethod("getAntProjectHelper");
                    assert getHelper.getReturnType() == AntProjectHelper.class;
                } catch (NoSuchMethodException | SecurityException ex) {
                    broken = true;
                    Exceptions.printStackTrace(ex);
                }
            }
            if (getHelper != null) {
                try {
                    return (AntProjectHelper) getHelper.invoke(j2seProjectUnwrapped);
                } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
                    Exceptions.printStackTrace(ex);
                    broken = true;
                }
            }
        }
        return null;
    }

    static Project j2seProject(Project project) {
        if (j2seProjectType != null) {
            return (Project) project.getLookup().lookup(j2seProjectType);
        }
        for (Object o : project.getLookup().lookupAll(Object.class)) {
            if (o instanceof Project && "J2SEProject".equals(o.getClass().getSimpleName())) {
                j2seProjectType = o.getClass();
                return (Project) o;
            }
        }
        return null;
    }

    public static String nodeToString(Node elem) throws IOException {
        return nodeToString(elem, 4);
    }

    @SuppressWarnings("deprecation")
    public static String nodeToString(Node elem, int indent) throws IOException {
        com.sun.org.apache.xml.internal.serialize.OutputFormat format = new com.sun.org.apache.xml.internal.serialize.OutputFormat();
        format.setIndenting(true);
        format.setStandalone(true);
        format.setPreserveEmptyAttributes(true);
        format.setAllowJavaNames(true);
        format.setPreserveSpace(true);
        format.setIndent(indent);
        Writer out = new StringWriter();
        com.sun.org.apache.xml.internal.serialize.XMLSerializer serializer = new com.sun.org.apache.xml.internal.serialize.XMLSerializer(out, null);
        serializer.serialize(elem);
        return out.toString() + '\n';
    }

    private Hacks() {
        throw new AssertionError();
    }
}

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
package org.nemesis.jfs;

import java.nio.charset.Charset;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.NestingKind;
import javax.tools.JavaFileManager;

/**
 *
 * @author Tim Boudreau
 */
final class JFSJavaFileObjectImpl extends JFSFileObjectImpl implements JFSJavaFileObject {

    JFSJavaFileObjectImpl(JFSBytesStorage storage, JavaFileManager.Location location, Name name, Charset encoding) {
        super(storage, location, name, encoding);
    }

    @Override
    public Kind getKind() {
        return name().kind();
    }

    @Override
    public boolean isNameCompatible(String simpleName, Kind kind) {
        return kind == name().kind() && name().nameMatches(simpleName);
    }

    @Override
    public NestingKind getNestingKind() {
        // javac's impl returns null
        return null;
//        if (name().isNestedClass()) {
//            return NestingKind.MEMBER;
//        } else {
//            return NestingKind.TOP_LEVEL;
//        }
    }

    @Override
    public Modifier getAccessLevel() {
//        return Modifier.PUBLIC;
        // javac's impl returns null
        return null;
    }

}

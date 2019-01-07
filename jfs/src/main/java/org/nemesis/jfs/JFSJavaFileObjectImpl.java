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

    public JFSJavaFileObjectImpl(JFSBytesStorage storage, JavaFileManager.Location location, Name name, Charset encoding) {
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

package org.nemesis.antlr.live.language;

import java.io.IOException;
import java.io.OutputStream;
import static java.nio.charset.StandardCharsets.UTF_8;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;
import org.openide.util.Exceptions;
import org.nemesis.adhoc.mime.types.AdhocMimeTypes;

/**
 *
 * @author Tim Boudreau
 */
public final class SampleFiles {

    private static final String ANTLR_FOLDER = "antlr";
    private static final String SAMPLES_FOLDER = "grammar-samples";
    private static final String SFS_PATH = ANTLR_FOLDER + "/" + SAMPLES_FOLDER + "/";
    private static final String SAMPLE_NAME = "sample";

    private SampleFiles() {
        throw new AssertionError();
    }

    public static DataObject sampleFile(String mimeType) {
        try {
            FileObject fo = _sampleFile(mimeType);
            return DataObject.find(fo);
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
            return null;
        }
    }

    public static boolean isSampleFile(DataObject dob) {
        FileObject fo = dob.getPrimaryFile();
        boolean result = SAMPLE_NAME.equals(fo.getName());
        if (result) {
            FileObject parent = fo.getParent();
            result = parent != null && "grammar-samples".equals(parent.getName());
            if (result) {
                FileObject parentParent = parent.getParent();
                // good enough
                result = parentParent != null && "antlr".equals(parentParent.getName());
            }
        }
        return result;
    }

    private static FileObject _sampleFile(String mimeType) throws IOException {
        assert AdhocMimeTypes.isAdhocMimeType(mimeType);
        String ext = AdhocMimeTypes.fileExtensionFor(mimeType);
        String fileName = SAMPLE_NAME + '.' + ext;
        FileObject sampleFile = FileUtil.getConfigFile(SFS_PATH + fileName);
        if (sampleFile == null) {
            sampleFile = FileUtil.createData(FileUtil.getConfigRoot(), SFS_PATH + fileName);
            try (OutputStream out = sampleFile.getOutputStream()) {
                // XXX if we wanted to be too clever by half, we could
                // derive a text that could parse from the grammer
                out.write(DEFAULT_TEXT.getBytes(UTF_8));
            }
            sampleFile.setAttribute("sample", true);
            DynamicLanguages.ensureRegistered(mimeType);
            FileUtil.setMIMEType(ext, mimeType);
        } else {
            DynamicLanguages.ensureRegistered(mimeType);
        }
        return sampleFile;
    }

    private static String DEFAULT_TEXT
            = "// ignore-emscripten no threads support\n"
            + "\n"
            + "use std::thread;\n"
            + "\n"
            + "pub fn main() {\n"
            + "    let mut result = thread::spawn(child);\n"
            + "    println!(\"1\");\n"
            + "    thread::yield_now();\n"
            + "    println!(\"2\");\n"
            + "    thread::yield_now();\n"
            + "    println!(\"3\");\n"
            + "    result.join();\n"
            + "}\n"
            + "\n"
            + "fn child() {\n"
            + "    println!(\"4\"); thread::yield_now(); println!(\"5\"); thread::yield_now(); println!(\"6\");\n"
            + "}";
}

package org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool;

import org.nemesis.misc.utils.reflection.ReflectionPath;
import org.nemesis.antlr.v4.netbeans.v8.util.isolation.ReflectiveInvoker;
import org.nemesis.antlr.v4.netbeans.v8.util.isolation.ForeignInvocationResult;
import java.io.File;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.nemesis.misc.utils.reflection.ReflectionPath.ResolutionResultType;

/**
 *
 * @author Tim Boudreau
 */
final class AntlrInvoker implements ReflectiveInvoker<AntlrInvocationResult> {

    private static final Pattern ARG_PATTERN = Pattern.compile("(.*?)<(arg(\\d*))>([^\\<]+)?");
    private final String[] args;
    // for tests
    Reference<Class<?>> antlrClass;
    Reference<ClassLoader> loader;
    boolean captureOutputStreams;

    public AntlrInvoker(String... args) {
        this(false, args);
    }

    public AntlrInvoker(boolean captureOutputStreams, String... args) {
        this.args = args;
        this.captureOutputStreams = captureOutputStreams;
    }

    @Override
    public boolean captureStandardOutputAndError() {
        return captureOutputStreams;
    }

    /**
     * Used to name the invocation thread.
     *
     * @return A string respresentation of the command line args
     */
    public String toString() {
        StringBuilder sb = new StringBuilder("antlr ");
        for (int i = 0; i < args.length; i++) {
            sb.append(args[i]);
            if (i != args.length - 1) {
                sb.append(' ');
            }
        }
        return sb.toString();
    }

    public AntlrInvocationResult invoke(ClassLoader loader, ForeignInvocationResult<AntlrInvocationResult> res) throws Exception {
        this.loader = new WeakReference<>(loader);
        Class<?> antlrTool = Class.forName("org.antlr.v4.Tool", true, loader);
        Class<?> antlrClass = antlrTool;
        this.antlrClass = new WeakReference<>(antlrTool);
        Constructor<?> constructor = antlrTool.getConstructor(String[].class);
        Object tool = constructor.newInstance((Object) args);
        Field dir = antlrTool.getField("inputDirectory");
        File path = Paths.get(args[args.length - 1]).toFile();
        dir.set(tool, path);
        Field returnDontExit = antlrTool.getDeclaredField("return_dont_exit");
        returnDontExit.setAccessible(true);
        returnDontExit.set(tool, Boolean.TRUE);
        AntlrInvocationResult result = null;
        // Either we parse proxied errors out of what Antlr writes to stdout,
        // or we can fetch them by some brutal reflection, which is faster but
        // potentially less robust across future versions of ANTLR which might
        // incompatibly change that interface
        if (!captureOutputStreams) {
            Class<?> listenerInterface = Class.forName("org.antlr.v4.tool.ANTLRToolListener", false, loader);
            ListenerProxy ih = new ListenerProxy();
            Object listenerProxy = Proxy.newProxyInstance(loader, new Class<?>[]{listenerInterface}, ih);
            Method addListenerMethod = antlrClass.getMethod("addListener", listenerInterface);
            addListenerMethod.invoke(tool, listenerProxy);
            result = ih.result;
        }

        if (Thread.interrupted()) {
            return new AntlrInvocationResult();
        }
        Method method = antlrTool.getMethod("processGrammarsOnCommandLine");
        method.invoke(tool);
        if (captureOutputStreams) {
            result = new AntlrInvocationResult();
            List<ParsedAntlrError> errors = res.parseErrorOutput(new AntlrErrorParser());
            ParsedAntlrError.computeFileOffsets(errors);
            result.errors.addAll(errors);
        }
        Collections.sort(result.errors);
        return result;
    }

    private static final class ListenerProxy implements InvocationHandler {

        AntlrInvocationResult result = new AntlrInvocationResult();

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            switch (method.getName()) {
                case "toString":
                    return "ListenerProxy";
                case "hashCode":
                    return 1;
                case "equals":
                    return false;
                case "info":
                    if (args.length == 1 && args[0] instanceof String) {
                        result.infoMessages.add((String) args[0]);
                    }
                    return null;
                case "error":
                case "warning":
                    if (args.length == 1) {
                        try {
                            ParsedAntlrError err = reflectiveError(args[0]);
                            if (err != null) {
                                result.errors.add(err);
                            }
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        }
                    }
                    return null;
            }
            return null;
        }
    }

    private static ParsedAntlrError reflectiveError(Object o) throws Exception {
        // XXX some of this may be fragile under future versions of ANTLR, while
        // command line parsing is likely to be more stable.  However, this lets
        // us collect character offsets in the file without rereading the file.
        ReflectionPath<String> message = new ReflectionPath<>("getErrorType().msg", String.class);
        ReflectionPath<String> fileName = new ReflectionPath<>("fileName", String.class);
        ReflectionPath<Object[]> args = new ReflectionPath<>("args", Object[].class);
        ReflectionPath<String> severity = new ReflectionPath<>("getErrorType().severity.text", String.class);
        ReflectionPath<Integer> code = new ReflectionPath<>("getErrorType().code", Integer.class);
        ReflectionPath<Integer> line = new ReflectionPath<>("line", Integer.class);
        ReflectionPath<Integer> lineOffset = new ReflectionPath<>("charPosition", Integer.class);
        ReflectionPath<Integer> startOffset = new ReflectionPath<>("offendingToken.getStartIndex()", Integer.class);
        ReflectionPath<Integer> stopOffset = new ReflectionPath<>("offendingToken.getStopIndex()", Integer.class);

        ReflectionPath.resolve(o, message, fileName, args, severity, code, line, lineOffset, startOffset, stopOffset);

        boolean anyFailed = false;
        for (ReflectionPath<?> v : new ReflectionPath<?>[]{message, fileName, args, severity, code, line, lineOffset}) {
            if (v.result().type() != ResolutionResultType.SUCCESS && v.result().type() != ResolutionResultType.NULL_VALUE) {
                anyFailed = true;
            }
        }
        if (!anyFailed) {
            // Poor man's version of ST4 templates, but enough for this
            String msg;
            if (args.result().isSuccess()) {
                String template = message.get().get();
                StringBuilder sb = new StringBuilder();
                Matcher m = ARG_PATTERN.matcher(template);
                Object[] ag = args.get().get();
                while (m.find()) {
                    sb.append(m.group(1));
                    String arg = m.group(2);
                    if ("arg".equals(arg)) {
                        if (ag.length > 0) {
                            sb.append(ag[0]);
                        } else {
                            sb.append("<arg>");
                        }
                    } else {
                        int index = Integer.parseInt(m.group(3)) - 1;
                        if (index < ag.length) {
                            sb.append(ag[index]);
                        }
                    }
                    if (m.group(4) != null) {
                        sb.append(m.group(4));
                    }
                }
                msg = sb.toString();
            } else {
                msg = message.get().get();
            }
            ParsedAntlrError result = new ParsedAntlrError("error".equals(severity.get().get()), code.get().get(), Paths.get(fileName.get().get()),
                    line.get().get(), lineOffset.get().get(), msg);

            if (startOffset.result().isSuccess()) {
                result.fileOffset = startOffset.get().get();
                if (stopOffset.result().isSuccess()) {
                    result.length = (stopOffset.get().get() + 1) - result.fileOffset;
                }
            }
            return result;
        }
        return null;
    }
}

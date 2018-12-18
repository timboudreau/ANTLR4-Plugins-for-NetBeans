package org.nemesis.antlr.v4.netbeans.v8.util.reflection;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Allows for drilling through the fields and sub-fields of reflectively
 * obtained foreign objects, fetching values of fields.  Methods are
 * specified by appending () to the name.  E.g. foo.errorType().code.
 * <p>
 A call to resolve() with a collection of ReflectionPath instances is
 expected first - this allows field and method instances to be pooled when
 looking up a large number of things.
 </p>
 *
 * @author Tim Boudreau
 */
public final class ReflectionPath<T> {

    private final String valuePath;
    private final Class<T> type;
    private ResolutionResult result;

    public ReflectionPath(String valuePath, Class<T> type) {
        assert valuePath != null && type != null;
        this.valuePath = valuePath;
        this.type = type;
    }

    @Override
    public String toString() {
        return valuePath;
    }

    public boolean equals(Object o) {
        return o == this ? true : o == null ? false :
                o instanceof ReflectionPath<?> ?
                ((ReflectionPath<?>) o).type == type
                && ((ReflectionPath<?>) o).valuePath.equals(valuePath) : false;
    }

    public int hashCode() {
        return valuePath.hashCode() + 7 * type.hashCode();
    }

    public String path() {
        return valuePath;
    }

    public Optional<T> get() {
        ResolutionResult result = result();
        switch (result.type) {
            case SUCCESS:
                return Optional.of(type.cast(result.value));
            default:
                return Optional.empty();
        }
    }

    public static void resolve(Object on, ReflectionPath<?>... many) {
        ResolutionContext ctx = new ResolutionContext();
        for (ReflectionPath<?> v : many) {
            v.resolve(ctx, on);
        }
    }

    public ResolutionResult result() {
        if (result == null) {
            throw new IllegalStateException("Not resolved: " + valuePath);
        }
        return result;
    }

    ResolutionResult resolve(ResolutionContext ctx, Object on) {
        assert on != null;
        String[] parts = valuePath.split("\\.");
        Object curr = on;
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String name = parts[i];
            if (current.length() > 0) {
                current.append('.');
            }
            current.append(name);
            boolean method = false;
            if (name.endsWith("()")) {
                name = name.substring(0, name.length() - 2);
                method = true;
            }
            if (method) {
                result = invokeMethod(name, curr, ctx, current.toString());
            } else {
                result = invokeField(name, curr, ctx, current.toString());
            }
            if (i == parts.length - 1) {
                return result;
            } else {
                switch (result.type) {
                    case NULL_VALUE:
                    case EXCEPTION:
                    case NO_SUCH_ELEMENT:
                        return result;
                    case SUCCESS:
                        curr = result.value;
                }
            }

        }
        return null;
    }

    private ResolutionResult invokeMethod(String methodName, Object on, ResolutionContext ctx, String currentPath) {
        Method method = ctx.findMethod(methodName, on);
        if (method == null) {
            return new ResolutionResult(ResolutionResultType.NO_SUCH_ELEMENT, null, currentPath);
        }
        Object result = null;
        try {
            result = method.invoke(on);
            return result == null ? new ResolutionResult(currentPath) : new ResolutionResult(ResolutionResultType.SUCCESS, result, currentPath);
        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
            Throwable t = ex;
            while(t.getCause() != null) {
                t = t.getCause();
            }
            return new ResolutionResult(t, currentPath);
        }
    }

    private ResolutionResult invokeField(String fieldName, Object on, ResolutionContext ctx, String currentPath) {
        Field field = ctx.findField(fieldName, on);
        if (field == null) {
            return new ResolutionResult(ResolutionResultType.NO_SUCH_ELEMENT, null, currentPath);
        }
        Object result = null;
        try {
            result = field.get(on);
            return result == null ? new ResolutionResult(currentPath) : new ResolutionResult(ResolutionResultType.SUCCESS, result, currentPath);
        } catch (Exception | Error ex) {
            Throwable t = ex;
            while(t.getCause() != null) {
                t = t.getCause();
            }
            return new ResolutionResult(t, currentPath);
        }
    }

    public static class ResolutionResult {

        private final ResolutionResultType type;
        private final Throwable thrown;
        private final Object value;
        private final String path;

        ResolutionResult(String path) {
            this(ResolutionResultType.NULL_VALUE, null, path);
        }

        ResolutionResult(ResolutionResultType type, Object value, String path) {
            this.type = type;
            this.thrown = null;
            this.value = value;
            this.path = path;
        }

        ResolutionResult(Throwable thrown, String path) {
            this.thrown = thrown;
            this.type = ResolutionResultType.EXCEPTION;
            this.path = path;
            this.value = null;
        }

        public boolean isSuccess() {
            return type == ResolutionResultType.SUCCESS;
        }

        public Object value() {
            return value;
        }

        public Throwable thrown() {
            return thrown;
        }

        public String path() {
            return path;
        }

        public ResolutionResultType type() {
            return type;
        }

        public String toString() {
            return type.name() + (value == null ? "" : " - " + value + " - ")
                    + (thrown != null ? " " + thrown + " " : "")
                    + " @ " + path;
        }
    }

    public static enum ResolutionResultType {
        NO_SUCH_ELEMENT,
        NULL_VALUE,
        SUCCESS,
        EXCEPTION
    }

    public static final class ResolutionContext {

        private final Map<Class<?>, String> nonMatches = new HashMap<>();
        private final Map<Class<?>, Map<String, Field>> fields = new HashMap<>();
        private final Map<Class<?>, Map<String, Method>> methods = new HashMap<>();

        public ResolutionResult getResult(ReflectionPath<?> pth, Object on) {
            ResolutionResult res = pth.resolve(this, on);
            return res;
        }

        public <T,R> R get(ReflectionPath<T> path, Object on, Function<Object,R> converter) {
            ResolutionResult res = path.resolve(this, on);
            if (res.value == null) {
                return null;
            }
            return converter.apply(res.value);
        }

        private Field cachedField(Class<?> type, String name) {
            Map<String, Field> m = fields.get(type);
            if (m != null) {
                return m.get(name);
            }
            return null;
        }

        private Method cachedMethod(Class<?> type, String name) {
            Map<String, Method> m = methods.get(type);
            if (m != null) {
                return m.get(name);
            }
            return null;
        }

        private void addField(Class<?> type, Field field) {
            Map<String, Field> m = fields.get(type);
            if (m == null) {
                m = new HashMap<>();
                fields.put(type, m);
            }
            m.put(field.getName(), field);
        }

        private void addMethod(Class<?> type, Method method) {
            Map<String, Method> m = methods.get(type);
            if (m == null) {
                m = new HashMap<>();
                methods.put(type, m);
            }
            m.put(method.getName(), method);
        }

        Field findField(String field, Object on) {
            Class<?> type = on.getClass();
            Field result = null;
            while (type != Object.class) {
                result = cachedField(type, field);
                if (result != null) {
                    return result;
                }
                try {
                    result = type.getDeclaredField(field);
                    addField(on.getClass(), result);
                    result.setAccessible(true);
                    return result;
                } catch (NoSuchFieldException | SecurityException ex) {
                    // do nothing
                }
                type = type.getSuperclass();
            }
            return result;
        }

        Method findMethod(String method, Object on) {
            Class<?> type = on.getClass();
            Method result = null;
            while (type != Object.class) {
                result = cachedMethod(type, method);
                if (result != null) {
                    return result;
                }
                try {
                    result = type.getDeclaredMethod(method);
                    result.setAccessible(true);
                    addMethod(on.getClass(), result);
                    addMethod(type, result);
                    return result;
                } catch (NoSuchMethodException | SecurityException ex) {
                    // do nothing
                }
                type = type.getSuperclass();
            }
            return result;
        }
    }
}

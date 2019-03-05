package org.nemesis.registration.api;

import java.io.IOException;
import java.io.OutputStream;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.nemesis.registration.utils.AnnotationUtils;
import java.util.Set;
import javax.annotation.processing.Filer;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import org.nemesis.registration.codegen.ClassBuilder;
import org.openide.filesystems.annotations.LayerBuilder;
import org.openide.filesystems.annotations.LayerGeneratingProcessor;
import static org.nemesis.registration.NavigatorPanelRegistrationAnnotationProcessor.NAVIGATOR_PANEL_REGISTRATION_ANNOTATION;

/**
 *
 * @author Tim Boudreau
 */
public abstract class AbstractLayerGeneratingRegistrationProcessor extends LayerGeneratingProcessor {

    private AnnotationUtils utils;

    private final Delegates delegates = new Delegates(true);

    protected AbstractLayerGeneratingRegistrationProcessor() {
    }

    @Override
    public final Set<String> getSupportedAnnotationTypes() {
        Set<String> result = new HashSet<>(super.getSupportedAnnotationTypes());
        result.addAll(delegates.supportedAnnotationTypes());
        return result;
    }

    @Override
    public final Set<String> getSupportedOptions() {
        Set<String> result = new HashSet<>(super.getSupportedOptions());
        result.add(AnnotationUtils.AU_LOG);
        return result;
    }

    public final void logException(Throwable thrown, boolean fail) {
        utils().logException(thrown, fail);
    }

    public final void log(String val) {
        utils().log(val);
    }

    public final void log(String val, Object... args) {
        utils().log(val, args);
    }

    protected void installDelegates(Delegates delegates) {

    }

    @Override
    public synchronized final void init(ProcessingEnvironment processingEnv) {
        utils = new AnnotationUtils(processingEnv, getSupportedAnnotationTypes(), getClass());
        super.init(processingEnv);
        installDelegates(delegates);
        delegates.init(processingEnv, utils, this::writeOne, this::layer, this::addLayerTask);
        onInit(processingEnv, utils);
        used.clear();
    }

    protected void onInit(ProcessingEnvironment env, AnnotationUtils utils) {
    }

    protected final AnnotationUtils utils() {
        if (utils == null) {
            throw new IllegalStateException("Attempt to use utils before "
                    + "init() has been called.");
        }
        return utils;
    }

    protected void onBeforeHandleProcess(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {

    }

    protected void onAfterHandleProcess(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {

    }

    private boolean _validateAnnotationMirror(AnnotationMirror mirror, ElementKind kind) {
        return validateAnnotationMirror(mirror, kind) && delegates.validateAnnotationMirror(mirror, kind);
    }
    Set<Delegate> used = new HashSet<>();

    @Override
    public final boolean handleProcess(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        onBeforeHandleProcess(annotations, roundEnv);
        try {
            boolean done = true;
            Map<AnnotationMirror, Element> elementForAnnotation = new HashMap<>();
            for (Element el : utils().findAnnotatedElements(roundEnv, getSupportedAnnotationTypes())) {
                for (String annotationClass : getSupportedAnnotationTypes()) {
                    AnnotationMirror mirror = utils().findAnnotationMirror(el, annotationClass);
                    if (mirror == null) {
                        utils.warn("Could not locate annotation mirror for " + annotationClass + " - not on classpath?"
                                + " Ignoring annotation on " + el, el);
                        continue;
                    }
                    utils().log("Mirror {0} on kind {1} by {2} with {3}", mirror, el.getKind(), getClass().getSimpleName(), delegates);

                    if (!_validateAnnotationMirror(mirror, el.getKind())) {
                        continue;
                    }
                    boolean ok = false;
                    switch (el.getKind()) {
                        case CONSTRUCTOR:
                            try {
                                ExecutableElement constructor = (ExecutableElement) el;
                                done &= processConstructorAnnotation(constructor, mirror, roundEnv);
                                done &= delegates.processConstructorAnnotation(constructor, mirror, roundEnv, used);
                                ok = true;
                            } catch (Exception ex) {
                                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "IOException processing annotation " + annotationClass, el);
                                ex.printStackTrace();
                            }
                            break;
                        case METHOD:
                            try {
                                ExecutableElement method = (ExecutableElement) el;
                                done &= processMethodAnnotation(method, mirror, roundEnv);
                                done &= delegates.processMethodAnnotation(method, mirror, roundEnv, used);
                                ok = true;
                            } catch (Exception ex) {
                                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "IOException processing annotation " + annotationClass, el);
                                ex.printStackTrace();
                            }
                            break;
                        case FIELD:
                            try {
                                VariableElement var = (VariableElement) el;
                                done &= processFieldAnnotation(var, mirror, roundEnv);
                                done &= delegates.processFieldAnnotation(var, mirror, roundEnv, used);
                                ok = true;
                            } catch (Exception ex) {
                                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "IOException processing annotation " + annotationClass, el);
                                ex.printStackTrace();
                            }
                            break;
                        case INTERFACE:
                        case CLASS:
                            try {
                                TypeElement type = (TypeElement) el;
                                done &= processTypeAnnotation(type, mirror, roundEnv);
                                done &= delegates.processTypeAnnotation(type, mirror, roundEnv, used);
                                ok = true;
                            } catch (Exception ex) {
                                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "IOException processing annotation " + annotationClass, el);
                                ex.printStackTrace();
                            }
                            break;
                        default:
                            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                                    "Not applicable to " + el.getKind() + ": " + NAVIGATOR_PANEL_REGISTRATION_ANNOTATION, el);
                    }
                    if (ok) {
                        elementForAnnotation.put(mirror, el);
                    }
                }
            }
            try {
                done &= onRoundCompleted(elementForAnnotation, roundEnv);
                done &= delegates.onRoundCompleted(elementForAnnotation, roundEnv, used);
            } catch (Exception ex) {
                utils().logException(ex, true);
                ex.printStackTrace(System.out);
            }
            return done;
//            return false;
        } finally {
            try {
                onAfterHandleProcess(annotations, roundEnv);
            } finally {
                runLayerTasks(roundEnv);
            }
        }
    }

    private void runLayerTasks(RoundEnvironment roundEnv) {
        try {
            Set<Element> all = new HashSet<>();
            for (TaskContext task : layerTasks) {
                all.addAll(Arrays.asList(task.elements));
            }
            LayerBuilder b = null;
            for (Iterator<TaskContext> it = layerTasks.iterator(); it.hasNext();) {
                TaskContext task = it.next();
                if (b == null) {
                    // defer failure until we're inside here
                    b = layer(all.toArray(new Element[all.size()]));
                }
                boolean wasRaised = roundEnv.errorRaised();
                try {
                    task.task.run(b);
                    boolean nowRaised = roundEnv.errorRaised();
                    if (nowRaised && !wasRaised) {
                        utils().log("Generation failed in {0}", getClass());
                        break;
                    }
                } catch (Exception ex) {
                    ex.printStackTrace(System.out);
                    utils.fail(task.originatedBy + ": " + ex);
                } finally {
                    it.remove();
                }
            }
        } finally {
            layerTasks.clear();
        }
    }

    private final List<TaskContext> layerTasks = new ArrayList<>();

    /**
     * Add a task to build the layer, which will run on successful processing
     * completion.
     *
     * @param task The task
     * @param elements The elements generating for
     */
    protected void addLayerTask(LayerTask task, Element... elements) {
        utils().log("Add layer task: " + task);
        layerTasks.add(new TaskContext(task, elements, getClass().getName()));
    }

    private static final class TaskContext {

        final LayerTask task;
        final Element[] elements;
        private final String originatedBy;

        public TaskContext(LayerTask task, Element[] elements, String originatedBy) {
            this.task = task;
            this.elements = elements;
            this.originatedBy = originatedBy;
        }

        public String toString() {
            StringBuilder sb = new StringBuilder(originatedBy);
            sb.append("{").append(task).append("}");
            return sb.toString();
        }

    }

    protected boolean onRoundCompleted(Map<AnnotationMirror, Element> processed, RoundEnvironment env) throws Exception {
        return true;
    }

    protected boolean validateAnnotationMirror(AnnotationMirror mirror, ElementKind kind) {
        return true;
    }

    protected boolean processConstructorAnnotation(ExecutableElement constructor, AnnotationMirror mirror, RoundEnvironment roundEnv) throws Exception {
//        throw new IllegalStateException("Annotation not applicable to constructors or not implemented for them " + mirror.getAnnotationType());
        return true;
    }

    protected boolean processMethodAnnotation(ExecutableElement method, AnnotationMirror mirror, RoundEnvironment roundEnv) throws Exception {
//        throw new IllegalStateException("Annotation not applicable to methods or not implemented for them " + mirror.getAnnotationType());
        return true;
    }

    protected boolean processFieldAnnotation(VariableElement var, AnnotationMirror mirror, RoundEnvironment roundEnv) throws Exception {
//        throw new IllegalStateException("Annotation not applicable to fields or not implemented for them " + mirror.getAnnotationType());
        return true;
    }

    protected boolean processTypeAnnotation(TypeElement type, AnnotationMirror mirror, RoundEnvironment roundEnv) throws Exception {
//        throw new IllegalStateException("Annotation not applicable to classes or not implemented for them " + mirror.getAnnotationType());
        return true;
    }

    protected final void writeOne(ClassBuilder<String> cb, Element... elems) throws IOException {
        Filer filer = processingEnv.getFiler();
        JavaFileObject file = filer.createSourceFile(cb.fqn(), elems);
        try (OutputStream out = file.openOutputStream()) {
            out.write(cb.build().getBytes(UTF_8));
        }
    }
}

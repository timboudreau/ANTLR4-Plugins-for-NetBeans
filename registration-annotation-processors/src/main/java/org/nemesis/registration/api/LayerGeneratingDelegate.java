package org.nemesis.registration.api;

import java.util.function.BiConsumer;
import java.util.function.Function;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import org.nemesis.registration.codegen.ClassBuilder;
import org.nemesis.registration.utils.AnnotationUtils;
import org.nemesis.misc.utils.function.IOBiConsumer;
import org.openide.filesystems.annotations.LayerBuilder;

/**
 *
 * @author Tim Boudreau
 */
public abstract class LayerGeneratingDelegate extends Delegate {

    private Function<Element[], LayerBuilder> layerBuilderFetcher;
    private BiConsumer<LayerTask, Element[]> layerTaskAdder;

    protected LayerGeneratingDelegate() {
    }

    protected final void init(ProcessingEnvironment env, AnnotationUtils utils, IOBiConsumer<ClassBuilder<String>, Element[]> classWriter, Function<Element[], LayerBuilder> layerBuilderFetcher, BiConsumer<LayerTask, Element[]> layerTaskAdder) {
        this.layerBuilderFetcher = layerBuilderFetcher;
        this.layerTaskAdder = layerTaskAdder;
        super.init(env, utils, classWriter);
    }

    protected final LayerBuilder layer(Element... elements) {
        return layerBuilderFetcher.apply(elements);
    }

    protected final void addLayerTask(LayerTask task, Element... elements) {
        layerTaskAdder.accept(task, elements);
    }
}

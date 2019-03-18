package org.nemesis.registration.utils;

import java.util.function.Function;
import javax.lang.model.element.Element;

/**
 *
 * @author Tim Boudreau
 */
public class AnnotationMemberAsTypeTestBuilder<E extends Element, R> extends ElementTestBuilder<E, R, AnnotationMemberAsTypeTestBuilder<E, R>> {

    public AnnotationMemberAsTypeTestBuilder(AnnotationUtils utils, Function<AnnotationMemberAsTypeTestBuilder<E, R>, R> builder) {
        super(utils, builder);
    }

}

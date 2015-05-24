package de.benshu.jswizzle.model;

import java.lang.annotation.Annotation;

public interface AnnotatedReflection extends ElementReflection {
    default boolean isAnnotatedWith(Class<? extends Annotation> annotationClass) {
        return getMirror().getAnnotation(annotationClass) != null;
    }
}

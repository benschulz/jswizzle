package de.benshu.jswizzle.model;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;

public interface ElementReflection {
    Element getMirror();

    default ElementKind getKind() {
        return getMirror().getKind();
    }
}

package de.benshu.jswizzle.model;

import javax.lang.model.element.ExecutableElement;

public interface ExecutableElementReflection extends ElementReflection {
    @Override
    ExecutableElement getMirror();
}

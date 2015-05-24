package de.benshu.jswizzle.model;

import javax.lang.model.element.VariableElement;

public interface VariableDeclaration extends NamedReflection, HasModifiersMixin, AnnotatedReflection {
    @Override
    VariableElement getMirror();

    Type getType();
}

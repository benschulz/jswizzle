package de.benshu.jswizzle.model;

import javax.lang.model.element.VariableElement;

public class LocalVariableDeclaration implements VariableDeclaration {
    private final Reflection reflection;
    private final VariableElement mirror;

    public LocalVariableDeclaration(Reflection reflection, VariableElement mirror, Substitutions substitutions) {
        this.reflection = reflection;
        this.mirror = mirror;
    }

    @Override
    public VariableElement getMirror() {
        return mirror;
    }

    public String getName() {
        return mirror.getSimpleName().toString();
    }

    public Type getType() {
        return reflection.of(mirror.asType());
    }

    @Override
    public String toString() {
        return getName() + " : " + getType();
    }
}

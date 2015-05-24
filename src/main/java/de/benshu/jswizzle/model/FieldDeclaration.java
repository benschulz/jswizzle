package de.benshu.jswizzle.model;

import javax.lang.model.element.ElementKind;
import javax.lang.model.element.VariableElement;

import static com.google.common.base.Preconditions.checkArgument;

public class FieldDeclaration extends MemberDeclaration implements VariableDeclaration {
    private final Reflection reflection;
    private final VariableElement mirror;
    private final Substitutions substitutions;

    public FieldDeclaration(Reflection reflection, VariableElement mirror, Substitutions substitutions) {
        checkArgument(mirror.getKind() == ElementKind.FIELD);

        this.reflection = reflection;
        this.mirror = mirror;
        this.substitutions = substitutions;
    }

    @Override
    public VariableElement getMirror() {
        return mirror;
    }

    @Override
    public Type getType() {
        return substitutions.applyTo(reflection.of(mirror.asType()));
    }

    @Override
    public String getName() {
        return mirror.getSimpleName().toString();
    }

    @Override
    public String toString() {
        return getName() + " : " + getType();
    }
}

package de.benshu.jswizzle.model;

import com.google.common.collect.ImmutableList;
import de.benshu.commons.core.streams.Collectors;

import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;

import static com.google.common.base.Preconditions.checkArgument;

public class ConstructorDeclaration implements AnnotatedReflection, ParameterizedExecutableElementReflection, HasModifiersMixin {
    private final Reflection reflection;
    private final ExecutableElement mirror;

    public ConstructorDeclaration(Reflection reflection, ExecutableElement mirror) {
        checkArgument(mirror.getKind() == ElementKind.CONSTRUCTOR);

        this.reflection = reflection;
        this.mirror = mirror;
    }

    @Override
    public ExecutableElement getMirror() {
        return mirror;
    }

    public ImmutableList<LocalVariableDeclaration> getParameters() {
        return mirror.getParameters().stream()
                .map(reflection::of)
                .collect(Collectors.list());
    }
}

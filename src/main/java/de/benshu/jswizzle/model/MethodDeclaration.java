package de.benshu.jswizzle.model;

import com.google.common.collect.ImmutableList;
import de.benshu.commons.core.streams.Collectors;

import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;

import static com.google.common.base.Preconditions.checkArgument;

public class MethodDeclaration extends MemberDeclaration implements AnnotatedReflection, ParameterizedExecutableElementReflection, HasModifiersMixin {
    private final Reflection reflection;
    private final ExecutableElement mirror;
    private final Substitutions substitutions;

    public MethodDeclaration(Reflection reflection, ExecutableElement mirror, Substitutions substitutions) {
        checkArgument(mirror.getKind() == ElementKind.METHOD);

        this.reflection = reflection;
        this.mirror = mirror;
        this.substitutions = substitutions;
    }

    @Override
    public ExecutableElement getMirror() {
        return mirror;
    }

    @Override
    public String getName() {
        return mirror.getSimpleName().toString();
    }

    public TypeParameters getTypeParameters() {
        return reflection.ofTypeParametersOf(mirror, substitutions);
    }

    public ImmutableList<LocalVariableDeclaration> getParameters() {
        return mirror.getParameters().stream()
                .map(p -> new LocalVariableDeclaration(reflection, p, substitutions))
                .collect(Collectors.list());
    }

    public Type getReturnType() {
        return substitutions.applyTo(reflection.of(mirror.getReturnType()));
    }
}

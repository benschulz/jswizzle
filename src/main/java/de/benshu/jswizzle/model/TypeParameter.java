package de.benshu.jswizzle.model;

import com.google.common.base.CaseFormat;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import javax.lang.model.element.TypeParameterElement;
import java.util.stream.Stream;

import static de.benshu.commons.core.streams.Collectors.list;
import static java.util.stream.Collectors.joining;

public class TypeParameter implements JavaSourceConvertible {
    private final Reflection reflection;
    private final TypeParameterElement typeParameter;
    private final Substitutions substitutions;

    TypeParameter(Reflection reflection, TypeParameterElement typeParameter, Substitutions substitutions) {
        this.reflection = reflection;
        this.typeParameter = typeParameter;
        this.substitutions = substitutions;
    }

    public Identifier getName() {
        return Identifier.from(typeParameter.getSimpleName(), CaseFormat.UPPER_UNDERSCORE);
    }

    public String asJavaSource(ImmutableSet<AsJavaSourceOptions> options) {
        final ImmutableList<Type> upperBounds = getUpperBounds();

        return getName().getScreamingSnakeCased() + (upperBounds.isEmpty() ? ""
                : " extends " + upperBounds.stream().map(b -> b.asJavaSource(options)).collect(joining("&")));
    }

    public Stream<FullyQualifiedName> referencedTypes() {
        return getUpperBounds().stream().flatMap(Type::referencedTypes);
    }

    public ImmutableList<Type> getUpperBounds() {
        return typeParameter.getBounds().stream()
                .map(reflection::of)
                .map(substitutions::applyTo)
                .collect(list());
    }
}

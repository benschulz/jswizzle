package de.benshu.jswizzle.model;

import com.google.common.base.CaseFormat;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import javax.lang.model.element.TypeParameterElement;

import static de.benshu.jswizzle.utils.SwizzleCollectors.list;
import static de.benshu.jswizzle.utils.SwizzleCollectors.set;
import static java.util.stream.Collectors.joining;

public class TypeParameter implements JavaSourceConvertible {
    private final TypeParameterElement typeParameter;

    TypeParameter(TypeParameterElement typeParameter) {
        this.typeParameter = typeParameter;
    }

    public Identifier getName() {
        return Identifier.from(typeParameter.getSimpleName(), CaseFormat.UPPER_UNDERSCORE);
    }

    public String asJavaSource(ImmutableSet<AsJavaSourceOptions> options) {
        final ImmutableList<Type> upperBounds = getUpperBounds();

        return getName().getScreamingSnakeCased() + (upperBounds.isEmpty() ? ""
                : " extends " + upperBounds.stream().map(b -> b.asJavaSource(options)).collect(joining("&")));
    }

    public ImmutableSet<FullyQualifiedName> extractReferencedTypes() {
        return getUpperBounds().stream()
                .flatMap(b -> b.extractReferencedTypes().stream())
                .collect(set());
    }

    public ImmutableList<Type> getUpperBounds() {
        return typeParameter.getBounds().stream()
                .map(Type::from)
                .collect(list());
    }
}

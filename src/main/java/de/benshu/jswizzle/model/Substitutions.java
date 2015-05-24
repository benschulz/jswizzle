package de.benshu.jswizzle.model;

import com.google.common.collect.ImmutableMap;

import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ErrorType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.SimpleTypeVisitor8;
import javax.lang.model.util.Types;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.Maps.immutableEntry;
import static de.benshu.commons.core.streams.Collectors.list;
import static de.benshu.commons.core.streams.Collectors.map;

public class Substitutions {
    public static Substitutions none() {
        return new Substitutions(null, ImmutableMap.of());
    }

    private final Reflection reflection;
    private final ImmutableMap<TypeParameterElement, Type> substitutions;

    private Substitutions(Reflection reflection, ImmutableMap<TypeParameterElement, Type> substitutions) {
        this.reflection = reflection;
        this.substitutions = substitutions;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof Substitutions && isEqualTo((Substitutions) obj);
    }

    private boolean isEqualTo(Substitutions other) {
        return other == this || other.substitutions.equals(substitutions);
    }

    @Override
    public int hashCode() {
        return substitutions.hashCode();
    }

    public Type applyTo(Type type) {
        return substitutions.isEmpty() ? type
                : new SubstitutingTypeVisitor().visit(type.getMirror());
    }

    private class SubstitutingTypeVisitor extends SimpleTypeVisitor8<Type, Void> {
        private final Types types;

        public SubstitutingTypeVisitor() {
            this.types = reflection.getProcessingEnvironment().getTypeUtils();
        }

        @Override
        public Type visitDeclared(DeclaredType declaredType, Void aVoid) {
            return reflection.of(types.getDeclaredType(
                    (TypeElement) declaredType.asElement(),
                    applySubstitutionsToArgumentsOf(declaredType)
            ));
        }

        @Override
        public Type visitTypeVariable(TypeVariable variable, Void aVoid) {
            return substitutions.entrySet().stream()
                    .filter(e -> e.getKey().asType().equals(variable))
                    .map(Map.Entry::getValue)
                    .findAny()
                    .orElse(reflection.of(variable));
        }

        @Override
        public Type visitError(ErrorType errorType, Void aVoid) {
            return reflection.of(errorType.getTypeArguments().isEmpty() ? errorType
                    : types.getDeclaredType((TypeElement) errorType.asElement(), applySubstitutionsToArgumentsOf(errorType)));
        }

        @Override
        public Type visitPrimitive(PrimitiveType primitiveType, Void aVoid) {
            return reflection.of(primitiveType);
        }

        @Override
        public Type visitWildcard(WildcardType wildcardType, Void aVoid) {
            return reflection.of(wildcardType);
        }

        private TypeMirror[] applySubstitutionsToArgumentsOf(DeclaredType declaredType) {
            return declaredType.getTypeArguments().stream()
                    .map(this::visit)
                    .map(Type::getMirror)
                    .toArray(TypeMirror[]::new);
        }

        @Override
        protected Type defaultAction(TypeMirror e, Void aVoid) {
            throw new AssertionError();
        }
    }

    public static class InParametersPreparation {
        private final Reflection reflection;

        InParametersPreparation(Reflection reflection) {
            this.reflection = reflection;
        }

        public InArgumentsPreparation of(List<? extends TypeParameterElement> parameters) {
            return new InArgumentsPreparation(reflection, parameters);
        }
    }

    public static class InArgumentsPreparation {
        private final Reflection reflection;
        private final List<? extends TypeParameterElement> parameters;

        InArgumentsPreparation(Reflection reflection, List<? extends TypeParameterElement> parameters) {
            this.reflection = reflection;
            this.parameters = parameters;
        }

        public Substitutions throughVariables() {
            return through(parameters.stream()
                    .map(Element::asType)
                    .map(reflection::of)
                    .collect(list()));
        }

        public Substitutions through(List<? extends Type> arguments) {
            checkArgument(arguments.size() == parameters.size());

            return new Substitutions(reflection, IntStream.range(0, parameters.size())
                    .mapToObj(i -> immutableEntry(parameters.get(i), arguments.get(i)))
                    .collect(map()));
        }
    }
}

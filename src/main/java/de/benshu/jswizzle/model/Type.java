package de.benshu.jswizzle.model;

import com.google.common.collect.ImmutableSet;

import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ErrorType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.SimpleTypeVisitor8;

import static java.util.stream.Collectors.joining;

public class Type implements JavaSourceConvertible {
    public static Type from(TypeMirror typeMirror) {
        return new Type(typeMirror);
    }

    private final TypeMirror type;

    private Type(TypeMirror type) {
        this.type = type;
    }

    public String asJavaSource(ImmutableSet<AsJavaSourceOptions> options) {
        return new AsJavaSourceVisitor(options).visit(type);
    }

    public ImmutableSet<FullyQualifiedName> extractReferencedTypes() {
        return TypeReferenceCollectingVisitor.INSTANCE.visit(type, ImmutableSet.builder()).build();
    }

    private static class TypeReferenceCollectingVisitor extends SimpleTypeVisitor8<ImmutableSet.Builder<FullyQualifiedName>, ImmutableSet.Builder<FullyQualifiedName>> {
        private static TypeReferenceCollectingVisitor INSTANCE = new TypeReferenceCollectingVisitor();

        @Override
        public ImmutableSet.Builder<FullyQualifiedName> visitDeclared(DeclaredType declaredType, ImmutableSet.Builder<FullyQualifiedName> aggregator) {
            aggregator.add(FullyQualifiedName.of((TypeElement) declaredType.asElement()));
            declaredType.getTypeArguments().forEach(a -> visit(a, aggregator));
            return aggregator;
        }

        @Override
        public ImmutableSet.Builder<FullyQualifiedName> visitArray(ArrayType arrayType, ImmutableSet.Builder<FullyQualifiedName> aggregator) {
            return visit(arrayType.getComponentType());
        }

        @Override
        public ImmutableSet.Builder<FullyQualifiedName> visitWildcard(WildcardType wildcardType, ImmutableSet.Builder<FullyQualifiedName> aggregator) {
            if (wildcardType.getExtendsBound() != null)
                return visit(wildcardType.getExtendsBound(), aggregator);
            else if (wildcardType.getSuperBound() != null)
                return visit(wildcardType.getSuperBound(), aggregator);
            else
                return aggregator;
        }

        @Override
        protected ImmutableSet.Builder<FullyQualifiedName> defaultAction(TypeMirror typeMirror, ImmutableSet.Builder<FullyQualifiedName> aggregator) {
            return aggregator;
        }
    }

    private static class AsJavaSourceVisitor extends SimpleTypeVisitor8<String, Void> {
        private final ImmutableSet<AsJavaSourceOptions> options;

        public AsJavaSourceVisitor(ImmutableSet<AsJavaSourceOptions> options) {
            this.options = options;
        }

        @Override
        public String visitDeclared(DeclaredType declaredType, Void v) {
            return FullyQualifiedName.of((TypeElement) declaredType.asElement()).asJavaSource(options) + (declaredType.getTypeArguments().isEmpty() ? ""
                    : "<" + declaredType.getTypeArguments().stream().map(this::visit).collect(joining(", ")) + ">");
        }

        @Override
        public String visitPrimitive(PrimitiveType primitiveType, Void v) {
            return primitiveType.getKind().name().toLowerCase();
        }

        @Override
        public String visitArray(ArrayType arrayType, Void v) {
            return this.visit(arrayType.getComponentType()) + "[]";
        }

        @Override
        public String visitTypeVariable(TypeVariable typeVariable, Void v) {
            return typeVariable.asElement().getSimpleName().toString();
        }

        @Override
        public String visitError(ErrorType errorType, Void v) {
            // best effort..
            return errorType.toString();
        }

        @Override
        public String visitWildcard(WildcardType wildcardType, Void aVoid) {
            return "?"
                    + (wildcardType.getExtendsBound() == null ? "" : " extends " + visit(wildcardType.getExtendsBound()))
                    + (wildcardType.getSuperBound() == null ? "" : " super " + visit(wildcardType.getSuperBound()));
        }

        @Override
        protected String defaultAction(TypeMirror typeMirror, Void v) {
            throw new AssertionError();
        }
    }
}

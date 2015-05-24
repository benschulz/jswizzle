package de.benshu.jswizzle.model;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.util.ElementFilter;
import java.util.Optional;
import java.util.stream.Stream;

import static de.benshu.commons.core.streams.Collectors.list;

public class TypeDeclaration implements NamedReflection, AnnotatedReflection, HasModifiersMixin {
    private final Reflection reflection;
    private final TypeElement mirror;
    private final Type type;
    private final Substitutions substitutions;

    public TypeDeclaration(Reflection reflection, TypeElement mirror, Type type, Substitutions substitutions) {
        this.reflection = reflection;
        this.mirror = mirror;
        this.type = type;
        this.substitutions = substitutions;
    }

    @Override
    public TypeElement getMirror() {
        return mirror;
    }

    public Type asType() {
        return type;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof TypeDeclaration && isEqualTo((TypeDeclaration) obj);
    }

    private boolean isEqualTo(TypeDeclaration other) {
        return other == this || other.mirror.equals(mirror) && other.substitutions.equals(substitutions);
    }

    @Override
    public int hashCode() {
        return mirror.hashCode();
    }

    @Override
    public String getName() {
        return mirror.getSimpleName().toString();
    }

    public FullyQualifiedName getQualifiedName() {
        return FullyQualifiedName.of(mirror);
    }

    @Override
    public String toString() {
        return type.toString();
    }

    public Package getPackage() {
        return new Package(reflection, reflection.getProcessingEnvironment().getElementUtils().getPackageOf(mirror));
    }

    public TypeParameters getTypeParameters() {
        return reflection.ofTypeParametersOf(mirror, substitutions);
    }

    public Stream<ConstructorDeclaration> constructors() {
        return ElementFilter.constructorsIn(mirror.getEnclosedElements()).stream()
                .map(c -> new ConstructorDeclaration(reflection, c));
    }

    public Stream<MethodDeclaration> staticMethods() {
        return ElementFilter.methodsIn(mirror.getEnclosedElements()).stream()
                .map(m -> new MethodDeclaration(reflection, m, substitutions));
    }

    public Optional<TypeDeclaration> getSuperclass() {
        return Optional.of(mirror.getSuperclass())
                .filter(t -> t.getKind() != TypeKind.NONE)
                .map(DeclaredType.class::cast)
                .map(this::toTypeDeclaration);
    }

    public Stream<TypeDeclaration> supertypes() {
        return Stream.concat(superclasses(), interfaces());
    }

    public Stream<TypeDeclaration> superclasses() {
        return getSuperclass().map(Stream::of).orElse(Stream.empty());
    }

    public Stream<TypeDeclaration> interfaces() {
        return mirror.getInterfaces().stream()
                .map(DeclaredType.class::cast)
                .map(this::toTypeDeclaration);
    }

    public Stream<MemberDeclaration> allMemberDeclarations() {
        return Stream.concat(declaredMemberDeclarations(), inheritedMemberDeclarations());
    }

    public Stream<MemberDeclaration> declaredMemberDeclarations() {
        return mirror.getEnclosedElements().stream().flatMap(this::toPotentialMemberDeclaration);
    }

    private Stream<MemberDeclaration> toPotentialMemberDeclaration(Element element) {
        if (element.getModifiers().contains(Modifier.STATIC))
            return Stream.empty();

        switch (element.getKind()) {
            case FIELD:
                return Stream.of(new FieldDeclaration(reflection, (VariableElement) element, substitutions));
            case METHOD:
                return Stream.of(new MethodDeclaration(reflection, (ExecutableElement) element, substitutions));
            default:
                return Stream.empty();
        }
    }

    public Stream<MemberDeclaration> inheritedMemberDeclarations() {
        return supertypes().flatMap(TypeDeclaration::allMemberDeclarations);
    }

    private TypeDeclaration toTypeDeclaration(DeclaredType supertype) {
        final TypeElement supertypeElement = (TypeElement) supertype.asElement();

        return new TypeDeclaration(reflection, supertypeElement, substitutions.applyTo(reflection.of(supertype)), reflection.substitutions()
                .of(supertypeElement.getTypeParameters())
                .through(supertype.getTypeArguments().stream()
                        .map(reflection::of)
                        .map(substitutions::applyTo)
                        .collect(list())));
    }
}

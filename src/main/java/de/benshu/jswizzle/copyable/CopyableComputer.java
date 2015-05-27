package de.benshu.jswizzle.copyable;

import com.google.common.base.CaseFormat;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import de.benshu.jswizzle.MixinComputer;
import de.benshu.jswizzle.internal.Template;
import de.benshu.jswizzle.model.AnnotatedReflection;
import de.benshu.jswizzle.model.AsJavaSourceOptions;
import de.benshu.jswizzle.model.ConstructorDeclaration;
import de.benshu.jswizzle.model.ExecutableElementReflection;
import de.benshu.jswizzle.model.FieldDeclaration;
import de.benshu.jswizzle.model.Identifier;
import de.benshu.jswizzle.model.Import;
import de.benshu.jswizzle.model.MemberDeclaration;
import de.benshu.jswizzle.model.MethodDeclaration;
import de.benshu.jswizzle.model.MixinComponent;
import de.benshu.jswizzle.model.ParameterizedExecutableElementReflection;
import de.benshu.jswizzle.model.Reflection;
import de.benshu.jswizzle.model.Type;
import de.benshu.jswizzle.model.TypeDeclaration;
import org.kohsuke.MetaInfServices;

import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.google.common.collect.Maps.immutableEntry;
import static de.benshu.commons.core.streams.Collectors.list;
import static de.benshu.commons.core.streams.Collectors.map;
import static de.benshu.commons.core.streams.Collectors.set;
import static de.benshu.commons.core.streams.Collectors.setMultimap;
import static java.util.stream.Collectors.joining;

@MetaInfServices(CopyableComputer.class)
public class CopyableComputer extends MixinComputer {
    @Override
    public MixinComponent computeFor(Reflection reflection, Element e) {
        final TypeDeclaration mix = reflection.of((TypeElement) e);
        final Type mixType = mix.asType();

        final Optional<ParameterizedExecutableElementReflection> constructorOrFactory = findConstructorOrFactory(mix);
        final ImmutableList<Property> properties = determineProperties(mix, constructorOrFactory);

        final String simpleMixType = mixType.asJavaSource(AsJavaSourceOptions.SIMPLE_NAMES);

        return new MixinComponent() {
            @Override
            public TypeDeclaration getMix() {
                return mix;
            }

            @Override
            public ImmutableSet<Import> getRequiredImports() {
                final Stream<Import> mixImport = Stream.of(Import.of(mix.getQualifiedName()));

                final Stream<Import> propertyImports = properties.stream()
                        .flatMap(p -> p.getType().referencedTypes())
                        .map(Import::of);

                return Stream.concat(mixImport, propertyImports).collect(set());
            }

            @Override
            public String getBody() {
                return properties.stream()
                        .map(this::toCopyMethod)
                        .collect(Collectors.joining());
            }

            private String toCopyMethod(Property property) {
                if (constructorOrFactory.isPresent())
                    return Template.render("copy-method.java.template", ImmutableMap.of(
                            "simpleMixType", simpleMixType,
                            "property", property,
                            // TODO Figure out how to move this to the template.
                            "copyInvocation", copyInvocationFor(constructorOrFactory.get(), property)
                    ));
                else
                    return Template.render("abstract-copy-method.java.template", ImmutableMap.of(
                            "simpleMixType", simpleMixType,
                            "property", property
                    ));
            }

            private String copyInvocationFor(ExecutableElementReflection constructorOrFactory, Property property) {
                return copyInvocationStartFor(constructorOrFactory) + copyInvocationArgumentList(property);
            }

            private String copyInvocationStartFor(ExecutableElementReflection constructorOrFactory) {
                switch (constructorOrFactory.getKind()) {
                    case METHOD:
                        MethodDeclaration methodDeclaration = (MethodDeclaration) constructorOrFactory;
                        return simpleMixType.split("<")[0] + "." + methodDeclaration.getName();
                    case CONSTRUCTOR:
                        return "new " + simpleMixType;
                    default:
                        throw new AssertionError();
                }
            }

            private String copyInvocationArgumentList(Property property) {
                return "(\n" + properties.stream().map(p -> p == property ? "                %CHANGED%" : determineGetOf(p)).collect(joining(",\n")) + "\n        )";
            }

            private String determineGetOf(Property property) {
                return "                ((" + simpleMixType + ") this)." + property.getAccessor();
            }
        };
    }

    private Optional<ParameterizedExecutableElementReflection> findConstructorOrFactory(TypeDeclaration mix) {
        final Stream<ParameterizedExecutableElementReflection> annotated = Stream.concat(
                mix.constructors().filter(c -> c.isAnnotatedWith(CopyConstructor.class)),
                mix.staticMethods().filter(m -> m.isAnnotatedWith(CopyFactory.class))
        );

        final Stream<ConstructorDeclaration> implicit = mix.isAbstract()
                ? Stream.empty()
                : mix.constructors()
                .filter(c -> !c.isPrivate())
                .sorted((a, b) -> -(a.getParameters().size() - b.getParameters().size()));

        return Stream.concat(annotated, implicit).findFirst();
    }

    private ImmutableList<Property> determineProperties(TypeDeclaration mix, Optional<ParameterizedExecutableElementReflection> constructorOrFactory) {
        final ImmutableSetMultimap<Identifier, Property> nonExcludedProperties = mix.allMemberDeclarations()
                .flatMap(this::toPotentialProperty)
                .map(p -> immutableEntry(p.getName(), p))
                .collect(setMultimap())
                .asMap().entrySet().stream()
                .filter(e -> e.getValue().stream().noneMatch(Property::isExcluded))
                .flatMap(e -> e.getValue().stream().map(p -> immutableEntry(e.getKey(), p)))
                .collect(setMultimap());

        final ImmutableSetMultimap<Identifier, Property> includedProperties = nonExcludedProperties.entries().stream()
                .anyMatch(e -> e.getValue().isIncluded())
                ? nonExcludedProperties.entries().stream().filter(e -> e.getValue().isIncluded()).collect(setMultimap())
                : nonExcludedProperties;

        final ImmutableMap<Identifier, Property> properties = includedProperties.asMap().entrySet().stream()
                .filter(e -> e.getValue().stream().noneMatch(Property::isExcluded))
                .map(e -> immutableEntry(e.getKey(), e.getValue().iterator().next())) // select any of the equally named nonExcludedProperties
                .collect(map());

        if (constructorOrFactory.isPresent())
            return constructorOrFactory.get().getParameters().stream()
                    .map(p -> {
                        final Identifier id = Identifier.from(p.getName());
                        final Type type = p.getType();

                        return properties.getOrDefault(id, new Property(p, id, type, "NO_SUCH_PROPERTY_" + id.getScreamingSnakeCased()));
                    })
                    .collect(list());
        else
            return ImmutableList.copyOf(properties.values());
    }

    private Stream<Property> toPotentialProperty(MemberDeclaration memberDeclaration) {
        if (memberDeclaration.isPrivate())
            return Stream.empty();

        switch (memberDeclaration.getKind()) {
            case FIELD:
                return Stream.of(toProperty((FieldDeclaration) memberDeclaration));
            case METHOD:
                return toPotentialProperty((MethodDeclaration) memberDeclaration);
            default:
                return Stream.empty();
        }

    }

    private Property toProperty(FieldDeclaration field) {
        final String name = field.getName();
        final Identifier id = Identifier.from(name);
        final Type type = field.getType();

        return new Property(field, id, type, name);
    }

    private Stream<Property> toPotentialProperty(MethodDeclaration method) {
        if (!method.getTypeParameters().isEmpty() || !method.getParameters().isEmpty() || !method.getName().matches("^(get|is)[A-Z].*"))
            return Stream.empty();

        final String name = method.getName();
        final Identifier id = Identifier.from(name.substring(name.startsWith("get") ? 3 : 2), CaseFormat.UPPER_CAMEL);
        final Type type = method.getReturnType();

        return Stream.of(new Property(method, id, type, name + "()"));
    }

    public static class Property {
        private final AnnotatedReflection reflection;
        private final Identifier name;
        private final Type type;
        private final String simpleTypeName;
        private final String accessor;

        public Property(AnnotatedReflection reflection, Identifier name, Type type, String accessor) {
            this.reflection = reflection;
            this.name = name;
            this.type = type;
            this.simpleTypeName = type.asJavaSource(AsJavaSourceOptions.SIMPLE_NAMES);
            this.accessor = accessor;
        }

        public boolean isExcluded() {
            return reflection.isAnnotatedWith(Copyable.Exclude.class);
        }

        public boolean isIncluded() {
            return reflection.isAnnotatedWith(Copyable.Include.class);
        }

        public Identifier getName() {
            return name;
        }

        public String getSimpleTypeName() {
            return simpleTypeName;
        }

        public Type getType() {
            return type;
        }

        public String getAccessor() {
            return accessor;
        }
    }
}

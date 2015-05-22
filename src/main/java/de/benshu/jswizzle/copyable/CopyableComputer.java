package de.benshu.jswizzle.copyable;

import com.google.common.base.CaseFormat;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import de.benshu.jswizzle.MixinComputer;
import de.benshu.jswizzle.internal.Template;
import de.benshu.jswizzle.model.AsJavaSourceOptions;
import de.benshu.jswizzle.model.FullyQualifiedName;
import de.benshu.jswizzle.model.Identifier;
import de.benshu.jswizzle.model.Import;
import de.benshu.jswizzle.model.MixinComponent;
import de.benshu.jswizzle.model.Type;
import org.kohsuke.MetaInfServices;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.ElementFilter;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.google.common.collect.Maps.immutableEntry;
import static de.benshu.jswizzle.utils.SwizzleCollectors.list;
import static de.benshu.jswizzle.utils.SwizzleCollectors.map;
import static de.benshu.jswizzle.utils.SwizzleCollectors.set;
import static de.benshu.jswizzle.utils.SwizzleCollectors.setMultimap;
import static java.util.stream.Collectors.joining;

@MetaInfServices(CopyableComputer.class)
public class CopyableComputer extends MixinComputer {
    @Override
    public MixinComponent computeFor(ProcessingEnvironment processingEnvironment, Element e) {
        final TypeElement mix = (TypeElement) e;
        final Type mixType = Type.from(mix.asType());

        final Optional<ExecutableElement> constructorOrFactory = findConstructorOrFactory(mix);
        final ImmutableList<Property> properties = determineProperties(processingEnvironment, mix, constructorOrFactory);

        return new MixinComponent() {
            @Override
            public TypeElement getMix() {
                return mix;
            }

            @Override
            public ImmutableSet<Import> getRequiredImports() {
                final Stream<Import> mixImport = Stream.of(FullyQualifiedName.of(mix)).map(Import::of);

                final Stream<Import> propertyImports = properties.stream()
                        .flatMap(p -> p.getType().extractReferencedTypes().stream())
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
                            "qualifiedMixType", mixType.asJavaSource(),
                            "property", property,
                            // TODO Figure out how to move this to the template.
                            "copyInvocation", copyInvocationFor(constructorOrFactory.get(), property)
                    ));
                else
                    return Template.render("abstract-copy-method.java.template", ImmutableMap.of(
                            "qualifiedMixType", mixType.asJavaSource(),
                            "property", property
                    ));
            }

            private String copyInvocationFor(ExecutableElement constructorOrFactory, Property property) {
                return copyInvocationStartFor(constructorOrFactory) + copyInvocationArgumentList(property);
            }

            private String copyInvocationStartFor(ExecutableElement constructorOrFactory) {
                return constructorOrFactory.getKind() == ElementKind.CONSTRUCTOR
                        ? "new " + mixType.asJavaSource()
                        : mixType.asJavaSource().split("<")[0] + "." + constructorOrFactory.getSimpleName();
            }

            private String copyInvocationArgumentList(Property property) {
                return "(\n" + properties.stream().map(p -> p == property ? "                %CHANGED%" : determineGetOf(p)).collect(joining(",\n")) + "\n        )";
            }

            private String determineGetOf(Property property) {
                return "                ((" + mixType.asJavaSource() + ") this)." + property.getAccessor();
            }
        };
    }

    private Optional<ExecutableElement> findConstructorOrFactory(TypeElement mix) {
        final List<ExecutableElement> constructors = ElementFilter.constructorsIn(mix.getEnclosedElements());
        final List<ExecutableElement> methods = ElementFilter.methodsIn(mix.getEnclosedElements());

        final ImmutableSet<ExecutableElement> annotated = FluentIterable.from(ImmutableList.<ExecutableElement>of())
                .append(FluentIterable.from(constructors).filter(c -> c.getAnnotation(CopyConstructor.class) != null))
                .append(FluentIterable.from(methods).filter(m -> m.getModifiers().contains(Modifier.STATIC) && m.getAnnotation(CopyFactory.class) != null))
                .toSet();

        final ImmutableSet<ExecutableElement> implicit = mix.getModifiers().contains(Modifier.ABSTRACT)
                ? ImmutableSet.of()
                : ImmutableSet.copyOf(constructors.stream()
                .filter(c -> !c.getModifiers().contains(Modifier.PRIVATE))
                .sorted((a, b) -> -(a.getParameters().size() - b.getParameters().size()))
                .limit(1).iterator());

        return FluentIterable.from(annotated).append(implicit).toList().stream().findFirst();
    }

    private ImmutableList<Property> determineProperties(ProcessingEnvironment processingEnvironment, TypeElement mix, Optional<ExecutableElement> constructorOrFactory) {
        final ImmutableSetMultimap<Identifier, Property> nonExcludedProperties = getAllElements(processingEnvironment, mix)
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
                        final Identifier id = Identifier.from(p.getSimpleName());
                        final Type type = Type.from(p.asType());
                        return properties.getOrDefault(id, new Property(p, id, type, "NO_SUCH_PROPERTY_" + id.getScreamingSnakeCased()));
                    })
                    .collect(list());
        else
            return ImmutableList.copyOf(properties.values());
    }

    private Stream<Element> getAllElements(ProcessingEnvironment processingEnvironment, TypeElement typeElement) {
        return typeElement == null ? Stream.empty() : Stream.concat(
                typeElement.getEnclosedElements().stream(),
                getInheritedElements(processingEnvironment, typeElement)
        );
    }

    // FIXME Type arguments must be substituted in resulting properties' types.
    private Stream<Element> getInheritedElements(ProcessingEnvironment processingEnvironment, TypeElement typeElement) {
        return Stream.concat(
                getAllElements(processingEnvironment, (TypeElement) processingEnvironment.getTypeUtils().asElement(typeElement.getSuperclass())),
                typeElement.getInterfaces().stream()
                        .flatMap(i -> getAllElements(processingEnvironment, (TypeElement) processingEnvironment.getTypeUtils().asElement(i)))
        );
    }

    private Stream<Property> toPotentialProperty(Element element) {
        if (element.getModifiers().contains(Modifier.PRIVATE))
            return Stream.empty();

        switch (element.getKind()) {
            case FIELD:
                return Stream.of(toProperty((VariableElement) element));
            case METHOD:
                return toPotentialProperty((ExecutableElement) element);
            default:
                return Stream.empty();
        }

    }

    private Property toProperty(VariableElement field) {
        final String name = field.getSimpleName().toString();
        final Identifier id = Identifier.from(name);
        final Type type = Type.from(field.asType());

        return new Property(field, id, type, name);
    }

    private Stream<Property> toPotentialProperty(ExecutableElement method) {
        if (!method.getTypeParameters().isEmpty() || !method.getParameters().isEmpty() || !method.getSimpleName().toString().matches("^(get|is)[A-Z].*"))
            return Stream.empty();

        final String name = method.getSimpleName().toString();
        final Identifier id = Identifier.from(name.substring(name.startsWith("get") ? 3 : 2), CaseFormat.UPPER_CAMEL);
        final Type type = Type.from(method.getReturnType());

        return Stream.of(new Property(method, id, type, name + "()"));
    }

    public static class Property {
        private final Element element;
        private final Identifier name;
        private final Type type;
        private final String simpleTypeName;
        private final String accessor;

        public Property(Element element, Identifier name, Type type, String accessor) {
            this.element = element;
            this.name = name;
            this.type = type;
            this.simpleTypeName = type.asJavaSource(AsJavaSourceOptions.SIMPLE_NAMES);
            this.accessor = accessor;
        }

        public boolean isExcluded() {
            return element.getAnnotation(Copyable.Exclude.class) != null;
        }

        public boolean isIncluded() {
            return element.getAnnotation(Copyable.Include.class) != null;
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

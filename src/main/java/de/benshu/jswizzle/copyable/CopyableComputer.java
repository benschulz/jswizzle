package de.benshu.jswizzle.copyable;

import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
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
import javax.lang.model.util.ElementFilter;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static de.benshu.jswizzle.utils.SwizzleCollectors.orderedSet;
import static de.benshu.jswizzle.utils.SwizzleCollectors.set;
import static java.util.stream.Collectors.joining;

@MetaInfServices(CopyableComputer.class)
public class CopyableComputer extends MixinComputer {
    @Override
    public MixinComponent computeFor(ProcessingEnvironment processingEnvironment, Element e) {
        final TypeElement mix = (TypeElement) e;
        final Type mixType = Type.from(mix.asType());

        final ExecutableElement constructorOrFactory = findConstructorOrFactory(mix);

        final ImmutableSet<Property> properties = constructorOrFactory.getParameters().stream()
                .map(p -> new Property(
                        Identifier.from(p.getSimpleName()),
                        Type.from(p.asType())))
                .collect(orderedSet());

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
                return Template.render("copy-method.java.template", ImmutableMap.of(
                        "simpleMixName", mixType.asJavaSource(AsJavaSourceOptions.SIMPLE_NAMES),
                        "property", property,
                        "copyInvocation", copyInvocationFor(constructorOrFactory, property)
                ));
            }

            private String copyInvocationFor(ExecutableElement constructorOrFactory, Property property) {
                return copyInvocationStartFor(constructorOrFactory) + copyInvocationArgumentList(property);
            }

            private String copyInvocationStartFor(ExecutableElement constructorOrFactory) {
                return constructorOrFactory.getKind() == ElementKind.CONSTRUCTOR
                        ? "new " + mixType.asJavaSource(AsJavaSourceOptions.SIMPLE_NAMES)
                        : mixType.asJavaSource(AsJavaSourceOptions.SIMPLE_NAMES).split("<")[0] + "." + constructorOrFactory.getSimpleName();
            }

            private String copyInvocationArgumentList(Property property) {
                return "(" + properties.stream().map(p -> p == property ? "%CHANGED%" : determineGetOf(p)).collect(joining(", ")) + ")";
            }

            private String determineGetOf(Property property) {
                return "((" + mixType.asJavaSource(AsJavaSourceOptions.SIMPLE_NAMES) + ")this)." + property.getName().getCamelCased();
            }
        };
    }

    private ExecutableElement findConstructorOrFactory(TypeElement mix) {
        final List<ExecutableElement> constructors = ElementFilter.constructorsIn(mix.getEnclosedElements());
        final List<ExecutableElement> methods = ElementFilter.methodsIn(mix.getEnclosedElements());

        final ImmutableSet<ExecutableElement> annotated = FluentIterable.from(ImmutableList.<ExecutableElement>of())
                .append(FluentIterable.from(constructors).filter(c -> c.getAnnotation(CopyConstructor.class) != null))
                .append(FluentIterable.from(methods).filter(m -> m.getModifiers().contains(Modifier.STATIC) && m.getAnnotation(CopyFactory.class) != null))
                .toSet();

        return FluentIterable.from(annotated)
                .append(constructors.stream().max((a, b) -> a.getParameters().size() - b.getParameters().size()).get())
                .first().get();
    }

    public static class Property {
        private final Identifier name;
        private final Type type;
        private final String simpleTypeName;

        public Property(Identifier name, Type type) {
            this.name = name;
            this.type = type;
            this.simpleTypeName = type.asJavaSource(AsJavaSourceOptions.SIMPLE_NAMES);
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
    }
}

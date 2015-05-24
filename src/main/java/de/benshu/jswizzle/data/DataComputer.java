package de.benshu.jswizzle.data;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import de.benshu.jswizzle.MixinComputer;
import de.benshu.jswizzle.internal.Template;
import de.benshu.jswizzle.model.AsJavaSourceOptions;
import de.benshu.jswizzle.model.Identifier;
import de.benshu.jswizzle.model.Import;
import de.benshu.jswizzle.model.MixinComponent;
import de.benshu.jswizzle.model.Reflection;
import de.benshu.jswizzle.model.Type;
import de.benshu.jswizzle.model.TypeDeclaration;
import org.kohsuke.MetaInfServices;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;

import static de.benshu.commons.core.streams.Collectors.set;

@MetaInfServices(DataComputer.class)
public class DataComputer extends MixinComputer {
    @Override
    public MixinComponent computeFor(Reflection reflection, Element e) {
        if (e instanceof TypeElement)
            return accessorsForAllFields(reflection, (TypeElement) e);
        else
            return accessorForField(reflection, (VariableElement) e);
    }

    private MixinComponent accessorsForAllFields(Reflection reflection, TypeElement mix) {
        final ImmutableSet<Property> properties = mix.getEnclosedElements().stream()
                .filter(e -> e.getKind() == ElementKind.FIELD)
                .filter(e -> e.getAnnotation(Data.Exclude.class) == null)
                .map(VariableElement.class::cast)
                .map(v -> new Property(v, reflection.of(v.asType())))
                .collect(set());

        return createMixinComponent(reflection.of(mix), properties);
    }

    private MixinComponent accessorForField(Reflection reflection, VariableElement field) {
        final TypeElement mix = (TypeElement) field.getEnclosingElement();
        final Property property = new Property(field, reflection.of(field.asType()));

        return createMixinComponent(reflection.of(mix), ImmutableSet.of(property));
    }

    private MixinComponent createMixinComponent(final TypeDeclaration mix, final ImmutableSet<Property> properties) {
        return new MixinComponent() {
            @Override
            public TypeDeclaration getMix() {
                return mix;
            }

            @Override
            public ImmutableSet<Import> getRequiredImports() {
                return properties.stream()
                        .flatMap(p -> p.getType().referencedTypes())
                        .map(Import::of)
                        .collect(set());
            }

            @Override
            public String getBody() {
                return Template.render("accessors.java.template", ImmutableMap.of(
                        "qualifiedMixType", mix.asType().asJavaSource(),
                        "properties", properties
                ));
            }
        };
    }

    public static class Property {
        private final Identifier name;
        private final Type type;
        private final String simpleTypeName;
        private final boolean writable;

        public Property(VariableElement field, Type type) {
            this.name = Identifier.from(field.getSimpleName());
            this.type = type;
            this.simpleTypeName = type.asJavaSource(AsJavaSourceOptions.SIMPLE_NAMES);
            this.writable = !field.getModifiers().contains(Modifier.FINAL);
        }

        public Identifier getName() {
            return name;
        }

        public Type getType() {
            return type;
        }

        public String getSimpleTypeName() {
            return simpleTypeName;
        }

        public boolean isWritable() {
            return writable;
        }
    }
}

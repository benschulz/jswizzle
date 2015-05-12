package de.benshu.jswizzle.data;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import de.benshu.jswizzle.MixinComputer;
import de.benshu.jswizzle.internal.Template;
import de.benshu.jswizzle.model.AsJavaSourceOptions;
import de.benshu.jswizzle.model.Identifier;
import de.benshu.jswizzle.model.Import;
import de.benshu.jswizzle.model.MixinComponent;
import de.benshu.jswizzle.model.Type;
import org.kohsuke.MetaInfServices;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;

import static de.benshu.jswizzle.utils.SwizzleCollectors.set;

@MetaInfServices(DataComputer.class)
public class DataComputer extends MixinComputer {
    @Override
    public MixinComponent computeFor(ProcessingEnvironment processingEnvironment, Element e) {
        if (e instanceof TypeElement)
            return accessorsForAllFields((TypeElement) e);
        else
            return accessorForField((VariableElement) e);
    }

    private MixinComponent accessorsForAllFields(TypeElement mix) {
        final ImmutableSet<Property> properties = mix.getEnclosedElements().stream()
                .filter(e -> e.getKind() == ElementKind.FIELD)
                .filter(e -> e.getAnnotation(Data.Exclude.class) == null)
                .map(e -> new Property((VariableElement) e))
                .collect(set());

        return createMixinComponent(mix, properties);
    }

    private MixinComponent accessorForField(VariableElement field) {
        final TypeElement mix = (TypeElement) field.getEnclosingElement();
        final Property property = new Property(field);

        return createMixinComponent(mix, ImmutableSet.of(property));
    }

    private MixinComponent createMixinComponent(final TypeElement mix, final ImmutableSet<Property> properties) {
        return new MixinComponent() {
            @Override
            public TypeElement getMix() {
                return mix;
            }

            @Override
            public ImmutableSet<Import> getRequiredImports() {
                return properties.stream()
                        .flatMap(p -> p.getType().extractReferencedTypes().stream())
                        .map(Import::of)
                        .collect(set());
            }

            @Override
            public String getBody() {
                return Template.render("accessors.java.template", ImmutableMap.of(
                        "simpleMixName", Type.from(mix.asType()).asJavaSource(AsJavaSourceOptions.SIMPLE_NAMES),
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

        public Property(VariableElement field) {
            this.name = Identifier.from(field.getSimpleName());
            this.type = Type.from(field.asType());
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

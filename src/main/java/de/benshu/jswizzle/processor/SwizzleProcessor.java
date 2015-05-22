/*
 *  (c) tolina GmbH, 2014
 */
package de.benshu.jswizzle.processor;

import com.google.common.base.CaseFormat;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Iterables;
import de.benshu.jswizzle.MixinComputer;
import de.benshu.jswizzle.Swizzle;
import de.benshu.jswizzle.internal.SwizzleMixin;
import de.benshu.jswizzle.internal.Template;
import de.benshu.jswizzle.model.Identifier;
import de.benshu.jswizzle.model.Import;
import de.benshu.jswizzle.model.Mixin;
import de.benshu.jswizzle.model.MixinComponent;
import de.benshu.jswizzle.model.Type;
import de.benshu.jswizzle.model.TypeParameters;
import de.benshu.jswizzle.utils.SwizzleCollectors;
import org.kohsuke.MetaInfServices;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ErrorType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.annotation.Annotation;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static de.benshu.jswizzle.utils.SwizzleCollectors.list;
import static de.benshu.jswizzle.utils.SwizzleCollectors.set;

@MetaInfServices(Processor.class)
@SupportedAnnotationTypes("*")
public class SwizzleProcessor extends AbstractProcessor {
    @Override
    public final boolean process(final Set<? extends TypeElement> annotations, final RoundEnvironment roundEnvironment) {
        try {
            if (roundEnvironment.processingOver() || annotations.isEmpty())
                return false;

            final ImmutableSetMultimap<TypeElement, MixinComponent> mixins = annotations.stream()
                    .filter(a -> a.getAnnotation(Swizzle.class) != null)
                    .map(this::loadAnnotation)
                    .flatMap(a -> compute(roundEnvironment, a))
                    .collect(SwizzleCollectors.setMultimap(MixinComponent::getMix, c -> c));

            mixins.asMap().entrySet().stream().forEach(m -> {
                final ImmutableSet<TypeMirror> candidates = m.getKey().getInterfaces().stream()
                        .filter(this::isMixinCandidate)
                        .collect(SwizzleCollectors.set());

                if (candidates.size() == 1) {
                    final DeclaredType mixin = (DeclaredType) candidates.iterator().next();
                    final Identifier name = Identifier.from(simpleNameOf(mixin), CaseFormat.UPPER_CAMEL);

                    generateMixin(new Mixin(mixin, name, m.getKey(), ImmutableSet.copyOf(m.getValue())));
                }
            });

            return false;
        } catch (Exception e) {
            final StringWriter stringWriter = new StringWriter();
            final PrintWriter pw = new PrintWriter(stringWriter);
            pw.println(e.getMessage());
            e.printStackTrace(pw);

            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, stringWriter.toString());
            return false;
        }
    }

    private boolean isMixinCandidate(TypeMirror mixin) {
        final Element ifaceElement = processingEnv.getTypeUtils().asElement(mixin);

        return ErrorType.class.isInstance(mixin)
                || DeclaredType.class.isInstance(mixin) && ifaceElement.getAnnotation(SwizzleMixin.class) != null;

    }

    private void generateMixin(Mixin mixin) {
        try {
            final String mixinFqn = processingEnv.getElementUtils().getPackageOf(mixin.getMix()).getQualifiedName().toString()
                    + "." + mixin.getName().getPascalCased();

            try (Writer w = processingEnv.getFiler().createSourceFile(mixinFqn, mixin.getMix()).openWriter()) {
                w.write(renderMixin(mixin));
            }
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }

    private String renderMixin(Mixin mixin) {
        final ImmutableList<String> typeArguments = mixin.getReference().getTypeArguments().stream()
                .map(TypeVariable.class::cast)
                .map(v -> v.asElement().getSimpleName().toString())
                .collect(list());

        final String pkg = processingEnv.getElementUtils().getPackageOf(mixin.getMix()).getQualifiedName().toString();
        final Pattern redundantImport = Pattern.compile("\\A" + Pattern.quote(pkg) + "\\.[^\\.]+\\z");

        return Template.render("mixin.java.template", ImmutableMap.<String, Object>builder()
                        .put("package", pkg)
                        .put("imports", mixin.getComponents().stream()
                                .flatMap(c -> c.getRequiredImports().stream().map(Import::toString))
                                .filter(redundantImport.asPredicate().negate())
                                .distinct()
                                .sorted()
                                .collect(set()))
                        .put("name", mixin.getName())
                        .put("typeParameters", TypeParameters.of(mixin.getMix()).select(typeArguments).asJavaSource())
                        .put("components", mixin.getComponents().stream().map(MixinComponent::getBody).collect(set()))
                        .put("superMixins", findSupermixins(mixin))
                        .build()
        );
    }

    private ImmutableSet<String> findSupermixins(Mixin mixin) {
        return findSupermixinsInSupertypesOf(mixin.getMix()).collect(set());
    }

    private Stream<String> findSupermixinsInSupertypesOf(TypeElement typeElement) {
        return Stream.concat(Stream.of(typeElement.getSuperclass()), typeElement.getInterfaces().stream())
                .map(t -> (TypeElement) processingEnv.getTypeUtils().asElement(t))
                .filter(e -> e != null)
                .flatMap(this::findSupermixin);
    }

    private Stream<String> findSupermixin(TypeElement typeElement) {
        final Optional<String> supermixin = typeElement.getInterfaces().stream()
                .filter(this::isMixinCandidate)
                .map(Type::from)
                .map(Type::asJavaSource)
                .findAny();

        return supermixin.isPresent()
                ? Stream.of(supermixin.get())
                : findSupermixinsInSupertypesOf(typeElement);
    }

    private String simpleNameOf(TypeMirror mixin) {
        return processingEnv.getTypeUtils().asElement(mixin).getSimpleName().toString();
    }

    private Class<? extends Annotation> loadAnnotation(TypeElement annotation) {
        try {
            return Class.forName(annotation.getQualifiedName().toString()).asSubclass(Annotation.class);
        } catch (ClassNotFoundException e) {
            throw Throwables.propagate(e);
        }
    }

    private Stream<MixinComponent> compute(RoundEnvironment roundEnvironment, Class<? extends Annotation> annotation) {
        final Class<? extends MixinComputer> computerClass = annotation.getAnnotation(Swizzle.class).computer();
        final MixinComputer computer = Iterables.getOnlyElement(ServiceLoader.load(computerClass, computerClass.getClassLoader()));

        return roundEnvironment.getElementsAnnotatedWith(annotation).stream().map(e -> computer.computeFor(processingEnv, e));
    }

}
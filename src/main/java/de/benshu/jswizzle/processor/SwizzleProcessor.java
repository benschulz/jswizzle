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
import de.benshu.jswizzle.model.AsJavaSourceOptions;
import de.benshu.jswizzle.model.Identifier;
import de.benshu.jswizzle.model.Import;
import de.benshu.jswizzle.model.Mixin;
import de.benshu.jswizzle.model.MixinComponent;
import de.benshu.jswizzle.model.Reflection;
import de.benshu.jswizzle.model.Type;
import de.benshu.jswizzle.model.TypeDeclaration;
import de.benshu.jswizzle.model.TypeParameters;
import org.kohsuke.MetaInfServices;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
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
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static de.benshu.commons.core.streams.Collectors.list;
import static de.benshu.commons.core.streams.Collectors.set;
import static de.benshu.commons.core.streams.Collectors.setMultimap;

@MetaInfServices(Processor.class)
@SupportedAnnotationTypes("*")
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class SwizzleProcessor extends AbstractProcessor {
    @Override
    public final boolean process(final Set<? extends TypeElement> annotations, final RoundEnvironment roundEnvironment) {
        try {
            if (roundEnvironment.processingOver() || annotations.isEmpty())
                return false;

            Function<? super MixinComponent, ? extends MixinComponent> valueFunction = c -> c;
            final ImmutableSetMultimap<TypeDeclaration, MixinComponent> mixins = annotations.stream()
                    .filter(a -> a.getAnnotation(Swizzle.class) != null)
                    .map(this::loadAnnotation)
                    .flatMap(a -> compute(roundEnvironment, a))
                    .collect(setMultimap(MixinComponent::getMix, valueFunction));

            mixins.asMap().entrySet().stream().forEach(m -> {
                final ImmutableSet<TypeDeclaration> candidates = m.getKey().interfaces()
                        .filter(this::isMixinCandidate)
                        .collect(set());

                if (candidates.size() == 1) {
                    final TypeDeclaration mixin = candidates.iterator().next();
                    final Identifier name = Identifier.from(mixin.getName(), CaseFormat.UPPER_CAMEL);

                    generateMixin(new Mixin((DeclaredType) mixin.asType().getMirror(), name, m.getKey(), ImmutableSet.copyOf(m.getValue())));
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

    private boolean isMixinCandidate(TypeDeclaration iface) {
        final TypeMirror typeMirror = iface.asType().getMirror();
        final TypeMirror declarationTypeMirror = typeMirror instanceof DeclaredType
                ? ((DeclaredType) typeMirror).asElement().asType()
                : typeMirror;

        return ErrorType.class.isInstance(declarationTypeMirror) || iface.isAnnotatedWith(SwizzleMixin.class);
    }

    private void generateMixin(Mixin mixin) {
        try {
            final String mixinFqn = mixin.getMix().getPackage().getQualifiedName().toString()
                    + "." + mixin.getName().getPascalCased();

            try (Writer w = processingEnv.getFiler().createSourceFile(mixinFqn, mixin.getMix().getMirror()).openWriter()) {
                w.write(renderMixin(mixin));
            }
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }

    private String renderMixin(Mixin mixin) {
        final Reflection reflection = Reflection.reflectionFor(processingEnv);

        final ImmutableList<String> typeArguments = mixin.getReference().getTypeArguments().stream()
                .map(TypeVariable.class::cast)
                .map(v -> v.asElement().getSimpleName().toString())
                .collect(list());

        final TypeParameters typeParameters = mixin.getMix().getTypeParameters().select(typeArguments);
        final Stream<Import> typeBoundImports = typeParameters.referencedTypes().map(Import::of);

        final ImmutableSet<Type> supermixins = findSupermixins(mixin);

        final Stream<Import> allImports = Stream.of(
                typeBoundImports,
                supermixins.stream()
                        .flatMap(t -> ((DeclaredType) t.getMirror()).getTypeArguments().stream().map(reflection::of))
                        .flatMap(Type::referencedTypes)
                        .map(Import::of),
                mixin.getComponents().stream()
                        .flatMap(c -> c.getRequiredImports().stream())
        ).flatMap(s -> s);

        final String pakkage = mixin.getMix().getPackage().getQualifiedName().toString();
        final Pattern redundantImport = Pattern.compile("\\A" + Pattern.quote(pakkage) + "\\.[^\\.]+\\z");

        final ImmutableSet<String> imports = allImports
                .map(Import::toString)
                .filter(redundantImport.asPredicate().negate())
                .distinct()
                .sorted()
                .collect(set());

        return Template.render("mixin.java.template", ImmutableMap.<String, Object>builder()
                        .put("package", pakkage)
                        .put("imports", imports)
                        .put("name", mixin.getName())
                        .put("typeParameters", typeParameters.asJavaSource(AsJavaSourceOptions.SIMPLE_NAMES))
                        .put("components", mixin.getComponents().stream().map(MixinComponent::getBody).collect(set()))
                        .put("superMixins", supermixins.stream().map(s -> s.asJavaSource(AsJavaSourceOptions.SIMPLE_NAMES)).collect(set()))
                        .build()
        );
    }

    private ImmutableSet<Type> findSupermixins(Mixin mixin) {
        return findSupermixinsInSupertypesOf(mixin.getMix()).collect(set());
    }

    private Stream<Type> findSupermixinsInSupertypesOf(TypeDeclaration typeElement) {
        return typeElement.supertypes().flatMap(this::findSupermixin);
    }

    private Stream<Type> findSupermixin(TypeDeclaration supertype) {
        final Optional<Type> supermixin = supertype.interfaces()
                .filter(this::isMixinCandidate)
                .map(TypeDeclaration::asType)
                .findAny();

        return supermixin.map(Stream::of).orElseGet(() -> findSupermixinsInSupertypesOf(supertype));
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

        return roundEnvironment.getElementsAnnotatedWith(annotation).stream().map(e -> computer.computeFor(Reflection.reflectionFor(processingEnv), e));
    }

}
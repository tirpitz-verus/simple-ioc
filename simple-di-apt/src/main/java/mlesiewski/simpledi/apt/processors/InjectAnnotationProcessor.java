package mlesiewski.simpledi.apt.processors;

import mlesiewski.simpledi.apt.Logger;
import mlesiewski.simpledi.apt.SimpleDiAptException;
import mlesiewski.simpledi.core.annotations.Bean;
import mlesiewski.simpledi.core.annotations.Inject;
import mlesiewski.simpledi.apt.model.*;

import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;

import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PROTECTED;

/**
 * Processes @Inject annotations.
 */
public class InjectAnnotationProcessor {

    /** collector ref */
    private final GeneratedCodeCollector collector;

    public InjectAnnotationProcessor(GeneratedCodeCollector collector) {
        this.collector = collector;
    }

    /** @param roundEnv elements to processSupertypes */
    public void process(RoundEnvironment roundEnv) {
        roundEnv.getElementsAnnotatedWith(Inject.class).forEach(this::processElement);
    }

    /** @param generatedCodes all the generated elements */
    public void processSupertypes(Collection<GeneratedCode> generatedCodes) {
        generatedCodes.stream()
                .filter(GeneratedCode::hasSource)
                .forEach(this::addFieldDependencies);
    }

    private void addFieldDependencies(GeneratedCode generated) {
        BeanName beanName = generated.beanName();
        BeanEntity bean = collector.getBean(beanName);
        supertypesOf(generated.getSource()).forEach(source -> addFiledDependenciesToA(bean, source));
    }

    private void addFiledDependenciesToA(BeanEntity bean, TypeElement source) {
        source.getEnclosedElements().stream()
                .filter(this::hasAtInjectAnnotation)
                .filter(this::isAField)
                .forEach(field -> addFieldDependencyToABean(field, bean));
    }

    private Collection<TypeElement> supertypesOf(TypeElement childClass) {
        return supertypesOf(childClass, new ArrayList<>());
    }

    private Collection<TypeElement> supertypesOf(TypeElement childClass, Collection<TypeElement> accumulator) {
        DeclaredType superclass = (DeclaredType) childClass.getSuperclass();
        TypeElement superElement = (TypeElement) superclass.asElement();
        boolean notTheObject = !superElement.getQualifiedName().contentEquals(Object.class.getName());
        if (notTheObject) {
            accumulator.add(superElement);
            supertypesOf(superElement, accumulator);
        }
        return accumulator;
    }

    private boolean isAField(Element element) {
        return element.getKind().isField();
    }

    private boolean hasAtInjectAnnotation(Element element) {
        return element.getAnnotation(Inject.class) != null;
    }

    /** Top level processSupertypes method with common validation. Delegates to other methods. */
    private void processElement(Element element) {
        Logger.note("processing element '" + element.getSimpleName() + "'");
        Inject annotation = element.getAnnotation(Inject.class);
        Validators.validBeanName(annotation.name(), Inject.class, element);
        switch (element.getKind()) {
            case FIELD: processField(element); break;
            case PARAMETER: processParameter(element); break;
            case CONSTRUCTOR: processConstructor(element); break;
            default: throw new SimpleDiAptException(Inject.class.getName() + " can only be applied to fields, constructors and constructor parameters", element);
        }
    }

    /** Processes annotated constructors. */
    private void processConstructor(Element element) {
        Validators.validAccessibility(element, Inject.class, "fields, constructors and constructor parameter");
        BeanEntity beanEntity = getEnclosingBeanEntity(element);

        ExecutableElement constructor = (ExecutableElement) element;
        BeanConstructor beanConstructor = makeBeanConstructor(constructor);
        beanEntity.constructor(beanConstructor);
        if (beanEntity.constructorIsDefault()) {
            beanEntity.constructor(beanConstructor);
        } else if (!beanEntity.constructor().equals(beanConstructor)) {
            throw new SimpleDiAptException("bean constructor redefinition", element);
        }
    }

    /** Processes annotated parameters of a constructor. */
    private void processParameter(Element parameter) {
        Validators.isNotAPrimitive(parameter, Inject.class);
        ExecutableElement constructor = (ExecutableElement) parameter.getEnclosingElement();
        BeanEntity beanEntity = getEnclosingBeanEntity(constructor);

        BeanConstructor beanConstructor;
        if (beanEntity.constructorIsDefault()) {
            beanConstructor = makeBeanConstructor(constructor);
            beanEntity.constructor(beanConstructor);
        } else {
            beanConstructor = beanEntity.constructor();
            if (beanConstructor.hasDifferentParametersTo(constructor)) {
                throw new SimpleDiAptException("a bean can have only one injection constructor", parameter);
            }
        }

        Inject annotation = parameter.getAnnotation(Inject.class);
        DeclaredType injectedType = (DeclaredType) parameter.asType();
        BeanName paramBeanName = new BeanName(annotation, injectedType);
        String paramName = parameter.getSimpleName().toString();
        beanConstructor.set(paramName, paramBeanName);

        registerInjected(paramBeanName, injectedType, (TypeElement) constructor.getEnclosingElement());
    }

    /** Processes class fields. Ignores fields in an abstract class. */
    private void processField(Element field) {
        Validators.isNotAPrimitive(field, Inject.class);
        Validators.isNotStatic(field, Inject.class, "fields");

        if (inAbstractClass(field)) {
            return;
        }

        BeanEntity beanEntity = getEnclosingBeanEntity(field);

        addFieldDependencyToABean(field, beanEntity);
    }

    private void addFieldDependencyToABean(Element field, BeanEntity beanEntity) {
        Inject annotation = field.getAnnotation(Inject.class);
        DeclaredType declaredType = (DeclaredType) field.asType();
        BeanName beanName = new BeanName(annotation, declaredType);
        String fieldName = field.getSimpleName().toString();

        Set<Modifier> modifiers = field.getModifiers();
        boolean inaccessible = modifiers.contains(PRIVATE) || modifiers.contains(PROTECTED);
        if (inaccessible) {
            Optional<ExecutableElement> setter = getSetterMethodFor(field, fieldName);
            ExecutableElement method = setter.orElseThrow(() -> new SimpleDiAptException("private or protected field with no setter cannot be annotated with " + Inject.class.getSimpleName(), field));
            beanEntity.setter(method.getSimpleName().toString(), beanName);
        } else {
            beanEntity.field(fieldName, beanName);
        }
    }

    /** @return true if the enclosing class is abstract */
    private boolean inAbstractClass(Element field) {
        Element aBeanClass = field.getEnclosingElement();
        return aBeanClass.getModifiers().contains(ABSTRACT);
    }

    // helpers

    /** if not already registered than registers a new bean provider for the type of the element*/
    private void registerInjected(BeanName beanName, DeclaredType injectedType, TypeElement source) {
        if (!collector.hasBean(beanName)) {
            Validators.validBeanConstructor(injectedType);
            ClassEntity injectedClassEntity = ClassEntity.from(injectedType);
            BeanEntity injectedEntity = BeanEntity.builder().from(injectedClassEntity).withName(beanName.nameFromAnnotation()).withScope(beanName.scopeFromAnnotation()).build();
            BeanProviderEntity provider = new BeanProviderEntity(injectedEntity, source);
            collector.registrable(provider);
        }
    }

    /** @return element representing a setter method - or not */
    private Optional<ExecutableElement> getSetterMethodFor(Element field, String fieldName) {
        String setterName = "set" + fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1);
        Element aClassElement = field.getEnclosingElement();
        return aClassElement.getEnclosedElements().stream()
                .filter(e -> e.getKind().equals(ElementKind.METHOD))
                .map(e -> (ExecutableElement) e)
                .filter(e -> e.getModifiers().isEmpty() || e.getModifiers().contains(Modifier.PUBLIC))
                .filter(e -> !e.getModifiers().contains(Modifier.STATIC))
                .filter(e -> {
                    String methodName = e.getSimpleName().toString();
                    return methodName.equals(setterName) || methodName.equals(fieldName);
                })
                .filter(e -> e.getParameters().size() == 1 && e.getParameters().get(0).asType().equals(field.asType()))
                .findFirst();
    }

    /** @return new {@link BeanConstructor} from the constructor provided. */
    private BeanConstructor makeBeanConstructor(ExecutableElement constructor) {
        BeanConstructor beanConstructor = new BeanConstructor();
        constructor.getParameters().stream().forEachOrdered(parameter -> {
            Validators.isNotAPrimitive(parameter, Inject.class);
            String paramName = parameter.getSimpleName().toString();
            Inject annotation = parameter.getAnnotation(Inject.class);
            DeclaredType paramType = (DeclaredType) parameter.asType();
            BeanName paramBeanName;
            if (annotation != null) {
                paramBeanName = new BeanName(annotation, paramType);
            } else {
                paramBeanName = new BeanName(paramType);
            }
            beanConstructor.add(paramName, paramBeanName);
            registerInjected(paramBeanName, paramType, (TypeElement) constructor.getEnclosingElement());
        });
        boolean throwsExceptions = constructor.getThrownTypes().isEmpty();
        beanConstructor.throwsExceptions(throwsExceptions);
        return beanConstructor;
    }

    /** @return {@link BeanEntity} with a correct name */
    private BeanEntity getEnclosingBeanEntity(Element element) {
        TypeElement aBeanClass = (TypeElement) element.getEnclosingElement();
        DeclaredType declaredType = (DeclaredType) aBeanClass.asType();
        Validators.validBeanConstructor(declaredType);
        Bean annotation = aBeanClass.getAnnotation(Bean.class);
        BeanName beanName;
        if (annotation != null) {
            beanName = new BeanName(annotation, declaredType);
        } else {
            beanName = new BeanName(declaredType);
        }
        return getBeanEntity(aBeanClass, beanName);
    }

    /** @return {@link BeanEntity} form the {@link #collector} - if its not there than a new {@link BeanEntity} will be created and pun into {@link #collector} */
    private BeanEntity getBeanEntity(TypeElement beanClass, BeanName beanClassName) {
        if (collector.hasBean(beanClassName)) {
            return collector.getBean(beanClassName);
        } else {
            ClassEntity beanClassEntity = ClassEntity.from(beanClass.asType());
            BeanEntity beanEntity = BeanEntity.builder().from(beanClassEntity).withName(beanClassName.nameFromAnnotation()).withScope(beanClassName.scopeFromAnnotation()).build();
            BeanProviderEntity provider = new BeanProviderEntity(beanEntity, beanClass);
            collector.registrable(provider);
            return beanEntity;
        }
    }
}

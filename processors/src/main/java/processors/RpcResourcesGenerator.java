package processors;

import com.google.auto.service.AutoService;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.MirroredTypeException;
import javax.tools.Diagnostic;

import clearnet.annotations.RPCMethod;
import clearnet.annotations.RPCMethodScope;
import clearnet.annotations.ResultType;

@AutoService(Processor.class)
@SupportedSourceVersion(SourceVersion.RELEASE_7)
public class RpcResourcesGenerator extends BaseProcessor {

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return new HashSet<>(Arrays.asList(
                RPCMethodScope.class.getCanonicalName(),
                RPCMethod.class.getCanonicalName()
        ));
    }

    @Override
    public boolean process(Set<? extends TypeElement> set, RoundEnvironment roundEnvironment) {
        Map<String, Set<NameTypePair>> tree = new HashMap<>();
        for (Element source : roundEnvironment.getElementsAnnotatedWith(RPCMethodScope.class)) {
            if (source instanceof TypeElement) {
                RPCMethodScope scopeAnnotation = source.getAnnotation(RPCMethodScope.class);
                for (Element element : source.getEnclosedElements()) {
                    if (!(element instanceof ExecutableElement)) continue;
                    addMethodIfHas(element, tree, scopeAnnotation.value());
                }
            } else {
                addMethodIfHas(source, tree, null);
            }
        }

        for (Element source : roundEnvironment.getElementsAnnotatedWith(RPCMethod.class)) {
            addMethodIfHas(source, tree, null);
        }

        writeResourcesFile(tree);
        writeSubscriberClass(tree);
        return true;
    }

    private void addMethodIfHas(Element element, Map<String, Set<NameTypePair>> tree, String scope) {
        RPCMethod methodAnnotation = element.getAnnotation(RPCMethod.class);
        RPCMethodScope scopeAnnotation = element.getAnnotation(RPCMethodScope.class);

        if (methodAnnotation == null && scopeAnnotation == null && scope == null)
            return;   // todo error

        if (methodAnnotation != null && scopeAnnotation != null) {
            error(element, "The element can have only one of these annotations: " + RPCMethod.class.getSimpleName() + " or " + RPCMethodScope.class.getSimpleName());
        }

        String method;


        if (scopeAnnotation != null) {
            scope = scopeAnnotation.value();
            method = element.getSimpleName().toString();
        } else if (methodAnnotation != null) {
            String[] parts = methodAnnotation.value().split("\\.");
            if (parts.length != 2) {
                error(element, "Invalid method \"" + methodAnnotation.value() + "\". It must looks: scope.method.");
                return;
            }
            scope = parts[0];
            method = parts[1];
        } else {
            method = element.getSimpleName().toString();
        }

        if (!scope.isEmpty() && !scope.matches("[a-zA-Z0-9]+")) {
            error(element, "Invalid scope");
            return;
        }

        if (!method.matches("[a-zA-Z0-9]+")) {
            error(element, "Invalid method");
            return;
        }

        Set<NameTypePair> methods = tree.get(scope);
        if (methods == null) {
            methods = new HashSet<>();
            tree.put(scope, methods);
        }

        methods.add(new NameTypePair(method, resolveCallbackType((ExecutableElement) element)));
    }

    private TypeName resolveCallbackType(ExecutableElement element) {
        ResultType annotation = element.getAnnotation(ResultType.class);
        if (annotation != null) {

            // solution from https://stackoverflow.com/questions/7687829/java-6-annotation-processing-getting-a-class-from-an-annotation
            try {
                return TypeName.get(annotation.value());
            } catch (MirroredTypeException mte) {
                return TypeName.get(mte.getTypeMirror());
            }
        }

        for (VariableElement parameter : element.getParameters()) {
            if (parameter.asType().toString().startsWith("clearnet.interfaces.RequestCallback")) {
                DeclaredType dclt = (DeclaredType) parameter.asType();
                return TypeName.get(dclt.getTypeArguments().get(0));
            }
        }
        warning(element, "Cannot define result type");
        return TypeName.get(Object.class);
    }

    private TypeSpec buildScopeClass(String scope, Collection<NameTypePair> pairs) {
        TypeSpec.Builder builder = TypeSpec.classBuilder(scope.isEmpty() ? "NoScope" : scope)
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL);

        Set<String> methods = new HashSet<>();
        for (NameTypePair pair : pairs) methods.add(pair.name);
        for (String method : methods) {
            builder.addField(
                    FieldSpec.builder(String.class, method, Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                            .initializer("$S", scope.isEmpty() ? method : scope + "." + method)
                            .build()
            );
        }

        return builder.build();
    }

    private void writeResourcesFile(Map<String, Set<NameTypePair>> tree) {
        if (tree.isEmpty()) return;

        TypeSpec.Builder builder = TypeSpec.classBuilder("NR")
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL);

        for (Map.Entry<String, Set<NameTypePair>> entry : tree.entrySet()) {
            builder.addType(buildScopeClass(entry.getKey(), entry.getValue()));
        }

        try {
            // todo add custom compiler option with package name
            JavaFile javaFile = JavaFile.builder(processingEnv.getOptions().get("android.databinding.modulePackage"), builder.build())
                    .build();
            javaFile.writeTo(processingEnv.getFiler());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void writeSubscriberClass(Map<String, Set<NameTypePair>> tree) {
        if (tree.isEmpty()) return;

        TypeSpec.Builder builder = TypeSpec.classBuilder("ClearNet")
                .addModifiers(Modifier.PUBLIC);

        TypeName callbackStorageTypeName = ClassName.bestGuess("clearnet.interfaces.ICallbackStorage");

        final String callbachStorageFieldName = "callbackStorage";
        builder.addMethod(MethodSpec.constructorBuilder().addParameter(
                ParameterSpec.builder(ClassName.bestGuess("clearnet.interfaces.ICallbackStorage"), callbachStorageFieldName).build()
        )
                .addModifiers(Modifier.PUBLIC)
                .addStatement("this.$L = $L", callbachStorageFieldName, callbachStorageFieldName)
                .build());

        builder.addField(callbackStorageTypeName, "callbackStorage", Modifier.PRIVATE, Modifier.FINAL);

        ArrayList<String> sortedScopes = new ArrayList<>(tree.keySet());
        Collections.sort(sortedScopes);
        ArrayList<NameTypePair> sortedPairs;
        for (String scope : sortedScopes) {
            sortedPairs = new ArrayList<>(tree.get(scope));
            Collections.sort(sortedPairs);
            addClassForScope(builder, scope, sortedPairs);
        }

        try {
            String packageName = processingEnv.getOptions().get("packageName");
            if(packageName == null || packageName.isEmpty()){
                throw new IllegalArgumentException("Argument 'packageName' not set in your gradle file");
            }

            JavaFile.builder(packageName, builder.build())
                    .build()
                    .writeTo(processingEnv.getFiler());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void addClassForScope(TypeSpec.Builder typeBuilder, String scope, Collection<NameTypePair> pairs) {
        final String name = scope.isEmpty() ? "NoScope" : scope;
        TypeSpec.Builder builder = TypeSpec.classBuilder(scope.isEmpty() ? "NoScope" : capitalize(scope))
                .addModifiers(Modifier.PUBLIC);


        final Set<String> names = new HashSet<>();
        final Set<String> excepted = new HashSet<>();
        for (NameTypePair pair : pairs) {
            if (!names.add(pair.name)) {
                warning(null, "Conflicted result type for method " + scope + "." + pair.name + ". Method missed");
                excepted.add(pair.name);
            }
        }

        for (NameTypePair pair : pairs) {
            if(excepted.contains(pair.name)) continue;
            TypeName subscriberTypeName = ParameterizedTypeName.get(ClassName.bestGuess("clearnet.utils.Subscriber"), pair.callbackType);
            builder.addMethod(
                    MethodSpec.methodBuilder(pair.name)
                            .returns(subscriberTypeName)
                            .addModifiers(Modifier.PUBLIC)
                            .addStatement("return new $T(callbackStorage, $S)", subscriberTypeName, scope.isEmpty() ? pair.name : scope + "." + pair.name)
                            .build()
            );
        }

        TypeSpec result = builder.build();

        typeBuilder.addType(result);
        typeBuilder.addField(
                FieldSpec.builder(ClassName.get("", capitalize(name)), name, Modifier.PUBLIC, Modifier.FINAL)
                        .initializer("new $L()", capitalize(name))
                        .build()
        );
    }

    private String capitalize(final String line) {
        return Character.toUpperCase(line.charAt(0)) + line.substring(1);
    }

    private static class NameTypePair implements Comparable<NameTypePair> {
        final String name;
        final TypeName callbackType;

        private NameTypePair(String name, TypeName callbackType) {
            this.name = name;
            this.callbackType = callbackType;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof NameTypePair)) return false;

            NameTypePair that = (NameTypePair) o;

            if (name != null ? !name.equals(that.name) : that.name != null) return false;
            return callbackType != null ? callbackType.equals(that.callbackType) : that.callbackType == null;

        }

        @Override
        public int hashCode() {
            int result = name != null ? name.hashCode() : 0;
            result = 31 * result + (callbackType != null ? callbackType.hashCode() : 0);
            return result;
        }

        @Override
        public int compareTo(NameTypePair nameTypePair) {
            return name.compareTo(nameTypePair.name);
        }
    }
}

package processors;

import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.TypeSpec;

import java.io.IOException;

import javax.annotation.processing.AbstractProcessor;
import javax.lang.model.element.Element;
import javax.tools.Diagnostic;

public abstract class BaseProcessor extends AbstractProcessor {
    void error(Element e, String msg) {
        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, msg, e);
    }


    void warning(Element e, String msg) {
        processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING, msg, e);
    }

    protected void writeFile(String pack, TypeSpec typeSpec) {
        try {
            JavaFile javaFile = JavaFile.builder(pack, typeSpec).build();
            javaFile.writeTo(processingEnv.getFiler());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}

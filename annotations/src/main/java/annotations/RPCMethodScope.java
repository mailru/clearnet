package annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The annotation means rpc method name before the dot.
 * Name after the dot will be taken from the name of the java interface method.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface RPCMethodScope {
    String value();

    String NO_SCOPE = "";
}

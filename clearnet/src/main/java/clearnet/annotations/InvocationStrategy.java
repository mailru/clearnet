package clearnet.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface InvocationStrategy {
    clearnet.InvocationStrategy[] value();

    long cacheExpiresAfter() default NEVER;

    long MINUTE = 1000 * 60;
    long HOUR = MINUTE * 60;
    long DAY = HOUR * 24;
    long WEEK = DAY * 7;
    long NEVER = Long.MAX_VALUE;
}

package clearnet;

import org.jetbrains.annotations.NotNull;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.Executor;

public class Wrapper<I> {
    private final Object lock;
    private final Executor executor;
    private I source;

    @NotNull
    @SuppressWarnings("unchecked")
    public static <R> R stub(Class<R> clazz) {
        return (R) Proxy.newProxyInstance(
                clazz.getClassLoader(),
                new Class[]{clazz},
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object o, Method method, Object[] objects) throws Throwable {
                        return null;
                    }
                }
        );
    }

    public Wrapper(@NotNull Object lock, @NotNull Executor executor, @NotNull I source) {
        this.lock = lock;
        this.executor = executor;
        this.source = source;
    }

    @NotNull
    @SuppressWarnings("unchecked")
    public I create(Class<? super I> interfaceType) {
        return (I) Proxy.newProxyInstance(
                this.getClass().getClassLoader(),
                new Class[]{interfaceType},
                handler
        );
    }

    public void stop() {
        source = null;
    }

    private InvocationHandler handler = new InvocationHandler() {
        @Override
        public Object invoke(Object proxy, final Method method, final Object[] args) throws Throwable {
            synchronized (lock) {
                if (source == null) return null;
            }

            executor.execute(new Runnable() {
                @Override
                public void run() {
                    synchronized (lock) {
                        if (source == null) return;
                        try {
                            method.invoke(source, args);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
            });
            return null;
        }
    };
}

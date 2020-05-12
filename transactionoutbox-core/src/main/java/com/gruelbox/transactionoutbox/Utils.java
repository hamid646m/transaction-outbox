package com.gruelbox.transactionoutbox;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import javax.validation.Validation;
import javax.validation.ValidationException;
import javax.validation.Validator;
import lombok.extern.slf4j.Slf4j;
import net.sf.cglib.proxy.Callback;
import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.MethodInterceptor;
import org.objenesis.Objenesis;
import org.objenesis.ObjenesisStd;
import org.objenesis.instantiator.ObjectInstantiator;
import org.slf4j.Logger;
import org.slf4j.event.Level;

@Slf4j
class Utils {

  private static final Objenesis objenesis = new ObjenesisStd();

  @SuppressWarnings({"SameParameterValue", "WeakerAccess", "UnusedReturnValue"})
  static boolean safelyRun(String gerund, ThrowingRunnable runnable) {
    try {
      runnable.run();
      return true;
    } catch (Exception e) {
      log.error("Error when {}", gerund, e);
      return false;
    }
  }

  @SuppressWarnings("unused")
  static void safelyClose(AutoCloseable... closeables) {
    safelyClose(Arrays.asList(closeables));
  }

  static void safelyClose(Iterable<? extends AutoCloseable> closeables) {
    closeables.forEach(
        d -> {
          if (d == null) return;
          safelyRun("closing resource", d::close);
        });
  }

  static void uncheck(ThrowingRunnable runnable) {
    try {
      runnable.run();
    } catch (Exception e) {
      uncheckAndThrow(e);
    }
  }

  static <T> T uncheckedly(Callable<T> runnable) {
    try {
      return runnable.call();
    } catch (Exception e) {
      return uncheckAndThrow(e);
    }
  }

  static <T> T uncheckAndThrow(Throwable e) {
    if (e instanceof RuntimeException) {
      throw (RuntimeException) e;
    }
    if (e instanceof Error) {
      throw (Error) e;
    }
    throw new UncheckedException(e);
  }

  @SuppressWarnings({"unchecked", "cast"})
  static <T> T createProxy(Class<T> clazz, BiFunction<Method, Object[], T> processor) {
    if (clazz.isInterface()) {
      // Fastest - we can just proxy an interface directly
      return (T)
          Proxy.newProxyInstance(
              clazz.getClassLoader(),
              new Class[]{clazz},
              (proxy, method, args) -> processor.apply(method, args));
    } else if (hasDefaultConstructor(clazz)) {
      // CGLIB on its own can create an instance
      return (T)
          Enhancer.create(
              clazz,
              (MethodInterceptor)
                  (o, method, objects, methodProxy) -> processor.apply(method, objects));
    } else {
      // Slowest - we need to use Objenesis and CGLIB together
      MethodInterceptor methodInterceptor = (o, method, objects, methodProxy) -> processor.apply(method, objects);
      Enhancer enhancer = new Enhancer();
      enhancer.setSuperclass(clazz);
      enhancer.setCallbackTypes(new Class<?>[]{MethodInterceptor.class});
      enhancer.setInterceptDuringConstruction(true);
      Class<T> proxyClass = enhancer.createClass();
      // TODO could cache the ObjectInstantiators - see ObjenesisSupport in spring-aop
      ObjectInstantiator<T> oi = objenesis.getInstantiatorOf(proxyClass);
      T proxy = oi.newInstance();
      ((net.sf.cglib.proxy.Factory) proxy).setCallbacks(new Callback[]{methodInterceptor});
      enhancer.setInterceptDuringConstruction(false);
      return proxy;
    }
  }

  static <T> T firstNonNull(T one, Supplier < T > two) {
    if (one == null) return two.get();
    return one;
  }

  static void logAtLevel (Logger logger, Level level, String message, Object...args){
    switch (level) {
      case ERROR:
        logger.error(message, args);
        break;
      case WARN:
        logger.warn(message, args);
        break;
      case INFO:
        logger.info(message, args);
        break;
      case DEBUG:
        logger.debug(message, args);
        break;
      case TRACE:
        logger.trace(message, args);
        break;
      default:
        logger.warn(message, args);
        break;
    }
  }

  private static boolean hasDefaultConstructor(Class<?> clazz) {
    try {
      clazz.getConstructor();
      return true;
    } catch (NoSuchMethodException e) {
      return false;
    }
  }
}
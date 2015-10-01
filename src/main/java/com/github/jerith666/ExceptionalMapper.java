package com.github.jerith666;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

public final class ExceptionalMapper {

    public static void main(String[] args) {
        CompletableFuture<List<Class<?>>> classes =
                Stream.of("java.lang.String", "java.lang.Integer", "java.lang.Double")
                      .map(MonadUtils.applyOrDie(Class::forName))
                      .map(cfc -> cfc.thenApply(Class::getSuperclass))
                      .collect(MonadUtils.cfCollector(ArrayList::new,
                                                      List::add,
                                                      (List<Class<?>> l1, List<Class<?>> l2) -> { l1.addAll(l2); return l1; },
                                                      x -> x));
        classes.thenAccept(System.out::println)
               .exceptionally(t -> { System.out.println("unable to get class: " + t); return null; });
    }
}

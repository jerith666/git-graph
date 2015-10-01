package com.github.jerith666;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class ExceptionalMapper {

    public static void main(String[] args) {
        Collector<Class<?>, ?, List<Class<?>>> toList = Collectors.toList();
        CompletableFuture<List<Class<?>>> classes =
                Stream.of("java.lang.String", "java.lang.Integer", "java.lang.Double")
                      .map(MonadUtils.applyOrDie(Class::forName))
                      .map(cfc -> cfc.thenApply(Class::getSuperclass))
                      .collect(MonadUtils.cfCollector(toList.supplier(),
                                                      toList.accumulator(),
                                                      toList.combiner(),
                                                      toList.finisher()));
        classes.thenAccept(System.out::println)
               .exceptionally(t -> { System.out.println("unable to get class: " + t); return null; });
    }
}

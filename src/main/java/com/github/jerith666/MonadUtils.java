package com.github.jerith666;

import static java.util.concurrent.CompletableFuture.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.BinaryOperator;
import java.util.stream.Stream;

public final class MonadUtils {
    @FunctionalInterface
    public static interface ExceptionalFunction<T,R,E extends Throwable>{
        R apply(T t) throws E;
    }

    public static <T,R,E extends Throwable> CompletableFuture<R> applyOrDie(T t, ExceptionalFunction<T,R,E> ef){
        CompletableFuture<R> cf = new CompletableFuture<R>();
        try{
            cf.complete(ef.apply(t));
        }
        catch(Throwable e){
            cf.completeExceptionally(e);
        }
        return cf;
    }

    public static <K,V> BinaryOperator<Map<K,V>> mapCollapser(){
        return (m1, m2) -> {
            Map<K,V> m = new LinkedHashMap<K,V>(m1);
            m.putAll(m2);
            return m;
         };
    }

    public static <T,R,E extends Throwable> CompletableFuture<R> reduceStages(Stream<T> source,
                                                                              R identity,
                                                                              ExceptionalFunction<T, R, E> stageCreator,
                                                                              BinaryOperator<R> stageAccumulator){
        return source.map(s -> applyOrDie(s, stageCreator))
                     .reduce(completedFuture(identity),
                             (s1, s2) -> s1.thenCombine(s2, stageAccumulator));
    }
}

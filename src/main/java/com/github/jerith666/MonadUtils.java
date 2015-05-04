package com.github.jerith666;

import static java.util.concurrent.CompletableFuture.*;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Stream;

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.SetMultimap;

public final class MonadUtils {
    @FunctionalInterface
    public static interface ExceptionalFunction<T,R,E extends Throwable>{
        R apply(T t) throws E;
    }

    public static <T,A,R> Collector<T, A, R> toSingleCompletableFuture(){
        return new Collector<T, A, R>(){
            
        };
    }

    public static <T,R,E extends Throwable> Function<T,CompletableFuture<R>> applyOrDie(ExceptionalFunction<T,R,E> ef){
        return t -> applyOrDie(t, ef);
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

    public static <T,C extends Collection<T>> BiFunction<? super C,T,C> collectionAdder(Supplier<C> s){
        return (c, t) -> {C c3 = s.get(); c3.addAll(c); c3.add(t); return c3;};
    }

    public static <T,C extends Collection<T>> BiFunction<? super C,? super C,C> collectionCombiner(Supplier<C> s){
        return (c1, c2) -> {C c3 = s.get(); c3.addAll(c1); c3.addAll(c2); return c3;};
    }

    public static <K,V> BinaryOperator<SetMultimap<K,V>> multimapCollapser(){
        return (m1, m2) -> {
            SetMultimap<K, V> m = LinkedHashMultimap.create(m1);
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

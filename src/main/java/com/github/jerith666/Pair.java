package com.github.jerith666;

import static com.google.common.base.Objects.*;

import com.google.common.base.Objects;

public final class Pair<T,U> {
    public final T _t;
    public final U _u;

    public Pair(T t, U u){
        _t = t;
        _u = u;
    }

    @Override
    public boolean equals(Object obj) {
        if(!(obj instanceof Pair)) return false;
        Pair<?,?> p = (Pair<?, ?>) obj;
        return equal(_t, p._t) && equal(_u, p._u);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(_t, _u);
    }

    @Override
    public String toString() {
        return "[Pair: " + _t + ", " + _u + "]";
    }
}

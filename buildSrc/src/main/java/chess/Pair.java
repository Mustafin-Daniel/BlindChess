package chess;

/** A helper class that makes it possible to return two values from a function. */
public final class Pair<T1, T2> {
    public final T1 first;
    public final T2 second;

    public Pair(T1 first, T2 second) {
        this.first = first;
        this.second = second;
    }
}

package org.jabref.model.oostyle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class OOListUtil {

    public static <T, U> List<U> map(List<T> list, Function<T, U> fun) {
        return list.stream().map(e -> fun.apply(e)).collect(Collectors.toList());
    }

    public static <A, B, R> List<R> zip(List<A> a,
                                        List<B> b,
                                        BiFunction<? super A, ? super B, R> fun) {
        if (a.size() != b.size()) {
            throw new RuntimeException("a.size != b.size");
        }
        List<R> result = new ArrayList<>(a.size());
        for (int i = 0; i < a.size(); i++) {
            result.add(fun.apply(a.get(i), b.get(i)));
        }
        return result;
    }

    /** Integers 0..(n-1) */
    public static List<Integer> makeIndices(int n) {
        return Stream.iterate(0, i -> i + 1).limit(n).collect(Collectors.toList());
    }

    /** Return indices so that list.get(indices.get(i)) is sorted. */
    public static <T extends U, U> List<Integer> order(List<T> list, Comparator<U> comparator) {
        List<Integer> ii = makeIndices(list.size());
        Collections.sort(ii, new Comparator<Integer>() {
                @Override public int compare(final Integer o1, final Integer o2) {
                    return comparator.compare((U) list.get(o1), (U) list.get(o2));
                }
            });
        return ii;
    }
}

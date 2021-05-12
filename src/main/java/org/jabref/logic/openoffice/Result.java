package org.jabref.logic.openoffice;

import java.util.Optional;
import java.util.function.Consumer;

/*
 * error cannot be null
 * result cannot be null
 * If Void is not allowed for R, use VoidResult instead.
 */
public class Result<R, E> {
    private final Optional<R> result;
    private final Optional<E> error;

    private Result(Optional<R> result, Optional<E> error) {
        this.result = result;
        this.error = error;
    }

    public static <R, E> Result<R, E> OK(R result) {
        return new Result(Optional.of(result), Optional.empty());
    }

    public static <R, E> Result<R, E> Error(E error) {
        return new Result(Optional.empty(), Optional.of(error));
    }

    public boolean isPresent() {
        return result.isPresent();
    }

    public boolean isEmpty() {
        return !isPresent();
    }

    public boolean isError() {
        return error.isPresent();
    }

    public boolean isOK() {
        return !isError();
    }

    public R get() {
        if (isError()) {
            throw new RuntimeException("Cannot get from error");
        }
        return result.orElse(null);
    }

    /*
     * Throw away the error part.
     */
    public Optional<R> getOptional() {
        return result;
    }

    public E getError() {
        return error.get();
    }

    public Result<R, E> ifError(Consumer<E> fun) {
        if (isError()) {
            fun.accept(getError());
        }
        return this;
    }

    public Result<R, E> ifPresent(Consumer<R> fun) {
        if (isPresent()) {
            fun.accept(get());
        }
        return this;
    }

    public Result<R, E> setIfPresent(Settable<R> out) {
        if (isPresent()) {
            out.set(get());
        }
        return this;
    }

}


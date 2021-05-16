package org.jabref.logic.openoffice;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

/*
 * error cannot be null
 * result cannot be null
 *
 * Void is not allowed for R, use VoidResult instead.
 *
 * Out of `isPresent()` and `isError()` exactly one is true.
 */
public class Result<R, E> {
    private final Optional<R> result;
    private final Optional<E> error;

    /**
     * Exactly one of the arguments should be Optional.empty()
     *
     * @param result
     * @param error
     */
    private Result(Optional<R> result, Optional<E> error) {
        this.result = result;
        this.error = error;
    }

    /**
     * @param result Null is not allowed.
     */
    public static <R, E> Result<R, E> OK(R result) {
        return new Result(Optional.of(result), Optional.empty());
    }

    /**
     * @param error Null is not allowed.
     */
    public static <R, E> Result<R, E> Error(E error) {
        return new Result(Optional.empty(), Optional.of(error));
    }

    /*
     * Test state
     */

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

    /*
     * getters
     */

    public R get() {
        if (isError()) {
            throw new RuntimeException("Cannot get from error");
        }
        return result.get();
    }

    public E getError() {
        return error.get();
    }

    /*
     * Conditionals
     */

    public Result<R, E> ifPresent(Consumer<R> fun) {
        if (isPresent()) {
            fun.accept(get());
        }
        return this;
    }

    public Result<R, E> ifError(Consumer<E> fun) {
        if (isError()) {
            fun.accept(getError());
        }
        return this;
    }

    public <F> Result<R, F> mapError(Function<E, F> fun) {
        if (isError()) {
            return Error(fun.apply(getError()));
        } else {
            return OK(get());
        }
    }

    public <F> Result<R, F> map(Function<R, S> fun) {
        if (isError()) {
            return Error(getError());
        } else {
            return OK(fun.apply(get()));
        }
    }

    /** Throw away the error part. */
    public Optional<R> getOptional() {
        return result;
    }

    /** Throw away the result part. */
    public VoidResult<E> asVoidResult() {
        if (isError()) {
            return VoidResult.Error(getError());
        } else {
            return VoidResult.OK();
        }
    }

}


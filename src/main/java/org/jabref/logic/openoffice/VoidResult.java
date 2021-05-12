package org.jabref.logic.openoffice;

import java.util.Optional;
import java.util.function.Consumer;

/*
 * error cannot be null
 */
public class VoidResult<E> {
    private final Optional<E> error;

    private VoidResult(Optional<E> error) {
        this.error = error;
    }

    public static <E> VoidResult<E> OK() {
        return new VoidResult(Optional.empty());
    }

    public static <E> VoidResult<E> Error(E error) {
        return new VoidResult(Optional.of(error));
    }

    public boolean isError() {
        return error.isPresent();
    }

    public boolean isOK() {
        return !isError();
    }

    public E getError() {
        return error.get();
    }

    public VoidResult<E> ifError(Consumer<E> fun) {
        if (isError()) {
            fun.accept(getError());
        }
        return this;
    }
}


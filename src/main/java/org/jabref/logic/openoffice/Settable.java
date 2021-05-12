package org.jabref.logic.openoffice;

import java.util.Optional;

public class Settable<T> {
    private Optional<T> value;
    public Settable() {
        value = Optional.empty();
    }

    public boolean isEmpty() {
        return value.isEmpty();
    }

    public boolean isPresent() {
        return value.isPresent();
    }

    public T get() {
        return value.get();
    }

    public void set(T value) {
        this.value = Optional.of(value);
    }
}

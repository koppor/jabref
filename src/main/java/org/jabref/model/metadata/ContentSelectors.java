package org.jabref.model.metadata;

import org.jabref.model.entry.field.Field;

import java.util.*;

public class ContentSelectors {

    private final List<ContentSelector> contentSelectors;

    public ContentSelectors() {
        contentSelectors = new ArrayList<>();
    }

    public void addContentSelector(ContentSelector contentSelector) {
        Objects.requireNonNull(contentSelector);

        this.contentSelectors.add(contentSelector);
    }

    public static ContentSelector parse(Field key, String values) {
        Objects.requireNonNull(key);
        Objects.requireNonNull(values);

        List<String> valueList = Arrays.asList(values.split(";"));

        return new ContentSelector(key, valueList);
    }

    public List<String> getSelectorValuesForField(Field field) {
        for (ContentSelector selector : contentSelectors) {
            if (selector.getField().equals(field)) {
                return selector.getValues();
            }
        }

        return Collections.emptyList();
    }

    public List<ContentSelector> getContentSelectors() {
        return Collections.unmodifiableList(contentSelectors);
    }

    public void removeSelector(Field field) {
        ContentSelector toRemove = null;

        for (ContentSelector selector : contentSelectors) {
            if (selector.getField().equals(field)) {
                toRemove = selector;
                break;
            }
        }

        if (toRemove != null) {
            contentSelectors.remove(toRemove);
        }
    }

    public List<Field> getFieldsWithSelectors() {
        List<Field> result = new ArrayList<>(contentSelectors.size());

        for (ContentSelector selector : contentSelectors) {
            result.add(selector.getField());
        }

        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ContentSelectors that = (ContentSelectors) o;
        return Objects.equals(contentSelectors, that.contentSelectors);
    }

    @Override
    public int hashCode() {
        return Objects.hash(contentSelectors);
    }

    @Override
    public String toString() {
        return "ContentSelectors{" + "contentSelectors="
                + contentSelectors + ", fieldsWithSelectors="
                + getFieldsWithSelectors() + '}';
    }
}

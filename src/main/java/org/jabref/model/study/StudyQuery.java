package org.jabref.model.study;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonSetter;

@JsonPropertyOrder({"query", "refinements"})
public class StudyQuery {
    private String query;
    private Map<String, String> queryRefinements = new HashMap<>();

    public StudyQuery(String query) {
        this.query = query;
    }

    /**
     * Used for Jackson deserialization
     */
    public StudyQuery() {

    }

    // Serialization as query
    @JsonGetter("query")
    public String getBaseQuery() {
        return query;
    }

    @JsonGetter("refinements")
    public Map<String, String> getQueryRefinements() {
        return queryRefinements;
    }

    public String getLibrarySpecificQueryOrDefault(String libraryName) {
        return queryRefinements.getOrDefault(libraryName, query);
    }

    // Deserialization from query
    @JsonSetter("query")
    public void setBaseQuery(String query) {
        this.query = query;
    }

    @JsonSetter("refinements")
    public void setQueryRefinements(Map<String, String> queryRefinements) {
        this.queryRefinements = queryRefinements;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        StudyQuery that = (StudyQuery) o;

        if (!queryRefinements.equals(that.getQueryRefinements())) {
            return false;
        }

        return getBaseQuery() != null ? getBaseQuery().equals(that.getBaseQuery()) : that.getBaseQuery() == null;
    }

    @Override
    public int hashCode() {
        return getBaseQuery() != null ? getBaseQuery().hashCode() : 0;
    }

    @Override
    public String toString() {
        return "QueryEntry{" +
                "query='" + query + '\'' + ", " +
                queryRefinements +
                '}';
    }
}

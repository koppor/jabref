package org.jabref.logic.shared.restserver.rest.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class BibEntryDTO {
    public String entryType;
    public String citationKey;

    public String address;
    public String author;
    public String booktitle;
    public String chapter;
    public String edition;
    public String editor;
    public String howpublished;
    public String institution;
    public String journal;
    public String month;
    public String note;
    public String number;
    public String organization;
    public String pages;
    public String publisher;
    public String school;
    public String series;
    public String title;
    public String volume;
    public String year;
    public String date;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if ((o == null) || (getClass() != o.getClass())) {
            return false;
        }

        BibEntryDTO that = (BibEntryDTO) o;

        if (!entryType.equals(that.entryType)) {
            return false;
        }
        if (!citationKey.equals(that.citationKey)) {
            return false;
        }
        if (address != null ? !address.equals(that.address) : that.address != null) {
            return false;
        }
        if (author != null ? !author.equals(that.author) : that.author != null) {
            return false;
        }
        if (booktitle != null ? !booktitle.equals(that.booktitle) : that.booktitle != null) {
            return false;
        }
        if (chapter != null ? !chapter.equals(that.chapter) : that.chapter != null) {
            return false;
        }
        if (edition != null ? !edition.equals(that.edition) : that.edition != null) {
            return false;
        }
        if (editor != null ? !editor.equals(that.editor) : that.editor != null) {
            return false;
        }
        if (howpublished != null ? !howpublished.equals(that.howpublished) : that.howpublished != null) {
            return false;
        }
        if (institution != null ? !institution.equals(that.institution) : that.institution != null) {
            return false;
        }
        if (journal != null ? !journal.equals(that.journal) : that.journal != null) {
            return false;
        }
        if (month != null ? !month.equals(that.month) : that.month != null) {
            return false;
        }
        if (note != null ? !note.equals(that.note) : that.note != null) {
            return false;
        }
        if (number != null ? !number.equals(that.number) : that.number != null) {
            return false;
        }
        if (organization != null ? !organization.equals(that.organization) : that.organization != null) {
            return false;
        }
        if (pages != null ? !pages.equals(that.pages) : that.pages != null) {
            return false;
        }
        if (publisher != null ? !publisher.equals(that.publisher) : that.publisher != null) {
            return false;
        }
        if (school != null ? !school.equals(that.school) : that.school != null) {
            return false;
        }
        if (series != null ? !series.equals(that.series) : that.series != null) {
            return false;
        }
        if (title != null ? !title.equals(that.title) : that.title != null) {
            return false;
        }
        if (volume != null ? !volume.equals(that.volume) : that.volume != null) {
            return false;
        }
        if (year != null ? !year.equals(that.year) : that.year != null) {
            return false;
        }
        return date != null ? date.equals(that.date) : that.date == null;
    }

    @Override
    public int hashCode() {
        int result = entryType.hashCode();
        result = (31 * result) + citationKey.hashCode();
        return result;
    }
}



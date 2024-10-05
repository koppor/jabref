package org.jabref.logic.layout.format;

import org.jabref.logic.layout.LayoutFormatter;
import org.jabref.model.entry.Month;

import java.util.Optional;

public class ShortMonthFormatter implements LayoutFormatter {

    @Override
    public String format(String fieldText) {
        Optional<Month> month = Month.parse(fieldText);
        return month.map(Month::getShortName).orElse("");
    }
}

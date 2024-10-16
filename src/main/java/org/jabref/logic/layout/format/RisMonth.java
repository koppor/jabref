package org.jabref.logic.layout.format;

import org.jabref.logic.layout.LayoutFormatter;
import org.jabref.model.entry.Month;

import java.util.Locale;
import java.util.Optional;

public class RisMonth implements LayoutFormatter {

    @Override
    public String format(String month) {
        if (month == null) {
            return "";
        }

        Optional<Month> parsedMonth = Month.getMonthByShortName(month);
        return parsedMonth.map(Month::getTwoDigitNumber).orElse(month.toLowerCase(Locale.ROOT));
    }
}

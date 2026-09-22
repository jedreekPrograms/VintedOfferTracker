package pl.flipbot.listing;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Locale;

@Converter
public class HistoryOutcomeConverter
        implements AttributeConverter<HistoryOutcome, String> {

    @Override
    public String convertToDatabaseColumn(HistoryOutcome attribute) {
        return attribute == null ? null : attribute.name();
    }

    @Override
    public HistoryOutcome convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return null;
        }

        return switch (dbData.trim().toUpperCase(Locale.ROOT)) {
            case "PURCHASED", "PURCHASED_BY_ME", "BOUGHT_BY_ME", "BOUGHT" ->
                    HistoryOutcome.PURCHASED;
            case "REJECTED", "REJECTED_BY_ME", "SKIPPED_BY_ME" ->
                    HistoryOutcome.REJECTED;
            case "MISSED_OPPORTUNITY" ->
                    HistoryOutcome.MISSED_OPPORTUNITY;
            case "UNCLASSIFIED" ->
                    HistoryOutcome.UNCLASSIFIED;
            default ->
                    HistoryOutcome.UNCLASSIFIED;
        };
    }
}

package pl.flipbot.listing;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Locale;

@Converter
public class MissedOpportunityReasonConverter
        implements AttributeConverter<MissedOpportunityReason, String> {

    @Override
    public String convertToDatabaseColumn(MissedOpportunityReason attribute) {
        return attribute == null ? null : attribute.name();
    }

    @Override
    public MissedOpportunityReason convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return null;
        }

        return switch (dbData.trim().toUpperCase(Locale.ROOT)) {
            case "SOLD_BEFORE_PURCHASE" ->
                    MissedOpportunityReason.SOLD_BEFORE_PURCHASE;
            case "NO_FUNDS" ->
                    MissedOpportunityReason.NO_FUNDS;
            case "TOO_SLOW" ->
                    MissedOpportunityReason.TOO_SLOW;
            case "OTHER" ->
                    MissedOpportunityReason.OTHER;
            default -> null;
        };
    }
}

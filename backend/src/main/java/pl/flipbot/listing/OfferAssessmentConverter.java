package pl.flipbot.listing;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Locale;

@Converter
public class OfferAssessmentConverter
        implements AttributeConverter<OfferAssessment, String> {

    @Override
    public String convertToDatabaseColumn(OfferAssessment attribute) {
        return attribute == null
                ? OfferAssessment.UNASSESSED.name()
                : attribute.name();
    }

    @Override
    public OfferAssessment convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return OfferAssessment.UNASSESSED;
        }

        return switch (dbData.trim().toUpperCase(Locale.ROOT)) {
            case "LEGIT" -> OfferAssessment.LEGIT;
            case "SCAM" -> OfferAssessment.SCAM;
            case "UNASSESSED" -> OfferAssessment.UNASSESSED;
            default -> OfferAssessment.UNASSESSED;
        };
    }
}

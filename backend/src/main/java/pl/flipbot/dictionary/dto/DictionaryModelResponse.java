package pl.flipbot.dictionary.dto;

import pl.flipbot.bot.configuration.TargetMode;

import java.math.BigDecimal;

public record DictionaryModelResponse(

        Long id,

        String name,

        Long brandId,

        String brandName,

        TargetMode targetMode,

        BigDecimal proposedOfferPrice,

        BigDecimal expectedResalePrice

) {
}

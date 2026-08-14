package pl.flipbot.dictionary.dto;

import pl.flipbot.bot.configuration.TargetMode;

import java.math.BigDecimal;
import java.util.List;

public record DictionaryModelResponse(

        Long id,

        String name,

        Long brandId,

        String brandName,

        Long categoryId,

        String categoryName,

        String categoryPath,

        List<String> categoryPathElements,

        TargetMode targetMode,

        BigDecimal proposedOfferPrice,

        BigDecimal expectedResalePrice

) {
}

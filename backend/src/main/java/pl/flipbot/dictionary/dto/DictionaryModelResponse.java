package pl.flipbot.dictionary.dto;

public record DictionaryModelResponse(

        Long id,

        String name,

        Long brandId,

        String brandName

) {
}
package pl.flipbot.dictionary.dto;

import java.util.List;

public record DictionaryCategoryResponse (
        Long id,

        String name,

        String path,

        List<String> categoryPath

){
}

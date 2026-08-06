package pl.flipbot.dictionary;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DictionaryCategoryRepository
        extends JpaRepository<DictionaryCategory, Long> {

    boolean existsByPathIgnoreCase(
            String path
    );

    List<DictionaryCategory> findAllByOrderByPathAsc();

}
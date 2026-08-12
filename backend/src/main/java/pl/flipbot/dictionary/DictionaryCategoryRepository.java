package pl.flipbot.dictionary;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DictionaryCategoryRepository
        extends JpaRepository<DictionaryCategory, Long> {

    boolean existsByPathIgnoreCase(
            String path
    );

    boolean existsByPathIgnoreCaseAndIdNot(
            String path,
            Long id
    );

    List<DictionaryCategory> findAllByOrderByPathAsc();
}

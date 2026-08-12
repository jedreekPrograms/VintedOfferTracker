package pl.flipbot.dictionary;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DictionaryBrandRepository
        extends JpaRepository<DictionaryBrand, Long> {

    boolean existsByNameIgnoreCase(
            String name
    );

    boolean existsByNameIgnoreCaseAndIdNot(
            String name,
            Long id
    );

    List<DictionaryBrand> findAllByOrderByNameAsc();
}

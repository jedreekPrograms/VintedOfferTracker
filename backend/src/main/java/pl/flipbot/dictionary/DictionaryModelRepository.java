package pl.flipbot.dictionary;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DictionaryModelRepository
        extends JpaRepository<DictionaryModel, Long> {

    boolean existsByBrand_IdAndNameIgnoreCase(
            Long brandId,
            String name
    );

    boolean existsByBrand_IdAndNameIgnoreCaseAndIdNot(
            Long brandId,
            String name,
            Long id
    );

    boolean existsByBrand_Id(
            Long brandId
    );

    List<DictionaryModel> findAllByBrand_IdOrderByNameAsc(
            Long brandId
    );
}

package pl.flipbot.dictionary;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DictionaryModelRepository extends JpaRepository<DictionaryModel, Long> {

    boolean existsByBrand_IdAndNameIgnoreCase(
            Long brandId,
            String name
    );

    List<DictionaryModel> findAllByBrand_IdOrderByNameAsc(
            Long brandId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select model
            from DictionaryModel model
            where model.id = :modelId
            """)
    Optional<DictionaryModel> findByIdForUpdate(
            @Param("modelId") Long modelId
    );
}

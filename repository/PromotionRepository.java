package renewal.common.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import renewal.common.entity.Promotion;

public interface PromotionRepository extends JpaRepository<Promotion, Long>, JpaSpecificationExecutor<Promotion> {

    @Query("""
                SELECT p
                FROM Promotion p
                WHERE p.startTime <= CURRENT_TIMESTAMP
                  AND p.endTime   >= CURRENT_TIMESTAMP
            """)
    List<Promotion> findActivePromotions();

}

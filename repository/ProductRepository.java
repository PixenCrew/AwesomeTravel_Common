package renewal.common.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import renewal.common.entity.Product;
import renewal.common.entity.TimeDeal;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

  // Tour → CountryCode 기준
  List<Product> findAllByTour_Country_CountryCodeIn(Collection<String> countryCodes);

  // Tour → Schedules → Location → CityCode 기준
  List<Product> findDistinctByTour_Schedules_Locations_CityCode_CityCodeIn(Collection<String> cityCodes);

  @Query("""
          SELECT p
          FROM Product p
          WHERE p.timeDeal IS NOT NULL
            AND (p.isActive IS NULL OR p.isActive = true)
            AND p.timeDeal.startTime <= CURRENT_TIMESTAMP
            AND p.timeDeal.endTime   >= CURRENT_TIMESTAMP
          ORDER BY p.timeDeal.endTime ASC
      """)
  List<Product> findActiveTimeDealProducts();

  List<Product> findByTimeDeal(TimeDeal timeDeal);

  // 최근 등록 상품 조회 (비로그인 사용자용)
  @Query("SELECT p FROM Product p ORDER BY p.id DESC")
  List<Product> findRecentProducts(org.springframework.data.domain.Pageable pageable);

  /** cutoffDays가 설정된 상품 목록 (마감일/홀드 반납 스케줄용) */
  List<Product> findByCutoffDaysIsNotNull();

}

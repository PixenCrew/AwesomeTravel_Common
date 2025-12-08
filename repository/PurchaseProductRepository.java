package renewal.common.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import renewal.common.entity.Product;
import renewal.common.entity.PurchaseProduct;

public interface PurchaseProductRepository extends JpaRepository<PurchaseProduct, Long> {

  @Query("SELECT DISTINCT pp FROM PurchaseProduct pp " +
      "JOIN FETCH pp.product " +
      "LEFT JOIN FETCH pp.passengers p " +
      "LEFT JOIN FETCH p.countryCode " +
      "WHERE pp.id = :id")
  Optional<PurchaseProduct> findByIdWithAll(@Param("id") Long id);

  List<PurchaseProduct> findByUserId(Long userid);
  
  @Query("SELECT p FROM PurchaseProduct p WHERE p.product = :product")
  List<PurchaseProduct> findByProduct(@Param("product") Product product);

  @Query("""
       SELECT p
       FROM PurchaseProduct p
       WHERE p.product = :product
         AND p.departDateTime >= :departDateStart
         AND p.departDateTime < :departDateEnd
       ORDER BY p.purchaseDate ASC
      """)
  List<PurchaseProduct> findByProductAndDepartDate(
      @Param("product") Product product,
      @Param("departDateStart") java.time.LocalDateTime departDateStart,
      @Param("departDateEnd") java.time.LocalDateTime departDateEnd);

}

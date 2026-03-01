package renewal.common.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import renewal.common.entity.PackageSeatHold;

public interface PackageSeatHoldRepository extends JpaRepository<PackageSeatHold, Long> {

    Optional<PackageSeatHold> findByProductIdAndDepartDateAndOutboundSeatClassIdAndReturnSeatClassId(
        Long productId,
        LocalDate departDate,
        Long outboundSeatClassId,
        Long returnSeatClassId);

    /** 마감 반납 대상: 아직 반납 안 했고, 출발일이 지난 홀드 */
    List<PackageSeatHold> findByReleasedAtIsNullAndDepartDateBefore(LocalDate beforeDate);

    /** 특정 출발일의 미반납 홀드 (마감일 N일 전 반납 시 사용: departDate = today + N) */
    List<PackageSeatHold> findByReleasedAtIsNullAndDepartDate(LocalDate departDate);

    /** (productId, departDate) 단위 미반납 홀드 (상품별 cutoffDays 기준 반납 시 사용) */
    List<PackageSeatHold> findByReleasedAtIsNullAndProductIdAndDepartDate(Long productId, LocalDate departDate);
}

package renewal.common.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 패키지 상품별·출발일·항공 조합별 좌석 홀드.
 * 가능 인원(maxCapacity)만큼 출국/귀국 SeatClass에서 미리 확보하고,
 * 결제 시 allocated만 증가, 취소 시 allocated 감소, 마감 시 미사용분 반납.
 */
@Entity
@Table(uniqueConstraints = {
    @UniqueConstraint(name = "uk_package_hold_combo",
        columnNames = {"product_id", "depart_date", "outbound_seat_class_id", "return_seat_class_id"})
})
@Getter
@Setter
@NoArgsConstructor
public class PackageSeatHold {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "depart_date", nullable = false)
    private LocalDate departDate;

    @Column(name = "outbound_seat_class_id", nullable = false)
    private Long outboundSeatClassId;

    @Column(name = "return_seat_class_id", nullable = false)
    private Long returnSeatClassId;

    /** 홀드한 총 좌석 수 (Tour.maxCapacity) */
    @Column(name = "total_held", nullable = false)
    private Long totalHeld;

    /** 이미 결제 확정된 좌석 수 */
    @Column(name = "allocated", nullable = false)
    private Long allocated = 0L;

    /** 미사용분 반납 완료 시각 (null이면 아직 반납 전) */
    @Column(name = "released_at")
    private LocalDateTime releasedAt;

    public PackageSeatHold(Long productId, LocalDate departDate, Long outboundSeatClassId, Long returnSeatClassId, Long totalHeld) {
        this.productId = productId;
        this.departDate = departDate;
        this.outboundSeatClassId = outboundSeatClassId;
        this.returnSeatClassId = returnSeatClassId;
        this.totalHeld = totalHeld != null ? totalHeld : 0L;
        this.allocated = 0L;
    }
}

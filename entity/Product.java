package renewal.common.entity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Transient;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import renewal.common.entity.SeatClass.SeatClassType;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class Product extends AuditingFields implements Cloneable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column
    private Long id;

    @Enumerated(EnumType.STRING)
    private ProductType productType; // INDEPENDENT, PACKAGE

    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "tour_id")
    private Tour tour;

    private String title;
    private Long price; // 그냥 대표가격[성인기준]임. 실제 가격 조회는 연결된 Tour price* 참조
    private Long cutoffDays; // 특정 출발일 기준 N일전까지 예약 받음

    // 노옵션, 노쇼핑 여부
    private boolean noShopping;
    private boolean noOption;

    // 상품조회시 조회된 항공권,호텔,투어 가격 저장용 필드 (DB 저장 X)
    @Transient
    private Long finalPriceAdult;
    @Transient
    private Long finalPriceYouth;
    @Transient
    private Long finalPriceInfant;

    @Transient
    private Airline airline;

    @Transient
    private Long reservedSeats;
    @Transient
    private Long availableSeats;

    @ManyToOne(fetch = FetchType.EAGER)
    private TimeDeal timeDeal;

    @Transient
    private LocalDateTime departDateTime;
    @Transient
    private LocalDateTime returnDateTime;

    // 여유일 없음 = FINISHED (예약불가)
    // 여유일 있음 + 좌석있음 = AVAILABLE (예약가능)
    // 여유일 있음 + 좌석있음 + 최소출발 인원 달성 = CONFIRMED (출발확정)
    // 여유일 있음 + 좌석없음 = WAITING (예약대기 신청 가능)

    @Transient
    private ProductStatus productStatus = ProductStatus.AVAILABLE;

    public void UpdateProductStatus() {
        if (LocalDateTime.now().isBefore(departDateTime.minusDays(cutoffDays))) {
            if (availableSeats > 0) {
                this.productStatus = ProductStatus.AVAILABLE;
                if (reservedSeats >= tour.getMinCapacity()) {
                    this.productStatus = ProductStatus.CONFIRMED;
                }
            } else {
                this.productStatus = ProductStatus.WAITING;
            }
        } else {
            this.productStatus = ProductStatus.FINISHED;
        }
    }

    @Transient
    private Long bak; // N박 숙박횟수

    @Transient
    private Long il; // M일

    private Boolean isActive = true; // 상품 활성화 여부 (기본값: true)

    @Enumerated(EnumType.STRING)
    private DepartTimeType departTimeType;

    // 이미지 URL들
    @ElementCollection
    private List<String> photos = new ArrayList<>();

    private String thumbnail;
    private String image;

    // 상품정보 - 포함사항
    @ElementCollection
    private List<Info> include = new ArrayList<>();

    // 상품정보 - 불포함사항
    @ElementCollection
    private List<Info> exclude = new ArrayList<>();

    // 상품정보 - 약관 / 사전 작성된 html로 대체
    // @ElementCollection
    // private List<Info> term = new ArrayList<>();

    // 검색용 키워드들
    @ElementCollection
    private Set<String> keywords = new HashSet<>();

    // 대상 항공권등급
    // @JdbcTypeCode(SqlTypes.JSON)
    // @Column(columnDefinition = "json")
    @JdbcTypeCode(SqlTypes.JSON)
    private Set<SeatClassType> seatClassTypes;

    // 일정표

    // 리뷰 요약
    // private Long totalReview = 0L; // 총 리뷰 수
    // @Column(precision = 3, scale = 2)
    // private BigDecimal avgerageReview = new BigDecimal("0.00"); // 평점 평균
    private Long star1 = 0L; // 1점 리뷰 수
    private Long star2 = 0L; // 2점 리뷰 수
    private Long star3 = 0L; // ...
    private Long star4 = 0L;
    private Long star5 = 0L;

    // private Long[] stars = new Long[]{0L,0L,0L,0L,0L};

    // 최근 or 찜 상품 표시용
    @Transient
    private LocalDateTime viewedAt;

    @Embeddable
    @Getter
    @Setter
    @NoArgsConstructor
    public static class Info {
        private String title; // 제목 (굵게)
        private String content; // 내용
        private String appendix; // 추가내용 (흐리게)
    }

    public void UpdateAvg(int star) {
        // totalReview++;
        switch (star) {
            case 1:
                star1++;
                break;
            case 2:
                star2++;
                break;
            case 3:
                star3++;
                break;
            case 4:
                star4++;
                break;
            case 5:
                star5++;
                break;
        }

        // Long sum = star1 + star2 * 2 + star3 * 3 + star4 * 4 + star5 * 5;
        // avgerageReview = BigDecimal.valueOf(sum)
        // .divide(
        // BigDecimal.valueOf(totalReview),
        // 2,
        // RoundingMode.HALF_UP);
    }

    public enum ProductType {
        INDEPENDENT, PACKAGE
    }

    @Getter
    @AllArgsConstructor
    public enum DepartTimeType {
        EARLY_MORNING(0, 5), // 00:00 ~ 05:59
        MORNING(6, 11), // 06:00 ~ 11:59
        AFTERNOON(12, 23); // 12:00 ~ 23:59

        private final int startHour;
        private final int endHour;
    }

    // 예약가능, 출발확정, 예약대기, 예약마감
    public enum ProductStatus {
        AVAILABLE,
        CONFIRMED,
        WAITING,
        FINISHED
    }

    private static long nvl(Long v) {
        return v != null ? v : 0L;
    }

    // Review 통계 메서드 (DB에 star1~star5가 null일 수 있음)
    public Long getTotalReviews() {
        return nvl(star1) + nvl(star2) + nvl(star3) + nvl(star4) + nvl(star5);
    }

    public Double getAverageRating() {
        long total = getTotalReviews();
        if (total == 0)
            return 0.0;
        return (nvl(star1) * 1.0 + nvl(star2) * 2.0 + nvl(star3) * 3.0 + nvl(star4) * 4.0 + nvl(star5) * 5.0) / total;
    }

    public Double getStarPercentage(int starNumber) {
        long total = getTotalReviews();
        if (total == 0)
            return 0.0;
        long count;
        switch (starNumber) {
            case 1: count = nvl(star1); break;
            case 2: count = nvl(star2); break;
            case 3: count = nvl(star3); break;
            case 4: count = nvl(star4); break;
            case 5: count = nvl(star5); break;
            default: count = 0L;
        }
        return (count * 100.0) / total;
    }

    @Override
    public Object clone() throws CloneNotSupportedException {
        return (Product) super.clone(); // 얕은 복사 수행
    }

}

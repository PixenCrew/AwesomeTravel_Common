package renewal.common.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "banner")
public class Banner extends AuditingFields {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    @Column(nullable = false)
    private Boolean active = true;

    @Column(nullable = false, length = 512)
    private String file;

    @Column(nullable = false, length = 512)
    private String url;

    // 배너가 표시될 위치 타입
    @Enumerated(EnumType.STRING)
    @Column(name = "location_type", nullable = false)
    private BannerLocationType locationType = BannerLocationType.HOME;

    // 구체적인 위치 식별자 (예: menuCode "101", "201" 또는 "air_search" 등)
    @Column(name = "location_identifier", length = 50)
    private String locationIdentifier;

    public enum BannerLocationType {
        HOME,           // 홈페이지 메인 배너
        SUB_MENU,       // 서브메뉴 페이지 배너 (menuCode 사용)
        AIR_SEARCH,     // 항공선택 페이지 배너
        PRODUCT_LIST,   // 상품 목록 페이지 배너
        PRODUCT_DETAIL  // 상품 상세 페이지 배너
    }

    public Banner(Integer displayOrder, String title, LocalDate startDate, LocalDate endDate, String file, String url) {
        this.displayOrder = displayOrder;
        this.title = title;
        this.startDate = startDate;
        this.endDate = endDate;
        this.file = file;
        this.url = url;
        this.active = true;
        this.locationType = BannerLocationType.HOME;
    }

    public Banner(Integer displayOrder, String title, LocalDate startDate, LocalDate endDate, String file, String url, BannerLocationType locationType, String locationIdentifier) {
        this.displayOrder = displayOrder;
        this.title = title;
        this.startDate = startDate;
        this.endDate = endDate;
        this.file = file;
        this.url = url;
        this.active = true;
        this.locationType = locationType;
        this.locationIdentifier = locationIdentifier;
    }
}


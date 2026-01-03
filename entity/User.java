package renewal.common.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@DynamicInsert
@DynamicUpdate
public class User extends AuditingFields {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 기본 로그인 정보
    @Column(nullable = false, unique = true, length = 100)
    private String email;
    private boolean emailVerified;

    @Column(length = 255)
    private String password; // 소셜 로그인은 null 가능

    @Column(nullable = false, length = 50)
    private String name;

    @Column
    private String profileImage;

    @Column(length = 20)
    private String phone;

    private LocalDate birthDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserProvider provider; // LOCAL, GOOGLE, NAVER

    @Column(length = 100)
    private String providerId; // 소셜로그인 고유 ID

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role; // USER, ADMIN

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private UserStatus status = UserStatus.ACTIVE; // ACTIVE, WITHDRAWN, BANNED

    private LocalDateTime lastLoginAt;

    @OneToMany(mappedBy = "writer")
    @Builder.Default
    private List<Review> reviewComments = new ArrayList<>();

    @OneToMany(mappedBy = "user")
    @Builder.Default
    private List<PurchaseAir> airPurchases = new ArrayList<>();

    // @OneToMany(mappedBy = "user")
    // private List<PurchaseProduct> productPurchases = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    private MemberGrade grade = MemberGrade.BASIC;

    // 최근 본 상품
    @Builder.Default
    @ElementCollection
    @CollectionTable(joinColumns = @JoinColumn(name = "user_id"))
    private List<RecentViewedItem> recentProducts = new ArrayList<>();

    // 찜한 상품
    @Builder.Default
    @ElementCollection
    @CollectionTable(joinColumns = @JoinColumn(name = "user_id"))
    private List<RecentViewedItem> likedProducts = new ArrayList<>();

    // 동의한 약관
    @Builder.Default
    @ElementCollection
    @CollectionTable(joinColumns = @JoinColumn(name = "user_id"))
    private Map<String, Boolean> terms = new HashMap<>();

    // // 보유 쿠폰
    // @OneToMany(mappedBy = "user")
    // @Builder.Default
    // private List<UserCoupon> userCoupons = new ArrayList<>();

    // 보유 포인트
    private Long point;

    // @PrePersist
    // protected void onCreate() {
    // this.createdAt = LocalDateTime.now();
    // this.status = this.status == null ? UserStatus.ACTIVE : this.status;
    // }

    // @PreUpdate
    // protected void onUpdate() {
    // this.updatedAt = LocalDateTime.now();
    // }

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @Embeddable
    public static class RecentViewedItem {
        private Long productId;
        private LocalDateTime viewedAt;
    }

    public enum UserStatus {
        INACTIVE, ACTIVE, WITHDRAWN, BANNED
    }

    public enum UserRole {
        USER, ADMIN
    }

    public enum UserProvider {
        LOCAL, GOOGLE, NAVER
    }

    public enum MemberGrade {
        BASIC, GREEN, BLUE, PURPLE, BLACK
    }

}

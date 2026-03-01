package renewal.common.entity;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(uniqueConstraints = {
    @UniqueConstraint(name = "uk_air_class_type", columnNames = {"air_id", "class_type"})
})
@NoArgsConstructor
@Getter
@Setter
public class SeatClass {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne
    @JoinColumn(name = "air_id", nullable = false)
    private Air air;
    @Enumerated(EnumType.STRING) // Enum을 DB에 문자열로 저장
    private SeatClassType classType; // 좌석 등급

    private Long priceAdult; // 해당 등급의 가격
    private Long priceYouth; // 해당 등급의 청소년 가격
    private Long priceInfant; // 해당 등급의 영유아 가격
    
    private Long maxSeats; // 해당 등급의 최대 좌석 수
    private Long availableSeats; // 잔여 좌석 수

    @OneToMany(mappedBy = "seatClass", cascade = CascadeType.ALL)
    private List<AirReservation> airReservations = new ArrayList<>();

    public void reserveSeats(Long requiredPersons) throws Exception {
        if (availableSeats >= requiredPersons) {
            availableSeats -= requiredPersons;
        } else {
            throw new Exception("잔여 좌석 에러");
        }
    }

    public void cancelSeats(Long canceledPersons) throws Exception {
        if ((availableSeats+canceledPersons) <= maxSeats) {
            availableSeats += canceledPersons;
        }
    }

    public void decreaseAvailableSeats(int reserveCount) {
        availableSeats -= reserveCount;
    }

    public void increaseAvailableSeats(int cancelCount) {
        availableSeats += cancelCount;
    }
    
    // Alias getter for price
    public Long getPrice() {
        return priceAdult;
    }

    public enum SeatClassType {
        ECONOMY,
        PREMIUMECONOMY,
        BUSINESS,
        FIRST
    }

}

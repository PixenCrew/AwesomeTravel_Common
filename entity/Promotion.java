package renewal.common.entity;

import java.time.LocalDateTime;

import org.springframework.format.annotation.DateTimeFormat;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToOne;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import jakarta.persistence.Column;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class Promotion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title; // 제목
    private String description; // 텍스트 설명
    @Column(length = 1000)
    private String thumnailImg; // 대표이미지
    @Column(length = 1000)
    private String contentImg; // 상세페이지 이미지

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime startTime; // 노출 시작일
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime endTime; // 노출 만료일

    @OneToOne(fetch = FetchType.LAZY)
    private MenuCode menuCode; // 해당 기획전에 해당하는 메뉴코드

    public boolean isActive() {
        return (LocalDateTime.now().isAfter(startTime) && LocalDateTime.now().isBefore(endTime));
    }
}

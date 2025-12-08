package renewal.common.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import renewal.common.entity.AirportCode;
import renewal.common.entity.SeatClass;
import renewal.common.entity.SeatClass.SeatClassType;

public interface SeatClassRepository extends JpaRepository<SeatClass, Long> {
        Logger log = LoggerFactory.getLogger(SeatClassRepository.class);
        
        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("SELECT s FROM SeatClass s WHERE s.id = :id")
        Optional<SeatClass> findByIdWithLock(@Param("id") Long id);

        SeatClass findTop1ByAir_DepartDateTimeBetweenAndAir_DepartAirportAndAir_ArriveAirportAndClassTypeInOrderByPriceAdultAsc(
                        LocalDateTime startOfDay,
                        LocalDateTime endOfDay,
                        AirportCode departAirport,
                        AirportCode arriveAirport,
                        Set<SeatClassType> classTypes);

        // 커스텀 쿼리: airportCode 문자열로 직접 비교 (객체 비교 문제 해결)
        @Query("""
                SELECT sc FROM SeatClass sc
                JOIN sc.air a
                JOIN a.departAirport da
                JOIN a.arriveAirport aa
                WHERE a.departDateTime >= :startDateTime
                  AND a.departDateTime <= :endDateTime
                  AND da.airportCode = :departAirportCode
                  AND aa.airportCode = :arriveAirportCode
                  AND sc.classType IN :classTypes
                  AND a.status = renewal.common.entity.Air.AirStatus.ACTIVE
                ORDER BY sc.priceAdult ASC
                """)
        List<SeatClass> findLowestPriceSeatsByAirportCodes(
                        @Param("startDateTime") LocalDateTime startDateTime,
                        @Param("endDateTime") LocalDateTime endDateTime,
                        @Param("departAirportCode") String departAirportCode,
                        @Param("arriveAirportCode") String arriveAirportCode,
                        @Param("classTypes") Set<SeatClassType> classTypes);

        // 너무 길어서 래핑
        default SeatClass findLowestPriceSeat(
                        LocalDateTime startOfDay,
                        LocalDateTime endOfDay,
                        AirportCode departAirport,
                        AirportCode arriveAirport,
                        Set<SeatClass.SeatClassType> classTypes) {
                // airportCode 문자열로 직접 비교하는 커스텀 쿼리 사용
                if (departAirport != null && arriveAirport != null) {
                        String departCode = departAirport.getAirportCode();
                        String arriveCode = arriveAirport.getAirportCode();
                        
                        List<SeatClass> results = findLowestPriceSeatsByAirportCodes(
                                        startOfDay, endOfDay,
                                        departCode,
                                        arriveCode,
                                        classTypes);
                        
                        return results.isEmpty() ? null : results.get(0);
                } else {
                        // departAirport나 arriveAirport가 null인 경우
                }
                // fallback: 기존 메서드 사용
                return findTop1ByAir_DepartDateTimeBetweenAndAir_DepartAirportAndAir_ArriveAirportAndClassTypeInOrderByPriceAdultAsc(
                                startOfDay, endOfDay, departAirport, arriveAirport, classTypes);
        }

        @Query("""
                            SELECT DISTINCT sc FROM SeatClass sc
                            JOIN FETCH sc.air a
                            JOIN FETCH a.airline al
                            JOIN FETCH a.departAirport da
                            JOIN FETCH a.arriveAirport aa
                            LEFT JOIN FETCH a.flightSegments fs
                            WHERE sc.id IN :ids
                        """)
        List<SeatClass> findAllWithAirInfoByIds(@Param("ids") List<Long> ids);

        Optional<SeatClass> findByAirIdAndClassType(Long airId, SeatClassType classType);

}

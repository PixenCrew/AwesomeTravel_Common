package renewal.common.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import renewal.common.entity.AirportCode;
import renewal.common.entity.Location;
import renewal.common.entity.Location.LocationType;
import renewal.common.entity.PackageSeatHold;
import renewal.common.entity.Product;
import renewal.common.entity.Product.ProductStatus;
import renewal.common.entity.PurchaseBase.ConfirmedSeatClass;
import renewal.common.entity.PurchaseBase.PurchaseStatus;
import renewal.common.entity.PurchaseProduct;
import renewal.common.entity.Refund;
import renewal.common.entity.Schedule;
import renewal.common.entity.SeatClass;
import renewal.common.entity.TimeDeal;
import renewal.common.entity.TimeDeal.DiscountType;
import renewal.common.entity.Tour;
import renewal.common.repository.PackageSeatHoldRepository;
import renewal.common.repository.ProductRepository;
import renewal.common.repository.PurchaseProductRepository;
import renewal.common.repository.RefundRepository;
import renewal.common.repository.SeatClassRepository;

@Service
@RequiredArgsConstructor
public class ProductServiceCommon {
    
    private static final Logger log = LoggerFactory.getLogger(ProductServiceCommon.class);

    private final ProductRepository productRepo;
    private final PurchaseProductRepository purchaseProductRepo;
    private final SeatClassRepository seatClassRepo;
    private final RefundRepository refundRepo;
    private final PackageSeatHoldRepository packageSeatHoldRepo;
    private final EmailService emailService;

    /**
     * 옵션별로 서로 다른 Tour/Location을 쓰기 위해 Tour(및 Schedules, Locations)를 복사한다.
     * 복사본은 영속화하지 않고 계산용으로만 사용한다. Location은 새 인스턴스로 복사하므로
     * 각 복사본에 setSeatClass를 호출해도 다른 복사본에 영향을 주지 않는다.
     *
     * @param source 원본 Tour (null이면 null 반환)
     * @return 복사된 Tour, 또는 null
     */
    public Tour copyTourForOption(Tour source) {
        if (source == null) {
            return null;
        }
        Hibernate.initialize(source.getSchedules());
        if (source.getSchedules() == null) {
            Tour copy = new Tour();
            copy.setCompany(source.getCompany());
            copy.setName(source.getName());
            copy.setCountry(source.getCountry());
            copy.setMaxCapacity(source.getMaxCapacity());
            copy.setMinCapacity(source.getMinCapacity());
            copy.setStartDate(source.getStartDate());
            copy.setEndDate(source.getEndDate());
            copy.setPriceAdult(source.getPriceAdult());
            copy.setPriceYouth(source.getPriceYouth());
            copy.setPriceInfant(source.getPriceInfant());
            copy.setHotelPriceSum(source.getHotelPriceSum());
            if (source.getKeywords() != null) {
                copy.setKeywords(new HashSet<>(source.getKeywords()));
            }
            copy.setSchedules(new ArrayList<>());
            return copy;
        }
        Tour copy = new Tour();
        copy.setCompany(source.getCompany());
        copy.setName(source.getName());
        copy.setCountry(source.getCountry());
        copy.setMaxCapacity(source.getMaxCapacity());
        copy.setMinCapacity(source.getMinCapacity());
        copy.setStartDate(source.getStartDate());
        copy.setEndDate(source.getEndDate());
        copy.setPriceAdult(source.getPriceAdult());
        copy.setPriceYouth(source.getPriceYouth());
        copy.setPriceInfant(source.getPriceInfant());
        copy.setHotelPriceSum(source.getHotelPriceSum());
        if (source.getKeywords() != null) {
            copy.setKeywords(new HashSet<>(source.getKeywords()));
        }
        List<Schedule> copySchedules = new ArrayList<>();
        for (Schedule srcSchedule : source.getSchedules()) {
            if (srcSchedule == null) continue;
            Hibernate.initialize(srcSchedule.getLocations());
            Schedule copySchedule = new Schedule();
            copySchedule.setTour(copy);
            copySchedule.setDay(srcSchedule.getDay());
            List<Location> copyLocations = new ArrayList<>();
            if (srcSchedule.getLocations() != null) {
                for (Location srcLoc : srcSchedule.getLocations()) {
                    if (srcLoc == null) continue;
                    Location copyLoc = new Location();
                    copyLoc.setSchedule(copySchedule);
                    copyLoc.setLocationType(srcLoc.getLocationType());
                    copyLoc.setName(srcLoc.getName());
                    copyLoc.setDescription(srcLoc.getDescription());
                    copyLoc.setCityCode(srcLoc.getCityCode());
                    copyLoc.setDepartAirport(srcLoc.getDepartAirport());
                    copyLoc.setArriveAirport(srcLoc.getArriveAirport());
                    copyLoc.setHotel(srcLoc.getHotel());
                    copyLocations.add(copyLoc);
                }
            }
            copySchedule.setLocations(copyLocations);
            copySchedules.add(copySchedule);
        }
        copy.setSchedules(copySchedules);
        return copy;
    }

    /**
     * 🔧 리스트용: 모든 SeatClass에 대해 Product를 복제 생성
     * 각 SeatClass마다 Product를 하나씩 생성하여 반환
     * 
     * @param baseProduct 기본 Product (복제 원본)
     * @param departDate 출발 날짜
     * @param schedule Schedule (항공편이 있는 Schedule)
     * @param location Location (AIR 타입의 Location)
     * @param startDateTime 조회 시작 시간
     * @param endDateTime 조회 종료 시간
     * @param departAirport 출발 공항
     * @param arriveAirport 도착 공항
     * @param seatClassTypes 허용된 좌석 등급 타입들
     * @return 각 SeatClass에 대한 Product 리스트
     */
    public List<Product> buildProductsForDate(
            Product baseProduct,
            LocalDate departDate,
            Schedule schedule,
            Location location,
            LocalDateTime startDateTime,
            LocalDateTime endDateTime,
            AirportCode departAirport,
            AirportCode arriveAirport,
            Set<SeatClass.SeatClassType> seatClassTypes) {
        
        List<Product> result = new ArrayList<>();
        
        // 모든 SeatClass 조회 (가격순 정렬)
        List<SeatClass> seats = seatClassRepo.findLowestPriceSeatsByAirportCodes(
                startDateTime,
                endDateTime,
                departAirport.getAirportCode(),
                arriveAirport.getAirportCode(),
                seatClassTypes);
        
        if (seats == null || seats.isEmpty()) {
            return result;
        }
        
        // 🔧 핵심: SeatClass를 그룹핑하여 (airId + seatClassType) 기준으로 최저가만 유지
        Map<String, SeatClass> grouped = new java.util.HashMap<>();
        for (SeatClass seat : seats) {
            if (seat.getAir() != null && seat.getAir().getId() != null && seat.getClassType() != null) {
                String key = seat.getAir().getId() + "_" + seat.getClassType().name();
                
                // 같은 항공 + 같은 좌석등급이면 "최저가"만 유지
                if (!grouped.containsKey(key) 
                    || (seat.getPriceAdult() != null && grouped.get(key).getPriceAdult() != null
                        && seat.getPriceAdult() < grouped.get(key).getPriceAdult())) {
                    grouped.put(key, seat);
                }
            }
        }
        
        // 🔧 그룹핑된 SeatClass만 사용하여 Product 생성
        for (SeatClass seat : grouped.values()) {
            try {
                // Product 복제
                Product cloned = (Product) baseProduct.clone();
                // 옵션별로 서로 다른 Tour/Location을 쓰기 위해 Tour 복사본 사용
                Tour tourCopy = copyTourForOption(baseProduct.getTour());
                cloned.setTour(tourCopy != null ? tourCopy : cloned.getTour());

                // Tour 및 Schedule 초기화 (복사본 기준)
                if (cloned.getTour() != null) {
                    if (cloned.getTour().getSchedules() != null) {
                        // 해당 Schedule 찾기
                        for (Schedule clonedSchedule : cloned.getTour().getSchedules()) {
                            if (clonedSchedule != null && Objects.equals(clonedSchedule.getDay(), schedule.getDay())) {
                                Hibernate.initialize(clonedSchedule.getLocations());
                                if (clonedSchedule.getLocations() != null) {
                                    // 해당 Location 찾아서 SeatClass 설정
                                    for (Location clonedLocation : clonedSchedule.getLocations()) {
                                        if (clonedLocation != null 
                                            && clonedLocation.getLocationType() == Location.LocationType.AIR
                                            && clonedLocation.getDepartAirport() != null
                                            && clonedLocation.getArriveAirport() != null
                                            && clonedLocation.getDepartAirport().getAirportCode().equals(departAirport.getAirportCode())
                                            && clonedLocation.getArriveAirport().getAirportCode().equals(arriveAirport.getAirportCode())) {
                                            clonedLocation.setSeatClass(seat);
                                            break;
                                        }
                                    }
                                }
                                break;
                            }
                        }
                    }
                }
                
                // 가격 계산
                Product calcProduct = calcSingleProduct(cloned, departDate);
                if (calcProduct != null && calcProduct.getFinalPriceAdult() != null) {
                    result.add(calcProduct);
                }
            } catch (CloneNotSupportedException e) {
                log.error("Product clone failed for seat class: {}", seat.getId(), e);
            }
        }
        
        return result;
    }

    public Product calcSingleProduct(Product product, LocalDate departDate) {
        Hibernate.initialize(product.getTour());
        Tour tour = product.getTour();

        // 초기 가격 계산
        Long finalPriceAdult = tour.getPriceAdult();
        Long finalPriceYouth = tour.getPriceYouth();
        Long finalPriceInfant = tour.getPriceInfant();
        
        // 가격 구성 추적용 변수
        Long tourPrice = tour.getPriceAdult();
        Long totalAirPrice = 0L;
        Long totalHotelPrice = 0L;
        
        // 각 항공편 정보 저장용 리스트
        java.util.List<String> airDetails = new java.util.ArrayList<>();

        List<Schedule> schedules = tour.getSchedules();

        if (schedules == null || schedules.isEmpty()) {
            return product;
        }

        for (Schedule sced : schedules) {
            if (sced == null) {
                continue;
            }
            
            // Schedule의 locations 컬렉션 초기화
            Hibernate.initialize(sced.getLocations());
            
            if (sced.getLocations() == null) {
                continue;
            }

            List<Location> locations = sced.getLocations();
            for (Location loc : locations) {
                if (loc == null) {
                    continue;
                }

                LocationType type = loc.getLocationType();
                if (type == LocationType.AIR) {

                    // // cutoffDays 적용 여부 선택
                    // LocalDate departDate = today;
                    // if (applyCutoff) {
                    // departDate = departDate.plusDays(product.getCutoffDays());
                    // }
                    // departDate = departDate.plusDays(sced.getDay());

                    LocalDate currentdepartDate = departDate.plusDays(sced.getDay());
                    // departTimeType은 선택 필터로 완화: 조회 시에는 하루 전체(00:00~23:59) 항공편 조회
                    int startHour = 0;
                    int endHour = 23;

                    LocalDateTime startDateTime = currentdepartDate.atTime(startHour, 0);
                    LocalDateTime endDateTime = currentdepartDate.atTime(endHour, 59, 59);

                    // LAZY 로딩된 AirportCode를 명시적으로 초기화
                    AirportCode departAirport = loc.getDepartAirport();
                    AirportCode arriveAirport = loc.getArriveAirport();
                    
                    if (departAirport != null) {
                        Hibernate.initialize(departAirport);
                    }
                    if (arriveAirport != null) {
                        Hibernate.initialize(arriveAirport);
                    }
                    
                    // AirportCode가 null이면 다음 Location으로
                    if (departAirport == null || arriveAirport == null) {
                        continue;
                    }
                    // System.out.println("\n=======findLowestPriceSeat========");
                    // System.out.println(startDateTime);
                    // System.out.println(endDateTime);
                    // System.out.println(departAirport.getAirportCode());
                    // System.out.println(arriveAirport.getAirportCode());
                    // System.out.println(product.getSeatClassTypes());
                    // 🔧 핵심 수정: SeatClass가 이미 설정되어 있으면 그것을 사용 (리스트용/달력용 모두)
                    // SeatClass가 없을 때만 findLowestPriceSeat() 호출 (일반 목록 화면용)
                    SeatClass finalSeat = null;
                    boolean isSeatClassPreSet = loc.getSeatClass() != null; // SeatClass가 이미 설정되어 있는지 확인
                    
                    if (isSeatClassPreSet) {
                        // 🔧 리스트용/달력용: 이미 설정된 SeatClass 사용
                        // ProductController에서 모든 SeatClass 조합을 생성한 경우
                        // 또는 달력 페이지에서 특정 SeatClass를 설정한 경우
                        finalSeat = loc.getSeatClass();
                    } else {
                        // 🔧 일반 목록 화면용: 가장 저렴한 항공편 찾기
                        // 이 경우는 ProductController의 findMultipleProductsForDate()를 거치지 않은 경우
                        finalSeat = seatClassRepo.findLowestPriceSeat(
                                startDateTime,
                                endDateTime,
                                departAirport,
                                arriveAirport,
                                product.getSeatClassTypes());
                    }

                    // 🔧 항공권 없으면 해당 Location만 건너뛰고 계속 진행 (전체 Product를 null로 반환하지 않음)
                    if (finalSeat == null) {
                        log.warn("[Price Calculation] SeatClass not found for Location - Day: {}, Depart: {} -> Arrive: {}", 
                            sced.getDay(),
                            departAirport != null ? departAirport.getAirportCode() : "N/A",
                            arriveAirport != null ? arriveAirport.getAirportCode() : "N/A");
                        continue; // 해당 Location만 건너뛰고 다음 Location 처리
                    }

                    // 🔥 핵심 수정: 실제 계산에 사용된 SeatClass를 Location에 설정 (breakdown 일치를 위해)
                    // 이렇게 하면 ProductDetailDto의 breakdown이 실제 계산 가격과 일치함
                    loc.setSeatClass(finalSeat);

                    // 🔧 SeatClass가 이미 설정된 경우에는 추가 엔티티 변경하지 않음 (리스트용/달력용)
                    // SeatClass가 설정되지 않은 경우에만 추가 엔티티 변경 (일반 목록 화면용)
                    if (!isSeatClassPreSet) {
                        // 일반 목록 화면용: 엔티티에 항공편 정보 설정
                        if (product.getAirline() == null) {
                            product.setAirline(finalSeat.getAir().getAirline());
                        }

                        if (product.getDepartDateTime() == null) { // 첫 항공권 출발시간 (=출국시간)
                            product.setDepartDateTime(finalSeat.getAir().getDepartDateTime());
                        }

                        // 한 product에 대해 항공권 도착시간 계속 덮어씌움 => 마지막 항공권의 도착시간 (=귀국시간)
                        product.setReturnDateTime(finalSeat.getAir().getArriveDateTime());
                    }
                    // 🔧 SeatClass는 항상 설정되었으므로, 이제 가격 계산만 수행

                    // 항공권 잔여좌석 확인로직 -> 해당 날짜의 상품 예약자 수 확인로직으로 변경
                    // // 한 product에 대해 항공권 잔여좌석 낮은쪽 계속 덮어씌움 => 예약 가능인 수 저장
                    // if (product.getAvailableSeats() == null
                    // || product.getAvailableSeats() > finalSeat.getAvailableSeats()) {
                    // product.setAvailableSeats(finalSeat.getAvailableSeats());
                    // }

                    Long airPrice = finalSeat.getPriceAdult();
                    finalPriceAdult += airPrice;
                    totalAirPrice += airPrice;
                    finalPriceYouth += finalSeat.getPriceYouth();
                    finalPriceInfant += finalSeat.getPriceInfant();
                    
                    // 항공편 정보 저장
                    String airInfo = String.format("Day %d: %s -> %s, Price: %d KRW", 
                        sced.getDay(),
                        departAirport != null ? departAirport.getAirportCode() : "N/A",
                        arriveAirport != null ? arriveAirport.getAirportCode() : "N/A",
                        airPrice);
                    airDetails.add(airInfo);
                    
                    // 항공권 가격 로그
                    log.debug("[Price Breakdown] Air added - Day: {}, Depart: {} -> Arrive: {}, Price: {} KRW, Total Air: {} KRW", 
                        sced.getDay(),
                        departAirport != null ? departAirport.getAirportCode() : "N/A",
                        arriveAirport != null ? arriveAirport.getAirportCode() : "N/A",
                        airPrice,
                        totalAirPrice);

                } else if (type == LocationType.HOTEL) {
                    Long hotelPrice = loc.getHotel().getPrice();
                    finalPriceAdult += hotelPrice;
                    totalHotelPrice += hotelPrice;
                    finalPriceYouth += hotelPrice;
                    // 영유아는 호텔 포함 안함
                    
                    // 호텔 가격 로그
                    log.debug("[Price Breakdown] Hotel added - Day: {}, Hotel Price: {} KRW, Total Hotel: {} KRW", 
                        sced.getDay(),
                        hotelPrice,
                        totalHotelPrice);
                }
            }
        }

        // 가격 구성 로그 출력 (상세 디버깅용)
        log.info("========== [Price Breakdown Analysis] Product ID: {}, Title: {} ==========", 
            product.getId(), product.getTitle());
        log.info("[Price Breakdown] Tour Base Price: {} KRW", tourPrice);
        
        // 각 항공편 개별 가격 출력
        if (!airDetails.isEmpty()) {
            log.info("[Price Breakdown] Air Details ({} flight(s)):", airDetails.size());
            for (String airDetail : airDetails) {
                log.info("  - {}", airDetail);
            }
            log.info("[Price Breakdown] Total Air Price: {} KRW", totalAirPrice);
        } else {
            log.warn("[Price Breakdown] ⚠️ 항공편이 없습니다! Total Air Price: {} KRW", totalAirPrice);
        }
        
        log.info("[Price Breakdown] Total Hotel Price: {} KRW", totalHotelPrice);
        Long calculatedSum = tourPrice + totalAirPrice + totalHotelPrice;
        log.info("[Price Breakdown] Final Price (Before Discount): {} KRW (Tour {} + Air {} + Hotel {} = {})", 
            finalPriceAdult, tourPrice, totalAirPrice, totalHotelPrice, calculatedSum);
        
        // 🔧 가격 불일치 경고
        if (!Objects.equals(finalPriceAdult, calculatedSum)) {
            log.warn("[Price Breakdown] ⚠️ 가격 불일치! FinalPriceAdult: {} != 계산합계: {} (차이: {})", 
                finalPriceAdult, calculatedSum, finalPriceAdult - calculatedSum);
        }
        
        product.setFinalPriceAdult(finalPriceAdult);
        product.setFinalPriceYouth(finalPriceYouth);
        product.setFinalPriceInfant(finalPriceInfant);

        // 항공권 잔여좌석 확인로직 -> 해당 날짜의 상품 예약자 수 확인로직으로 변경
        Long reserved = 0L;
        
        // 해당 항공편의 출발 시간을 기준으로 예약 필터링
        // product.getDepartDateTime()에는 해당 항공편의 정확한 출발 시간이 저장되어 있음
        LocalDateTime flightDepartTime = product.getDepartDateTime();
        LocalDateTime departDateStart;
        LocalDateTime departDateEnd;
        
        if (flightDepartTime != null) {
            // 해당 항공편의 출발 시간을 기준으로 ±2시간 범위로 필터링
            // (PurchaseProduct.departDateTime이 날짜만 저장되므로, 같은 날짜의 모든 예약을 가져온 후 시간으로 필터링)
            departDateStart = departDate.atStartOfDay();
            departDateEnd = departDate.plusDays(1).atStartOfDay();
        } else {
            // 출발 시간이 없는 경우 날짜만으로 조회
            departDateStart = departDate.atStartOfDay();
            departDateEnd = departDate.plusDays(1).atStartOfDay();
        }
        
        List<PurchaseProduct> purchaseProducts = purchaseProductRepo.findByProductAndDepartDate(product, departDateStart, departDateEnd);
        
        for (PurchaseProduct pp : purchaseProducts) {
            
            // 해당 항공편과 정확히 일치하는 예약만 카운트
            // 같은 항공사여도 출발 시간이 다르면 다른 항공편이므로 구분해야 함
            // PurchaseProduct의 finalSeatClasses에서 첫 번째 출국 항공편의 airId를 사용하여 정확히 구분
            boolean matchesFlight = true;
            
            // 현재 계산 중인 항공편의 Air ID 찾기
            Long currentAirId = null;
            if (product.getTour() != null && product.getTour().getSchedules() != null) {
                for (Schedule schedule : product.getTour().getSchedules()) {
                    if (schedule != null && Objects.equals(schedule.getDay(), 0L) && schedule.getLocations() != null) {
                        for (Location location : schedule.getLocations()) {
                            if (location != null && location.getLocationType() == LocationType.AIR 
                                && location.getSeatClass() != null && location.getSeatClass().getAir() != null) {
                                currentAirId = location.getSeatClass().getAir().getId();
                                break;
                            }
                        }
                        if (currentAirId != null) break;
                    }
                }
            }
            
            // PurchaseProduct의 finalSeatClasses에서 현재 계산 중인 항공편과 일치하는 airId 찾기
            Long purchaseAirId = null;
            if (pp.getFinalSeatClasses() != null && !pp.getFinalSeatClasses().isEmpty()) {
                // currentAirId가 있으면 정확히 일치하는 항공편 찾기
                if (currentAirId != null) {
                    for (renewal.common.entity.PurchaseBase.ConfirmedSeatClass confirmedSeat : pp.getFinalSeatClasses()) {
                        if (confirmedSeat != null && confirmedSeat.getAirId() != null 
                            && confirmedSeat.getAirId().equals(currentAirId)) {
                            purchaseAirId = confirmedSeat.getAirId();
                            break;
                        }
                    }
                }
                
                // currentAirId로 매칭 실패한 경우, 출국 항공편(ICN) 중 첫 번째 사용 (하위 호환성)
                if (purchaseAirId == null) {
                    for (renewal.common.entity.PurchaseBase.ConfirmedSeatClass confirmedSeat : pp.getFinalSeatClasses()) {
                        if (confirmedSeat != null && confirmedSeat.getDepartAirport() != null 
                            && confirmedSeat.getDepartAirport().equals("ICN")) {
                            purchaseAirId = confirmedSeat.getAirId();
                            break;
                        }
                    }
                }
                
                // 여전히 찾지 못한 경우 첫 번째 항공편 사용 (최후의 수단)
                if (purchaseAirId == null && !pp.getFinalSeatClasses().isEmpty()) {
                    purchaseAirId = pp.getFinalSeatClasses().get(0).getAirId();
                }
            }
            
            if (currentAirId != null && purchaseAirId != null) {
                // Air ID로 정확히 매칭
                matchesFlight = currentAirId.equals(purchaseAirId);
            } else if (flightDepartTime != null && pp.getDepartDateTime() != null) {
                // Air ID가 없는 경우 출발 시간으로 매칭 (하위 호환성)
                LocalDateTime ppDepartTime = pp.getDepartDateTime();
                boolean sameDate = ppDepartTime.toLocalDate().equals(flightDepartTime.toLocalDate());
                boolean isMidnight = ppDepartTime.getHour() == 0 && ppDepartTime.getMinute() == 0;
                
                if (isMidnight) {
                    // 기존 데이터: 날짜만 저장된 경우
                    if (product.getAirline() != null && pp.getAirline() != null) {
                        matchesFlight = sameDate && product.getAirline().getCode().equals(pp.getAirline().getCode());
                    } else {
                        matchesFlight = sameDate;
                    }
                } else {
                    // 정확한 출발 시간이 저장된 경우: ±30분 이내만 매칭
                    LocalDateTime flightTimeStart = flightDepartTime.minusMinutes(30);
                    LocalDateTime flightTimeEnd = flightDepartTime.plusMinutes(30);
                    boolean timeMatch = (ppDepartTime.isAfter(flightTimeStart) || ppDepartTime.isEqual(flightTimeStart))
                                     && (ppDepartTime.isBefore(flightTimeEnd) || ppDepartTime.isEqual(flightTimeEnd));
                    matchesFlight = sameDate && timeMatch;
                }
            } else {
                // 모든 정보가 없는 경우 날짜만으로 매칭 (최후의 수단)
                matchesFlight = true;
            }
            
            // 결제 완료된 예약만 카운트 (취소되지 않고, 대기 상태가 아니며, 결제 완료된 경우)
            boolean isPaid = pp.getPurchaseStatus() == PurchaseStatus.PAID 
                    || (pp.getIsTransactionComplete() != null && pp.getIsTransactionComplete());
            
            // 환불 승인/완료된 경우만 좌석을 반환 (REQUESTED는 아직 좌석을 차지함)
            boolean refundApprovedOrCompleted = false;
            Refund refund = refundRepo.findByPurchaseIdAndRefundType(pp.getId(), Refund.RefundType.PRODUCT)
                    .orElse(null);
            if (refund != null && (refund.getStatus() == Refund.RefundStatus.APPROVED 
                    || refund.getStatus() == Refund.RefundStatus.COMPLETED)) {
                refundApprovedOrCompleted = true;
            }
            
            // 좌석 카운트 로직:
            // 1. PAID 상태인 예약은 항상 카운트 (waiting 여부와 관계없이)
            // 2. waiting=true인 예약도 좌석을 차지하는 것으로 간주하여 카운트
            // 3. 취소되지 않고, 환불 승인/완료되지 않았고, 해당 항공편과 일치하는 경우만 카운트
            boolean shouldCount = matchesFlight 
                    && pp.getPurchaseStatus() != PurchaseStatus.CANCELLED 
                    && !refundApprovedOrCompleted
                    && (isPaid || pp.isWaiting()); // PAID이거나 waiting인 경우 카운트
            
            if (shouldCount) {
                reserved += pp.getAdultCount();
                reserved += pp.getYouthCount();
                // reserved += pp.getInfantCount(); // 영유아는 인원수 카운트 안함
            }

            // waiting인 주문이 하나라도 있으면 예약대기 상품임
            if (pp.isWaiting()) {
                product.setProductStatus(ProductStatus.WAITING);
            }
        }
        
        Long maxCapacity = product.getTour().getMaxCapacity();
        product.setReservedSeats(reserved);
        Long calculatedAvailableSeats = maxCapacity - reserved;
        // 예약대기 예약도 카운트에 포함하므로 음수가 될 수 있음. 최소값은 0으로 설정
        product.setAvailableSeats(Math.max(0, calculatedAvailableSeats));

        // 타임딜 해당 상품인경우 할인가격 계산
        TimeDeal timeDeal = product.getTimeDeal();
        if (timeDeal != null && timeDeal.isActive()) {

            // 기존 가격 저장
            timeDeal.setOriginalPriceAdult(finalPriceAdult);
            timeDeal.setOriginalPriceYouth(finalPriceYouth);
            timeDeal.setOriginalPriceInfant(finalPriceInfant);

            Long beforeDiscount = finalPriceAdult;
            if (timeDeal.getDiscountType() == DiscountType.ABSOLUTE) {
                Long discountAmount = timeDeal.getValue();
                product.setFinalPriceAdult(finalPriceAdult - discountAmount);
                product.setFinalPriceYouth(finalPriceYouth - discountAmount);
                product.setFinalPriceInfant(finalPriceInfant - discountAmount);
                log.info("[Price Breakdown] TimeDeal Discount Applied (Absolute) - Original: {} KRW, Discount: {} KRW, Final: {} KRW", 
                    beforeDiscount, discountAmount, product.getFinalPriceAdult());
            } else {
                Long discountPercent = timeDeal.getValue();
                Long afterDiscount = finalPriceAdult * (100 - discountPercent) / 100;
                product.setFinalPriceAdult(afterDiscount);
                product.setFinalPriceYouth(finalPriceYouth * (100 - discountPercent) / 100);
                product.setFinalPriceInfant(finalPriceInfant * (100 - discountPercent) / 100);
                log.info("[Price Breakdown] TimeDeal Discount Applied (Percentage) - Original: {} KRW, Discount: {}%, Final: {} KRW", 
                    beforeDiscount, discountPercent, product.getFinalPriceAdult());
            }
        } else {
            log.info("[Price Breakdown] No TimeDeal - Final Price: {} KRW", finalPriceAdult);
        }
        
        log.info("========== [Price Breakdown Analysis Complete] Final Price: {} KRW ==========", product.getFinalPriceAdult());

        return product;
    }

    @Transactional
    public void requestRefund(Long id, Long amount, String reason) {
        PurchaseProduct purchaseProduct = purchaseProductRepo.findById(id).orElseThrow();
        
        // 이미 환불 요청이 있는지 확인
        Refund existingRefund = refundRepo.findByPurchaseIdAndRefundType(id, Refund.RefundType.PRODUCT)
                .orElse(null);
        
        if (existingRefund != null && existingRefund.getStatus() == Refund.RefundStatus.REQUESTED) {
            throw new IllegalStateException("이미 환불 요청이 진행 중입니다.");
        }
        
        // 환불 객체 생성 (REQUESTED 상태)
        Refund refund = new Refund(purchaseProduct, amount, reason);
        refundRepo.save(refund);
    }

    /**
     * 패키지(출국+귀국 2구간)에 대한 좌석 홀드 조회.
     * finalSeatClasses가 2개 미만이거나 SeatClass를 찾을 수 없으면 empty.
     */
    public Optional<PackageSeatHold> findPackageSeatHold(PurchaseProduct purchaseProduct) {
        if (purchaseProduct == null || purchaseProduct.getProduct() == null || purchaseProduct.getDepartDateTime() == null) {
            return Optional.empty();
        }
        List<ConfirmedSeatClass> list = purchaseProduct.getFinalSeatClasses();
        if (list == null || list.size() < 2) {
            return Optional.empty();
        }
        ConfirmedSeatClass out = list.get(0);
        ConfirmedSeatClass ret = list.get(1);
        if (out.getAirId() == null || out.getClassType() == null || ret.getAirId() == null || ret.getClassType() == null) {
            return Optional.empty();
        }
        Long outboundId = seatClassRepo.findByAirIdAndClassType(out.getAirId(), out.getClassType()).map(SeatClass::getId).orElse(null);
        Long returnId = seatClassRepo.findByAirIdAndClassType(ret.getAirId(), ret.getClassType()).map(SeatClass::getId).orElse(null);
        if (outboundId == null || returnId == null) {
            return Optional.empty();
        }
        return packageSeatHoldRepo.findByProductIdAndDepartDateAndOutboundSeatClassIdAndReturnSeatClassId(
            purchaseProduct.getProduct().getId(),
            purchaseProduct.getDepartDateTime().toLocalDate(),
            outboundId,
            returnId);
    }

    /**
     * 해당 (상품, 출발일, 출국/귀국 SeatClass)에 홀드가 존재하고 풀 여유(totalHeld - allocated > 0)가 있는지 여부.
     * 상품 노출 조건: 홀드에 풀 여유가 있으면 availableSeats 체크 없이 노출 가능.
     */
    public boolean hasHoldWithPoolSpace(Long productId, LocalDate departDate, Long outboundSeatClassId, Long returnSeatClassId) {
        if (productId == null || departDate == null || outboundSeatClassId == null || returnSeatClassId == null) {
            return false;
        }
        return packageSeatHoldRepo.findByProductIdAndDepartDateAndOutboundSeatClassIdAndReturnSeatClassId(
                productId, departDate, outboundSeatClassId, returnSeatClassId)
            .map(h -> (h.getTotalHeld() != null ? h.getTotalHeld() : 0L) - (h.getAllocated() != null ? h.getAllocated() : 0L) > 0)
            .orElse(false);
    }

    /**
     * 첫 예약 시 해당 (상품, 출발일, 출국/귀국 SeatClass)에 대해 홀드가 없으면 생성하고
     * Tour.maxCapacity만큼 출국/귀국 SeatClass에 reserveSeats 호출.
     */
    @Transactional
    public void ensurePackageSeatHold(PurchaseProduct purchaseProduct) {
        if (purchaseProduct == null || purchaseProduct.getProduct() == null || purchaseProduct.getDepartDateTime() == null) {
            return;
        }
        Tour tour = purchaseProduct.getProduct().getTour();
        if (tour == null) {
            return;
        }
        List<ConfirmedSeatClass> list = purchaseProduct.getFinalSeatClasses();
        if (list == null || list.size() < 2) {
            return;
        }
        ConfirmedSeatClass out = list.get(0);
        ConfirmedSeatClass ret = list.get(1);
        if (out.getAirId() == null || out.getClassType() == null || ret.getAirId() == null || ret.getClassType() == null) {
            return;
        }
        SeatClass outboundSc = seatClassRepo.findByAirIdAndClassType(out.getAirId(), out.getClassType()).orElse(null);
        SeatClass returnSc = seatClassRepo.findByAirIdAndClassType(ret.getAirId(), ret.getClassType()).orElse(null);
        if (outboundSc == null || returnSc == null) {
            return;
        }
        Long productId = purchaseProduct.getProduct().getId();
        LocalDate departDate = purchaseProduct.getDepartDateTime().toLocalDate();
        Long outboundId = outboundSc.getId();
        Long returnId = returnSc.getId();
        Optional<PackageSeatHold> existing = packageSeatHoldRepo.findByProductIdAndDepartDateAndOutboundSeatClassIdAndReturnSeatClassId(
            productId, departDate, outboundId, returnId);
        if (existing.isPresent()) {
            return;
        }
        Long maxCapacity = tour.getMaxCapacity() != null ? tour.getMaxCapacity() : 0L;
        if (maxCapacity <= 0) {
            return;
        }
        PackageSeatHold hold = new PackageSeatHold(productId, departDate, outboundId, returnId, maxCapacity);
        packageSeatHoldRepo.save(hold);
        try {
            outboundSc.reserveSeats(maxCapacity);
            seatClassRepo.save(outboundSc);
            returnSc.reserveSeats(maxCapacity);
            seatClassRepo.save(returnSc);
        } catch (Exception e) {
            log.warn("패키지 좌석 홀드 확보 실패 productId={} departDate={}: {}", productId, departDate, e.getMessage());
            throw new RuntimeException("패키지 좌석 홀드 확보 실패: " + e.getMessage(), e);
        }
    }

    /**
     * 결제 확정 시 패키지 홀드가 있으면 해당 인원만 allocated에 더함 (SeatClass는 홀드 시 이미 차감됨).
     */
    @Transactional
    public void allocatePackageSeatHold(PurchaseProduct purchaseProduct) {
        Optional<PackageSeatHold> holdOpt = findPackageSeatHold(purchaseProduct);
        if (holdOpt.isEmpty()) {
            return;
        }
        long n = (purchaseProduct.getAdultCount() != null ? purchaseProduct.getAdultCount() : 0L)
                + (purchaseProduct.getYouthCount() != null ? purchaseProduct.getYouthCount() : 0L);
        if (n <= 0) {
            return;
        }
        PackageSeatHold hold = holdOpt.get();
        hold.setAllocated((hold.getAllocated() != null ? hold.getAllocated() : 0L) + n);
        packageSeatHoldRepo.save(hold);
    }

    /**
     * 특정 출발일의 미사용 홀드분을 두 SeatClass에 반납 (마감일 = 출발 N일 전).
     * 오늘 날짜 기준으로 출발일이 (today + N)인 홀드에 대해 (total_held - allocated)만큼 cancelSeats 후 released_at 설정.
     */
    @Transactional
    public void releaseUnusedSeatsForDepartureDate(LocalDate departDate) {
        List<PackageSeatHold> holds = packageSeatHoldRepo.findByReleasedAtIsNullAndDepartDate(departDate);
        for (PackageSeatHold hold : holds) {
            releaseSingleHold(hold);
        }
    }

    /**
     * 특정 (상품, 출발일)에 대한 미사용 홀드분 반납. 상품별 cutoffDays 기준 마감일 반납 시 사용.
     */
    @Transactional
    public void releaseUnusedSeatsForProductAndDepartDate(Long productId, LocalDate departDate) {
        if (productId == null || departDate == null) {
            return;
        }
        List<PackageSeatHold> holds = packageSeatHoldRepo.findByReleasedAtIsNullAndProductIdAndDepartDate(productId, departDate);
        for (PackageSeatHold hold : holds) {
            releaseSingleHold(hold);
        }
    }

    private void releaseSingleHold(PackageSeatHold hold) {
        long toRelease = (hold.getTotalHeld() != null ? hold.getTotalHeld() : 0L) - (hold.getAllocated() != null ? hold.getAllocated() : 0L);
        if (toRelease <= 0) {
            hold.setReleasedAt(LocalDateTime.now());
            packageSeatHoldRepo.save(hold);
            return;
        }
        seatClassRepo.findById(hold.getOutboundSeatClassId()).ifPresent(sc -> {
            try {
                sc.cancelSeats(toRelease);
                seatClassRepo.save(sc);
            } catch (Exception e) {
                log.warn("홀드 반납 실패 outbound holdId={}: {}", hold.getId(), e.getMessage());
            }
        });
        seatClassRepo.findById(hold.getReturnSeatClassId()).ifPresent(sc -> {
            try {
                sc.cancelSeats(toRelease);
                seatClassRepo.save(sc);
            } catch (Exception e) {
                log.warn("홀드 반납 실패 return holdId={}: {}", hold.getId(), e.getMessage());
            }
        });
        hold.setReleasedAt(LocalDateTime.now());
        packageSeatHoldRepo.save(hold);
    }

    /**
     * 상품별 cutoffDays 기준 "오늘 마감"인 출발에 대해, PAID 예약 인원이 minCapacity 미만이면
     * 해당 출발의 모든 예약(PAID/RESERVED 등)을 기존 취소 플로우로 취소 (최소인원 미달로 출발 취소).
     */
    @Transactional
    public void checkAndCancelDeparturesBelowMinCapacity() {
        LocalDate today = LocalDate.now();
        List<Product> products = productRepo.findByCutoffDaysIsNotNull();
        for (Product product : products) {
            if (product.getCutoffDays() == null) {
                continue;
            }
            Tour tour = product.getTour();
            if (tour == null || tour.getMinCapacity() == null) {
                continue;
            }
            long minCapacity = tour.getMinCapacity();
            LocalDate departDate = today.plusDays(product.getCutoffDays());
            LocalDateTime start = departDate.atStartOfDay();
            LocalDateTime end = departDate.plusDays(1).atStartOfDay();
            List<PurchaseProduct> list = purchaseProductRepo.findByProductAndDepartDate(product, start, end);
            long paidCount = list.stream()
                .filter(pp -> pp.getPurchaseStatus() == PurchaseStatus.PAID)
                .mapToLong(pp -> (pp.getAdultCount() != null ? pp.getAdultCount() : 0L) + (pp.getYouthCount() != null ? pp.getYouthCount() : 0L))
                .sum();
            if (paidCount < minCapacity) {
                for (PurchaseProduct pp : list) {
                    if (pp.getPurchaseStatus() != PurchaseStatus.CANCELLED) {
                        try {
                            cancelPurchase(pp.getId());
                            log.info("최소인원 미달 취소: productId={}, departDate={}, purchaseProductId={}", product.getId(), departDate, pp.getId());
                        } catch (Exception e) {
                            log.warn("최소인원 미달 취소 실패 purchaseProductId={}: {}", pp.getId(), e.getMessage(), e);
                        }
                    }
                }
            }
        }
    }

    @Transactional
    public void cancelPurchase(Long id) {

        PurchaseProduct purchaseProduct = purchaseProductRepo.findById(id).get();
        
        // 취소할 예약의 좌석 수 계산 (waiting이 아닌 경우에만 좌석 반환)
        Long cancelledSeats = 0L;
        if (!purchaseProduct.isWaiting() && purchaseProduct.getPurchaseStatus() != PurchaseStatus.CANCELLED) {
            cancelledSeats = purchaseProduct.getAdultCount() + purchaseProduct.getYouthCount();
        }
        
        purchaseProduct.setPurchaseStatus(PurchaseStatus.CANCELLED);
        purchaseProduct.setWaiting(false);
        purchaseProductRepo.save(purchaseProduct);

        // 패키지 홀드가 있으면 allocated만 감소 (좌석은 홀드 풀에 반환, SeatClass 복원 안 함)
        Optional<PackageSeatHold> holdOpt = findPackageSeatHold(purchaseProduct);
        PackageSeatHold cancelledHold = null; // 대기열 처리 시 같은 조합 필터·가용석 계산용
        if (holdOpt.isPresent() && cancelledSeats > 0) {
            PackageSeatHold hold = holdOpt.get();
            hold.setAllocated((hold.getAllocated() != null ? hold.getAllocated() : 0L) - cancelledSeats);
            if (hold.getAllocated() < 0) {
                hold.setAllocated(0L);
            }
            packageSeatHoldRepo.save(hold);
            cancelledHold = hold;
        } else {
            // 홀드 없음: 패키지 취소 시 출국/귀국 좌석 복원 (동일 인원 N만큼 각 구간에 반환)
            List<ConfirmedSeatClass> finalSeatClasses = purchaseProduct.getFinalSeatClasses();
            if (finalSeatClasses != null && !finalSeatClasses.isEmpty() && cancelledSeats > 0) {
                for (ConfirmedSeatClass c : finalSeatClasses) {
                if (c.getAirId() == null || c.getClassType() == null) {
                    continue;
                }
                long n = (c.getSeatCountAdult() != null ? c.getSeatCountAdult() : 0L)
                        + (c.getSeatCountYouth() != null ? c.getSeatCountYouth() : 0L);
                if (n <= 0) {
                    continue;
                }
                seatClassRepo.findByAirIdAndClassType(c.getAirId(), c.getClassType())
                        .ifPresent(seatClass -> {
                            try {
                                seatClass.cancelSeats(n);
                                seatClassRepo.save(seatClass);
                            } catch (Exception e) {
                                log.warn("좌석 복원 실패 airId={} classType={}: {}", c.getAirId(), c.getClassType(), e.getMessage());
                            }
                        });
                }
            }
        }

        // 취소된 좌석이 없으면 대기 예약 처리 불필요
        if (cancelledSeats == 0) {
            return;
        }

        LocalDate cancelDepartDate = purchaseProduct.getDepartDateTime().toLocalDate();
        LocalDateTime cancelDepartDateStart = cancelDepartDate.atStartOfDay();
        LocalDateTime cancelDepartDateEnd = cancelDepartDate.plusDays(1).atStartOfDay();

        List<PurchaseProduct> candidate = purchaseProductRepo
                .findByProductAndDepartDate(
                        purchaseProduct.getProduct(),
                        cancelDepartDateStart,
                        cancelDepartDateEnd);

        // 같은 항공 조합(같은 홀드) 대기자만 순서대로 확정. 홀드가 있으면 해당 홀드 가용석만 사용.
        Long availableSeats;
        List<PurchaseProduct> toProcess;
        if (cancelledHold != null) {
            availableSeats = (cancelledHold.getTotalHeld() != null ? cancelledHold.getTotalHeld() : 0L)
                    - (cancelledHold.getAllocated() != null ? cancelledHold.getAllocated() : 0L);
            Long outId = cancelledHold.getOutboundSeatClassId();
            Long retId = cancelledHold.getReturnSeatClassId();
            toProcess = candidate.stream()
                    .filter(pp -> pp.getPurchaseStatus() != PurchaseStatus.CANCELLED && pp.isWaiting())
                    .filter(pp -> sameHoldCombo(pp, purchaseProduct.getProduct().getId(), cancelDepartDate, outId, retId))
                    .sorted((a, b) -> {
                        if (a.getPurchaseDate() == null && b.getPurchaseDate() == null) return 0;
                        if (a.getPurchaseDate() == null) return 1;
                        if (b.getPurchaseDate() == null) return -1;
                        return a.getPurchaseDate().compareTo(b.getPurchaseDate());
                    })
                    .toList();
        } else {
            // 홀드 없음(레거시): 동일 (product, departDate) 전체 기준으로 가용석 계산 후 대기열 처리
            Product product = productRepo.findById(purchaseProduct.getProduct().getId()).get();
            Product calcedProduct = calcSingleProduct(product, purchaseProduct.getDepartDateTime().toLocalDate());
            availableSeats = calcedProduct.getAvailableSeats() + cancelledSeats;
            toProcess = candidate.stream()
                    .filter(pp -> pp.getPurchaseStatus() != PurchaseStatus.CANCELLED && pp.isWaiting())
                    .sorted((a, b) -> {
                        if (a.getPurchaseDate() == null && b.getPurchaseDate() == null) return 0;
                        if (a.getPurchaseDate() == null) return 1;
                        if (b.getPurchaseDate() == null) return -1;
                        return a.getPurchaseDate().compareTo(b.getPurchaseDate());
                    })
                    .toList();
        }

        for (PurchaseProduct candidatePP : toProcess) {
            Long totalRequiredSeats = (candidatePP.getAdultCount() != null ? candidatePP.getAdultCount() : 0L)
                    + (candidatePP.getYouthCount() != null ? candidatePP.getYouthCount() : 0L);
            if (totalRequiredSeats <= availableSeats) {
                availableSeats -= totalRequiredSeats;
                candidatePP.setWaiting(false);
                purchaseProductRepo.save(candidatePP);

                if (candidatePP.getWaiterEmail() != null) {
                    emailService.sendWaitMail(candidatePP.getWaiterEmail(), candidatePP.getTitle());
                } else if (candidatePP.getWaiterNumber() != null) {
                    log.info("예약대기 확정 알림(문자) 대상: purchaseId={}, number={}", candidatePP.getId(), candidatePP.getWaiterNumber());
                }
            } else {
                break;
            }
        }

        // 홀드 없을 때만 Product 엔티티 가용석·상태 갱신 (홀드 있을 때는 홀드 단위로만 관리)
        if (cancelledHold == null) {
            Product product = productRepo.findById(purchaseProduct.getProduct().getId()).get();
            Product calcedProduct = calcSingleProduct(product, purchaseProduct.getDepartDateTime().toLocalDate());
            calcedProduct.setAvailableSeats(availableSeats);
            calcedProduct.UpdateProductStatus();
            productRepo.save(calcedProduct);
        }

        return;
    }

    /** 동일 (productId, departDate, outboundSeatClassId, returnSeatClassId) 조합인지 여부 */
    private boolean sameHoldCombo(PurchaseProduct pp, Long productId, LocalDate departDate, Long outboundSeatClassId, Long returnSeatClassId) {
        Optional<PackageSeatHold> opt = findPackageSeatHold(pp);
        if (opt.isEmpty()) {
            return false;
        }
        PackageSeatHold h = opt.get();
        return Objects.equals(h.getProductId(), productId)
                && Objects.equals(h.getDepartDate(), departDate)
                && Objects.equals(h.getOutboundSeatClassId(), outboundSeatClassId)
                && Objects.equals(h.getReturnSeatClassId(), returnSeatClassId);
    }
}

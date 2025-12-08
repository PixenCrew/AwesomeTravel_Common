package renewal.common.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import renewal.common.entity.AirportCode;
import renewal.common.entity.Location;
import renewal.common.entity.Location.LocationType;
import renewal.common.entity.Product;
import renewal.common.entity.Product.DepartTimeType;
import renewal.common.entity.Product.ProductStatus;
import renewal.common.entity.PurchaseBase.PurchaseStatus;
import renewal.common.entity.PurchaseProduct;
import renewal.common.entity.Refund;
import renewal.common.entity.Schedule;
import renewal.common.entity.SeatClass;
import renewal.common.entity.TimeDeal;
import renewal.common.entity.TimeDeal.DiscountType;
import renewal.common.entity.Tour;
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
    private final EmailService emailService;


    public Product calcSingleProduct(Product product, LocalDate departDate) {
        Hibernate.initialize(product.getTour());
        Tour tour = product.getTour();

        // 초기 가격 계산
        Long finalPriceAdult = tour.getPriceAdult();
        Long finalPriceYouth = tour.getPriceYouth();
        Long finalPriceInfant = tour.getPriceInfant();

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
                    DepartTimeType dtt = product.getDepartTimeType();
                    int startHour = sced.getDay() == 0 ? dtt.getStartHour() : 0;
                    int endHour = sced.getDay() == 0 ? dtt.getEndHour() : 23;

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
                    // 첫 번째 날(day 0)이고 특정 SeatClass가 지정된 경우 해당 SeatClass 사용
                    SeatClass finalSeat = null;
                    if (sced.getDay() == 0 && loc.getSeatClass() != null) {
                        // 특정 SeatClass가 이미 지정된 경우 (여러 항공편 처리용)
                        finalSeat = loc.getSeatClass();
                    } else {
                        // 일반적인 경우: 가장 저렴한 항공편 찾기
                        finalSeat = seatClassRepo.findLowestPriceSeat(
                                startDateTime,
                                endDateTime,
                                departAirport,
                                arriveAirport,
                                product.getSeatClassTypes());
                    }

                    // 항공권 없으면 null 반환
                    if (finalSeat == null) {
                        return null;
                    }

                    if (product.getAirline() == null) {
                        product.setAirline(finalSeat.getAir().getAirline());
                    }

                    loc.setSeatClass(finalSeat);

                    if (product.getDepartDateTime() == null) { // 첫 항공권 출발시간 (=출국시간)
                        product.setDepartDateTime(finalSeat.getAir().getDepartDateTime());
                    }

                    // 한 product에 대해 항공권 도착시간 계속 덮어씌움 => 마지막 항공권의 도착시간 (=귀국시간)
                    product.setReturnDateTime(finalSeat.getAir().getArriveDateTime());

                    // 항공권 잔여좌석 확인로직 -> 해당 날짜의 상품 예약자 수 확인로직으로 변경
                    // // 한 product에 대해 항공권 잔여좌석 낮은쪽 계속 덮어씌움 => 예약 가능인 수 저장
                    // if (product.getAvailableSeats() == null
                    // || product.getAvailableSeats() > finalSeat.getAvailableSeats()) {
                    // product.setAvailableSeats(finalSeat.getAvailableSeats());
                    // }

                    finalPriceAdult += finalSeat.getPriceAdult();
                    finalPriceYouth += finalSeat.getPriceYouth();
                    finalPriceInfant += finalSeat.getPriceInfant();

                } else if (type == LocationType.HOTEL) {
                    finalPriceAdult += loc.getHotel().getPrice();
                    finalPriceYouth += loc.getHotel().getPrice();
                    // 영유아는 호텔 포함 안함
                }
            }
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
                    if (schedule != null && schedule.getDay() == 0 && schedule.getLocations() != null) {
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

            if (timeDeal.getDiscountType() == DiscountType.ABSOLUTE) {
                product.setFinalPriceAdult(finalPriceAdult - timeDeal.getValue());
                product.setFinalPriceYouth(finalPriceYouth - timeDeal.getValue());
                product.setFinalPriceInfant(finalPriceInfant - timeDeal.getValue());
            } else {
                product.setFinalPriceAdult(finalPriceAdult * (100 - timeDeal.getValue()) / 100);
                product.setFinalPriceYouth(finalPriceYouth * (100 - timeDeal.getValue()) / 100);
                product.setFinalPriceInfant(finalPriceInfant * (100 - timeDeal.getValue()) / 100);
            }
        }

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

        // 취소된 좌석이 없으면 대기 예약 처리 불필요
        if (cancelledSeats == 0) {
            return;
        }

        // 취소된 예약의 출발 날짜 범위로 조회
        LocalDate cancelDepartDate = purchaseProduct.getDepartDateTime().toLocalDate();
        LocalDateTime cancelDepartDateStart = cancelDepartDate.atStartOfDay();
        LocalDateTime cancelDepartDateEnd = cancelDepartDate.plusDays(1).atStartOfDay();
        
        List<PurchaseProduct> candidate = purchaseProductRepo
                .findByProductAndDepartDate(
                        purchaseProduct.getProduct(),
                        cancelDepartDateStart,
                        cancelDepartDateEnd);

        Product product = productRepo.findById(purchaseProduct.getProduct().getId()).get();
        Product calcedProduct = calcSingleProduct(product, purchaseProduct.getDepartDateTime().toLocalDate());

        // 취소된 좌석을 반환
        Long availableSeats = calcedProduct.getAvailableSeats() + cancelledSeats;

        // 대기 중인 예약들을 순서대로 처리 (purchaseDate 순서대로 정렬 - 먼저 생성된 순서)
        candidate.sort((a, b) -> {
            if (a.getPurchaseDate() == null && b.getPurchaseDate() == null) return 0;
            if (a.getPurchaseDate() == null) return 1;
            if (b.getPurchaseDate() == null) return -1;
            return a.getPurchaseDate().compareTo(b.getPurchaseDate());
        });

        for (PurchaseProduct candidatePP : candidate) {
            Long totalRequiredSeats = candidatePP.getAdultCount() + candidatePP.getYouthCount();
            if (candidatePP.isWaiting() && candidatePP.getPurchaseStatus() != PurchaseStatus.CANCELLED) {
                if (totalRequiredSeats <= availableSeats) {
                    // 대기 예약을 확정 예약으로 변경
                    availableSeats -= totalRequiredSeats;
                    candidatePP.setWaiting(false);
                    purchaseProductRepo.save(candidatePP);

                    // candidatePP 해당 사용자 알람 보내기
                    if (candidatePP.getWaiterEmail() != null) {
                        emailService.sendWaitMail(candidatePP.getWaiterEmail(), candidatePP.getTitle());
                    } else if (candidatePP.getWaiterNumber() != null) {
                        System.out.println("=============== [TEST] 문자로 알림 보내는 기능 대체 ===================");
                        System.out.println("전화번호 : " + candidatePP.getWaiterNumber());
                        System.out.println("타이틀 : " + candidatePP.getTitle());
                        System.out.println("=============== [TEST] 문자로 알림 보내는 기능 대체 ===================");
                    }
                } else {
                    break; // 순번 건너뛰기 방지
                }
            }
        }

        // 최종 잔여 좌석 수를 상품에 반영
        calcedProduct.setAvailableSeats(availableSeats);
        calcedProduct.UpdateProductStatus();
        productRepo.save(calcedProduct);

        return;
    }
}

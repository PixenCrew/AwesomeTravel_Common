-- 테스트용 검색 상품 (2025-12 ~ 2026-01)
-- 각 INSERT 는 INSERT IGNORE 로 작성하여 기존 데이터와 충돌 시 무시됩니다.

-- 0. 테스트 전용 호텔
INSERT IGNORE INTO hotel
    (`is_active`, `id`, `max_room_count`, `price`, `address`, `city`,
     `description`, `email`, `name`, `number`, `website`, `hotel_type`)
VALUES
    (1, 19001, 65, 520000, '12 Rue de Rivoli, Paris', 'CDG',
     '루브르 인근 4성급 부티크 호텔', 'booking@mockparisstay.com', 'Mock Paris Stay',
     '02-0000-1234', 'https://mock-paris-stay.example.com', 'HOTEL'),
    (1, 19002, 80, 380000, '299 Chao Phraya Rd, Bangkok', 'BKK',
     '차오프라야 리버뷰 리조트', 'hello@mockbangkokresort.com', 'Mock Bangkok Riverside',
     '02-0000-5678', 'https://mock-bangkok-resort.example.com', 'RESORT');

-- 1. 투어 기본 정보
INSERT IGNORE INTO tour
    (id, company, name, country, max_capacity, min_capacity, start_date, end_date,
     price_adult, price_youth, price_infant, hotel_price_sum)
VALUES
    (9101, 'AwesomeTravel', '테스트 런던 겨울 5일', 'FR', 28, 10,
     '2026-01-10', '2026-01-25', 2250000, 1850000, 450000, 820000),
    (9102, 'AwesomeTravel', '테스트 방콕 연말 6일', 'TH', 32, 8,
     '2025-12-20', '2026-01-10', 1450000, 1150000, 350000, 460000);

-- 2. 일정(Schedule) 정의
-- 기존 데이터 업데이트 (day 값 수정)
UPDATE schedule SET day = 0 WHERE id = 19101;
UPDATE schedule SET day = 1 WHERE id = 19102;
UPDATE schedule SET day = 2 WHERE id = 19103;
UPDATE schedule SET day = 3 WHERE id = 19104;
UPDATE schedule SET day = 4 WHERE id = 19105;
UPDATE schedule SET day = 0 WHERE id = 19111;
UPDATE schedule SET day = 1 WHERE id = 19112;
UPDATE schedule SET day = 2 WHERE id = 19113;
UPDATE schedule SET day = 3 WHERE id = 19114;
UPDATE schedule SET day = 4 WHERE id = 19115;
UPDATE schedule SET day = 5 WHERE id = 19116;

-- 새 데이터 삽입 (없는 경우에만)
INSERT IGNORE INTO schedule (id, tour_id, day) VALUES
    -- 런던 패키지 5일 (1일차~5일차) - day는 0부터 시작
    (19101, 9101, 0),  -- 1일차: 출국
    (19102, 9101, 1),  -- 2일차: 현지 도착/호텔 체크인
    (19103, 9101, 2),  -- 3일차: 런던 시내 투어
    (19104, 9101, 3),  -- 4일차: 자유일정
    (19105, 9101, 4),  -- 5일차: 귀국
    -- 방콕 리조트 6일 (1일차~6일차) - day는 0부터 시작
    (19111, 9102, 0),  -- 1일차: 출국
    (19112, 9102, 1),  -- 2일차: 현지 도착/호텔 체크인
    (19113, 9102, 2),  -- 3일차: 방콕 시티투어
    (19114, 9102, 3),  -- 4일차: 자유일정
    (19115, 9102, 4),  -- 5일차: 자유일정
    (19116, 9102, 5);  -- 6일차: 귀국

-- 3. 일정별 Location (항공/호텔/포인트)
-- REPLACE INTO 사용: 기존 레코드가 있으면 삭제하고 새로 삽입
-- city_code는 외래키 제약 조건 때문에 NULL로 설정 (필요시 나중에 추가 가능)
REPLACE INTO location
    (locations_order, id, schedule_id, location_type, name, description,
     city_code, depart_airport, arrive_airport, hotel_id)
VALUES
    -- 런던 패키지 5일
    (1, 29101, 19101, 'AIR',   'ICN → CDG 출국',  '인천-파리 직항', NULL, 'ICN', 'CDG', NULL),
    (1, 29102, 19102, 'HOTEL', '파리 호텔 체크인', '시내 4성급 호텔', NULL, NULL, NULL, 19001),
    (1, 29103, 19103, 'POINT', '런던 시내 투어',   '버킹엄 궁·템즈강 전망', NULL, NULL, NULL, NULL),
    (1, 29104, 19104, 'POINT', '자유일정',        '자유롭게 런던 탐방', NULL, NULL, NULL, NULL),
    (1, 29105, 19105, 'AIR',   'CDG → ICN 귀국',  '파리-인천 직항', NULL, 'CDG', 'ICN', NULL),
    -- 방콕 리조트 6일
    (1, 29111, 19111, 'AIR',   'ICN → BKK 출국',  '인천-방콕 직항', NULL, 'ICN', 'BKK', NULL),
    (1, 29112, 19112, 'HOTEL', '리버 리조트 체크인', '리버뷰 룸 + 조식 포함', NULL, NULL, NULL, 19002),
    (1, 29113, 19113, 'POINT', '방콕 시티투어',    '차오프라야 크루즈 포함', NULL, NULL, NULL, NULL),
    (1, 29114, 19114, 'POINT', '자유일정',        '자유롭게 방콕 탐방', NULL, NULL, NULL, NULL),
    (1, 29115, 19115, 'POINT', '자유일정',        '자유롭게 방콕 탐방', NULL, NULL, NULL, NULL),
    (1, 29116, 19116, 'AIR',   'BKK → ICN 귀국',  '방콕-인천 직항', NULL, 'BKK', 'ICN', NULL);

-- 4. 항공편 & 좌석 (100일 범위 내 여러 날짜 추가)
INSERT IGNORE INTO air
    (id, flight_number, airline_code, depart_airport, depart_date_time,
     arrive_airport, arrive_date_time, stopovers, status, flight_type,
     created_at, created_by, modified_at, modified_by, flight_duration)
VALUES
    -- 런던 상품: 출국 (ICN → CDG) - 여러 날짜
    (41001, 'AT901', 'AF', 'ICN', '2026-01-15 09:10:00',
             'CDG', '2026-01-15 17:10:00', 0, 'ACTIVE', 'DIRECT',
             '2025-11-28 00:00:00', 'mock', '2025-11-28 00:00:00', 'mock', 480),
    (41005, 'AT903', 'AF', 'ICN', '2026-01-22 09:10:00',
             'CDG', '2026-01-22 17:10:00', 0, 'ACTIVE', 'DIRECT',
             '2025-11-28 00:00:00', 'mock', '2025-11-28 00:00:00', 'mock', 480),
    (41006, 'AT904', 'AF', 'ICN', '2026-01-29 09:10:00',
             'CDG', '2026-01-29 17:10:00', 0, 'ACTIVE', 'DIRECT',
             '2025-11-28 00:00:00', 'mock', '2025-11-28 00:00:00', 'mock', 480),
    -- 런던 상품: 귀국 (CDG → ICN) - 여러 날짜
    (41002, 'AT902', 'AF', 'CDG', '2026-01-19 12:20:00',
             'ICN', '2026-01-20 07:10:00', 0, 'ACTIVE', 'DIRECT',
             '2025-11-28 00:00:00', 'mock', '2025-11-28 00:00:00', 'mock', 550),
    (41007, 'AT905', 'AF', 'CDG', '2026-01-26 12:20:00',
             'ICN', '2026-01-27 07:10:00', 0, 'ACTIVE', 'DIRECT',
             '2025-11-28 00:00:00', 'mock', '2025-11-28 00:00:00', 'mock', 550),
    (41008, 'AT906', 'AF', 'CDG', '2026-02-02 12:20:00',
             'ICN', '2026-02-03 07:10:00', 0, 'ACTIVE', 'DIRECT',
             '2025-11-28 00:00:00', 'mock', '2025-11-28 00:00:00', 'mock', 550),
    -- 방콕 상품: 출국 (ICN → BKK) - 여러 날짜 (cutoff_days=15, 검색 범위: 15일~115일)
    -- 매일 항공편이 있도록 더 많은 날짜 추가
    (41003, 'AT911', 'SQ', 'ICN', '2025-12-15 08:40:00',
             'BKK', '2025-12-15 13:20:00', 0, 'ACTIVE', 'DIRECT',
             '2025-11-28 00:00:00', 'mock', '2025-11-28 00:00:00', 'mock', 280),
    (41017, 'AT921', 'SQ', 'ICN', '2025-12-16 08:40:00',
             'BKK', '2025-12-16 13:20:00', 0, 'ACTIVE', 'DIRECT',
             '2025-11-28 00:00:00', 'mock', '2025-11-28 00:00:00', 'mock', 280),
    (41018, 'AT922', 'SQ', 'ICN', '2025-12-17 08:40:00',
             'BKK', '2025-12-17 13:20:00', 0, 'ACTIVE', 'DIRECT',
             '2025-11-28 00:00:00', 'mock', '2025-11-28 00:00:00', 'mock', 280),
    (41019, 'AT923', 'SQ', 'ICN', '2025-12-18 08:40:00',
             'BKK', '2025-12-18 13:20:00', 0, 'ACTIVE', 'DIRECT',
             '2025-11-28 00:00:00', 'mock', '2025-11-28 00:00:00', 'mock', 280),
    (41020, 'AT924', 'SQ', 'ICN', '2025-12-19 08:40:00',
             'BKK', '2025-12-19 13:20:00', 0, 'ACTIVE', 'DIRECT',
             '2025-11-28 00:00:00', 'mock', '2025-11-28 00:00:00', 'mock', 280),
    (41021, 'AT925', 'SQ', 'ICN', '2025-12-20 08:40:00',
             'BKK', '2025-12-20 13:20:00', 0, 'ACTIVE', 'DIRECT',
             '2025-11-28 00:00:00', 'mock', '2025-11-28 00:00:00', 'mock', 280),
    (41009, 'AT912', 'SQ', 'ICN', '2025-12-22 08:40:00',
             'BKK', '2025-12-22 13:20:00', 0, 'ACTIVE', 'DIRECT',
             '2025-11-28 00:00:00', 'mock', '2025-11-28 00:00:00', 'mock', 280),
    (41028, 'AT932', 'SQ', 'ICN', '2025-12-22 14:20:00',
             'BKK', '2025-12-22 19:00:00', 0, 'ACTIVE', 'DIRECT',
             '2025-11-28 00:00:00', 'mock', '2025-11-28 00:00:00', 'mock', 280),
    (41010, 'AT913', 'SQ', 'ICN', '2025-12-30 08:40:00',
             'BKK', '2025-12-30 13:20:00', 0, 'ACTIVE', 'DIRECT',
             '2025-11-28 00:00:00', 'mock', '2025-11-28 00:00:00', 'mock', 280),
    (41013, 'AT917', 'SQ', 'ICN', '2026-01-06 08:40:00',
             'BKK', '2026-01-06 13:20:00', 0, 'ACTIVE', 'DIRECT',
             '2025-11-28 00:00:00', 'mock', '2025-11-28 00:00:00', 'mock', 280),
    (41014, 'AT918', 'SQ', 'ICN', '2026-01-13 08:40:00',
             'BKK', '2026-01-13 13:20:00', 0, 'ACTIVE', 'DIRECT',
             '2025-11-28 00:00:00', 'mock', '2025-11-28 00:00:00', 'mock', 280),
    -- 방콕 상품: 귀국 (BKK → ICN) - 여러 날짜 (출국일 + 5일 = Day 5)
    (41004, 'AT914', 'SQ', 'BKK', '2025-12-20 01:00:00',
             'ICN', '2025-12-20 06:20:00', 0, 'ACTIVE', 'DIRECT',
             '2025-11-28 00:00:00', 'mock', '2025-11-28 00:00:00', 'mock', 320),
    (41022, 'AT926', 'SQ', 'BKK', '2025-12-21 01:00:00',
             'ICN', '2025-12-21 06:20:00', 0, 'ACTIVE', 'DIRECT',
             '2025-11-28 00:00:00', 'mock', '2025-11-28 00:00:00', 'mock', 320),
    (41023, 'AT927', 'SQ', 'BKK', '2025-12-22 01:00:00',
             'ICN', '2025-12-22 06:20:00', 0, 'ACTIVE', 'DIRECT',
             '2025-11-28 00:00:00', 'mock', '2025-11-28 00:00:00', 'mock', 320),
    (41024, 'AT928', 'SQ', 'BKK', '2025-12-23 01:00:00',
             'ICN', '2025-12-23 06:20:00', 0, 'ACTIVE', 'DIRECT',
             '2025-11-28 00:00:00', 'mock', '2025-11-28 00:00:00', 'mock', 320),
    (41025, 'AT929', 'SQ', 'BKK', '2025-12-24 01:00:00',
             'ICN', '2025-12-24 06:20:00', 0, 'ACTIVE', 'DIRECT',
             '2025-11-28 00:00:00', 'mock', '2025-11-28 00:00:00', 'mock', 320),
    (41026, 'AT930', 'SQ', 'BKK', '2025-12-25 01:00:00',
             'ICN', '2025-12-25 06:20:00', 0, 'ACTIVE', 'DIRECT',
             '2025-11-28 00:00:00', 'mock', '2025-11-28 00:00:00', 'mock', 320),
    (41011, 'AT915', 'SQ', 'BKK', '2025-12-27 01:00:00',
             'ICN', '2025-12-27 06:20:00', 0, 'ACTIVE', 'DIRECT',
             '2025-11-28 00:00:00', 'mock', '2025-11-28 00:00:00', 'mock', 320),
    (41012, 'AT916', 'SQ', 'BKK', '2026-01-04 01:00:00',
             'ICN', '2026-01-04 06:20:00', 0, 'ACTIVE', 'DIRECT',
             '2025-11-28 00:00:00', 'mock', '2025-11-28 00:00:00', 'mock', 320),
    (41015, 'AT919', 'SQ', 'BKK', '2026-01-11 01:00:00',
             'ICN', '2026-01-11 06:20:00', 0, 'ACTIVE', 'DIRECT',
             '2025-11-28 00:00:00', 'mock', '2025-11-28 00:00:00', 'mock', 320),
    (41016, 'AT920', 'SQ', 'BKK', '2026-01-18 01:00:00',
             'ICN', '2026-01-18 06:20:00', 0, 'ACTIVE', 'DIRECT',
             '2025-11-28 00:00:00', 'mock', '2025-11-28 00:00:00', 'mock', 320);

INSERT IGNORE INTO seat_class
    (id, air_id, class_type, price_adult, price_youth, price_infant, max_seats, available_seats)
VALUES
    (51001, 41001, 'ECONOMY', 1200000,  900000, 300000, 40, 40),
    (51005, 41005, 'ECONOMY', 1210000,  910000, 310000, 40, 40),
    (51006, 41006, 'ECONOMY', 1190000,  890000, 290000, 40, 40),
    (51002, 41002, 'ECONOMY', 1220000,  920000, 320000, 40, 40),
    (51007, 41007, 'ECONOMY', 1230000,  930000, 330000, 40, 40),
    (51008, 41008, 'ECONOMY', 1210000,  910000, 310000, 40, 40),
    (51003, 41003, 'ECONOMY',  680000,  520000, 200000, 45, 45),
    (51017, 41017, 'ECONOMY',  685000,  525000, 205000, 45, 45),
    (51018, 41018, 'ECONOMY',  675000,  515000, 195000, 45, 45),
    (51019, 41019, 'ECONOMY',  690000,  530000, 210000, 45, 45),
    (51020, 41020, 'ECONOMY',  680000,  520000, 200000, 45, 45),
    (51021, 41021, 'ECONOMY',  685000,  525000, 205000, 45, 45),
    (51009, 41009, 'ECONOMY',  690000,  530000, 210000, 45, 45),
    (51028, 41028, 'ECONOMY',  720000,  550000, 220000, 45, 45),
    (51010, 41010, 'ECONOMY',  670000,  510000, 190000, 45, 45),
    (51013, 41013, 'ECONOMY',  690000,  530000, 210000, 45, 45),
    (51014, 41014, 'ECONOMY',  680000,  520000, 200000, 45, 45),
    (51004, 41004, 'ECONOMY',  690000,  530000, 210000, 45, 45),
    (51022, 41022, 'ECONOMY',  695000,  535000, 215000, 45, 45),
    (51023, 41023, 'ECONOMY',  685000,  525000, 205000, 45, 45),
    (51024, 41024, 'ECONOMY',  700000,  540000, 220000, 45, 45),
    (51025, 41025, 'ECONOMY',  690000,  530000, 210000, 45, 45),
    (51026, 41026, 'ECONOMY',  695000,  535000, 215000, 45, 45),
    (51011, 41011, 'ECONOMY',  700000,  540000, 220000, 45, 45),
    (51012, 41012, 'ECONOMY',  680000,  520000, 200000, 45, 45),
    (51015, 41015, 'ECONOMY',  690000,  530000, 210000, 45, 45),
    (51016, 41016, 'ECONOMY',  700000,  540000, 220000, 45, 45);

-- 5-1. 타임딜 (테스트용)
INSERT IGNORE INTO time_deal
    (id, start_time, end_time, discount_type, value,
     created_at, modified_at, created_by, modified_by)
VALUES
    (71001, '2025-11-28 00:00:00', '2026-12-31 23:59:59', 'PERCENT', 15,
     '2025-11-28 00:00:00', '2025-11-28 00:00:00', 'mock', 'mock'),
    (71002, '2025-11-28 00:00:00', '2026-12-31 23:59:59', 'ABSOLUTE', 200000,
     '2025-11-28 00:00:00', '2025-11-28 00:00:00', 'mock', 'mock');

-- 5. 상품(Product)
INSERT IGNORE INTO product
    (id, tour_id, title, price, cutoff_days, star1, star2, star3, star4, star5,
     created_at, modified_at, created_by, modified_by, product_type, is_active,
     seat_class_types, depart_time_type, thumbnail, time_deal_id)
VALUES
    (61001, 9101, '테스트 런던 패키지 5일 (성인 전용)', 2390000, 20,
     0, 0, 0, 0, 0,
     '2025-11-28 00:00:00', '2025-11-28 00:00:00', 'mock', 'mock',
     'PACKAGE', 1, '["ECONOMY"]', 'MORNING',
     'https://static.awesome-travel.test/images/search/london-thumb.jpg', 71001),
    (61002, 9102, '테스트 방콕 리조트 6일 (성인 1+청소년 1)', 1590000, 15,
     0, 0, 0, 0, 0,
     '2025-11-28 00:00:00', '2025-11-28 00:00:00', 'mock', 'mock',
     'PACKAGE', 1, '["ECONOMY"]', 'MORNING',
     'https://static.awesome-travel.test/images/search/bangkok-thumb.jpg', 71002);

-- 6. 상품 정보 (포함/불포함/특이사항)
INSERT IGNORE INTO product_include (product_id, title, content, appendix) VALUES
    (61001, '포함사항',  '왕복 항공권 · 4성급 호텔 3박 · 공항-호텔 전용 차량', '조식 3회 포함'),
    (61001, '특이사항', '전 일정 노옵션 · 노쇼핑 · 최소 출발 10명', '여권 만료일 6개월 이상'),
    (61002, '포함사항',  '왕복 항공권 · 리버뷰 리조트 4박 · 현지 가이드', '웰컴 마사지 1회'),
    (61002, '특이사항', '성인 1 + 청소년 1 기준 요금, 12세 미만 동반 불가', '최소 출발 8명');

INSERT IGNORE INTO product_exclude (product_id, title, content, appendix) VALUES
    (61001, '불포함',  '중식 · 석식 · 선택관광 · 개인 여행자 보험', '자유 일정 시 개인 경비'),
    (61002, '불포함',  '유류할증료 · 개인경비 · 여행자 보험', '선택 야시장 투어 (옵션)');

-- 8. 검색 키워드
INSERT IGNORE INTO product_keywords (product_id, keywords) VALUES
    (61001, '테스트'),
    (61001, '성인'),
    (61001, '유럽'),
    (61002, '테스트'),
    (61002, '성인'),
    (61002, '동남아');

-- 9. 테스트용 메뉴코드 (테스트 상품 2개 직접 연결)
INSERT IGNORE INTO menu_code(code, code2, name) VALUES
    ('999001', 999001, '테스트 패키지 모음');

INSERT IGNORE INTO menu_code_details(menu_code, target_column, value) VALUES
    ('999001', 'ID', '61001'),
    ('999001', 'ID', '61002');


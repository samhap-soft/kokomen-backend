-- 시스템 설계 면접 시드 문제(BASIC 5개).
-- 이 파일은 적용 뒤 고치지 않는다(validate-on-migrate). 문구를 바꿀 때는 새 버전에서 새 code로 문제를 넣고
-- 이전 문제의 state를 INACTIVE로 돌린다. created_at에는 DB DEFAULT가 없어 NOW(6)을 넣는다.
-- 자식 행은 code로 찾은 문제 id를 세션 변수에 담아 쓴다. Flyway는 한 스크립트를 한 커넥션에서 실행한다.

-- 1. URL 단축기를 설계해 보세요 (URL_SHORTENER)
INSERT INTO system_design_problem (code, difficulty, state, title, description, difficulty_comment, starter_diagram,
                                   created_at)
VALUES ('URL_SHORTENER', 'BASIC', 'ACTIVE',
        'URL 단축기를 설계해 보세요',
        '문자나 메신저 메시지에 넣을 짧은 링크를 만들어 주는 서비스예요. 긴 URL을 받아 짧은 URL을 돌려주고, 누군가 짧은 URL을 누르면 원래 주소로 보내 줘요. 많은 기업이 단체 문자에 이 링크를 넣어 보내기 때문에, 발송 직후에는 접속이 짧은 시간에 몰려요.',
        '리다이렉트 경로를 빠르게 하는 방법과 단축 키 생성·충돌 처리를 요구사항과 연결해 설명하면 좋은 평가를 받아요. 저장 용량은 가정한 수치로 대략 계산하면 되고, 장애 대응은 핵심 대비책만 짚어도 충분해요.',
        '{"blocks":[{"id":"client","kind":"CLIENT","label":"Client","props":[],"position":{"x":0,"y":80}},{"id":"api","kind":"API_SERVER","label":"API Server","props":[],"position":{"x":260,"y":80}},{"id":"db","kind":"DB","label":"DB","props":[],"position":{"x":520,"y":80}}],"connections":[{"id":"c1","source":"client","target":"api","label":"HTTPS"},{"id":"c2","source":"api","target":"db"}]}',
        NOW(6));
SET @url_shortener_id = (SELECT id FROM system_design_problem WHERE code = 'URL_SHORTENER');

INSERT INTO system_design_hidden_requirement (problem_id, code, name, answer, display_order, created_at)
VALUES (@url_shortener_id, 'DAILY_CREATION_VOLUME', '하루 생성량',
        '새로 만들어지는 단축 URL은 하루 평균 10만 개 정도예요.', 1, NOW(6)),
       (@url_shortener_id, 'READ_WRITE_RATIO', '조회와 생성 비율',
        '단축 URL로 접속하는 조회 요청은 생성 요청의 100배쯤 된다고 보면 돼요.', 2, NOW(6)),
       (@url_shortener_id, 'RETENTION_PERIOD', '보관 기간',
        '단축 URL은 만든 날부터 5년 동안 열려야 하고, 그 뒤에는 지워도 돼요.', 3, NOW(6)),
       (@url_shortener_id, 'SHORT_KEY_LENGTH', '단축 키 길이',
        '단문 문자에도 넣어야 해서 단축 키는 영문 대소문자와 숫자로 7자 이하여야 해요.', 4, NOW(6)),
       (@url_shortener_id, 'CLICK_STATISTICS', '클릭 통계',
        '링크별 클릭 수 같은 통계 기능은 이번 범위에서 빼도 돼요.', 5, NOW(6));

INSERT INTO system_design_evaluation_point (problem_id, code, description, display_order, created_at)
VALUES (@url_shortener_id, 'READ_PATH_CACHE',
        '조회가 생성보다 훨씬 많다는 점을 근거로 리다이렉트 경로에 캐시를 두었다', 1, NOW(6)),
       (@url_shortener_id, 'KEY_GENERATION_COLLISION',
        '단축 키 생성 방식(카운터 인코딩·해시 등)과 키 충돌을 막거나 처리하는 방법을 설명했다', 2, NOW(6)),
       (@url_shortener_id, 'STORAGE_ESTIMATION',
        '생성량·보관 기간·건당 크기로 전체 저장 용량을 추정하고 저장소 선택에 반영했다', 3, NOW(6)),
       (@url_shortener_id, 'REDIRECT_AVAILABILITY',
        'DB나 캐시에 장애가 나도 이미 보낸 링크가 계속 열리도록 대비책(복제·장애 전환 등)을 설명했다', 4, NOW(6)),
       (@url_shortener_id, 'REDIRECT_STATUS_TRADEOFF',
        '301과 302 리다이렉트의 장단점을 비교하고 근거를 들어 하나를 골랐다', 5, NOW(6));

INSERT INTO system_design_problem_question (problem_id, stage, question_order, content, target_point_codes, created_at)
VALUES (@url_shortener_id, 'HIGH_LEVEL', 1,
        '확인한 요구사항을 바탕으로 전체 구조를 그리고, 단축 URL을 만드는 요청과 단축 URL로 접속하는 요청이 각각 어떤 순서로 처리되는지 설명해 주세요.',
        '["READ_PATH_CACHE"]', NOW(6)),
       (@url_shortener_id, 'HIGH_LEVEL', 2,
        '단축 키는 어떤 방식으로 만들고, 서로 다른 URL이 같은 키를 받지 않게 하려면 어떻게 하나요?',
        '["KEY_GENERATION_COLLISION"]', NOW(6)),
       (@url_shortener_id, 'HIGH_LEVEL', 3,
        '데이터가 얼마나 쌓일지 추정해 보고, 그에 맞춰 단축 키와 원래 URL을 어떤 저장소에 어떤 형태로 둘지 설명해 주세요.',
        '["STORAGE_ESTIMATION"]', NOW(6)),
       (@url_shortener_id, 'DEEP_DIVE', 4,
        '리다이렉트 요청이 지금보다 크게 늘면 어디가 먼저 병목이 되고, 어떻게 대응하나요?',
        '["READ_PATH_CACHE"]', NOW(6)),
       (@url_shortener_id, 'DEEP_DIVE', 5,
        'DB 서버 한 대가 갑자기 내려가면 이미 메시지로 보낸 링크는 어떻게 되고, 어떻게 대비하나요?',
        '["REDIRECT_AVAILABILITY"]', NOW(6)),
       (@url_shortener_id, 'DEEP_DIVE', 6,
        '리다이렉트 응답을 301(영구 이동)과 302(임시 이동) 중 무엇으로 줄지, 두 방식의 장단점을 비교해 설명해 주세요.',
        '["REDIRECT_STATUS_TRADEOFF"]', NOW(6));

-- 2. 선착순 쿠폰 발급 시스템을 설계해 보세요 (FIRST_COME_COUPON)
INSERT INTO system_design_problem (code, difficulty, state, title, description, difficulty_comment, starter_diagram,
                                   created_at)
VALUES ('FIRST_COME_COUPON', 'BASIC', 'ACTIVE',
        '선착순 쿠폰 발급 시스템을 설계해 보세요',
        '배달 앱이 정해진 시각에 할인 쿠폰을 선착순으로 나눠 주는 이벤트를 열어요. 시작 시각이 되면 많은 사용자가 한꺼번에 발급 버튼을 누르고, 받은 쿠폰은 각자의 쿠폰함에 들어가요. 쿠폰 수량은 정해져 있어서 준비한 수량보다 많이 나가면 안 돼요.',
        '수량을 원자적으로 줄여 초과 발급을 막고, 같은 사용자의 중복 발급을 막는 방법을 설명하면 충분해요. DB에 몰리는 쓰기를 감당하는 방법과 저장 실패 시 보정까지 다루면 더 좋아요.',
        '{"blocks":[{"id":"client","kind":"CLIENT","label":"Client","props":[],"position":{"x":0,"y":80}},{"id":"api","kind":"API_SERVER","label":"API Server","props":[],"position":{"x":260,"y":80}},{"id":"db","kind":"DB","label":"DB","props":[],"position":{"x":520,"y":80}}],"connections":[{"id":"c1","source":"client","target":"api","label":"HTTPS"},{"id":"c2","source":"api","target":"db"}]}',
        NOW(6));
SET @first_come_coupon_id = (SELECT id FROM system_design_problem WHERE code = 'FIRST_COME_COUPON');

INSERT INTO system_design_hidden_requirement (problem_id, code, name, answer, display_order, created_at)
VALUES (@first_come_coupon_id, 'PEAK_TRAFFIC', '순간 트래픽 규모',
        '이벤트가 시작되면 10초 안에 약 30만 명이 발급을 시도하고, 한 사람이 평균 2번씩 버튼을 눌러요.', 1, NOW(6)),
       (@first_come_coupon_id, 'COUPON_STOCK', '쿠폰 수량',
        '쿠폰은 이벤트마다 5만 장을 준비해요.', 2, NOW(6)),
       (@first_come_coupon_id, 'PER_MEMBER_LIMIT', '1인당 발급 한도',
        '한 회원은 이벤트마다 쿠폰을 한 장만 받을 수 있어요.', 3, NOW(6)),
       (@first_come_coupon_id, 'RESULT_AND_INBOX_LATENCY', '결과 응답과 반영 시간',
        '성공인지 실패인지는 버튼을 누르고 1초 안에 보여 줘야 해요. 쿠폰함에는 1분 안에만 들어가면 돼요.', 4, NOW(6)),
       (@first_come_coupon_id, 'COUPON_REDEMPTION', '쿠폰 사용 처리',
        '주문할 때 쿠폰을 적용하는 기능은 이번 범위에서 빼도 돼요.', 5, NOW(6));

INSERT INTO system_design_evaluation_point (problem_id, code, description, display_order, created_at)
VALUES (@first_come_coupon_id, 'ATOMIC_STOCK_DECREMENT',
        '남은 수량 확인과 차감을 원자적으로 처리해 동시 요청에도 초과 발급을 막았다', 1, NOW(6)),
       (@first_come_coupon_id, 'HOT_STOCK_ROW_CONTENTION',
        '수량을 DB 한 행에서 줄이면 몰린 요청이 락을 기다리며 밀린다는 점을 짚고 이를 피했다', 2, NOW(6)),
       (@first_come_coupon_id, 'DUPLICATE_ISSUE_PREVENTION',
        '같은 사용자의 요청이 겹치거나 반복돼도 쿠폰이 중복 발급되지 않게 막았다', 3, NOW(6)),
       (@first_come_coupon_id, 'ISSUE_WRITE_LOAD',
        '몰린 쓰기가 DB 처리량을 넘는다는 점을 짚고, DB가 감당할 수 있게 하는 방법(비동기·묶음 저장 등)을 근거와 함께 제시했다', 4, NOW(6)),
       (@first_come_coupon_id, 'ISSUE_FAILURE_RECOVERY',
        '수량은 줄었는데 저장이 실패한 경우 재시도나 수량 복구 등으로 둘을 맞추는 방법을 설명했다', 5, NOW(6));

INSERT INTO system_design_problem_question (problem_id, stage, question_order, content, target_point_codes, created_at)
VALUES (@first_come_coupon_id, 'HIGH_LEVEL', 1,
        '확인한 요구사항을 바탕으로 전체 구조를 그리고, 쿠폰 발급 요청이 어떤 순서로 처리되는지 설명해 주세요.',
        '["ATOMIC_STOCK_DECREMENT","DUPLICATE_ISSUE_PREVENTION","ISSUE_WRITE_LOAD"]', NOW(6)),
       (@first_come_coupon_id, 'HIGH_LEVEL', 2,
        '남은 수량은 어디에 두고, 요청이 동시에 몰려도 준비한 수량보다 많이 발급되지 않게 하려면 어떻게 하나요?',
        '["ATOMIC_STOCK_DECREMENT","HOT_STOCK_ROW_CONTENTION"]', NOW(6)),
       (@first_come_coupon_id, 'HIGH_LEVEL', 3,
        '같은 사용자의 발급 요청이 거의 동시에 여러 번 들어오면 어떻게 처리하나요? 발급 기록을 어떤 구조로 저장하는지와 함께 설명해 주세요.',
        '["DUPLICATE_ISSUE_PREVENTION"]', NOW(6)),
       (@first_come_coupon_id, 'DEEP_DIVE', 4,
        '참여자가 크게 늘어 시작 순간 요청이 더 몰리면 어디가 먼저 병목이 되고, 어떻게 대응하나요?',
        '["HOT_STOCK_ROW_CONTENTION","ISSUE_WRITE_LOAD"]', NOW(6)),
       (@first_come_coupon_id, 'DEEP_DIVE', 5,
        '발급을 처리하는 도중 서버나 저장소가 실패하면 남은 수량과 실제로 저장된 쿠폰 수가 어긋날 수 있나요? 어떻게 맞추나요?',
        '["ISSUE_FAILURE_RECOVERY"]', NOW(6)),
       (@first_come_coupon_id, 'DEEP_DIVE', 6,
        '쿠폰함 저장을 발급 응답 전에 끝내는 방식과 응답 뒤로 미루는 방식 중 무엇을 골랐나요? 그 선택으로 감수하는 점도 설명해 주세요.',
        '["ISSUE_WRITE_LOAD","ISSUE_FAILURE_RECOVERY"]', NOW(6));

-- 3. 중고거래 앱의 1:1 채팅을 설계해 보세요 (ONE_TO_ONE_CHAT)
INSERT INTO system_design_problem (code, difficulty, state, title, description, difficulty_comment, starter_diagram,
                                   created_at)
VALUES ('ONE_TO_ONE_CHAT', 'BASIC', 'ACTIVE',
        '중고거래 앱의 1:1 채팅을 설계해 보세요',
        '중고거래 앱에서 구매자가 판매 글을 보고 채팅을 시작하면 판매자와 1:1 대화방이 생기고, 두 사람이 메시지를 주고받아요. 앱을 꺼 둔 사이에 온 메시지도 나중에 앱을 열면 받아 볼 수 있어야 해요. 많은 사용자가 퇴근 뒤 저녁 시간에 몰려서 채팅해요.',
        '지속 연결을 쓰는 이유, 접속하지 않은 상대의 메시지를 저장했다가 다시 접속할 때 넘겨 주는 흐름, 채팅 서버가 여러 대일 때 받는 사람이 연결된 서버로 메시지를 보내는 방법을 설명하면 좋아요.',
        '{"blocks":[{"id":"client","kind":"CLIENT","label":"Client","props":[],"position":{"x":0,"y":80}},{"id":"chat","kind":"API_SERVER","label":"Chat Server","props":[],"position":{"x":260,"y":80}}],"connections":[{"id":"c1","source":"client","target":"chat"}]}',
        NOW(6));
SET @one_to_one_chat_id = (SELECT id FROM system_design_problem WHERE code = 'ONE_TO_ONE_CHAT');

INSERT INTO system_design_hidden_requirement (problem_id, code, name, answer, display_order, created_at)
VALUES (@one_to_one_chat_id, 'USER_SCALE', '사용자 규모',
        '하루에 채팅을 쓰는 사용자는 100만 명이고, 가장 붐비는 시간에는 그중 10%가 동시에 접속해 있다고 보면 돼요.', 1, NOW(6)),
       (@one_to_one_chat_id, 'MESSAGE_VOLUME_AND_SIZE', '메시지 수와 크기',
        '채팅을 쓰는 사용자 한 명은 하루 평균 20개의 메시지를 보내고, 메시지 하나는 평균 50바이트 정도예요.', 2, NOW(6)),
       (@one_to_one_chat_id, 'DELIVERY_LATENCY', '전달 지연 목표',
        '두 사람이 모두 접속해 있으면 보낸 메시지가 1초 안에 상대 화면에 보여야 해요.', 3, NOW(6)),
       (@one_to_one_chat_id, 'MESSAGE_RETENTION', '메시지 보관 기간',
        '메시지는 3년 동안 서버에 보관하고, 그동안은 휴대폰을 바꿔도 지난 대화를 다시 볼 수 있어야 해요.', 4, NOW(6)),
       (@one_to_one_chat_id, 'MEDIA_ATTACHMENTS', '사진과 파일 전송',
        '사진이나 파일 전송은 이번 범위에서 빼도 돼요. 텍스트 메시지만 생각하면 돼요.', 5, NOW(6));

INSERT INTO system_design_evaluation_point (problem_id, code, description, display_order, created_at)
VALUES (@one_to_one_chat_id, 'PERSISTENT_CONNECTION',
        '폴링과 비교해 WebSocket 같은 지속 연결로 서버가 새 메시지를 바로 보내게 한 이유를 설명했다', 1, NOW(6)),
       (@one_to_one_chat_id, 'OFFLINE_DELIVERY',
        '접속하지 않은 사용자에게 온 메시지를 저장해 두고, 다시 접속하면 받지 못한 메시지만 골라 전달하는 방법을 설명했다', 2, NOW(6)),
       (@one_to_one_chat_id, 'MESSAGE_DATA_MODEL',
        '메시지를 대화방 ID와 순서 값(메시지 ID나 시각) 기준으로 저장해 대화를 순서대로 불러오는 구조를 설명했다', 3, NOW(6)),
       (@one_to_one_chat_id, 'CONNECTION_ROUTING',
        '채팅 서버가 여러 대일 때 받는 사람이 연결된 서버로 메시지를 넘기는 방법을 설명했다', 4, NOW(6)),
       (@one_to_one_chat_id, 'MESSAGE_DURABILITY',
        '서버나 연결이 실패해도 보낸 메시지가 사라지지 않게 하는 방법과 그 순서를 설명했다', 5, NOW(6));

INSERT INTO system_design_problem_question (problem_id, stage, question_order, content, target_point_codes, created_at)
VALUES (@one_to_one_chat_id, 'HIGH_LEVEL', 1,
        '확인한 요구사항을 바탕으로 판매자가 보낸 메시지가 구매자에게 전달되기까지의 전체 구조를 그리고, 어떤 순서로 처리되는지 설명해 주세요.',
        '["PERSISTENT_CONNECTION","MESSAGE_DURABILITY"]', NOW(6)),
       (@one_to_one_chat_id, 'HIGH_LEVEL', 2,
        '앱을 켜 둔 구매자에게 새 메시지를 전달하려면 클라이언트와 서버를 어떤 방식으로 연결하나요? 그 방식을 고른 이유도 설명해 주세요.',
        '["PERSISTENT_CONNECTION"]', NOW(6)),
       (@one_to_one_chat_id, 'HIGH_LEVEL', 3,
        '구매자가 다시 접속했을 때 그동안 온 메시지를 전달하려면, 메시지를 어디에 어떤 구조로 저장하나요?',
        '["OFFLINE_DELIVERY","MESSAGE_DATA_MODEL"]', NOW(6)),
       (@one_to_one_chat_id, 'DEEP_DIVE', 4,
        '사용자가 크게 늘어 채팅 서버를 여러 대로 늘리면, 판매자와 구매자가 서로 다른 서버에 연결돼 있을 때 메시지를 어떻게 전달하나요?',
        '["CONNECTION_ROUTING"]', NOW(6)),
       (@one_to_one_chat_id, 'DEEP_DIVE', 5,
        '채팅 서버 한 대가 갑자기 내려가면, 그 서버에 연결돼 있던 사용자는 어떻게 다시 연결해서 놓친 메시지까지 받나요?',
        '["CONNECTION_ROUTING","OFFLINE_DELIVERY"]', NOW(6)),
       (@one_to_one_chat_id, 'DEEP_DIVE', 6,
        '채팅 서버가 메시지를 받으면 저장을 먼저 끝내고 전달할지, 전달을 먼저 하고 저장할지 어떤 순서를 골랐나요? 두 방식의 장단점과 함께 설명해 주세요.',
        '["MESSAGE_DURABILITY"]', NOW(6));

-- 4. 쇼핑몰 검색어 자동완성을 설계해 보세요 (SEARCH_AUTOCOMPLETE)
INSERT INTO system_design_problem (code, difficulty, state, title, description, difficulty_comment, starter_diagram,
                                   created_at)
VALUES ('SEARCH_AUTOCOMPLETE', 'BASIC', 'ACTIVE',
        '쇼핑몰 검색어 자동완성을 설계해 보세요',
        '쇼핑몰 검색창에 글자를 하나씩 입력할 때마다, 지금까지 입력한 글자로 시작하는 추천 검색어를 목록으로 보여 주는 기능을 만들려고 해요. 추천 검색어는 사용자들이 실제로 검색한 기록을 바탕으로 정해요. 많은 사용자가 상품을 찾을 때마다 쓰는 기능이라, 저녁 시간과 할인 행사 때는 요청이 크게 몰려요.',
        '접두사별 추천 목록을 미리 만들어 메모리에서 돌려주는 조회 흐름과, 검색 로그를 모아 목록을 다시 만드는 집계 흐름을 나눠 설명하면 돼요. 갱신이 중간에 실패해도 반쯤 바뀐 목록이 보이지 않게 하는 방법까지 다루면 더 좋아요.',
        NULL,
        NOW(6));
SET @search_autocomplete_id = (SELECT id FROM system_design_problem WHERE code = 'SEARCH_AUTOCOMPLETE');

INSERT INTO system_design_hidden_requirement (problem_id, code, name, answer, display_order, created_at)
VALUES (@search_autocomplete_id, 'TRAFFIC_SCALE', '트래픽 규모',
        '하루 활성 사용자는 500만 명이고, 한 사람이 하루 평균 4번 검색해요. 검색 한 번에 자동완성 요청은 평균 5번 나간다고 보면 돼요.', 1, NOW(6)),
       (@search_autocomplete_id, 'KEYWORD_DATA_SIZE', '검색어 데이터 규모',
        '추천 후보가 되는 서로 다른 검색어는 약 200만 개이고, 검색어 하나는 평균 6글자예요.', 2, NOW(6)),
       (@search_autocomplete_id, 'SUGGESTION_COUNT_AND_RANKING', '추천 개수와 정렬 기준',
        '추천 검색어는 최대 10개이고, 최근 7일 동안 많이 검색된 순서로 보여 줘요. 사용자와 상관없이 모두에게 같은 목록이에요.', 3, NOW(6)),
       (@search_autocomplete_id, 'RESPONSE_LATENCY', '응답 지연 목표',
        '자동완성 API는 요청을 받은 뒤 100ms 안에 응답해야 해요.', 4, NOW(6)),
       (@search_autocomplete_id, 'REFRESH_INTERVAL', '추천 반영 주기',
        '새로 많이 검색되기 시작한 검색어는 1시간 안에 추천에 반영되면 충분해요.', 5, NOW(6));

INSERT INTO system_design_evaluation_point (problem_id, code, description, display_order, created_at)
VALUES (@search_autocomplete_id, 'PREFIX_TOP_N_PRECOMPUTE',
        '접두사마다 인기순 상위 추천어 목록을 미리 계산해 두고, 요청 때는 조회만 하게 했다', 1, NOW(6)),
       (@search_autocomplete_id, 'MEMORY_READ_PATH',
        '추천 조회가 DB까지 가지 않고 메모리나 캐시에서 끝나게 했다', 2, NOW(6)),
       (@search_autocomplete_id, 'ASYNC_LOG_AGGREGATION',
        '검색 로그를 추천 조회와 분리해 비동기로 모으고, 집계 결과로 추천 데이터를 갱신했다', 3, NOW(6)),
       (@search_autocomplete_id, 'SAFE_DATA_SWAP',
        '추천 데이터 갱신이 중간에 실패해도 반쯤 바뀐 목록 대신 이전 추천이 보이게 하는 방법(다 만든 뒤 한 번에 교체 등)을 설명했다', 4, NOW(6)),
       (@search_autocomplete_id, 'REQUEST_REDUCTION',
        '디바운스나 브라우저·CDN 캐시로 서버까지 오는 자동완성 요청을 줄였다', 5, NOW(6));

INSERT INTO system_design_problem_question (problem_id, stage, question_order, content, target_point_codes, created_at)
VALUES (@search_autocomplete_id, 'HIGH_LEVEL', 1,
        '확인한 요구사항을 바탕으로 전체 구조를 그리고, 글자를 입력했을 때 추천 검색어가 화면에 보이기까지의 흐름을 설명해 주세요.',
        '["PREFIX_TOP_N_PRECOMPUTE","MEMORY_READ_PATH"]', NOW(6)),
       (@search_autocomplete_id, 'HIGH_LEVEL', 2,
        '입력한 글자로 시작하는 추천 검색어를 빠르게 골라 보여 주려면, 데이터를 어떤 구조로 저장하나요?',
        '["PREFIX_TOP_N_PRECOMPUTE"]', NOW(6)),
       (@search_autocomplete_id, 'HIGH_LEVEL', 3,
        '사용자들이 검색한 기록은 어떻게 모으고 집계해서 추천 데이터에 반영하나요?',
        '["ASYNC_LOG_AGGREGATION","SAFE_DATA_SWAP"]', NOW(6)),
       (@search_autocomplete_id, 'DEEP_DIVE', 4,
        '할인 행사처럼 자동완성 요청이 평소보다 크게 늘면, 어디가 먼저 병목이 되고 어떻게 대응하나요?',
        '["MEMORY_READ_PATH","REQUEST_REDUCTION"]', NOW(6)),
       (@search_autocomplete_id, 'DEEP_DIVE', 5,
        '추천 데이터를 갱신하는 작업이 중간에 실패하면, 사용자는 어떤 추천을 보게 되나요?',
        '["SAFE_DATA_SWAP"]', NOW(6)),
       (@search_autocomplete_id, 'DEEP_DIVE', 6,
        '새로 뜨는 검색어가 추천에 반영되기까지의 시간을 지금보다 크게 줄이면, 무엇을 얻고 어떤 비용이 늘어나나요?',
        '["ASYNC_LOG_AGGREGATION","PREFIX_TOP_N_PRECOMPUTE"]', NOW(6));

-- 5. 동네 커뮤니티 앱의 사진 업로드를 설계해 보세요 (IMAGE_UPLOAD)
INSERT INTO system_design_problem (code, difficulty, state, title, description, difficulty_comment, starter_diagram,
                                   created_at)
VALUES ('IMAGE_UPLOAD', 'BASIC', 'ACTIVE',
        '동네 커뮤니티 앱의 사진 업로드를 설계해 보세요',
        '동네 커뮤니티 앱에서 사용자는 게시글을 쓸 때 휴대폰으로 찍은 사진을 여러 장 함께 올려요. 이웃들은 게시글 목록에서 작은 사진을 훑어보고, 글을 열면 큰 사진을 넘겨 봐요. 저녁 시간에는 많은 사용자가 몰려 사진을 올리고 둘러봐요.',
        '사진 파일과 메타데이터를 나눠 저장하고, 업로드 경로·작은 사진 생성·CDN 전달을 각각 고른 이유와 함께 설명하면 충분해요. 변환이 실패할 때의 처리까지 짚으면 더 좋아요.',
        '{"blocks":[{"id":"client","kind":"CLIENT","label":"Client","props":[],"position":{"x":0,"y":80}},{"id":"api","kind":"API_SERVER","label":"API Server","props":[],"position":{"x":260,"y":80}},{"id":"db","kind":"DB","label":"DB","props":[],"position":{"x":520,"y":80}}],"connections":[{"id":"c1","source":"client","target":"api","label":"HTTPS"},{"id":"c2","source":"api","target":"db"}]}',
        NOW(6));
SET @image_upload_id = (SELECT id FROM system_design_problem WHERE code = 'IMAGE_UPLOAD');

INSERT INTO system_design_hidden_requirement (problem_id, code, name, answer, display_order, created_at)
VALUES (@image_upload_id, 'USER_SCALE', '사용자 규모',
        '하루 활성 사용자는 100만 명이고, 그중 4%가 매일 게시글을 하나씩 올린다고 보면 돼요.', 1, NOW(6)),
       (@image_upload_id, 'PHOTO_COUNT_AND_SIZE', '사진 개수와 용량',
        '사진은 게시글마다 최대 10장까지 올릴 수 있고 평균 3장이에요. 원본은 장당 최대 20MB까지 받고, 평균은 2MB예요.', 2, NOW(6)),
       (@image_upload_id, 'PHOTO_VIEW_VOLUME', '사진 조회량',
        '사용자 한 명이 하루 평균 목록에서 작은 사진 60장, 글을 열어 큰 사진 15장을 봐요.', 3, NOW(6)),
       (@image_upload_id, 'LATENCY_TARGETS', '지연 시간 목표',
        '글 등록 요청은 1초 안에 응답해야 하고, 작은 사진은 글을 올린 뒤 5초 안에 목록에 보이면 돼요.', 4, NOW(6)),
       (@image_upload_id, 'RETENTION_PERIOD', '보관 기간',
        '원본을 포함한 모든 사진은 게시글이 남아 있는 동안 계속 보관해요. 글이 지워지면 사진도 함께 지워야 해요.', 5, NOW(6));

INSERT INTO system_design_evaluation_point (problem_id, code, description, display_order, created_at)
VALUES (@image_upload_id, 'STORAGE_METADATA_SPLIT',
        '사진 파일은 오브젝트 스토리지에 두고, DB에는 파일 경로와 게시글 연결·순서 같은 메타데이터만 저장했다', 1, NOW(6)),
       (@image_upload_id, 'UPLOAD_PATH_LOAD',
        '큰 파일 업로드가 API 서버의 연결·대역폭을 묶는 문제를 짚고, 이를 피하는 방법(저장소 직접 업로드 등)이나 이 규모에서 서버를 거쳐도 되는 근거를 제시했다', 2, NOW(6)),
       (@image_upload_id, 'ASYNC_IMAGE_PROCESSING',
        '작은 사진·큰 사진을 만드는 변환 작업을 업로드·글 등록 요청과 분리해 큐나 이벤트로 비동기 처리했다', 3, NOW(6)),
       (@image_upload_id, 'CDN_SIZED_DELIVERY',
        '사진 조회는 CDN을 거치게 하고, 목록에는 원본 대신 작은 사진을 내려 주었다', 4, NOW(6)),
       (@image_upload_id, 'PROCESSING_FAILURE_HANDLING',
        '변환 작업이 실패하거나 늦어질 때의 재시도와, 그동안 목록에 보여 줄 대체 표시를 설명했다', 5, NOW(6));

INSERT INTO system_design_problem_question (problem_id, stage, question_order, content, target_point_codes, created_at)
VALUES (@image_upload_id, 'HIGH_LEVEL', 1,
        '확인한 요구사항을 바탕으로 전체 구조를 그리고, 사진을 올려 게시글을 등록하는 요청과 이웃이 그 사진을 보는 요청이 각각 어떤 순서로 처리되는지 설명해 주세요.',
        '["STORAGE_METADATA_SPLIT","UPLOAD_PATH_LOAD","CDN_SIZED_DELIVERY"]', NOW(6)),
       (@image_upload_id, 'HIGH_LEVEL', 2,
        '사진 파일과, 그 파일이 어느 글의 몇 번째 사진인지 같은 정보는 어디에 어떤 형태로 저장하나요?',
        '["STORAGE_METADATA_SPLIT"]', NOW(6)),
       (@image_upload_id, 'HIGH_LEVEL', 3,
        '목록용 작은 사진과 상세용 큰 사진은 언제, 어디에서 만들어지나요? 그렇게 정한 이유도 함께 설명해 주세요.',
        '["ASYNC_IMAGE_PROCESSING"]', NOW(6)),
       (@image_upload_id, 'DEEP_DIVE', 4,
        '저녁 시간에 사진을 보는 요청이 크게 늘면 어디가 먼저 병목이 되고, 어떻게 대응하나요?',
        '["CDN_SIZED_DELIVERY"]', NOW(6)),
       (@image_upload_id, 'DEEP_DIVE', 5,
        '작은 사진을 만들다가 실패하거나 오래 걸리면 목록에는 무엇이 보이고, 어떻게 복구하나요?',
        '["PROCESSING_FAILURE_HANDLING","ASYNC_IMAGE_PROCESSING"]', NOW(6)),
       (@image_upload_id, 'DEEP_DIVE', 6,
        '사진을 API 서버가 받아 저장소에 올리는 방식과 앱이 저장소에 바로 올리는 방식 중 무엇을 고르는지, 이유와 함께 설명해 주세요.',
        '["UPLOAD_PATH_LOAD"]', NOW(6));

# Tính năng Học từ vựng (Study) — Tài liệu Nghiệp vụ & Luồng hoạt động

Ngôn ngữ: Tiếng Việt | English: [STUDY.md](STUDY.md)

Tài liệu đầy đủ về tính năng học từ vựng (Study): nghiệp vụ, trạng thái học, sinh đề trắc nghiệm, chấm điểm, theo dõi tiến độ và luồng gọi API.

Tài liệu liên quan: [BACKEND.md](BACKEND.md) (tổng quan hệ thống), §9 learning engine, §7.4 API.

---

## 1. Mục đích & Phạm vi

Tính năng Study biến một bộ thẻ (deck) thành phiên học tương tác:

- Người học thấy một **câu hỏi** (định nghĩa của một thẻ) và chọn **từ khóa (term)** đúng trong 4 phương án
- Hệ thống chấm điểm **phía server**, cập nhật **trạng thái học** của từ, ghi lại lần trả lời, và báo tiến độ của deck
- Trạng thái học được theo dõi **riêng cho từng người dùng, từng thẻ** — học deck công khai của người khác không ảnh hưởng đến chủ deck hay người học khác

Ngoài phạm vi (tương lai): chiều ngược (từ → định nghĩa), chế độ tự viết (writing), lên lịch ôn tập SRS, danh sách ôn lại.

---

## 2. Khái niệm cốt lõi

| Khái niệm | Ý nghĩa |
|---|---|
| **Deck** | Một bộ thẻ học. Công khai (ai cũng học được) hoặc riêng tư (chỉ chủ sở hữu) |
| **Card (Thẻ)** | `term` + `definition` (+ `hint` tùy chọn). Một deck chứa nhiều thẻ |
| **CardProgress** | Trạng thái học của một thẻ cho riêng một người: trạng thái + chuỗi đúng liên tiếp + đánh dấu sao. Không có dòng = mặc định `NOT_LEARNED` |
| **StudyAttempt** | Một lần trả lời được ghi lại (nhật ký lịch sử). Làm nền cho danh sách ôn lỗi và thống kê |
| **Câu hỏi (Question)** | Định nghĩa làm đề bài + 4 phương án từ khóa (1 đúng + 3 gây nhiễu) |
| **Quiz** | Một lô câu hỏi đã xáo trộn (mặc định 10, tối đa 50) |

**Quy tắc quan trọng: đáp án đúng KHÔNG BAO GIỜ được gửi xuống client.** Client chỉ gửi lại phương án người học đã chọn; server so sánh và chấm.

---

## 3. Trạng thái học

Mỗi từ (thẻ) có đúng một trạng thái cho mỗi người dùng:

| Trạng thái | Ý nghĩa | Cách đạt được |
|---|---|---|
| `NOT_LEARNED` | Chưa từng trả lời đúng | Trạng thái ban đầu |
| `STILL_LEARNING` | Đang học: đã đúng ít nhất một lần nhưng chưa thuộc (hoặc bị giáng cấp sau khi sai) | Lần trả lời đúng đầu tiên |
| `MASTERED` | Đã thuộc | 2 lần trả lời đúng liên tiếp |

Ngưỡng thuộc = **2 lần đúng liên tiếp** (`MASTERY_THRESHOLD = 2`, cùng hằng số ở bản tham khảo và entity production). Muốn khó hơn chỉ cần sửa một chỗ.

### Máy trạng thái

```
             đúng lần #1                 đúng lần #2
NOT_LEARNED ──────────────▶ STILL_LEARNING ──────────────▶ MASTERED
     ▲                         ▲    ▲                         │
     │        sai (giữ nguyên)─┘    └───────── sai ───────────┘
     │  (streak đã là 0)                    giáng cấp, streak = 0
```

### Bảng chuyển trạng thái (nghiệp vụ chính xác)

| Trạng thái hiện tại | Sự kiện | Trạng thái mới | Streak |
|---|---|---|---|
| NOT_LEARNED (streak 0) | đúng lần #1 | STILL_LEARNING | 1 |
| NOT_LEARNED | sai / bỏ qua | NOT_LEARNED (giữ nguyên) | 0 |
| STILL_LEARNING (streak 1) | đúng lần #2 | **MASTERED** | 2 |
| STILL_LEARNING | sai / bỏ qua | STILL_LEARNING (giữ nguyên) | 0 (reset) |
| MASTERED | đúng | MASTERED (giữ nguyên) | 2 |
| MASTERED | sai / bỏ qua | **STILL_LEARNING** (giáng cấp) | 0 (reset) |

Hệ quả cần nhớ:

- **Một lần sai không xóa sạch tiến độ** — từ đã thuộc chỉ rơi về STILL_LEARNING, cần thêm 2 lần đúng liên tiếp mới thuộc lại
- **Bỏ qua tính là sai** (không gửi `selectedCardId` = trả lời sai)
- Từ đã thuộc mà trả lời đúng tiếp vẫn giữ MASTERED (streak không tăng)

---

## 4. Mô hình dữ liệu

```
users 1──< card_progress >──1 cards     state, consecutive_correct, starred
  │ 1
  └──< study_attempts >──1 cards        mode, is_correct, selected_card_id
              └── decks                  (deck_id denormalized để truy vấn lịch sử nhanh)
```

**`card_progress`** (migration V4)

| Cột | Kiểu | Ghi chú |
|---|---|---|
| user_id, card_id | BIGINT | UNIQUE cùng nhau (`uq_progress_user_card`) — mỗi người dùng mỗi thẻ một dòng |
| state | VARCHAR(20) | `NOT_LEARNED` / `STILL_LEARNING` / `MASTERED` (ràng buộc CHECK) |
| consecutive_correct | INT | Chuỗi trả lời đúng hiện tại |
| starred | BOOLEAN | Người học đánh dấu từ khó |
| updated_at | TIMESTAMPTZ | Ghi khi thêm/sửa |

**`study_attempts`** (migration V5)

| Cột | Ghi chú |
|---|---|
| user_id, card_id, deck_id | Ai trả lời gì, trong deck nào (deck_id denormalized) |
| mode | `MULTIPLE_CHOICE` (ràng buộc CHECK; bổ sung khi có chế độ mới) |
| is_correct | Kết quả chấm |
| selected_card_id | Phương án người học chọn; NULL = bỏ qua |
| answered_at | Thời điểm trả lời |

Tạo dòng lười (lazy): dòng `card_progress` chỉ được ghi ở **lần trả lời hoặc bật sao đầu tiên**. Thẻ chưa học không tốn lưu trữ.

---

## 5. Luồng API

Tất cả endpoint nằm dưới `/api/decks/{deckId}/study` — **bắt buộc xác thực (JWT)**, kể cả GET. Base URL: `http://localhost:8081`.

| Method | Endpoint | Mục đích |
|---|---|---|
| GET | `/study/quiz?count=10&state=&starred=` | Lấy một lô câu hỏi |
| POST | `/study/answer` | Nộp một câu trả lời → chấm + cập nhật trạng thái |
| GET | `/study/progress` | Tiến độ của deck cho người gọi |
| POST | `/study/cards/{cardId}/star` | Bật/tắt đánh dấu sao |

### 5.1 Một phiên học hoàn chỉnh (ví dụ)

Học từ **"ambition"** trong deck 2:

```
Bước 1 — GET /api/decks/2/study/quiz?count=2
         Authorization: Bearer <token>

Phản hồi (KHÔNG kèm đáp án đúng):
{
  "deckId": 2,
  "direction": "DEFINITION_TO_TERM",
  "questions": [
    { "cardId": 5,
      "prompt": "hoài bão, khát vọng",
      "options": [
        { "cardId": 5, "text": "ambition" },
        { "cardId": 6, "text": "ambitous" },
        { "cardId": 7, "text": "alleviate" },
        { "cardId": 8, "text": "permission" } ] },
    { "cardId": 8, "prompt": "sự cho phép", "options": [ ... ] }
  ]
}

Bước 2 — người học chọn "ambition" → POST /api/decks/2/study/answer
         { "cardId": 5, "selectedCardId": 5 }

Phản hồi:
{ "correct": true, "cardId": 5, "correctCardId": 5, "correctTerm": "ambition",
  "state": "STILL_LEARNING", "consecutiveCorrect": 1,
  "deckProgress": { "total": 4, "mastered": 0, "stillLearning": 1,
                    "notLearned": 3, "percent": 0.0 } }

Bước 3 — trả lời đúng tiếp thẻ đó → state MASTERED, streak 2,
         deckProgress.percent tăng (1/4 = 25.0)

Bước 4 — sau đó trả lời sai thẻ 5 (chọn cardId 6):
         → correct: false, state STILL_LEARNING (bị giáng cấp), streak 0
```

### 5.2 Chi tiết request/response

**GET `/study/quiz`** — tham số query (tất cả tùy chọn):

| Tham số | Giá trị | Tác dụng |
|---|---|---|
| `count` | 1–50 (mặc định 10) | Số câu hỏi |
| `state` | `NOT_LEARNED` / `STILL_LEARNING` / `MASTERED` | Chỉ lấy câu hỏi từ các thẻ ở trạng thái này |
| `starred` | `true` / `false` | Chỉ lấy từ (chưa) đánh dấu sao |

Có thể kết hợp, ví dụ `?starred=true&state=STILL_LEARNING` = "luyện các từ khó đang học dở".

**POST `/study/answer`** — body:

| Trường | Bắt buộc | Ghi chú |
|---|---|---|
| `cardId` | có | Thẻ được hỏi |
| `selectedCardId` | không | Phương án đã chọn; bỏ trống/null = bỏ qua (tính sai) |
| `mode` | không | Mặc định `MULTIPLE_CHOICE` |

**POST `/study/cards/{cardId}/star`** — không cần body. Phản hồi chứa giá trị starred mới. Đánh dấu sao hoạt động cả trên deck công khai (đây là ghi chú cá nhân) và sẽ tạo dòng `card_progress` nếu chưa có (state `NOT_LEARNED`, starred).

### 5.3 Xử lý lỗi

| Tình huống | Kết quả |
|---|---|
| Thiếu/sai JWT ở mọi endpoint study | 401/403 (được khớp trước rule đọc deck công khai) |
| Deck không tồn tại, hoặc deck riêng tư của người khác | 404 |
| `cardId` không thuộc deck này | 404 |
| Giá trị `state` không hợp lệ trong filter | 400 |
| Giá trị `mode` không hợp lệ | 400 |
| Thiếu `cardId` trong body trả lời | 400 |

---

## 6. Cách sinh đề trắc nghiệm (logic phía server)

1. Xác định deck — phải công khai hoặc thuộc về người gọi
2. Nạp các dòng tiến độ của người gọi cho các thẻ trong deck (thiếu = `NOT_LEARNED`)
3. Lọc theo `state` / `starred` → nhóm câu hỏi
4. Xáo trộn nhóm, lấy tối đa `count` câu (tối đa 50)
5. Mỗi câu: phương án đúng + tối đa 3 phương án nhiễu **cùng deck**, các phương án được xáo trộn
6. Trả về câu hỏi không kèm bất kỳ thông tin đúng/sai

Trường hợp biên:

- Deck ít hơn 4 thẻ → mỗi câu có ít phương án hơn (nhóm nhiễu chính là các thẻ trong deck)
- Nhóm lọc rỗng → `questions: []` (HTTP 200, không phải lỗi)
- Thứ tự câu và thứ tự phương án được xáo trộn mỗi lần gọi (chống học vẹt theo vị trí)

---

## 7. Cách chấm điểm (logic phía server)

```
1. card      = tìm request.cardId trong deck này        (không có → 404)
2. selected  = tìm request.selectedCardId trong deck    (không hợp lệ → null)
3. correct   = (selected != null && selected.id == card.id)
4. progress  = tìm hoặc tạo card_progress(user, card)
5. correct ? progress.recordCorrect() : progress.recordIncorrect()   ← máy trạng thái
6. lưu dòng progress; ghi study_attempt(mode, is_correct, selected_card_id)
7. phản hồi: correct + trạng thái/streak mới + tiến độ cả deck
```

Nguyên tắc tin cậy: client chỉ khai báo *đã chọn gì*, không khai báo *đúng hay sai*. Server nắm quyền chấm.

---

## 8. Cách tính tiến độ

Theo từng deck, cho người đang gọi:

```
mastered      = số thẻ ở trạng thái MASTERED
stillLearning = số thẻ ở trạng thái STILL_LEARNING
notLearned    = tổng − mastered − stillLearning   (gồm cả thẻ chưa từng đụng tới)
percent       = làm tròn(mastered / tổng × 100, 1 chữ số thập phân)  (deck rỗng = 0.0)
```

Dùng cho: bảng điều khiển deck, gợi ý "học tiếp", huy động hoàn thành.

---

## 9. Hướng dẫn tích hợp cho client

Nên:

- Lưu JWT; gửi `Authorization: Bearer` ở **mọi** lệnh gọi study (kể cả GET quiz/progress)
- Hiển thị câu hỏi: `prompt` + `options` (server đã xáo trộn; xáo lại tùy ý)
- Gọi đúng một lệnh `answer` cho mỗi câu; điều khiển màn hình tiếp theo từ phản hồi (`correct`, `state`, `streak`)
- Khi sai, hiện đáp án đúng từ `correctCardId`/`correctTerm`
- Dùng `?state=STILL_LEARNING&starred=true` cho chế độ "luyện từ khó"
- Xem `questions: []` là "không còn gì để học" (màn hình trạng thái rỗng)

Không nên:

- Không bao giờ tin cờ đúng/sai từ client — server cũng không nhận cờ này
- Không cache câu hỏi kèm đáp án tự đánh dấu giữa các phiên — chấm điểm luôn ở server
- Không tự đoán trạng thái; luôn đọc `state`/`deckProgress` từ phản hồi answer/progress

Vòng lặp phiên học gợi ý:

```
while progress.percent < 100:
    quiz  = GET /study/quiz?state=STILL_LEARNING (nếu rỗng: bỏ filter)
    for q in quiz.questions:
        answer = POST /study/answer { cardId: q.cardId, selectedCardId: đã_chọn }
        hiển thị phản hồi(answer)
    if quiz.questions == [] và không còn NOT_LEARNED: break
```

---

## 10. Quyết định thiết kế & Lý do

| Quyết định | Lý do |
|---|---|
| Tiến độ đặt trong bảng `card_progress` riêng, không dùng cột trên `cards` | Trạng thái thuộc về từng người dùng; thẻ là nội dung dùng chung. Cột đơn sẽ phá deck nhiều người học |
| Thiếu dòng = NOT_LEARNED | Thẻ chưa học không tốn lưu trữ; không cần migration backfill |
| Chấm điểm chỉ ở server | Client không thể gian lận bằng cách gửi `correct: true` |
| Phương án nhiễu lấy từ cùng deck | Dễ nhầm lẫn hợp lý (ambition vs ambitous) — khó hơn và có lợi hơn từ ngẫu nhiên |
| Ghi lịch sử trả lời ngay, endpoint ôn lỗi để sau | Ghi mỗi câu trả lời rẻ; sau này làm "danh sách lỗi" + SRS không cần backfill |
| Bỏ qua = sai | Phù hợp nguyên tắc học thận trọng: chỉ kiến thức đã chứng minh mới được tính |
| Mọi endpoint study đều cần xác thực | Tiến độ mang tính cá nhân; học ẩn danh không có gì để lưu |

---

## 11. Lộ trình phát triển

1. **Danh sách ôn lỗi** — `GET /api/decks/{id}/study/mistakes` dựa trên `study_attempts` (schema đã sẵn sàng)
2. **Chiều ngược** — trắc nghiệm `TERM_TO_DEFINITION` (đề = từ khóa, phương án = định nghĩa)
3. **Chế độ FLASHCARD / WRITING** — thêm giá trị `StudyMode` mới + nới lỏng CHECK của V5 trong migration mới
4. **Lên lịch SRS** — dùng lịch sử trả lời để lên lịch ôn (kiểu SM-2), dần thay thế ngưỡng cố định 2 lần đúng

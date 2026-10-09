# Ôn tập SRS (Spaced Repetition System) — Tài liệu nghiệp vụ

> Tài liệu này mô tả nghiệp vụ và luồng hoạt động của tính năng **Ôn tập SRS** — phần bài sửa đổi của thuật toán SuperMemo SM-2, hoàn toàn tách biệt với máy trạng thái học MCQ (xem `STUDY.vi.md`).

---

## 1. Tổng quan

Tính năng Ôn tập trả lời câu hỏi: **"Hôm nay tôi cần ôn lại những thẻ nào, và ôn xong thì lần sau gặp lại khi nào?"**

- Người dùng xem mặt **thuật ngữ (term)**, tự nhớ trong đầu định nghĩa, rồi lật thẻ để tự đối chiếu.
- Người dùng **tự chấm** mức độ ghi nhớ của mình theo thang 1–4 (kiểu Anki).
- Backend áp dụng thuật toán **SM-2 sửa đổi** để tính khoảng thời gian đến lần ôn kế tiếp của từng thẻ.
- Hàng đợi ôn tập gom **mọi deck** (không giới hạn theo một deck như chế độ Study MCQ).

### Phân biệt với Study (MCQ)

| | Study (MCQ) | Review (SRS) |
|---|---|---|
| Phạm vi | Theo từng deck | Toàn bộ thẻ đến hạn của user |
| Cách trả lời | Chọn 1 trong 4 phương án | Tự nhớ → lật → tự chấm 1–4 |
| Chấm điểm | Server tự chấm | User tự chấm |
| Bảng dữ liệu | `card_progress` + `study_attempts` | `user_card_progress` |
| Máy trạng thái | NOT_LEARNED → STILL_LEARNING → MASTERED | NEW → LEARNING → REVIEW → MASTERED |

Hai hệ thống **dùng chung thẻ nhưng không ảnh hưởng lẫn nhau**.

---

## 2. Dữ liệu: `user_card_progress`

Mỗi dòng = trạng thái SRS của **1 user × 1 thẻ**, ràng buộc unique `(user_id, card_id)`.

| Cột | Ý nghĩa |
|---|---|
| `interval_days` | Số ngày đến lần ôn kế tiếp. `0` = ôn lại sau vài **phút** (chu kỳ học lại) |
| `repetition` | Số lần trả lời tốt liên tiếp (GOOD/EASY) tính từ lần quên gần nhất |
| `ease_factor` | Hệ số dễ — càng cao thì interval tăng càng nhanh |
| `next_review_date` | Mốc thời gian đến hạn; thẻ được coi là đến hạn khi `next_review_date <= now` |
| `status` | `NEW` / `LEARNING` / `REVIEW` / `MASTERED` |

**Tạo dòng lazily:** dòng đầu tiên chỉ được tạo khi user chấm điểm thẻ đó lần đầu (trạng thái ban đầu `NEW`, `ease = 2.5`, `interval = 0`, `next_review_date = now` → thẻ mới lập tức đến hạn trong lần lấy hàng đợi tiếp theo).

---

## 3. Trạng thái vòng đời

```
NEW ──(GOOD/EASY)──► LEARNING ──(interval ≥ 1 ngày)──► REVIEW ──(interval ≥ 21 ngày)──► MASTERED
  ▲                    │                                 │                                  │
  └──── AGAIN/HARD (lần đầu) đưa về LEARNING ◄───────────┴──────────────────────────────────┘
        (AGAIN luôn đưa thẻ về LEARNING bất kể đang ở đâu)
```

Ngữ nghĩa theo interval:

- `interval_days = 0` → đang trong chu kỳ học lại, ôn retry sau vài phút (LEARNING)
- `1 ≤ interval_days < 21` → REVIEW (đã tốt nghiệp sang cấp ngày)
- `interval_days ≥ 21` → MASTERED (`MASTERED_INTERVAL_DAYS = 21`)

`MASTERED` **không phải trạng thái kết thúc** — nếu sau này chấm AGAIN, thẻ quay về LEARNING ngay.

---

## 4. Thang chấm điểm (quality 1–4)

| Score | Tên | Nghĩa | Điều chỉnh ease |
|---|---|---|---|
| 1 | `AGAIN` | Quên hoàn toàn | **−0.20** |
| 2 | `HARD` | Nhớ nhưng rất khó khăn | **−0.15** |
| 3 | `GOOD` | Nhớ đúng | 0 |
| 4 | `EASY` | Nhớ tức thì | **+0.10** |

Ease bị **kẹp trong khoảng [1.3, 2.8]** (`MIN_EASE`/`MAX_EASE`), khởi tạo 2.5. Ease thấp = thẻ khó = interval tương lai tăng chậm.

---

## 5. Thuật toán xếp lịch (per-chấm-điểm)

Hằng số:

| Hằng số | Giá trị | Vai trò |
|---|---|---|
| `INITIAL_EASE` | 2.5 | Ease khởi tạo |
| `MIN_EASE` / `MAX_EASE` | 1.3 / 2.8 | Kẹp ease |
| `MAX_INTERVAL_DAYS` | 365 | Trần interval |
| `MASTERED_INTERVAL_DAYS` | 21 | Ngưỡng MASTERED |
| `RELEARN_MINUTES` | 10 phút | Delay học lại sau khi quên |
| `HARD_MULTIPLIER` | 1.2 | Hệ số tăng chậm khi HARD |
| `EASY_BONUS` | 1.3 | Nhân thêm khi EASY |

### 5.1 AGAIN (1) — Lapse
- `repetition = 0`, `interval = 0`
- `ease −0.20` (kẹp biên)
- `status = LEARNING`
- **Ôn lại sau 10 phút** (`next_review_date = now + 10 phút`)

### 5.2 HARD (2) — Struggle
- `ease −0.15`
- Nếu `status = NEW` **hoặc** `repetition = 0` (chưa từng nhớ ngon): về chu kỳ học lại → `interval = 0`, `status = LEARNING`, ôn lại sau **10 phút**
- Ngược lại: `interval = round(interval × 1.2)`, kẹp `[1, 365]`, `status` theo interval mới, hẹn sau `interval` ngày

### 5.3 GOOD (3) — Correct
- `repetition++`
- Lần đầu nhớ ngon (`repetition == 1`): `interval = 1` ngày
- Các lần sau: `interval = round(interval × easeFactor)`, kẹp `[1, 365]`
- Ease **không đổi**

### 5.4 EASY (4) — Instant recall
- `repetition++`
- Lần đầu nhớ ngon: `interval = 3` ngày (nhảy cóc nhanh hơn GOOD)
- Các lần sau: `interval = round(interval × easeFactor × 1.3)`, kẹp `[1, 365]`
- `ease +0.10`

### 5.5 Ví dụ

Thẻ mới, ease 2.5, user chấm liên tiếp:

| Lần | Chấm | repetition | interval | Trạng thái | Hẹn ôn lại |
|---|---|---|---|---|---|
| 1 | GOOD | 1 | 1 ngày | REVIEW | +1 ngày |
| 2 | GOOD | 2 | round(1×2.5)=3 ngày | REVIEW | +3 ngày |
| 3 | EASY | 3 | round(3×2.5×1.3)=10 ngày | REVIEW | +10 ngày |
| 4 | GOOD | 4 | round(10×2.5)=25 ngày | **MASTERED** | +25 ngày |
| 5 | AGAIN | 0 | 0 | LEARNING | +10 phút |

---

## 6. API

Tất cả endpoint **yêu cầu JWT** (nằm dưới rule `anyRequest().authenticated()`).

### 6.1 Lấy hàng đợi đến hạn

```
GET /api/reviews/due?limit=20
Authorization: Bearer <token>
```

- `limit` (mặc định 20, kẹp trong `[1, 100]`): số thẻ tối đa trả về
- Sắp xếp: **đến hạn lâu nhất trước** (oldest due first)
- Trả về:

```json
{
  "dueCount": 2,
  "serverTime": "2026-10-09T13:39:18.573Z",
  "items": [
    {
      "cardId": 42,
      "term": "ability",
      "definition": "khả năng",
      "hint": "danh từ",
      "deckId": 7,
      "status": "REVIEW",
      "intervalDays": 3,
      "easeFactor": 2.5,
      "repetition": 2,
      "nextReviewDate": "2026-10-06T00:00:00Z"
    }
  ]
}
```

`dueCount = items.size()` của đợt lấy này (không phải tổng số thẻ đến hạn còn lại sau limit).

### 6.2 Chấm điểm một thẻ

```
POST /api/reviews/{cardId}
Authorization: Bearer <token>
Content-Type: application/json

{ "quality": 3 }
```

- `quality`: bắt buộc, số nguyên `1–4` (`@Min(1) @Max(4)`)
- Không cần `user_id` — lấy từ JWT; chỉ ôn được thẻ của chính mình (theo nghĩa: mỗi user có dòng progress riêng, bất kỳ user nào cũng chấm được thẻ public mà họ đang học — quyền xem thẻ không bị kiểm tra lại ở đây)
- Lần chấm đầu tiên **tự tạo** dòng `user_card_progress`
- Trả về lịch mới:

```json
{
  "cardId": 42,
  "quality": 3,
  "status": "REVIEW",
  "intervalDays": 3,
  "easeFactor": 2.5,
  "repetition": 3,
  "nextReviewDate": "2026-10-12T13:39:18Z"
}
```

Lỗi: `404` card không tồn tại; `400` quality ngoài 1–4.

---

## 7. Luồng người dùng (frontend)

```
Vào /review
   │
   ├─ lỗi mạng/401 ──► Màn lỗi + "Thử lại" / "Về trang chủ"
   │
   ├─ items rỗng ──► "☕ Không có thẻ nào đến hạn ôn tập."
   │
   └─ có thẻ ──► Vòng lặp từng thẻ:
         1. Hiện TERM (mặt trước) + gợi ý (nếu có), thanh tiến độ "Thẻ x / y"
         2. User tự nhớ nghĩa → nhấn Space / Enter / bấm thẻ để LẬT
         3. Hiện DEFINITION (mặt sau) + 4 nút chấm:
              [ Quên / Lại = 1 ] [ Khó = 2 ] [ Được = 3 ] [ Dễ = 4 ]
            (hoặc phím 1–4)
         4. POST /api/reviews/{cardId} {quality}
         5. Thành công → sang thẻ kế (un-lật); hết hàng đợi → màn hoàn thành 🎉
```

Phím tắt: **Space/Enter** = lật · **1–4** = chấm (chỉ sau khi lật).

Điểm lưu ý FE:
- Nút chấm bị khóa khi đang chờ phản hồi (`submitting`) để tránh double-submit.
- Thẻ AGAIN chỉ quay lại trong **phiên hiện tại nếu phiên kéo dài ≥ 10 phút**; nếu không, lần lấy hàng đợi sau (lần truy cập `/review` tiếp theo) sẽ gặp lại.
- FE **không tự tính lịch** — mọi số interval/ease do server trả về là nguồn dữ liệu duy nhất.

---

## 8. Giới hạn & quy ước

- Batch tối đa **100** thẻ/lần lấy (`MAX_DUE_BATCH`) — hàng đợi dài sẽ quay lại lần lấy sau.
- Tran interval **365 ngày** — thẻ MASTERED lâu năm vẫn thỉnh thoảng được nhắc.
- AGAIN/HARD-lần-đầu đẩy thẻ về sau **10 phút**, không phải ngay lập tức — tránh lặp sát nhau trong cùng tích tắc.
- Kết quả chấm là **tự đánh giá**: server không kiểm chứng đúng/sai (khác với MCQ), độ chính xác của SRS phụ thuộc trung thực của người học.
- Trạng thái `status`, `easeFactor`, `repetition` trong response chỉ mang tính **tham khảo hiển thị**; FE không cần lưu.

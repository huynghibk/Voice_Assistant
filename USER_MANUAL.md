# VoiceAssistant — User Manual & Case Flow

**Phiên bản tài liệu:** 1.0  
**Ứng dụng:** VoiceAssistant  
**Nền tảng:** Android 8.0+ (API 26+)

---

## 1. Mục đích ứng dụng

**VoiceAssistant** là trợ lý giọng nói trên Android, giúp bạn:

1. **Nghe văn bản** (Text-to-Speech) ngay trên máy  
2. **Nói → chữ** (Speech-to-Text) để nhập nhanh vào ô đọc  
3. **Dịch văn bản / quét chữ camera** (online Gemini hoặc offline ML Kit)  
4. **Clone giọng**, quản lý thư viện giọng  
5. **Soạn dự án văn bản dài**, xuất audio, chỉnh timeline đa track  
6. **Tìm kiếm / hỏi AI** và đọc kết quả bằng TTS  

Ưu tiên: xử lý trên thiết bị khi có thể; chỉ dùng mạng khi cần (dịch online, tìm web).

---

## 2. Tổng quan cấu trúc app (Bottom navigation)

| Tab | Tên | Việc chính |
|-----|-----|------------|
| 1 | **TTS** | Nhập/ghi âm → phát TTS → xuất/chia sẻ |
| 2 | **Dịch** | Dịch văn bản + quét camera OCR |
| 3 | **Studio** | Dự án văn bản + Timeline audio (gộp 2 chức năng) |
| 4 | **Voices** | Thư viện giọng + clone giọng |
| 5 | **Search** | Tìm kiếm / trợ lý AI, đọc kết quả |
| 6 | **Cài đặt** | Ngôn ngữ giao diện + hướng dẫn |

Màn phụ (không nằm bottom bar):

- **Project Editor** — soạn / import / dịch trong dự án  
- **Clone Voice** — ghi mẫu → tạo giọng clone  
- **Camera Scanner** — chụp & OCR & dịch nhanh  

---

## 3. Sơ đồ tổng thể (high-level)

```text
                    ┌──────────────────────────────────────┐
                    │           VoiceAssistant             │
                    └──────────────────────────────────────┘
                                      │
        ┌─────────────┬───────────────┼───────────────┬─────────────┐
        ▼             ▼               ▼               ▼             ▼
     [TTS]        [Dịch]          [Studio]        [Voices]      [Search]
        │             │               │               │             │
   nhập/ghi âm    dịch text      Projects tab    preset/clone   hỏi/tìm
   Phát TTS       quét OCR       Timeline tab    lưu giọng      đọc KQ
   Xuất/Share     → gửi TTS      Editor/Render
                                      │
                                      ▼
                              [Cài đặt] ngôn ngữ UI
```

### Luồng dữ liệu cốt lõi (TTS)

```text
Văn bản (gõ / dán / ghi âm / từ Dịch-OCR)
        │
        ▼
  TextSplitter (chia câu)
        │
        ▼
  Piper ONNX  ──không có model──►  System TTS (Android)
        │
        ▼
  Voice Conversion (nếu giọng clone có embedding)
        │
        ▼
  PcmStreamPlayer → loa / BT / Cast
        │
        ▼
  Export WAV/MP3/M4A/FLAC → Share (FileProvider)
```

### Luồng dịch

```text
Văn bản / OCR
   │
   ├─ Có mạng + có Gemini key ──► GeminiOnlineEngine
   │                                    │ (lỗi)
   └─ Offline / lỗi online ─────────────┴──► MlKitOfflineEngine
                                                    │
                                                    ▼
                                              Text đã dịch
```

---

## 4. Chức năng theo màn hình

### 4.1 Tab TTS (Home)

**Mục đích:** Đọc nhanh mọi văn bản.

| Thành phần | Mô tả |
|------------|--------|
| Giọng đọc | Chọn preset (Tiếng Việt / English…) hoặc giọng đã clone |
| Ô văn bản | Gõ, dán, hoặc nhận text từ Dịch/Camera |
| **Ghi âm → Văn bản** | Speech-to-Text hệ thống → đổ vào ô nhập |
| Phát / Dừng | Stream TTS theo câu |
| Xuất / Chia sẻ | Xuất file audio, mở share sheet hệ thống |
| Định dạng xuất | WAV, MP3, … + chất lượng |

**Quyền cần:** Micro (`RECORD_AUDIO`) khi ghi âm.

---

### 4.2 Tab Dịch (Translate)

**Mục đích:** Tập trung dịch & quét chữ (tách khỏi TTS).

| Thành phần | Mô tả |
|------------|--------|
| Chọn ngôn ngữ nguồn / đích | VI, EN, 简体, 繁體, JA, KO, FR |
| Ô văn bản | Nhập nội dung cần dịch |
| Nút **Dịch** | Online Gemini → fallback ML Kit offline |
| Icon / nút **Camera** | Mở quét OCR |
| **Đưa vào bộ đọc TTS** | Gửi text đã dịch sang tab TTS |

**Camera Scanner (màn phụ):**

1. Xin quyền Camera  
2. Xem preview (CameraX)  
3. **Chụp & Dịch nhanh** → OCR (ML Kit) → dịch  
4. **Đưa vào bộ đọc TTS** hoặc trả về tab Dịch  

---

### 4.3 Tab Studio (Projects + Timeline)

**Mục đích:** Một nơi làm việc với dự án dài và dựng audio.

#### Tab con: Dự án (Projects)

- Danh sách dự án đã lưu  
- Tạo mới / mở Project Editor  
- Trong Editor: soạn text, import TXT/DOCX/EPUB/PDF, dịch offline, liên kết giọng, mở Timeline  

#### Tab con: Timeline

- Thêm / import clip audio  
- Chỉnh clip (vị trí, độ dài, …)  
- Render bằng FFmpeg filtergraph  
- Xuất bản theo định dạng  

---

### 4.4 Tab Voices

**Mục đích:** Quản lý giọng đọc.

- Xem giọng preset & giọng clone  
- **Clone Voice:** ghi mẫu (micro, NoiseSuppressor/AGC) → tạo embedding → lưu  
- Pipeline kỹ thuật: Piper (base) → Voice Conversion → giọng đích  

---

### 4.5 Tab Search

**Mục đích:** Hỏi / tìm nhanh rồi nghe kết quả.

- Lịch sử tìm (Room)  
- Tìm trong dự án (FTS)  
- Tìm web (DuckDuckGo khi có mạng)  
- Đọc kết quả bằng TTS  

---

### 4.6 Tab Cài đặt

| Mục | Mô tả |
|-----|--------|
| **Ngôn ngữ** | EN / VI / 简体 / 繁體 / JA / KO / FR — đổi cả giao diện |
| Giọng đọc / Xuất | Gợi ý dùng tab Voices & TTS |
| Hướng dẫn / Giới thiệu | Help ngắn trong app |

---

## 5. Case flow người dùng (User journeys)

### Case A — Đọc nhanh một đoạn văn

```text
Mở app → Tab TTS
  → Dán / gõ văn bản
  → Chọn giọng
  → [Phát] → nghe
  → (tuỳ chọn) [Xuất] / [Chia sẻ]
```

**Kết quả mong đợi:** Nghe được nội dung; có file nếu đã xuất.

---

### Case B — Nói rồi đọc lại bằng TTS

```text
Tab TTS
  → Cấp quyền Micro (lần đầu)
  → [Ghi âm → Văn bản] → nói rõ
  → Text hiện trong ô nhập
  → [Dừng ghi âm] (nếu cần)
  → [Phát]
```

**Kết quả mong đợi:** Lời nói thành chữ → TTS đọc lại.

---

### Case C — Dịch rồi đọc

```text
Tab Dịch
  → Chọn Từ / Sang (vd: EN → VI)
  → Dán text
  → [Dịch]
  → [Đưa vào bộ đọc TTS]
  → Tab TTS nhận text → [Phát]
```

**Offline:** không mạng hoặc không có Gemini key → ML Kit (có thể tải model lần đầu).  
**Online:** có mạng + `gemini.api.key` trong `local.properties`.

---

### Case D — Quét biển hiệu / giấy tờ rồi đọc

```text
Tab Dịch → icon Camera (hoặc Chụp & Dịch)
  → Cấp quyền Camera
  → Hướng khung hình vào chữ
  → [Chụp & Dịch nhanh]
  → Xem text OCR / đã dịch
  → [Đưa vào bộ đọc TTS]
  → [Phát] trên tab TTS
```

---

### Case E — Dự án văn bản dài + xuất

```text
Tab Studio → Dự án → [+] tạo mới
  → Soạn / Import file
  → (tuỳ chọn) dịch trong editor
  → Lưu
  → Phát thử / xuất từ editor hoặc mang text sang TTS
```

---

### Case F — Dựng timeline audio

```text
Tab Studio → Timeline
  → Import / thêm clip
  → Chỉnh vị trí, độ dài
  → Render / Xuất
```

---

### Case G — Clone giọng rồi dùng đọc

```text
Tab Voices → Clone
  → Cấp quyền Micro
  → Ghi mẫu nói rõ 1–3+ giây
  → Lưu giọng
  → Tab TTS → chọn giọng vừa clone → [Phát]
```

**Lưu ý:** Chưa có Piper `.onnx` thì TTS dùng giọng hệ thống; clone/VC đầy đủ khi có model pack.

---

### Case H — Đổi ngôn ngữ giao diện

```text
Tab Cài đặt → Ngôn ngữ → chọn chip (VI/EN/中文/…)
  → App áp dụng locale ngay
```

---

### Case I — Tìm thông tin rồi nghe

```text
Tab Search
  → Nhập câu hỏi / từ khoá
  → Xem kết quả (local / web)
  → Đọc kết quả bằng TTS (nếu màn hình hỗ trợ)
```

---

## 6. Ma trận quyền hệ thống

| Quyền | Khi nào cần |
|-------|-------------|
| `RECORD_AUDIO` | Ghi âm STT (TTS), Clone Voice |
| `CAMERA` | Quét OCR |
| `INTERNET` / `ACCESS_NETWORK_STATE` | Dịch Gemini, tải model ML Kit, Search web |
| Bluetooth / Cast liên quan | Định tuyến loa / tai nghe |

---

## 7. Công nghệ phía sau (cho người hiểu kỹ thuật)

| Lớp | Thành phần |
|-----|------------|
| UI | Jetpack Compose, Navigation, Material 3 |
| DI | Hilt |
| Data | Room, DataStore (ngôn ngữ), FileProvider |
| TTS | Piper ONNX → System TTS fallback → AudioTrack stream |
| STT | Android `SpeechRecognizer` |
| Dịch | `TranslationRepository`: Gemini → ML Kit |
| OCR | ML Kit Text Recognition + CameraX |
| Audio studio | FFmpeg-kit (audio), Timeline filtergraph |
| Search | Room FTS + DuckDuckGo |

**File cấu hình quan trọng:**

- `local.properties` → `gemini.api.key=...` (dịch online)  
- `assets/models/piper/*.onnx` — model TTS offline  
- `assets/models/vc/converter.onnx` — voice conversion (tuỳ chọn)  

---

## 8. Checklist “app sẵn sàng dùng”

- [ ] Cài app, mở được 6 tab  
- [ ] TTS: dán text → Phát có tiếng  
- [ ] TTS: Ghi âm → chữ vào ô → Phát  
- [ ] Dịch: dịch EN↔VI (online hoặc offline)  
- [ ] Camera: quét chữ → gửi TTS  
- [ ] Studio: tạo dự án + xem Timeline  
- [ ] Voices: xem preset; clone nếu có micro  
- [ ] Cài đặt: đổi ngôn ngữ UI  
- [ ] Xuất / chia sẻ file audio  

---

## 9. Xử lý sự cố thường gặp

| Hiện tượng | Hướng xử lý |
|------------|-------------|
| Phát ra tiếng “hú” / tone | Thiếu model Piper → đang demo cũ; bản hiện tại dùng System TTS. Cài Google Text-to-speech trên máy. |
| “Không khởi tạo được giọng đọc hệ thống” | Cài/ cập nhật **Google Text-to-speech**; thử lại. |
| Dịch online fail | Thêm `gemini.api.key`; hoặc dùng offline (cần mạng lần đầu tải model ML Kit). |
| Ghi âm không chạy | Cấp quyền Micro; kiểm tra emulator có mic ảo. |
| Camera không mở | Cấp quyền Camera; máy/emulator cần camera. |
| Emulator ABI / native lib | APK cần `x86_64` (đã cấu hình); Clean + Reinstall. |
| Tên app | Launcher hiển thị **VoiceAssistant**. |

---

## 10. Tóm tắt một dòng

> **VoiceAssistant** = nhập chữ (gõ / nói / quét) → dịch (nếu cần) → đọc bằng TTS / clone giọng → xuất & chia sẻ; kèm Studio dự án+timeline và Search trợ lý.

---

*Tài liệu này phản ánh cấu trúc app hiện tại (TTS · Dịch · Studio · Voices · Search · Cài đặt). Cập nhật khi thêm màn hoặc đổi luồng chính.*

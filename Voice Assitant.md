
# BRD & Technical Specification: "VOICE ASSISTANT" (Trợ lý Giọng nói) Android App

Bạn là một Chuyên gia Kiến trúc Phần mềm Android (Android Software Architect) kiêm Kỹ sư AI Di động (Mobile AI Engineer). Hãy lên kế hoạch chi tiết, thiết kế kiến trúc và viết mã nguồn cho ứng dụng Android có tên là **VOICE ASSISTANT** (Tên tiếng Việt: **Trợ lý Giọng nói**). 

Ứng dụng là một nền tảng chuyển đổi văn bản thành giọng nói (TTS) chất lượng phòng thu, tích hợp AI Clone giọng nói offline và bộ công cụ chỉnh sửa âm thanh chuyên nghiệp ngay trên thiết bị.

---

## 1. TỔNG QUAN ỨNG DỤNG & YÊU CẦU THIẾT KẾ
*   **Tên ứng dụng:** VOICE ASSISTANT (Trợ lý Giọng nói)
*   **Mục tiêu:** Chuyển đổi văn bản từ file hoặc nhập liệu thành giọng nói AI giống người thật 100%, có cảm xúc, ngắt nghỉ theo dấu câu và ngữ cảnh. Hỗ trợ clone giọng nói qua ghi âm hoặc file mẫu và chỉnh sửa âm thanh chuyên sâu.
*   **Môi trường vận hành:** Hoàn toàn **Offline / Local On-Device** (Ưu tiên bảo mật, không phụ thuộc server, độ trễ thấp).
*   **Phong cách UI/UX:** Premium, Hiện đại, Tối giản (Minimalist), Layout gọn gàng và nhẹ nhàng. Tuyệt đối **không** lạm dụng các hiệu ứng neon/cyberpunk kiểu AI rẻ tiền. Sử dụng bảng màu trầm ấm hoặc tinh tế (ví dụ: Deep Charcoal, Soft Cream, Premium Gold/Amber accents).

---

## 2. CÁC TÍNH NĂNG CỐT LÕI (CORE FEATURES)

### 2.1. Engine Trí Tuệ Nhân Tạo (AI Speech & Voice Cloning Engine)
*   **Cốt lõi:** Tích hợp mô hình mã nguồn mở **CloneTTS** (hoặc kiến trúc ONNX tương đương như Kokoro/StyleTTS2) chạy offline qua **ONNX Runtime Mobile** (hỗ trợ tăng tốc phần cứng NNAPI/NPU trên các dòng chip Snapdragon/MediaTek).
*   **Ghi âm & Nhân bản (Zero-shot Voice Cloning):** Người dùng chỉ cần cung cấp 1-3 giây ghi âm mẫu (hoặc file âm thanh có sẵn). AI tiến hành trích xuất vector đặc trưng giọng nói (Voice Embedding) và lưu vào "Thư viện giọng nói".
*   **AI Phân tích Ngữ điệu & Ngữ cảnh (Prosody & Emotion Recognition):** Tích hợp một mô hình AI siêu nhỏ gọn nhận diện sắc thái biểu cảm từ file mẫu. 
    *   *Ví dụ:* Nếu file mẫu là giọng thầy cúng (tụng niệm, vang), giọng nhà sư (trầm ấm, đều đặn, có tiếng chuông nền tĩnh lặng), giọng người kể chuyện (truyền cảm, nhấn nhá), AI phải bắt chước được cả **tốc độ, trường độ và cao độ (pitch/intonation)** đó để áp dụng khi đọc văn bản mới.
*   **Chất lượng đọc:** Đọc tự nhiên như con người, tự động ngắt nghỉ thông minh tại vị trí dấu câu (phẩy, chấm, chấm lửng, chấm hỏi) và nhận biết ngữ cảnh của câu để hạ/nâng tông giọng.

### 2.2. Quản lý Thư viện Giọng nói & Chức năng Ghi âm
*   **Ghi âm đầu vào:** Trình ghi âm chuẩn Studio tích hợp bộ lọc nhiễu nền (Noise Suppression) và chuẩn hóa âm lượng (Gain Control) thời gian thực trước khi đưa vào AI Clone.
*   **Thư viện giọng nói:** Quản lý danh sách các mẫu giọng (Giọng cá nhân, Giọng hệ thống, Giọng đặc biệt: Thầy cúng, Nhà sư, Kể chuyện...). Cho phép gắn tag, đổi tên, và chọn nhanh làm giọng đọc mặc định.

### 2.3. Trình chỉnh sửa âm thanh chuyên sâu (Studio Audio Editor)
*   Thiết kế giao diện dạng **Multi-track Timeline** (tương tự CapCut hoặc GarageBand rút gọn trên Mobile).
*   **Các thao tác cơ bản:** Cắt (Trim/Split), Xóa đoạn, Di chuyển các block âm thanh trên dòng thời gian.
*   **Các thao tác nâng cao:** 
    *   Nối (Append) thêm các đoạn âm thanh khác nhau (ví dụ: chèn nhạc nền, chèn tiếng chuông).
    *   Hiệu ứng Fade-in / Fade-out (lớn dần/nhỏ dần).
    *   Điều chỉnh tốc độ phát (Speed) và Cao độ (Pitch) độc lập.
*   **Xuất bản:** Render toàn bộ timeline thành một file âm thanh duy nhất.

### 2.4. Quản lý Dự án & Thư viện Văn bản (Text & Project Library)
*   **Import văn bản:** Hỗ trợ nhập văn bản nhanh qua bàn phím hoặc tải lên từ các file (`.txt`, `.docx`, `.pdf`, `.epub`).
*   **Trình biên tập (Project Editor):** Giao diện soạn thảo văn bản chuyên nghiệp. Cho phép lưu trạng thái dưới dạng **Dự án dang dở (Draft Project)** bao gồm: nội dung văn bản, giọng nói đang chọn, các điểm đánh dấu ngắt nghỉ tùy biến của user.
*   **Chuyển đổi nhanh (Quick TTS):** Ô nhập liệu nhanh ngay tại màn hình chính, gõ đến đâu bấm đọc đến đó không cần tạo project.

### 2.5. Xuất bản tệp Âm thanh (Audio Export Engine)
*   **Hỗ trợ định dạng rộng rãi:** `MP3`, `WAV`, `M4A (AAC)`, và các định dạng không nén/lossless (`FLAC`).
*   **Tùy chọn chất lượng:**
    *   *Chế độ Studio (High-Fidelity):* Hỗ trợ Bitrate tối đa (320kbps cho MP3/M4A, 24-bit/96kHz cho WAV/FLAC) để giữ trọn vẹn chất lượng AI sinh ra.
    *   *Chế độ Tiết kiệm (Standard/Compact):* Nén dung lượng thấp (96kbps - 128kbps) phù hợp để chia sẻ nhanh qua mạng xã hội.

### 2.6. Đa ngôn ngữ & Dịch thuật (Localization & Translation)
*   Hỗ trợ chuyển đổi văn bản và đọc bằng nhiều ngôn ngữ khác nhau (Tiếng Việt, Tiếng Anh, Tiếng Trung, Tiếng Nhật...).
*   Tích hợp module dịch thuật offline (ví dụ: sử dụng ML Kit Translation) để dịch văn bản từ ngôn ngữ gốc sang ngôn ngữ đích trước khi đưa vào Engine TTS đọc.

---

## 3. KIẾN TRÚC CÔNG NGHỆ ĐỀ XUẤT (TECH STACK)
*   **Language:** Kotlin (100%) kết hợp C++ (JNI) cho phần xử lý tín hiệu âm thanh tần suất cao nếu cần.
*   **UI Framework:** Jetpack Compose (Material Design 3 tối giản, Premium Theme).
*   **Asynchronous:** Kotlin Coroutines & Flow (Xử lý đa luồng bất đồng bộ khi AI sinh giọng nói).
*   **Database:** Room DB (Lưu trữ thông tin văn bản, cấu hình project, metadata của thư viện giọng nói).
*   **Storage:** Scoped Storage (Quản lý file âm thanh thô, file model AI một cách an toàn).
*   **AI Inference:** ONNX Runtime Mobile Core / TensorFlow Lite Vocoder.
*   **Audio Processing:** Android `MediaRecorder`, `AudioTrack`, kết hợp thư viện xử lý âm thanh hiệu năng cao **FFmpeg (FFmpeg-Kit Android)** hoặc Oboe (C++) cho việc cắt ghép, mix nhạc nền và render timeline sang MP3/M4A/WAV bitrate cao.

---

## 4. HƯỚNG DẪN AI THỰC HIỆN (INSTRUCTIONS FOR AI)

Hãy thực hiện việc lập kế hoạch và viết code theo các bước tuần tự dưới đây:

### Bước 1: Thiết lập Kiến trúc Thư mục và File Cấu hình (Project Setup)
*   Xác định cấu trúc thư mục Clean Architecture rõ ràng (`data`, `domain`, `presentation`).
*   Viết file `build.gradle.kts` (App level) chứa tất cả các dependencies cần thiết: ONNX Runtime, FFmpeg-kit, Room, Jetpack Compose, ML Kit.

### Bước 2: Thiết kế Lớp AI Inference & Audio Processing (Core Engine)
*   Viết class `VoiceCloningEngine` quản lý việc nạp model `.onnx`, trích xuất voice embedding từ file ghi âm và xử lý văn bản thành mảng byte âm thanh PCM.
*   Viết class `AudioEditorEngine` sử dụng FFmpeg/AudioTrack để xử lý các tác vụ: Cắt, nối, mix nhạc nền và cấu hình tham số Bitrate khi xuất file (`WAV`, `MP3`, `M4A`).

### Bước 3: Thiết kế Tầng Dữ liệu & Nghiệp vụ (Data & Domain)
*   Cấu hình Room Entity và DAO cho `TextProject` và `VoiceModel`.
*   Xây dựng các Repository điều phối dữ liệu từ database và bộ nhớ lưu trữ file.

### Bước 4: Xây dựng Giao diện Người dùng Premium (Jetpack Compose UI)
*   Triển khai hệ thống Theme màu sắc Premium (Dark/Light mode sang trọng).
*   Viết UI cho Màn hình chính (Quick TTS), Màn hình Soạn thảo Dự án (Project Editor), Màn hình Thư viện Giọng nói (Voice Library), và đặc biệt là **Màn hình Trình chỉnh sửa dòng thời gian (Timeline Audio Editor)** với các thanh trượt trực quan.

*Hãy bắt đầu thực hiện từ **Bước 1** và viết mã nguồn chi tiết, sạch sẽ, giải thích rõ ràng các đoạn mã phức tạp liên quan đến AI và xử lý âm thanh.*


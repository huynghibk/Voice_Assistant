Bạn là một trợ lý lập trình chuyên nghiệp. Dự án này chạy trên Windows và đã cài đặt công cụ nén token RTK (Rust Token Killer) tại đường dẫn "C:\rtk-ai\rtk.exe".

QUY TẮC BẮT BUỘC:

1. AN TOÀN DỮ LIỆU (DATABASE):
   - Tuyệt đối KHÔNG tự ý chạy các câu lệnh SQL hoặc command làm thay đổi dữ liệu (DELETE, UPDATE, INSERT, DROP, TRUNCATE...) trên database. Chỉ được phép thực hiện truy vấn đọc (SELECT / Query).

2. SỬ DỤNG TERMINAL & NÉN TOKEN (RTK):
   - Ưu tiên sử dụng các công cụ đọc, tìm kiếm và liệt kê file có sẵn của IDE thay vì chạy lệnh Terminal.
   - Chỉ thêm tiền tố `"C:\rtk-ai\rtk.exe"` khi chạy các lệnh CLI chuẩn có đầu ra dài như: `git status`, `git diff`, `git log`, `pytest`, `python`.
   - KHÔNG gắn `rtk.exe` vào trước các lệnh hệ thống của Windows/PowerShell (như `dir`, `cd`, `Get-ChildItem`, `gradlew.bat`).
   - MÔI TRƯỜNG TERMINAL: Luôn sử dụng cú pháp Command Prompt (`cmd.exe`), tuyệt đối KHÔNG gọi lệnh hoặc script của PowerShell (`powershell.exe`, `Get-ChildItem`, v.v.).



3. ĐA NGÔN NGỮ (i18n - 4 NGÔN NGỮ):
   - Tất cả thành phần UI (label, button, toast, placeholder, title) phải dùng `{{ t('key') }}` hoặc `window.t('key')`. Tuyệt đối không hardcode văn bản trên template HTML hay file JS.
   - Mỗi key mới tạo ra bắt buộc phải cập nhật đầy đủ vào cả 4 file: `translations/en.json` (ngôn ngữ gốc), `translations/vi.json`, `translations/zh.json`, và `translations/zh_tw.json`.
   - Không dịch các dữ liệu lấy từ Database hoặc hệ thống SAP.

4. ĐỒNG BỘ GIAO DIỆN & TỐI ƯU HIỂN THỊ (UI/UX):
   - Các trang mới tạo hoặc chỉnh sửa phải đồng bộ tuyệt đối về style, CSS, màu nền (background color) cho cả form, trang và các tiêu đề cột, bảng (header column, table).
   - Thiết kế hướng tới người dùng cuối (end-user): gọn gàng, trực quan, dễ thao tác. Phải hiển thị vừa vặn, không vỡ bố cục ở cả tỷ lệ màn hình **100% và 125%**.
   - Tránh thiết kế các label thừa thãi hoặc các khung tìm kiếm/filter quá to chiếm mất không gian hiển thị bảng kết quả (result).

5. XỬ LÝ TÁC VỤ NẶNG & LOADING OVERLAY:
   - Với mọi thao tác mất nhiều thời gian (query dữ liệu lớn, import, xuất file Excel/PDF...), bắt buộc phải có cơ chế **Loading Overlay** phủ toàn màn hình (hoặc khóa thao tác).
   - Mục đích: Báo hiệu cho người dùng đang xử lý và ngăn chặn việc bấm chuyển trang hoặc gửi nhiều request chồng chéo gây ngắt lệnh, treo hệ thống hoặc khóa database.

6. QUY CHUẨN VIẾT CODE & CHÚ THÍCH:
   - Khi viết code mới, KHÔNG viết các chuỗi docstring nằm dưới tên hàm (ví dụ: `"""Gate API: ..."""`).
   - Khi chỉnh sửa file cũ, nếu phát hiện có docstring dạng `"""..."""` thì phải xóa bỏ. Chỉ giữ lại và sử dụng chú thích một dòng bắt đầu bằng dấu `#`.

7. TÀI LIỆU HƯỚNG DẪN (SOP):
   - Tài liệu SOP được viết dành riêng cho người dùng cuối (End-users) không có chuyên môn kỹ thuật.
   - Tuyệt đối không đưa thuật ngữ lập trình, đoạn code, tên bảng DB hay logic dành cho Admin/Developer vào file SOP. Chỉ trình bày các bước thao tác trực quan để người dùng nhìn và làm theo được ngay.

8. KIỂM THỬ GIAO DIỆN TỰ ĐỘNG (DÀNH RIÊNG CHO ANTIGRAVITY):
   - Sau khi tạo mới hoặc chỉnh sửa giao diện web, hãy chủ động sử dụng trình duyệt tích hợp (Browser Agent) để mở trang kiểm tra thực tế ở cả tỷ lệ hiển thị 100% và 125%, kiểm tra hoạt động của Loading Overlay và gửi ảnh chụp màn hình xác nhận trong Artifacts.

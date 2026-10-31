# TV3 — Lịch học và đặt lịch

Phạm vi theo ảnh phân công đã được nhóm thống nhất: TV3 sở hữu `schedule/`,
`calendar/`, `notification/`, `MainActivity.java`, `activity_main.xml` và XML có tiền tố tương ứng.
Quyền sở hữu `schedule/` trong AGENTS.md/PROJECT_CONTRACT.md hiện còn theo phân công cũ.

## Đã triển khai

- CRUD SQLite thực tế trên `study_schedules`, dùng schema của DatabaseHelper qua repository riêng của TV3.
- Repository ràng buộc tài khoản Student; truy vấn ra studentId từ bảng students.
- Kiểm tra môn còn tồn tại, ngày thực (kể cả năm nhuận), giờ HH:mm, địa điểm, trạng thái.
- Lịch cùng sinh viên/cùng ngày bị trùng nếu `old.startTime < new.endTime AND old.endTime > new.startTime`.
- Kiểm tra và lưu trong cùng transaction; sửa loại chính ID hiện tại; lịch Cancelled không chặn giờ.
- Chuyển Cancelled sang Pending/Confirmed kiểm tra trùng lại. Student tự quản lý ba trạng thái theo đề.
- Lịch quá khứ vẫn xem/sửa/ghi nhận được; chỉ lịch trong tương lai mới có nhắc học. Không hỗ trợ buổi qua đêm.
- Tìm môn/mã môn/địa điểm/ngày/giờ (không dấu), lọc trạng thái, lịch ngày/tuần/tháng.
- Tuần bắt đầu thứ Hai. Số chấm trong lịch đếm tất cả lịch chưa hủy, độc lập bộ lọc tìm kiếm/trạng thái.
- Form dùng picker, giữ bản nháp khi xoay màn hình; database chạy trên executor nền.
- Nhắc khoảng 15 phút trước buổi học; cập nhật/hủy alarm khi sửa/xóa/hủy; thông báo thay đổi.
- Nhắc dùng AlarmManager inexact: hệ thống có thể giao trễ. Không yêu cầu quyền exact alarm.

## TV1: nối phiên sau đăng nhập

TV3 không tạo tài khoản demo hoặc đăng nhập giả. Commit TV1 `73a444a` đã triển khai
API database và seed Admin `admin` / `admin123` khi tạo database mới. MainActivity vẫn
hiển thị trạng thái chưa đăng nhập cho đến khi TV1 cung cấp phiên Student hợp lệ.

Sau khi **xác thực thành công** tài khoản có role chính xác `Student`:

```java
StudentSession.establish(context, account.getId());
startActivity(new Intent(context, MainActivity.class));
```

Khi đăng xuất hoặc chuyển sang Admin:

```java
StudentSession.clear(context); // Xóa phiên và hủy alarm của sinh viên.
```

Import `com.example.studyschedule.schedule.StudentSession`. Cầu nối này dùng SharedPreferences
riêng `study_schedule_student_session`, key `accountId` (long). Không lưu mật khẩu.
Repository luôn kiểm tra tài khoản còn tồn tại, role `Student`, hồ sơ students khớp accountId.
Không dùng accountId thay studentId, không nhận studentId từ Intent bên ngoài.
TV1 phải giữ cầu nối đồng bộ với phiên đăng nhập chính, bao gồm khi phiên hết hạn.
MainActivity hiện là màn hình Student; TV1 điều hướng Admin tới dashboard riêng.
TV2 cung cấp subjects trong cùng SQLite database; chưa có môn thì nút tạo lịch bị khóa.

## TV1: ghép cấu hình thông báo vào AndroidManifest.xml

Theo quy định repository, TV3 không sửa manifest/Gradle của TV1. Không cần thư viện mới.
**Thông báo nền chưa hoạt động cho đến khi TV1 ghép cấu hình sau.** UI có thông báo rõ
và không yêu cầu quyền khi receiver chưa được khai báo.

Thêm trực tiếp dưới `<manifest>`:

```xml
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
```

Thêm bên trong `<application>`:

```xml
<receiver android:name=".notification.ScheduleReminderReceiver" android:exported="false" />
<receiver android:name=".notification.ScheduleRestoreReceiver" android:exported="false">
    <intent-filter>
        <action android:name="android.intent.action.BOOT_COMPLETED" />
        <action android:name="android.intent.action.MY_PACKAGE_REPLACED" />
        <action android:name="android.intent.action.TIME_SET" />
        <action android:name="android.intent.action.TIMEZONE_CHANGED" />
    </intent-filter>
</receiver>
```

Sau đó mở màn hình Student, bấm **Thông báo** để cấp quyền trên Android 13+.
Receiver đọc lại lịch/phiên để không gửi nội dung cũ hoặc lịch đã hủy/xóa.
Sau reboot/đổi múi giờ/cập nhật app, receiver khôi phục nhắc của phiên Student hiện tại.
Force-stop app ngăn alarm cho đến lần mở app tiếp theo. Đổi ngày/giờ/múi giờ sẽ diễn giải
lịch theo giờ địa phương mới. Không đồng bộ đa thiết bị, không gửi push từ server.

Tài liệu Android: [inexact alarms](https://developer.android.com/develop/background-work/services/alarms),
[quyền thông báo](https://developer.android.com/develop/ui/views/notifications/notification-permission).

## Kiểm thử

```powershell
.\gradlew.bat assembleDebug testDebugUnitTest assembleDebugAndroidTest
.\gradlew.bat connectedDebugAndroidTest
```

Unit test: ngày/giờ/trạng thái, năm nhuận, biên thời gian, khoảng lịch.
Instrumentation: SQLite in-memory dùng schema thật của TV1, CRUD, ownership,
rollback khi trùng, hai lịch nối tiếp, hai sinh viên, hai ngày, khôi phục lịch đã hủy.
Fixtures không ghi vào database thật hoặc SharedPreferences đăng nhập.

Kiểm tra thủ công sau tích hợp: đăng nhập hai Student luân phiên; thử tìm không dấu;
xoay màn hình khi điền form; thử tháng 2/năm mới; sửa/hủy/xóa lịch và kiểm tra alarm;
tắt/cấp quyền thông báo, reboot máy, thay đổi múi giờ. Thử Pending và Confirmed
trong 15 phút tới để nhận nhắc sớm, rồi sửa/hủy để kiểm tra nhắc cũ không xuất hiện.

## Kết quả kiểm tra ngày 31/10/2026

- Đã merge fast-forward TV1 `73a444a` vào main, giữ nguyên code TV3. Fixture SQLite dùng
  account IDs 101/102/103 và username riêng để không trùng Admin seed của TV1.
- `assembleDebug testDebugUnitTest assembleDebugAndroidTest --offline --console=plain --no-configuration-cache`
  sau merge và tất cả chỉnh sửa cuối: BUILD SUCCESSFUL (48 giây).
- 10 unit tests thành công: 9 test TV3 và 1 test mẫu có sẵn.
- 8 test SQLite đã biên dịch thành APK test; chưa chạy trên thiết bị (không có thiết bị kết nối,
  yêu cầu mở emulator không được chấp thuận).
- XML của module parse thành công; `git diff --check` không báo lỗi whitespace.
- Chưa kiểm thử giao diện/notification trên emulator; cấu hình manifest và đăng nhập vẫn cần TV1 tích hợp.

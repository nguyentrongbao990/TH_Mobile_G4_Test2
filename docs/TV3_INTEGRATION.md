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
- Student không được khôi phục Cancelled; Admin xác nhận lại phải kiểm tra trùng giờ. Student chỉ tạo/sửa Pending và hủy Cancelled; Confirmed do Admin duyệt. Student sửa lịch đã xác nhận phải gửi duyệt lại.
- Lịch quá khứ vẫn xem/hủy/xóa được; không đặt mới hoặc sửa lịch vào thời điểm đã qua. Chỉ lịch Confirmed trong tương lai mới có nhắc học. Không hỗ trợ buổi qua đêm.
- Tìm môn/mã môn/địa điểm/ngày/giờ (không dấu), lọc trạng thái, lịch ngày/tuần/tháng.
- Tuần bắt đầu thứ Hai. Số chấm trong lịch đếm tất cả lịch chưa hủy, độc lập bộ lọc tìm kiếm/trạng thái.
- Form dùng picker, giữ bản nháp khi xoay màn hình; database chạy trên executor nền.
- Nhắc khoảng 15 phút trước buổi học; cập nhật/hủy alarm khi sửa/xóa/hủy; thông báo thay đổi.
- Nhắc dùng AlarmManager inexact: hệ thống có thể giao trễ. Không yêu cầu quyền exact alarm.

## Tích hợp phiên đăng nhập

Đã pull TV1 `1951660`. `LoginActivity` xác thực rồi dùng `SessionManager.saveSession`;
SessionManager tự đồng bộ cầu nối StudentSession khi đăng nhập/khôi phục phiên. TV3
không cần tự ghi SharedPreferences của TV1 hoặc gọi establish lần nữa.

MainActivity kiểm tra `session.requireLogin(this)` trong mỗi `onResume`, bao gồm mở từ
thông báo, trở lại từ nền và sau xoay màn hình. Phiên không hợp lệ về LoginActivity;
Admin vào MainActivity được chuyển tới StudentListActivity và đóng màn hình Student.
Nội dung Student bị ẩn cho đến khi kiểm tra phiên thành công. Nếu đọc database lỗi,
màn hình cho phép thử lại hoặc đăng xuất thay vì hiện dữ liệu phiên cũ.

Nút **Đăng xuất** trong activity_main.xml gọi `session.logout(this)`. API TV1 xóa cả
auth_session và StudentSession, hủy alarm/thông báo, xóa task cũ và mở LoginActivity;
Back không trở lại được màn hình Student. Nếu một Student khác đăng nhập khi activity
cũ còn tồn tại, host tạo lại fragment theo accountId để không dùng lại form/dữ liệu cũ.
Cùng một tài khoản vẫn giữ fragment khi xoay màn hình.

StudentSession dùng SharedPreferences riêng `study_schedule_student_session`, key
`accountId` (long). Repository kiểm tra role `Student` và hồ sơ students khớp accountId;
không dùng accountId thay studentId hoặc nhận studentId từ Intent bên ngoài.
TV2 cung cấp subjects trong cùng SQLite database; chưa có môn thì nút tạo lịch bị khóa.

## Cấu hình thông báo (TV1 đã ghép)

Theo quy định repository, TV3 không sửa manifest/Gradle của TV1. Commit `1951660` đã
khai báo quyền và receiver bên dưới; không thêm lần nữa. UI vẫn kiểm tra cấu hình
và quyền runtime trước khi hẹn nhắc. Không cần thư viện mới.

Các quyền dưới `<manifest>`:

```xml
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
```

Các receiver bên trong `<application>`:

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
- Cập nhật sau pull `1951660`: TV1 đã nối phiên và manifest; TV3 đã bổ sung guard onResume,
  điều hướng Admin và nút Student logout. Lượt build cho thay đổi mới này chưa chạy vì
  yêu cầu quyền thực thi không được chấp thuận. Các kết quả build ở trên thuộc bản trước.
- Cần kiểm tra thiết bị cho thay đổi mới: đăng nhập Student → về nền → xóa/hết phiên → mở lại;
  logout → Back; mở MainActivity với phiên Admin; xoay màn hình giữ bản nháp; chuyển hai Student.
- Chưa kiểm thử thủ công giao diện/notification và nút logout mới trên emulator.


## Bản tích hợp sau nhánh TV2

- Admin vào StudentListActivity; các nút Môn học và Duyệt lịch mở quản lý môn và duyệt lịch thật.
- Dropdown trạng thái trong editor đã thay bằng nhãn Pending. Menu Student chỉ còn sửa/gửi duyệt lại, hủy hoặc xóa. Repository từ chối tự Confirmed và mở lại Cancelled.
- Tác vụ ghi và kết quả được giữ trong OperationViewModel khi xoay; editor khôi phục khóa lưu từ trạng thái tác vụ, không tự gửi lần hai.
- StudentSession.clear/establish và việc hiển thị nhắc dùng cùng khóa ScheduleNotifications.class. Worker mang accountId cũ bị chặn trước notify; receipt chỉ được ghi nếu thông báo thực sự được đăng.
- Không dùng UI test cũ coi Student tự Confirmed là hành vi đúng. Các regression test mới kiểm tra Admin duyệt, Pending sau sửa và chặn ngày/giờ quá khứ.

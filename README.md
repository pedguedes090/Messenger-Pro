## Dự án gốc

- https://github.com/Mino260806/MessengerPro/

## Tính năng nổi bật

- Chặn gửi đã xem.
- Chặn gửi trạng thái đang nhập.
- Đính kèm tệp bất kỳ.
- Khóa cuộc trò chuyện.
- Tải link video hỗ trợ Facebook, TikTok, Instagram, Douyin.

## Trạng thái lịch sử chat

- Tính năng lịch sử chat đang phát triển.
- Có thể thay đổi thêm trong các bản cập nhật tiếp theo.

## Cài đặt bằng MRVPatch Manager

1. Gỡ Facebook và Messenger đang cài trên máy để tránh xung đột.
2. Tải APK Messenger đúng phiên bản sau:
	https://www.apkmirror.com/apk/facebook-2/messenger/facebook-messenger-553-0-0-60-55-release/facebook-messenger-553-0-0-60-55-6-android-apk-download/?redirected=thank_you_invalid_nonce
3. Tải và cài MRVPatch Manager:
	https://github.com/NeonOrbit/MRVPatchManager/releases/tag/v2.3.7
4. Tải file mPro-v1.2.3.apk trong mục Releases của repo này.
5. Mở MRVPatch Manager, vào Settings.
6. Chọn Advanced configurations.
7. Bật Allow third-party modules.
8. Tích chọn Messenger Pro trong danh sách module.
9. Quay lại Home, chọn Manual.
10. Chọn APK file và trỏ tới file Messenger đã tải từ APKMirror.
11. Tiến hành patch và cài bản Messenger đã patch.

## Build APK (dành cho dev)

Windows PowerShell:

```powershell
.\gradlew.bat assembleDebug
```

APK output:

- app/build/outputs/apk/debug/mPro-v1.2.3.apk

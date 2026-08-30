## Dự án gốc

- https://github.com/Mino260806/MessengerPro/

## Tính năng nổi bật

- Chặn gửi đã xem.
- Chặn gửi trạng thái đang nhập.
- Đính kèm tệp bất kỳ.
- Khóa cuộc trò chuyện.
- Tải link video hỗ trợ Facebook, TikTok, Instagram, Douyin.
- Cho phép chụp màn hình (Allow screen capture).
- Chặn phát hiện chụp màn hình (Block screenshot detection).
- Mở link bằng trình duyệt ngoài (Open links externally).
- Lịch sử tin nhắn (Message history).
- **Facebook 576**: chặn đã xem story + chặn quảng cáo bảng tin.

## Phiên bản mục tiêu

| Ứng dụng | Gói | Phiên bản | versionCode |
|----------|-----|-----------|-------------|
| Messenger | `com.facebook.orca` | 576.0.0.47.92 | **345212666** |
| Facebook | `com.facebook.katana` | 576.0.0.42.73 | **474227017** |

## Kiến trúc bản phát hành (3 APK)

Mỗi bản release gồm 3 APK đã vá sẵn:

1. **Messenger đã patch** - Messenger 576 đã inject module `tn.amin.mpro2` +
   ChatHeadEnabler (`app.neonorbit.chatheadenabler`), ký bằng key MRV cố định.
2. **MessengerPro module** - module LSPosed (`tn.amin.mpro2`).
3. **Facebook đã patch** - Facebook 576 single APK, chặn đã xem story + chặn ads.

- Người dùng **root (LSPosed)**: chỉ cần cài module (số 2).
- Người dùng **không root**: cài cả 3 APK (Messenger và Facebook ký cùng key nên
  trạng thái đăng nhập được chia sẻ).

Quy trình đóng gói / vá tự động xem tại [`pipeline/PIPELINE.md`](pipeline/PIPELINE.md).

## Cài đặt bằng MRVPatch Manager (root / tự vá)

1. Gỡ Facebook và Messenger đang cài trên máy để tránh xung đột.
2. Tải APK Messenger 576 (`com.facebook.orca` 576.0.0.47.92 / 345212666) từ
   APKMirror (hoặc host riêng của bạn).
3. Tải và cài MRVPatch Manager:
   https://github.com/NeonOrbit/MRVPatchManager/releases/tag/v2.3.7
4. Tải file `mPro-v*-debug.apk` trong mục Releases của repo này.
5. Mở MRVPatch Manager, vào Settings.
6. Chọn Advanced configurations.
7. Bật Allow third-party modules.
8. Tích chọn Messenger Pro trong danh sách module.
9. Quay lại Home, chọn Manual.
10. Chọn APK file và trỏ tới file Messenger đã tải.
11. Tiến hành patch và cài bản Messenger đã patch.

## Build APK (dành cho dev)

Windows PowerShell:

```powershell
.\gradlew.bat assembleDebug
```

APK output:

- `app/build/outputs/apk/debug/mPro-v*-debug.apk`

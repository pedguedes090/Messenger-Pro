# Messenger Pro

Bộ công cụ mở rộng cho **Messenger** và **Facebook** trên Android — chặn đã xem,
chặn quảng cáo, tải video, mở khoá nhiều tính năng ẩn… không cần root.

> Dự án gốc: [Mino260806/MessengerPro](https://github.com/Mino260806/MessengerPro/)

---

## ✨ Tính năng

**Messenger**
- Chặn gửi **đã xem**.
- Chặn gửi trạng thái **đang nhập**.
- Đính kèm tệp bất kỳ.
- Khóa cuộc trò chuyện.
- Tải video từ link Facebook / TikTok / Instagram / Douyin.
- Cho phép **chụp màn hình**.
- Chặn **phát hiện chụp màn hình**.
- Mở link bằng **trình duyệt ngoài**.
- **Lịch sử tin nhắn**.

**Facebook**
- Chặn **đã xem story**.
- Chặn **quảng cáo** bảng tin.

---

## 📦 Tải về

Vào mục [**Releases**](https://github.com/pedguedes090/Messenger-Pro/releases) và tải
bản mới nhất (hiện là `v1.2.10`). Mỗi bản gồm **4 file**:

| File | Là gì | Ai cần |
|------|-------|--------|
| `Messenger-v576.0.0.47.92.apk` | Messenger đã vá (inject module) | Không root |
| `Facebook-v576.0.0.42.73.apk` | Facebook đã vá (chặn seen + ads) | Không root |
| `mPro-v*-debug.apk` | Module MessengerPro | Root (LSPosed) |
| `ChatHeadEnabler-v*.apk` | Module bật chat head | Root, tuỳ chọn |

Messenger và Facebook được ký cùng một key nên **trạng thái đăng nhập được đồng bộ**
(đăng nhập 1 lần, cả 2 app dùng chung).

---

## 🚀 Cài đặt

### Cách 1 — Không root (đơn giản nhất)

1. Gỡ Messenger + Facebook đang cài (nếu có).
2. Tải 3 file: `Messenger-v576…`, `Facebook-v576…`, `mPro-v*-debug.apk`.
3. Cài cả 3 file.
4. Mở Messenger, đăng nhập → các tính năng tự chạy.

### Cách 2 — Đã root (LSPosed)

1. Cài [LSPosed](https://github.com/LSPosed/LSPosed) (kèm Magisk/Zygisk).
2. Cài `mPro-v*-debug.apk` (+ `ChatHeadEnabler-v*.apk` nếu muốn chat head).
3. Bật module **Messenger Pro** trong LSPosed → khởi động lại.

### Cách 3 — Tự vá bằng MRVPatch Manager (root)

Nếu muốn tự vá Messenger bằng app (dùng khi muốn thêm module khác):

1. Tải và cài [MRVPatch Manager](https://github.com/NeonOrbit/MRVPatchManager/releases/latest).
2. Tải Messenger 576 từ APKMirror (bản arm64-v8a, `nodpi`, versionCode `345212652`).
3. Mở MRVPatch Manager → Settings → Advanced configurations → bật **Allow third-party modules**.
4. Tích chọn **Messenger Pro** trong danh sách module.
5. Về Home → **Manual** → chọn file Messenger đã tải → **Patch** → cài bản đã patch.

---

## 📌 Phiên bản hỗ trợ

| Ứng dụng | Gói | Phiên bản | versionCode |
|----------|-----|-----------|-------------|
| Messenger | `com.facebook.orca` | 576.0.0.47.92 | **345212652** |
| Facebook | `com.facebook.katana` | 576.0.0.42.73 | **474227017** |

> ⚠️ Messenger/Facebook **hay cập nhật** nên các bản mới hơn có thể chưa được hỗ trợ.
> Dùng đúng phiên bản trong bảng để đảm bảo tính năng hoạt động.

---

## 🛠 Dành cho nhà phát triển

### Build module

```powershell
.\gradlew.bat assembleDebug
```

APK output: `app/build/outputs/apk/debug/mPro-v*-debug.apk`

### Pipeline tự động (CI)

Repo có sẵn GitHub Actions để **tự vá + release**: push tag `v*` là CI tự build module,
kéo APK nguồn từ host, vá Messenger (MRV) + Facebook (dex tĩnh), rồi đẩy 4 APK lên
Releases. Chi tiết: [`pipeline/PIPELINE.md`](pipeline/PIPELINE.md).

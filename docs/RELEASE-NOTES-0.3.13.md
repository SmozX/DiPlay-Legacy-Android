# DiPlay v0.3.13 — 掌讯等老旧车机倒车防死锁图层让出与冷启动策略、API 17 USB兼容修复与有线重试硬门禁

## 更新

- **掌讯等老旧车机倒车保护专属策略自动落地与硬件图层让出**（Issue #315）：
  - 针对掌讯 PF8227 / MTK 8227L、全志等老旧车机及 Android 4.x / API <= 19 设备，自动智能开启倒车延迟冷启动策略，车友无需深翻设置菜单即可开箱即用；
  - 挂倒挡进入时，不仅彻底释放硬件解码器 `MediaCodec` 并清空帧队列，同时对 `SurfaceView` 硬件叠加图层执行临时隐藏让出（`View.INVISIBLE`），杜绝与倒车摄像头画面及随动轨迹线在底层 GPU/硬件合成器中发生图层死锁；
  - 退出倒挡并延迟 2.5 秒确认重获窗口焦点后，自动恢复 SurfaceView 显示并冷启动解码器，重新索取全新 IDR 关键帧出图。
- **API 17 (Android 4.2) 设备 USB bulkTransfer NoSuchMethodError 崩溃修复**（Issue #371, #368）：
  - 修复 API 17 原生 Android 缺失 5 参数重载 `UsbDeviceConnection.bulkTransfer(..., timeout)` 导致的运行时崩溃问题；
  - 引入兼容回退调用机制，保障 Android 4.2 等超低版本车机平稳进行 USB Bulk 数据交互并完善底层诊断日志。
- **有线连接自动重试停止硬门禁机制**（Issue #359）：
  - 强化有线连接重试停止（`startupRetryStopped`）锁存器的硬拦截逻辑；
  - 避免显示配置刷新、延迟恢复或等待态状态报告绕过重试上限反复拉起底层连接栈，仅在用户点击 Retry 按钮、物理重新插拔 USB 或切换传输模式时重置。

## 验证

- 版本 `0.3.13` / `versionCode 133`，包名 `com.shihab.diplay`，最低 Android 4.2（API 17）。
- 完整单元测试全部通过，发布包内嵌源码 commit 严格核验通过。

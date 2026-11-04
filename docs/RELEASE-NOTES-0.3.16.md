# DiPlay v0.3.16 — 桌面Dock栏启动器置顶消除、NCM发送停滞看门狗与底层日志降载

## 更新

- **车机桌面常驻 Dock 栏置顶恢复实机优化**（Issue #363）：
  - 针对部分车机启动器（Launcher）常驻 Dock 栏无法消除的问题，改用通过启动器 Activity（`DiPlayActivity`）配合 `FLAG_ACTIVITY_NEW_TASK` 拉起置顶，确保窗口管理层级真正重排，瞬间迫使桌面 Dock 栏退出；
  - 锁定视窗挤压保护的宽高比对比基准为既定 baseline bounds，杜绝因尺寸变更回调提前改写显示尺寸导致挤压防御被跳过出现黑边；
  - 在系统诊断导出中补全 `Experimental display: dockBarRefront=` 开关状态输出，并将恢复日志明确为 `full height restored; dock re-front episode closed`。
- **有线 NCM 模式发送停滞看门狗与阶梯恢复**（Issue #305）：
  - 新增针对 bulk-OUT 数据路径的停滞检测 watchdog：当底层数据通道处于激活状态且待恢复队列非空、但在 10 秒内无任何进行中传输且已完成计数未变时，主动推进 failure ladder；
  - 确保有线传输挂起时能及时递进至 re-claim 接口重新认领，彻底杜绝会话静默卡死。
- **NCM 数据转发层日志轻量化采样与性能优化**（Issue #305）：
  - 将原先高码率下对每一帧网络包均复制头部并写入调试日志的逻辑，优化为每 300 帧采样记录一次；
  - 显著降低老旧小堆内存车机（Allwinner T3、MTK 8227L 等）在有线高速传输下的 CPU 占用与 GC 压力。

## 验证

- 版本 `0.3.16` / `versionCode 136`，包名 `com.shihab.diplay`，最低 Android 4.2（API 17）。
- 完整 Gradle 检查通过：单元测试全部通过，各模块 Lint 与构建成功，发布包内嵌源码 commit 严格核验通过。

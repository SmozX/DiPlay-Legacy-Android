# DiPlay v0.3.14 — 有线连接消除冗余重枚举叠加与总线抗震荡优化

## 更新

- **有线连接防止重复触发供应商重枚举（Vendor Re-enumeration）叠加机制**（Issue #368）：
  - 针对全志 T3、AC8317 等老旧车机在有线连接或自动重连场景下，当手机已经在该次接入（attach）中完成过重枚举并切至 CarPlay 配置时，禁止二次触发强制重枚举（`forcedReenumerationForAttach`）；
  - 避免短时间内连续执行重枚举导致底层 USB 总线脱落、僵尸状态残留及重连失败，大幅提升有线连接与运行期稳定性。

## 验证

- 版本 `0.3.14` / `versionCode 134`，包名 `com.shihab.diplay`，最低 Android 4.2（API 17）。
- 完整单元测试全部通过，发布包内嵌源码 commit 严格核验通过。

# 吉他调音器（Android）

Kotlin + Jetpack Compose 编写的吉他调音器，完全离线，音高识别算法（YIN）自行实现。

- 识别范围 55–1500 Hz，带噪声门与中值平滑
- 显示音名（科学音高记号）、实际频率、音分偏差、标准音频率
- 标准 / Drop D / 降半音 / Open G / DADGAD，可手动锁定琴弦
- 基准音 A4 可在 430–450 Hz 之间调节（本地保存）

## 下载

每次推送到 `main` 分支，GitHub Actions 会先运行单元测试，通过后编译 APK 并发布到
[Releases](../../releases/latest) 页面。

## 代码结构

- `app/src/main/java/.../pitch/` 纯 Kotlin 的音高检测（YIN）、平滑、乐理计算
- `app/src/main/java/.../audio/` AudioRecord 麦克风采集
- `app/src/main/java/.../ui/` Compose 界面
- `app/src/test/` 算法单元测试（合成正弦波与多泛音信号，误差要求 ±1 音分）

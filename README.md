# FluidCloudRedirect (流体云重定向)

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)

将 OPPO/一加「流体云」(智慧决策服务 `com.oplus.metis`)识别出的网页链接重定向到你选择的浏览器。

LSPosed 模块,基于 **libxposed 新 API**(`io.github.libxposed:api:102.0.0`,minApi/targetApi=101)。

## 工作原理

复制链接后,流体云会把识别出的网页卡片硬编码到 OPPO 浏览器打开。本模块在卡片执行层做改写,三层 hook 互为备份:

| 级别 | 目标 | 作用 |
|---|---|---|
| 主 | `TextIntent.invoke(Context)` | 执行层咽喉,所有卡片打开必经 |
| 冗余 | `SchemeData/WebData.parseIntent(Context)` | 包名硬编码产生点改写,卡片图标/名称同步变对 |
| 监控 | `ZoomOpenHandler.startByZoom`(仅记录) | 未来出现绕过 TextIntent 直开路径时预警 |

仅挂钩 `com.oplus.metis` 的 `:text_intent` 子进程,其余进程零干预。

## 重定向规则(应用内可开关)

1. `heytapbrowser://webpage?url=<真实URL>` 协议卡片 → 提取并打开真实网址(默认开);
2. 文字搜索卡片 `textType == 29`(默认开);
3. 目标被硬编码为 `com.heytap.browser` 的 http(s) 网页卡片(默认开);
4. 任意浏览器网页卡片(实验性,默认关)。

## 安装与使用

1. 安装 APK,LSPosed 管理器中启用模块;
2. 作用域确认包含「智慧决策服务」(scope.list 已默认声明;也可在应用内点「一键申请作用域」);
3. 重启手机或 `adb shell am force-stop com.oplus.metis`;
4. 应用内选择目标浏览器;选择通过 LSPosed 远程偏好下发,**即时生效**,无需重启 metis;
5. 「禁用重定向」= 保持系统默认行为。

要求:Android 10+(minSdk 29)、支持 libxposed 新 API 的 LSPosed。

日志:LSPosed 管理器 → 日志,过滤 `FluidRedirect`;应用内可开「详细日志」。

## 构建

```bat
gradlew assembleDebug      :: 产物 app\build\outputs\apk\debug\app-debug.apk
gradlew assembleRelease    :: R8 混淆 + release 签名
gradlew testDebugUnitTest  :: 策略层单元测试
```

- 构建后自动执行 `verifyXposedMetadata` 任务,断言 APK 内存在 `META-INF/xposed/` 三件套;
- release 签名读取根目录 `keystore.properties`(不入库)。自建签名:生成自己的 keystore 后按以下格式写 `keystore.properties` 即可,并同步修改 `app/build.gradle.kts` 的引用(工程已内置读取逻辑):

  ```properties
  storeFile=<keystore 文件路径>
  storePassword=<密码>
  keyAlias=<别名>
  keyPassword=<密码>
  ```

## 工程结构

```
app/src/main/java/com/mo/fkLTY
├─ hook/      MainHook(入口)· RedirectHooker(主)· ParseIntentHooker(冗余)
│             MonitorHooker(监控)· ReflectedTextIntent(反射封装)· HookLog
├─ engine/    Policy(规则判定,纯 JVM,含单测)· RedirectEngine(改写执行)
│             BrowserAvailability · RedirectPrefs(远程偏好键)
├─ service/   PrefsBridge(XposedService 封装:作用域查询/一键申请/远程偏好写入)
└─ ui/        Compose M3 主屏(状态卡 · 规则开关 · 浏览器列表 · 诊断 · 关于)
app/src/main/resources/META-INF/xposed/   java_init.list · module.prop · scope.list
```

## 已知注意事项

- hook 进程内远程偏好**只读**(libxposed API 语义),写入只能发生在设置 App 端;
- `META-INF/xposed` 三件套由 `verifyXposedMetadata` 任务把关,改打包方式时请保留该校验;
- 智慧决策服务未来若把「打开动作」下沉到检测层,监控位日志会首先暴露,届时主 hook 迁移即可。

## 开源协议

本项目基于 [GPL-3.0](LICENSE) 协议开源。

Copyright (c) 2026 wangliaoqiongchen

任何对本项目代码的复制、修改与再分发,均须遵循 GPL-3.0:衍生作品必须同样以 GPL-3.0 开源并保留版权声明。

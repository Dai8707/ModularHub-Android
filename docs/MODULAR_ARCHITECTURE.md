# ModularHub-Android 多仓组件化架构设计文档

> 版本：v1.0  
> 适用对象：Android 壳工程（Host）+ 多 Git 仓业务组件（Module/Lib）  
> 目标：通过 YAML 声明式管理依赖，支持 **本地源码编译** 与 **远程 AAR 编译** 一键切换

---

## 1. 背景与目标

### 1.1 为什么需要这套架构

传统单仓多 Module 方案在团队规模扩大后会遇到：

| 问题 | 多仓 + YAML 方案 |
|------|------------------|
| 全量 clone 慢、CI 耗时长 | 按需拉取组件，壳工程轻量 |
| 业务线互相阻塞 | 各组件独立发版、独立仓库 |
| 联调与集成环境不一致 | YAML 统一声明 source/aar 模式 |
| 依赖版本混乱 | 中心化版本管理与锁定 |

大厂（字节、阿里、美团、腾讯等）的通用做法是：

1. **壳工程（App Shell）**：只负责 Application、路由注册、全局初始化、打包签名
2. **组件仓（Component Repo）**：每个业务/基础能力独立 Git 仓库
3. **依赖清单（Manifest）**：YAML/JSON 描述组件来源、版本、编译模式
4. **Gradle 插件/脚本**：解析清单，动态 `include` Module 或引入 AAR/Maven 坐标

ModularHub-Android 即上述 **壳工程 + 依赖编排层**。

### 1.2 核心能力

- **分 Git 仓**：每个 Module/Lib 可独立仓库、独立 CI、独立发版
- **YAML 配置拉取**：声明 git url、branch/tag/commit、本地路径
- **编译模式切换**：`source`（本地源码） / `aar`（预编译产物）
- **环境隔离**：personal（个人联调）、dev、staging、release 多套 profile
- **可脚本化**：`sync-modules` 一键同步源码，`assemble` 自动走对应依赖链

---

## 2. 总体架构

```
┌─────────────────────────────────────────────────────────────────┐
│                    ModularHub-Android (Host)                     │
│  app / build-logic / gradle / modules.yaml / local.override.yaml │
└────────────────────────────┬────────────────────────────────────┘
                             │ 解析 YAML → 动态依赖
         ┌───────────────────┼───────────────────┐
         ▼                   ▼                   ▼
   ┌───────────┐      ┌───────────┐      ┌───────────┐
   │  Module A │      │  Module B │      │  Lib Base │
   │  (Git 仓)  │      │  (Git 仓)  │      │  (Git 仓)  │
   └───────────┘      └───────────┘      └───────────┘
         │                   │                   │
         └───────────────────┴───────────────────┘
                             │
              source 模式：project(':module-xxx')
              aar 模式：  com.xxx:module-xxx:1.2.3
```

### 2.1 仓库角色划分

| 仓库类型 | 命名示例 | 职责 |
|----------|----------|------|
| Host 壳工程 | `ModularHub-Android` | 打包、集成、YAML、全局配置 |
| 业务组件 | `component-login` | 登录业务 UI + 逻辑 |
| 业务组件 | `component-home` | 首页 |
| 基础库 | `lib-network` | 网络层 |
| 基础库 | `lib-router-api` | 路由 API（编译期解耦） |
| 组件聚合发版 | `component-maven`（可选） | 统一发布 AAR 到 Maven |

### 2.2 模块分层（推荐）

```
app (壳)
  └── feature-*     业务组件（可独立运行 debug apk）
  └── lib-*         基础能力（无 Application，纯 library）
  └── api-*         接口层（仅 interface / Router path / Model，无实现）
```

**依赖规则（强制）**：

- `app` → 可依赖所有层
- `feature` → 可依赖 `lib`、`api`；**禁止** feature 间直接依赖
- `feature` 互调 → 仅通过 `api` + 路由（ARouter / TheRouter / 自研 Router）
- `lib` → 仅依赖其他 `lib`，不依赖 `feature`
- `api` → 零实现或极薄，供多方引用

---

## 3. 目录结构（Host 壳工程）

```
ModularHub-Android/
├── app/                              # 主 Application Module
├── build-logic/                      # 复合构建：Convention Plugin
│   └── convention/
│       ├── build.gradle.kts
│       └── src/main/kotlin/
│           ├── ModularHubPlugin.kt
│           └── ModuleManifestParser.kt
├── gradle/
│   └── libs.versions.toml            # 版本目录（Version Catalog）
├── scripts/
│   ├── sync-modules.gradle.kts       # YAML → clone/checkout
│   └── module-settings.gradle.kts    # 动态 include / dependency
├── config/
│   ├── modules.yaml                  # 主清单（提交 Git）
│   ├── modules.dev.yaml              # 环境覆盖（可选）
│   └── local.override.yaml           # 本地覆盖（.gitignore，不进库）
├── modules/                          # 源码模式 checkout 目录（.gitignore）
│   ├── component-login/
│   ├── component-home/
│   └── lib-network/
├── repos/                            # 可选：AAR 本地缓存
├── settings.gradle.kts
├── build.gradle.kts
└── docs/
    └── MODULAR_ARCHITECTURE.md
```

> `modules/` 与 `local.override.yaml` 建议加入 `.gitignore`，避免误提交联调源码。

---

## 4. YAML 配置规范

### 4.1 主配置文件 `config/modules.yaml`

```yaml
# 全局默认
defaults:
  mode: aar                    # 默认编译模式：source | aar
  git:
    ref: main                  # 默认分支
  maven:
    group_prefix: com.modularhub
    repository: https://nexus.company.com/repository/maven-releases/

# 当前激活的环境 profile（可被 local.override 覆盖）
profile: release               # personal | dev | staging | release

# 环境级覆盖：同一组件在不同 profile 下可使用不同 mode/version
profiles:
  personal:
    defaults:
      mode: source             # 个人联调默认走源码
  dev:
    defaults:
      mode: source
  staging:
    defaults:
      mode: aar
  release:
    defaults:
      mode: aar

# 组件清单
modules:
  # ---------- 基础库 ----------
  - name: lib-router-api
    type: lib
    git:
      url: git@github.com:your-org/lib-router-api.git
      ref: main
    source:
      path: modules/lib-router-api
      gradle_path: :lib-router-api    # 组件仓内 module 路径
    aar:
      artifact: lib-router-api
      version: 1.0.0

  - name: lib-network
    type: lib
    git:
      url: git@github.com:your-org/lib-network.git
      ref: v2.1.0                      # 支持 tag
    source:
      path: modules/lib-network
      gradle_path: :lib-network
    aar:
      artifact: lib-network
      version: 2.1.0

  # ---------- 业务组件 ----------
  - name: component-login
    type: feature
    git:
      url: git@github.com:your-org/component-login.git
      ref: develop
    source:
      path: modules/component-login
      gradle_path: :component-login
    aar:
      artifact: component-login
      version: 1.3.2
    # 组件级覆盖（优先级高于 defaults）
    profiles:
      personal:
        mode: source
      release:
        mode: aar
        aar:
          version: 1.3.0               # release 锁定更低版本

  - name: component-home
    type: feature
    enabled: true                      # false 则完全不集成
    git:
      url: git@github.com:your-org/component-home.git
      ref: main
    source:
      path: modules/component-home
      gradle_path: :component-home
    aar:
      artifact: component-home
      version: 2.0.1
```

### 4.2 本地覆盖 `config/local.override.yaml`

> **仅本机生效**，用于「我只改 login 组件，其余走 AAR」。

```yaml
profile: personal

modules:
  - name: component-login
    mode: source
    git:
      ref: feature/oauth-refactor      # 覆盖分支

  - name: component-home
    mode: aar                          # 其余保持 aar

  - name: lib-network
    mode: source
    source:
      path: /Users/me/work/lib-network  # 支持绝对路径指向已有 clone
```

### 4.3 字段说明

| 字段 | 必填 | 说明 |
|------|------|------|
| `name` | 是 | 组件唯一标识，与 Maven artifact 建议一致 |
| `type` | 是 | `lib` / `feature` / `api` |
| `enabled` | 否 | 默认 `true` |
| `mode` | 否 | `source` / `aar`，可被 profile 覆盖 |
| `git.url` | source 时必填 | SSH/HTTPS 仓库地址 |
| `git.ref` | 否 | branch / tag / commit SHA |
| `source.path` | source 时必填 | 相对 Host 根目录或绝对路径 |
| `source.gradle_path` | 是 | 组件 settings 中的 project path |
| `aar.artifact` | aar 时必填 | Maven artifactId |
| `aar.version` | aar 时必填 | 语义化版本，release 必须锁定 |

### 4.4 合并优先级（从高到低）

```
local.override.yaml  >  modules.{profile}.yaml  >  module.profiles  >  modules.yaml defaults
```

---

## 5. Gradle 集成方案

### 5.1 解析流程

```
settings.gradle.kts
    │
    ├─ 1. 读取并 merge YAML
    ├─ 2. 对 mode=source 的模块：
    │      - 执行 sync（clone / fetch / checkout）
    │      - settings.include(source.gradle_path)
    │      - projectDir 指向 source.path
    ├─ 3. 对 mode=aar 的模块：
    │      - 不 include
    │      - 在 dependencyResolutionManagement 添加 Maven 仓库
    └─ 4. app/build.gradle.kts 通过统一 API 声明依赖
```

### 5.2 `settings.gradle.kts` 示例

```kotlin
pluginManagement {
    includeBuild("build-logic")
}

plugins {
    id("modularhub.settings")
}

// 由 build-logic 中的 ModularHubSettingsPlugin 完成：
// - 解析 config/modules.yaml + override
// - 动态 include 源码模块
// - 注入 ext["MODULAR_MODULES"] 供 app 模块读取
```

### 5.3 动态依赖 API（app 模块）

```kotlin
// app/build.gradle.kts
dependencies {
    modularHub("component-login")
    modularHub("component-home")
    modularHub("lib-network")
    modularHub("lib-router-api")
}
```

`modularHub(name)` 扩展函数逻辑：

```kotlin
fun DependencyHandler.modularHub(name: String) {
    val module = rootProject.extra["modularModules"]
        .find { it.name == name }
        ?: error("Module '$name' not found in modules.yaml")

    when (module.resolvedMode) {
        ModuleMode.SOURCE -> {
            add("implementation", project(module.source.gradlePath))
        }
        ModuleMode.AAR -> {
            add("implementation",
                "${module.mavenGroup}:${module.aar.artifact}:${module.aar.version}")
        }
    }
}
```

### 5.4 源码同步脚本 `scripts/sync-modules.gradle.kts`

可由 Task `syncModules` 触发，也可在 configuration 阶段自动执行（推荐 **显式 Task**，避免每次 sync 都 git pull）。

```kotlin
tasks.register("syncModules") {
    group = "modularhub"
    description = "Clone or update source modules defined in modules.yaml"

    doLast {
        manifest.modules
            .filter { it.resolvedMode == ModuleMode.SOURCE && it.enabled }
            .forEach { module ->
                syncGitRepo(
                    url = module.git.url,
                    ref = module.git.ref,
                    dest = file(module.source.path)
                )
            }
    }
}
```

**推荐工作流**：

```bash
./gradlew syncModules          # 拉取/更新源码组件
./gradlew :app:assembleDebug   # 编译
```

### 5.5 组件仓 `settings.gradle.kts` 约定

每个组件仓库应能 **独立打开 Android Studio 调试**，结构示例：

```
component-login/
├── component-login/          # Android Library Module（与 name 一致）
├── component-login-api/      # 可选：对外 API
├── gradle/
├── build.gradle.kts
└── settings.gradle.kts       # include(":component-login")
```

Host 集成时：

```kotlin
// Host settings 中
include(":component-login")
project(":component-login").projectDir =
    file("modules/component-login/component-login")
```

> `source.gradle_path` 与组件仓实际 module 名必须对齐，建议在脚手架模板中固化。

---

## 6. 编译模式详解

### 6.1 Source 模式（本地源码）

**适用**：个人开发、联调、Bugfix、组件尚未发版。

| 优点 | 缺点 |
|------|------|
| 可断点调试、即时改代码 | 首次 clone 耗时 |
| 不依赖 Maven 发版 | 组件版本不可复现（除非 ref 锁 commit） |

**要求**：

- `git.ref` 在 CI 中应使用 **tag 或 commit SHA**，不用浮动 branch
- 组件仓需与 Host 的 `compileSdk`、`AGP`、`Kotlin` 版本在 `libs.versions.toml` 中对齐

### 6.2 AAR 模式（预编译产物）

**适用**：集成测试、Staging、Release、他人不需要改组件代码。

| 优点 | 缺点 |
|------|------|
| 构建快、可复现 | 无法直接改组件代码 |
| 版本语义清晰 | 需完善 Maven 发版流程 |

**AAR 来源（三选一或组合）**：

1. **私服 Maven**（推荐）：Nexus / Artifactory / 阿里云效 / GitHub Packages
2. **本地 Maven**：`~/.m2` 或项目 `repos/`
3. **FlatDir**（不推荐生产）：`libs/xxx.aar`

### 6.3 模式切换示例

| 场景 | 操作 |
|------|------|
| 只改 login | `local.override.yaml` 设 login=source，其余 aar |
| 提测 | profile 切 `staging`，全 aar + 锁定 version |
| 发版 | profile=`release`，禁止 source，YAML 中 version 与 Maven 一致 |
| 新人 onboarding | 默认 profile=dev，先全 aar 能跑，再按需 source |

---

## 7. 组件发版与版本管理

### 7.1 发版流程

```
组件仓 MR 合并 → 打 Tag (v1.3.2) → CI 发布 AAR 到 Maven
                                      ↓
Host 更新 modules.yaml 中 aar.version → 集成验证 → Host 发版
```

### 7.2 版本策略

- **Lib**：严格 SemVer，Breaking change 升 Major
- **Feature 组件**：SemVer，Patch 仅 Bugfix
- **Host**：应用版本 `versionName` 独立；依赖组件版本由 YAML 管理

### 7.3 `libs.versions.toml` 与 YAML 分工

| 文件 | 管理对象 |
|------|----------|
| `libs.versions.toml` | AndroidX、Kotlin、三方 SDK |
| `modules.yaml` | 内部组件 git 坐标与 aar 版本 |

避免两处重复：组件 **不** 写进 toml，统一走 YAML。

---

## 8. 路由与组件通信

### 8.1 原则

- Feature **禁止** `implementation project(':other-feature')`
- 跳转：`Router.navigate("/login/main")`
- 服务发现：`Router.getService(IAccountService::class.java)`

### 8.2 API 模块

```
lib-router-api          # Router 接口
component-login-api     # ILoginService、LoginRoutePath
component-login         # 实现 + UI
```

Host `modules.yaml` 中 login 的 aar 模式通常引入：

- `component-login`（实现）
- `component-login-api`（若 Host 或其他组件需编译期依赖接口）

可在 YAML 增加 `transitive_modules` 或由组件 POM 传递依赖。

---

## 9. CI/CD 建议

### 9.1 Host 仓库 CI

```yaml
# .github/workflows/build.yml 伪代码
jobs:
  build:
    steps:
      - uses: actions/checkout@v4
      - name: Validate modules.yaml
        run: ./gradlew validateModulesYaml
      - name: Build (AAR mode)
        env:
          MODULAR_PROFILE: release
        run: ./gradlew :app:assembleRelease
```

**要点**：

- CI **永远** `profile=release` + 全 `aar`，保证可复现
- 增加 `validateModulesYaml`：检查 version 存在、git url 合法、enabled 模块可解析

### 9.2 组件仓库 CI

- `./gradlew :component-xxx:assembleRelease`
- `./gradlew publish` 上传到 Maven
- 可选：合并后 bot 自动 bump Host 的 YAML（PR 到 Host 仓）

---

## 10. 安全与合规

- `local.override.yaml` **不得** 提交含内网 IP、Token 的路径
- Git 依赖优先 **SSH** 或 CI **Deploy Key**
- Maven 私服使用 **read-only** 凭证给开发者，publish 仅 CI
- `modules.yaml` 中 `enabled: false` 可用于下线组件，而非删代码

---

## 11. 常用命令速查

| 命令 | 说明 |
|------|------|
| `./gradlew syncModules` | 按 YAML 拉取 source 模式组件 |
| `./gradlew :app:assembleDebug` | 调试包 |
| `./gradlew listModules` | 打印当前 profile 下各组件 mode/version |
| `./gradlew validateModulesYaml` | 校验配置 |
| `./gradlew :app:dependencies` | 检查实际依赖树 |

---

## 12. 落地路线图（建议分阶段）

### Phase 1 — 骨架（1～2 周）

- [ ] 初始化 Host：`app` + `build-logic`
- [ ] 实现 YAML 解析 + `modularHub()` 依赖 API
- [ ] 接入 1 个 demo lib（source + aar 双模式跑通）

### Phase 2 — 工具链（2～3 周）

- [ ] `syncModules` Task + `listModules` / `validateModulesYaml`
- [ ] `local.override.yaml` 本地覆盖
- [ ] Maven 私服 + 组件 publish 模板

### Phase 3 — 规范（持续）

- [ ] 组件脚手架（Gradle Plugin / cookiecutter）
- [ ] Router + api 模块规范 Code Review Checklist
- [ ] CI release 流水线 + 版本自动 PR

---

## 13. FAQ

**Q：source 和 aar 混用会不会 ABI 不一致？**  
A：会。因此 Host 维护统一的 `compileSdk`、Kotlin、AGP 版本；组件 CI 用与 Host 相同的 Docker 镜像构建 AAR。

**Q：组件仓 module 名和 YAML 的 name 必须相同吗？**  
A：`name` 是逻辑名；`source.gradle_path` 是 Gradle path。建议保持一致，降低心智负担。

**Q：能否不用 YAML，用 JSON/TOML？**  
A：可以。YAML 更易读、支持注释，适合多人维护；解析可用 SnakeYAML。

**Q：和 Git Submodule 比？**  
A：Submodule 偏「子仓库指针」，缺少 mode/version/profile 语义；YAML 方案是 **依赖编排**，Submodule 可作为底层实现之一，但不建议暴露给业务开发者。

**Q：Instant App / Dynamic Feature 呢？**  
A：本方案聚焦 **组件化编译期集成**；Dynamic Delivery 是运行时交付，可并存但需额外 Play Feature 配置。

---

## 14. 附录 A：最小 `modules.yaml` 模板

```yaml
defaults:
  mode: aar
  maven:
    group_prefix: com.modularhub
    repository: https://nexus.example.com/repository/releases/

profile: dev

modules:
  - name: lib-base
    type: lib
    git:
      url: git@github.com:org/lib-base.git
      ref: main
    source:
      path: modules/lib-base
      gradle_path: :lib-base
    aar:
      artifact: lib-base
      version: 1.0.0
```

## 15. 附录 B：`.gitignore` 建议片段

```gitignore
# 本地源码 checkout
/modules/

# 本地 YAML 覆盖
/config/local.override.yaml

# 本地 AAR 缓存
/repos/
```

---

## 16. 附录 C：与大厂方案对照

| 能力 | 字节（beehive / 组件化） | 阿里（Atlas / 模块化） | 本方案 ModularHub |
|------|-------------------------|------------------------|-------------------|
| 多 Git 仓 | ✅ | ✅ | ✅ YAML git 坐标 |
| 源码/产物切换 | ✅ replaceModule | ✅ bundle 配置 | ✅ source/aar mode |
| 路由解耦 | ✅ | ✅ | ✅ Router + api |
| 声明式配置 | 多 JSON | bundle.json | **modules.yaml** |
| 本地 override | ✅ | 部分支持 | ✅ local.override.yaml |

---

**文档维护**：架构变更时请同步更新 `modules.yaml` 字段说明与 Phase 清单。  
**下一步**：可在本仓库实现 `build-logic` 中的 `ModularHubSettingsPlugin` 与示例 `config/modules.yaml`，需要时可指定优先落地的 Phase。

# ModularHub-Android

Android **壳工程（Host App）**，用于承载和集成组件化架构下的各业务模块与基础库。

> **壳子是壳子**：本仓库只包含 App 宿主、Gradle 插件和组件集成配置，**不包含**各业务组件 / 基础库的源码。

---

## 这是什么

ModularHub-Android 本身**不包含具体业务逻辑**，它的职责是：

- 作为 App 的 **Application 宿主**，负责打包、签名、全局初始化
- 通过 **YAML 配置** 统一管理各组件的来源与依赖方式
- 在 **本地源码编译** 与 **远程 AAR 编译** 之间灵活切换
- 支持组件 **分 Git 仓** 独立开发、独立发版

适合中大型 Android 团队做多仓组件化改造时的 **集成壳**。

---

## 解决什么问题

| 痛点 | 本项目的做法 |
|------|-------------|
| 单仓越来越大，clone / 编译慢 | 组件拆到独立 Git 仓，壳工程按需拉取 |
| 联调时要改组件，集成时要用稳定版 | YAML 配置 `source` / `aar` 模式一键切换 |
| 依赖关系混乱 | 壳工程统一声明组件清单，Gradle 插件自动解析 |
| 不同环境依赖不一致 | 支持 `dev` / `staging` / `release` 等 profile |

---

## 核心能力

1. **YAML 驱动依赖** — 在项目根目录 `modules.yaml` 中声明组件名、Git 地址、版本、编译模式
2. **动态 Module 集成** — `source` 模式自动 `include` 本地源码模块
3. **AAR 集成** — `aar` 模式从 Maven 私服拉取预编译产物
4. **本地覆盖** — 根目录 `local.override.yaml` 仅本机生效，方便个人联调
5. **Gradle 工具任务** — 列出组件、校验配置、同步 Git 源码

---

## 项目结构

```
ModularHub-Android/          ← 壳仓（你 clone 的这个）
├── app/                     # 薄壳：Application + 打包（无 MainActivity）
├── build-logic/             # YAML 解析 + Gradle 插件
├── modules.yaml             # 组件清单（根目录，Git 地址 / 版本 / 编译模式）
├── local.override.yaml.example
├── modules/                 # 组件源码落地目录（gitignore，不提交）
└── docs/MODULAR_ARCHITECTURE.md

component-xxx/                 ← 独立 Git 仓（业务项目：app + feature-xxx）
project-main/                  ← 主框架项目（MainActivity、BottomNav）
```

**依赖关系：**

```
壳 app → feature-main → feature-home / category / cart / mine
```

---

## 同事只 clone 壳子，怎么跑起来？

**Gradle Sync 不会自动拉组件代码。** 需要按编译模式选择：

### 方式 A：源码联调（`mode: source`）

```bash
git clone <壳工程>
cd ModularHub-Android

# 1. 拉组件源码（必须，Sync 不会帮你做）
./gradlew syncModules

# 2. Android Studio Sync / 编译
./gradlew :app:assembleDebug
```

前提：根目录 `modules.yaml` 里已配置真实的 `git.url`，且本机有仓库访问权限。

### 方式 B：集成稳定版（`mode: aar`）

将 `profile` 设为 `staging` / `release`，组件走 Maven AAR，**不需要** clone 组件仓，Gradle Sync 即可。

前提：组件已发布到 Maven 私服，且 `modules.yaml` 中配置了 `maven.repository`。

---

## 常用命令

```bash
# 查看当前组件解析结果（mode / 路径 / 版本）
./gradlew listModules

# 校验 modules.yaml 配置
./gradlew validateModulesYaml

# 从 Git 拉取 source 模式组件（首次 / 更新）
./gradlew syncModules

# 编译 Debug 包
./gradlew :app:assembleDebug
```

---

## 配置说明

主配置文件：项目根目录 `modules.yaml`

```yaml
defaults:
  mode: source

profile: dev

modules:
  - name: lib-base
    type: lib
    git:
      url: git@github.com:your-org/lib-base.git
      ref: main
    source:
      path: modules/lib-base      # syncModules 落地路径
      gradle_path: :lib-base
    aar:
      artifact: lib-base
      version: 1.0.0
```

在 `app/build.gradle` 中声明依赖：

```groovy
modularHub {
    implementation 'feature-main'   // 壳只引主框架
}
```

`modules.yaml` 中仍需声明各 Tab 的 feature（供 settings include），Tab 依赖在 `feature-main` 内部编排。

个人联调时，复制 `local.override.yaml.example` 为 `local.override.yaml`，可单独切换某个组件的编译模式或 Git 分支。

---

## 接入新组件

1. 在 **独立 Git 仓** 创建组件工程
2. 在根目录 `modules.yaml` 添加条目（`git.url` + `source.path` + `aar` 坐标）
3. 执行 `./gradlew syncModules`
4. 在 `app/build.gradle` 的 `modularHub { }` 中增加依赖
5. 发版后将 profile 切到 `aar`，走 Maven 集成

详细设计见 [docs/MODULAR_ARCHITECTURE.md](docs/MODULAR_ARCHITECTURE.md)。

---

## 技术栈

- Gradle 8.9 + Groovy DSL（`.gradle`）
- Android Gradle Plugin 8.7
- Kotlin 2.0
- SnakeYAML（解析组件清单）

---

## License

待定

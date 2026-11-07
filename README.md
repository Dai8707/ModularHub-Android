# ModularHub-Android

Android **壳工程**试验项目。业务在独立 Git 仓；壳只负责 Application、打包，以及按 `modules.yaml` 用**带版本号的坐标**集成组件。

> 试验约定：先在这套 GitHub 仓库验证，公司钉钉 / GitLab / 正式 Maven 等跑通后再接。

---

## 关联仓库

| 仓库 | 说明 |
|------|------|
| [ModularHub-Android](https://github.com/Dai8707/ModularHub-Android) | 壳工程（本仓库） |
| [project-main](https://github.com/Dai8707/project-main) | 主框架（MainActivity、BottomNav） |
| [project-home](https://github.com/Dai8707/project-home) | 首页 Tab |
| [project-category](https://github.com/Dai8707/project-category) | 分类 Tab |
| [project-cart](https://github.com/Dai8707/project-cart) | 购物车 Tab |
| [project-mine](https://github.com/Dai8707/project-mine) | 我的 Tab |

---

## 核心约定

| 字段 / 行为 | 含义 |
|-------------|------|
| `enabled` | 这条是否参与集成 |
| `mode: source` | 把仓库拉到 `modules/`，方便改代码 |
| `mode: aar` | 不拉仓库 |
| 依赖解析 | **一律** `group:artifact:aar.version`，**不用** `project()` |

`mode` 不决定依赖写法。改完源码后先 `publishModules` 打出对应版本，再让壳按坐标取包。

```
壳 app → com.modularhub:feature-main:x.y.z
              └── 其 POM / 编译依赖再指向各 Tab 的带版本坐标
```

---

## 只 clone 壳子，怎么跑起来？

先按 [docs/GITHUB_PACKAGES.md](docs/GITHUB_PACKAGES.md) 在 `~/.gradle/gradle.properties` 配好 `gpr.user` / `gpr.token`（私人 Maven = GitHub Packages）。

```bash
git clone git@github.com:Dai8707/ModularHub-Android.git
cd ModularHub-Android

# Sync / 任意 Gradle 任务：source 模式缺仓会自动 clone
./gradlew :app:assembleDebug

# 改完组件源码后，按 YAML 版本发到 mavenLocal（有 Token 时同步 GitHub Packages）
./gradlew publishModules
```

`mode: source` 且本地还没有仓库时，**Gradle 配置阶段会自动 clone**（已有则跳过）。  
不想自动拉：在 `gradle.properties` 写 `modularHub.autoSync=false`。

`profile: release` 时不允许仍有 `mode: source` 的启用条目；CI 只 checkout 壳 + 下载钉死的 YAML，从 Maven 取包。

---

## 常用命令

```bash
./gradlew listModules
./gradlew validateModulesYaml
./gradlew publishModules
./gradlew :app:assembleDebug
```

---

## 配置

根目录 `modules.yaml`。壳里只写：

```groovy
modularHub {
    implementation 'feature-main'
}
```

个人联调复制 `local.override.yaml.example` → `local.override.yaml`（已 gitignore）。

---

## 目录

```
ModularHub-Android/
├── app/                 # Application + 打包
├── build-logic/         # YAML 解析、坐标依赖、publish Task
├── modules.yaml
├── modules/             # 自动 clone 落地，壳仓忽略
└── docs/
```


详细设计见 [docs/MODULAR_ARCHITECTURE.md](docs/MODULAR_ARCHITECTURE.md)。

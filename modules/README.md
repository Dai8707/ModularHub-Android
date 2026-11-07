# modules/

业务项目（独立 Git 仓）的本地落地目录，缺仓时由 Gradle Sync 自动 clone。壳仓 **不提交** 此目录内容。

## 结构

```
modules/project-xxx/
├── app/              ← 独立运行调试
└── feature-xxx/      ← 打成带版本号的 AAR，壳按坐标依赖
```

## 清单

| 项目 | Library | 职责 |
|------|---------|------|
| `project-main` | `feature-main` | MainActivity、BottomNav |
| `project-home` | `feature-home` | 首页 |
| `project-category` | `feature-category` | 分类 |
| `project-cart` | `feature-cart` | 购物车 |
| `project-mine` | `feature-mine` | 我的 |

## 依赖

壳 **不 include** 这些模块。本地改完后：

```bash
./gradlew publishModules   # → mavenLocal，版本来自 modules.yaml
./gradlew :app:assembleDebug
```

壳 `app` 只声明 `feature-main`；Tab 版本坐标写在 `feature-main` 的依赖里，并与 YAML 对齐。

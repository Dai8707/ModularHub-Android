# modules/

本目录是 **业务项目（独立 Git 仓）的本地落地目录**，由 `./gradlew syncModules` 拉取。

壳工程 **不会提交** 此目录下的项目内容（见壳仓 `.gitignore`）。

## 标准结构（每个业务项目）

```
modules/project-xxx/
├── .gitignore
├── settings.gradle
├── app/                    ← 独立运行调试
└── feature-xxx/            ← 业务 Library（壳集成）
```

## 项目清单

| 项目 | Library | 职责 |
|------|---------|------|
| `project-main` | `feature-main` | **主框架**：MainActivity、BottomNav、Tab 调度 |
| `project-home` | `feature-home` | 首页 Tab |
| `project-category` | `feature-category` | 分类 Tab |
| `project-cart` | `feature-cart` | 购物车 Tab |
| `project-mine` | `feature-mine` | 我的 Tab |

## 壳工程依赖关系

```
壳 app（仅 Application + 打包）
  └── feature-main
        ├── feature-home
        ├── feature-category
        ├── feature-cart
        └── feature-mine
```

`modules.yaml` 中需声明 **全部 feature**（供 settings include），壳 `app/build.gradle` **只引 `feature-main`**。

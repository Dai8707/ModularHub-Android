# ModularHub 组件化架构（试验版）

与 `seeker-incentive/Android发版控制面-架构.md` 对齐。本仓库是壳工程试验床。

## 1. 分层

```
壳工程 ModularHub-Android
  └── 项目主工程 project-main（feature-main）
        └── 组件 project-home / category / cart / mine
              └── lib（试验阶段暂无）
```

- 壳只依赖 `feature-main` 的 **Maven 坐标**。
- 主工程按坐标依赖各 Tab。
- 组件之间不直接依赖。

## 2. YAML

- `enabled`：是否参与集成。
- `mode: source`：缺仓时 Gradle Sync 自动 clone 到 `modules/project-xxx`。
- `mode: aar`：不拉仓库。
- `aar.version`：**本地和远程都必须有**；依赖解析只用这个版本。

没有 `download` 字段。没有 `implementation project(...)`。

## 3. 本地流程

```
Gradle Sync（缺仓自动 clone）→ publishModules → :app:assembleDebug
```


`publishModules` 把 `mode: source` 的组件按 YAML 版本发到 `mavenLocal`（先 Tab，再 `feature-main`）。壳始终解析：

```
com.modularhub:feature-main:1.0.0
```

## 4. 正式包

`profile: release` → 全部 `mode: aar`。若仍有 source，构建失败。CI 只 checkout 壳，按 YAML 从 Maven 取包。

## 5. 插件职责

| 插件 | 做什么 |
|------|--------|
| `modularhub.settings` | 读 YAML；缺仓自动 clone；**不** include 业务模块；加 `mavenLocal` / 私服 |
| `modularhub.deps` | `modularHub { implementation 'xxx' }` → 带版本坐标 |
| `modularhub.root` | `publishModules` / `listModules` / `validateModulesYaml` |

## 6. 与旧 Demo 的差异

| 旧行为 | 现行为 |
|--------|--------|
| `mode: source` → `project(':feature-xxx')` | `mode: source` 只拉代码 |
| settings include 源码模块 | 壳工程不 include 业务模块 |
| 本地联调改完即可编壳 | 改完需 `publishModules` 后再编壳 |

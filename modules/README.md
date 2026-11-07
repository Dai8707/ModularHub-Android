# modules/

业务项目（独立 Git 仓）的本地落地目录，缺仓时由 Gradle Sync 自动 clone 到 `modules/project-xxx/`。壳仓 **忽略** 此目录内容。

## 结构

```
modules/project-xxx/
├── app/
└── feature-xxx/
```

壳 **不 include** 这些模块。改完源码后：

```bash
./gradlew publishModules
./gradlew :app:assembleDebug
```

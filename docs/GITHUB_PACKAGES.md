# 用 GitHub Packages 当私人 Maven

试验阶段把组件 AAR 发到你自己的 GitHub Packages，壳按坐标拉取。公司私服以后只换地址和 Token。

## 1. 创建 Token

1. 打开 [GitHub → Settings → Developer settings → Personal access tokens](https://github.com/settings/tokens)
2. 建议用 **Fine-grained**（或 Classic）
3. 权限至少：
   - `read:packages`
   - `write:packages`
   - 对仓库 `Dai8707/ModularHub-Android` 有 Contents 写权限（包挂在这个仓下）
4. 生成后复制 Token（只显示一次）

## 2. 本机配凭证（不要提交到 Git）

编辑用户目录下的 Gradle 配置（没有就新建）：

`C:\Users\dailongtao\.gradle\gradle.properties`

```properties
gpr.user=Dai8707
gpr.token=github_pat_xxxx或ghp_xxxx
```

也可用环境变量：`GITHUB_ACTOR` + `GITHUB_TOKEN`。

## 3. 工程里已经接好的地址

`modules.yaml`：

```yaml
defaults:
  maven:
    group_prefix: com.modularhub
    repository: "https://maven.pkg.github.com/Dai8707/ModularHub-Android"
```

解析顺序：`mavenLocal` → GitHub Packages → Google / Maven Central。

## 4. 发布与编译

```bash
cd ModularHub-Android
./gradlew publishModules      # 有 Token → mavenLocal + GitHub Packages；无 Token → 仅 mavenLocal
./gradlew :app:assembleDebug  # source 缺仓会在配置阶段自动 clone
```


发成功后，在 GitHub 打开 [ModularHub-Android → Packages](https://github.com/Dai8707/ModularHub-Android/packages) 能看到 `feature-home` 等包。

## 5. 注意

| 点 | 说明 |
|----|------|
| 版本不可覆盖 | 同一 `group:artifact:version` 发过就不能再发同版本，要改 `modules.yaml` 里的 `aar.version` |
| Token 别进仓库 | 只放 `~/.gradle/gradle.properties` 或 CI Secret |
| 包可见性 | 默认跟仓库权限走；私有仓的包也是私有的，拉包必须带 Token |
| 和后台的关系 | 后台以后只管 YAML / 版本号；Gradle 仍用这里的地址和本机/CI Token 拉包 |

## 6. 以后换公司 Maven

改 `modules.yaml` 的 `repository`，把 `gpr.*` 换成公司私服账号即可，`modularHub { implementation 'xxx' }` 不用改。

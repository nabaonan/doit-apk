/**
 * 仓库选择：本地开发走阿里云镜像（国内快），CI 只用官方仓库。
 *
 * GitHub Actions 的 runner 在境外，访问 maven.aliyun.com 会被反代直接返回
 * 502 Bad Gateway。Gradle 遇到某个依赖只在被污染的那个仓库里找过，就会在
 * 配置阶段直接报 "Could not resolve all artifacts"，整套构建还没开始就挂了。
 * 之前 CI 连红三次都卡在这里，所以这里按环境隔离：CI 环境变量 CI=true，
 * 与 GitHub Actions 自动注入的一致，官方仓库反而更快更稳。
 */
pluginManagement {
    val onCi = System.getenv("CI").equals("true", ignoreCase = true)
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        if (!onCi) {
            // 仅本机：国内镜像优先，加速拉取
            maven("https://maven.aliyun.com/repository/gradle-plugin")
            maven("https://maven.aliyun.com/repository/public")
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    val onCi = System.getenv("CI").equals("true", ignoreCase = true)
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        if (!onCi) {
            maven("https://maven.aliyun.com/repository/public")
        }
        mavenCentral()
    }
}

rootProject.name = "doit"
include(":app")

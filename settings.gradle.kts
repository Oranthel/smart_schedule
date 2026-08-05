// 判断是否在 CI 环境（GitHub Actions 等）
val isCi = System.getenv("CI") != null || System.getenv("GITHUB_ACTIONS") != null

pluginManagement {
    repositories {
        if (isCi) {
            // CI：使用官方源（国内镜像不可达）
            google()
            mavenCentral()
            gradlePluginPortal()
        } else {
            // 本地开发：国内镜像优先
            maven { url = uri("https://maven.aliyun.com/repository/google") }
            maven { url = uri("https://maven.aliyun.com/repository/central") }
            maven { url = uri("https://maven.huaweicloud.com/repository/maven/") }
            google()
            mavenCentral()
            gradlePluginPortal()
        }
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        if (isCi) {
            google()
            mavenCentral()
        } else {
            maven { url = uri("https://maven.aliyun.com/repository/google") }
            maven { url = uri("https://maven.aliyun.com/repository/central") }
            maven { url = uri("https://maven.huaweicloud.com/repository/maven/") }
            google()
            mavenCentral()
        }
    }
}

rootProject.name = "SmartPlanner"
include(":app")

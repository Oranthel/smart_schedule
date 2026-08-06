pluginManagement {
    repositories {
        // CI 环境用官方源（国内镜像在 GitHub Actions 海外服务器不可达）
        if (System.getenv("CI") != null || System.getenv("GITHUB_ACTIONS") != null) {
            google()
            mavenCentral()
            gradlePluginPortal()
        } else {
            // 本地开发：国内镜像优先加速
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
        if (System.getenv("CI") != null || System.getenv("GITHUB_ACTIONS") != null) {
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

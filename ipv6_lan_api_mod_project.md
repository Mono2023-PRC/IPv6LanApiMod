# IPv6 LAN API — NeoForge Mod 项目文档

> 一个 NeoForge 1.21.1 客户端 Mod，当玩家在单人游戏中**打开对局域网开放**后，自动启动一个轻量级 Web API 服务，将本机公网 IPv6 地址暴露给其他设备访问。

---

## 一、功能概述

| 功能 | 说明 |
|------|------|
| **触发条件** | 单人游戏世界中点击"对局域网开放"后自动启动 |
| **关闭条件** | 退出该单人游戏世界（服务器停止）时自动关闭 |
| **Web API** | 监听 `0.0.0.0:24016`，访问 `/` 或 `/ip` 返回 JSON |
| **返回格式** | `{"ip":"240e:123:456:789::1"}` |
| **IP 更新** | 每 30 分钟轮询一次公网 IPv6，支持多 API 故障转移 |
| **占用** | 使用 JDK 内置 `HttpServer`，零第三方 HTTP 依赖 |

---

## 二、项目文件结构

```
ipv6-lan-api/
├── .github/
│   └── workflows/
│       └── build.yml              # GitHub Actions 自动构建
├── gradle/
│   └── wrapper/
│       ├── gradle-wrapper.jar
│       └── gradle-wrapper.properties
├── src/
│   └── main/
│       ├── java/com/example/ipv6lanapi/
│       │   ├── IPv6LanApiMod.java         # Mod 主类与生命周期管理
│       │   ├── WebApiServer.java          # 内嵌 Web API 服务 (端口 24016)
│       │   ├── IPv6Fetcher.java           # 多源 IPv6 获取与定时更新
│       │   └── mixin/
│       │       └── IntegratedServerMixin.java  # 监听"开放局域网"事件
│       └── resources/
│           ├── META-INF/
│           │   └── neoforge.mods.toml     # Mod 元数据
│           ├── mixins.ipv6lanapi.json     # Mixin 配置
│           └── pack.mcmeta
├── build.gradle
├── gradle.properties
├── settings.gradle
└── README.md
```

---

## 三、核心代码

### 3.1 Mod 主类 — `IPv6LanApiMod.java`

```java
package com.example.ipv6lanapi;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 客户端 Mod 主类。
 * 当单人游戏世界开放局域网时（由 Mixin 触发）启动 Web API 和 IP 定时更新器；
 * 当退出该世界（服务器停止）时自动关闭所有服务。
 */
@Mod(value = "ipv6lanapi", dist = Dist.CLIENT)
public class IPv6LanApiMod {
    public static final Logger LOGGER = LoggerFactory.getLogger("IPv6LanApi");

    private static WebApiServer webServer;
    private static IPv6Fetcher ipv6Fetcher;

    public IPv6LanApiMod() {
        NeoForge.EVENT_BUS.register(this);
    }

    /**
     * 由 IntegratedServerMixin 在玩家点击"对局域网开放"后调用。
     */
    public static void onLanOpened(int port) {
        LOGGER.info("LAN world opened on port {}, starting IPv6 API service...", port);

        if (webServer == null) {
            webServer = new WebApiServer();
            webServer.start();
        }
        if (ipv6Fetcher == null) {
            ipv6Fetcher = new IPv6Fetcher();
            ipv6Fetcher.start();
        }
    }

    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        LOGGER.info("Server stopped, shutting down IPv6 API service...");
        shutdown();
    }

    public static void shutdown() {
        if (ipv6Fetcher != null) {
            ipv6Fetcher.stop();
            ipv6Fetcher = null;
        }
        if (webServer != null) {
            webServer.stop();
            webServer = null;
        }
    }
}
```

### 3.2 Web API 服务 — `WebApiServer.java`

```java
package com.example.ipv6lanapi;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * 基于 JDK 内置 HttpServer 的轻量级 API 服务。
 * 监听 0.0.0.0:24016，对根路径和 /ip 均返回当前 IPv6 地址。
 */
public class WebApiServer {
    private static final int PORT = 24016;
    private HttpServer server;

    public void start() {
        try {
            server = HttpServer.create(new InetSocketAddress(PORT), 0);
            server.createContext("/", new IpHandler());
            server.createContext("/ip", new IpHandler());
            server.setExecutor(null); // 使用默认线程池
            server.start();
            IPv6LanApiMod.LOGGER.info("Web API server started on http://0.0.0.0:{}", PORT);
        } catch (IOException e) {
            IPv6LanApiMod.LOGGER.error("Failed to start Web API server on port {}", PORT, e);
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            IPv6LanApiMod.LOGGER.info("Web API server stopped");
        }
    }

    static class IpHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String ip = IPv6Fetcher.getCurrentIp();
            String response = String.format("{\"ip\":\"%s\"}", ip);
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);

            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(200, bytes.length);

            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }
}
```

### 3.3 IPv6 获取器 — `IPv6Fetcher.java`

```java
package com.example.ipv6lanapi;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 多源公网 IPv6 地址获取器。
 * 服务启动时立即获取一次，之后每 30 分钟轮询更新。
 * 支持多 API 故障转移，只要有一个成功即采用。
 */
public class IPv6Fetcher {
    private static final String[] API_URLS = {
        "https://v6.yinghualuo.cn/bejson",
        "https://ifconfig.co",
        "https://api6.ipify.org?format=json",
        "https://ipv6.ip.sb"
    };

    private static volatile String currentIp = "";
    private ScheduledExecutorService scheduler;

    public void start() {
        fetchIp(); // 立即获取一次

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "IPv6-Fetcher");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(this::fetchIp, 30, 30, TimeUnit.MINUTES);
    }

    public void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    public static String getCurrentIp() {
        return currentIp;
    }

    private void fetchIp() {
        for (String apiUrl : API_URLS) {
            try {
                URL url = new URL(apiUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (IPv6-LAN-API)");
                conn.setRequestProperty("Accept", "application/json");

                int code = conn.getResponseCode();
                if (code == 200) {
                    try (InputStreamReader reader = new InputStreamReader(conn.getInputStream())) {
                        JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                        if (json.has("ip")) {
                            String ip = json.get("ip").getAsString();
                            if (isValidIPv6(ip)) {
                                currentIp = ip;
                                IPv6LanApiMod.LOGGER.info("IPv6 address updated via {}: {}", apiUrl, ip);
                                return;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                IPv6LanApiMod.LOGGER.warn("IPv6 API failed {}: {}", apiUrl, e.getMessage());
            }
        }
        IPv6LanApiMod.LOGGER.warn("All IPv6 APIs failed. Keeping current IP: {}", currentIp);
    }

    private boolean isValidIPv6(String ip) {
        return ip != null && !ip.isEmpty() && ip.contains(":");
    }
}
```

### 3.4 Mixin 注入 — `IntegratedServerMixin.java`

```java
package com.example.ipv6lanapi.mixin;

import com.example.ipv6lanapi.IPv6LanApiMod;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.GameType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 注入 IntegratedServer#publishToLAN 方法，在玩家成功开放局域网后触发 Mod 逻辑。
 */
@Mixin(IntegratedServer.class)
public class IntegratedServerMixin {

    @Inject(method = "publishToLAN", at = @At("RETURN"))
    private void ipv6lanapi$onPublishToLAN(
            GameType gameType,
            boolean cheats,
            int port,
            CallbackInfoReturnable<Component> cir
    ) {
        IPv6LanApiMod.onLanOpened(port);
    }
}
```

---

## 四、配置文件

### 4.1 `gradle.properties`

```properties
org.gradle.jvmargs=-Xmx3G
org.gradle.daemon=false

# Minecraft / NeoForge
minecraft_version=1.21.1
minecraft_version_range=[1.21.1,1.22)
neo_version=21.1.0
neo_version_range=[21.1.0,)
loader_version_range=[4,)

# Parchment 映射（可选，方便开发）
parchment_minecraft_version=1.21.1
parchment_mappings_version=2024.11.17

# Mod 信息
mod_id=ipv6lanapi
mod_name=IPv6 LAN API
mod_license=MIT
mod_version=1.0.0
mod_group_id=com.example.ipv6lanapi
mod_authors=YourName
mod_description=Expose public IPv6 address via Web API when LAN world is opened
```

### 4.2 `settings.gradle`

```gradle
pluginManagement {
    repositories {
        gradlePluginPortal()
        maven { url = 'https://maven.neoforged.net/releases' }
    }
}

plugins {
    id 'org.gradle.toolchains.foojay-resolver-convention' version '0.8.0'
}

rootProject.name = 'ipv6-lan-api'
```

### 4.3 `build.gradle`

```gradle
plugins {
    id 'java-library'
    id 'maven-publish'
    id 'net.neoforged.moddev' version '1.0.21'
}

version = mod_version
group = mod_group_id

base {
    archivesName = mod_id
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

neoForge {
    version = project.neo_version

    parchment {
        mappingsVersion = project.parchment_mappings_version
        minecraftVersion = project.parchment_minecraft_version
    }

    runs {
        client {
            client()
        }
    }

    mods {
        "${mod_id}" {
            sourceSet(sourceSets.main)
        }
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Gson 通常已由 Minecraft 传递依赖，显式声明确保版本
    implementation 'com.google.code.gson:gson:2.11.0'
}

tasks.withType(ProcessResources).configureEach {
    var replaceProperties = [
        minecraft_version: minecraft_version,
        minecraft_version_range: minecraft_version_range,
        neo_version: neo_version,
        neo_version_range: neo_version_range,
        loader_version_range: loader_version_range,
        mod_id: mod_id,
        mod_name: mod_name,
        mod_license: mod_license,
        mod_version: mod_version,
        mod_authors: mod_authors,
        mod_description: mod_description
    ]
    inputs.properties replaceProperties
    filesMatching(['META-INF/neoforge.mods.toml']) {
        expand replaceProperties
    }
}

tasks.withType(JavaCompile).configureEach {
    options.encoding = 'UTF-8'
}
```

### 4.4 `neoforge.mods.toml`

```toml
modLoader="javafml"
loaderVersion="${loader_version_range}"
license="${mod_license}"

[[mods]]
modId="${mod_id}"
version="${mod_version}"
displayName="${mod_name}"
description='''${mod_description}'''
authors="${mod_authors}"

[[dependencies.${mod_id}]]
modId="neoforge"
type="required"
versionRange="${neo_version_range}"
ordering="NONE"
side="CLIENT"

[[dependencies.${mod_id}]]
modId="minecraft"
type="required"
versionRange="${minecraft_version_range}"
ordering="NONE"
side="CLIENT"
```

### 4.5 `mixins.ipv6lanapi.json`

```json
{
    "required": true,
    "minVersion": "0.8",
    "package": "com.example.ipv6lanapi.mixin",
    "compatibilityLevel": "JAVA_21",
    "mixins": [
        "IntegratedServerMixin"
    ],
    "client": [],
    "injectors": {
        "defaultRequire": 1
    }
}
```

### 4.6 `pack.mcmeta`

```json
{
    "pack": {
        "description": "IPv6 LAN API resources",
        "pack_format": 46
    }
}
```

---

## 五、GitHub Actions 自动构建

创建 `.github/workflows/build.yml`：

```yaml
name: Build IPv6 LAN API Mod

on:
  push:
    branches: [main, master]
  pull_request:
    branches: [main, master]

permissions:
  contents: read

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Set up Java 21
        uses: actions/setup-java@v4
        with:
          java-version: "21"
          distribution: "temurin"

      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v3
        with:
          cache-read-only: ${{ github.ref != 'refs/heads/main' }}

      - name: Grant execute permission
        run: chmod +x gradlew

      - name: Build with Gradle
        run: ./gradlew build --no-daemon

      - name: Upload Artifact
        uses: actions/upload-artifact@v4
        with:
          name: ipv6lanapi-mod
          path: build/libs/*.jar
          if-no-files-found: error
```

**自动发布 Release**（可选）：

```yaml
name: Release
on:
  push:
    tags:
      - "v*"
permissions:
  contents: write
jobs:
  release:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: "21"
          distribution: "temurin"
      - uses: gradle/actions/setup-gradle@v3
      - run: chmod +x gradlew
      - run: ./gradlew build --no-daemon
      - uses: softprops/action-gh-release@v2
        with:
          files: build/libs/*.jar
          generate_release_notes: true
```

---

## 六、使用说明

### 6.1 构建步骤

1. Fork/Clone 本项目到 GitHub 仓库；
2. 修改 `gradle.properties` 中的 `mod_authors` 等信息；
3. Push 到 GitHub，Actions 自动构建；
4. 在 Actions 页面下载 Artifact（或打 Tag 自动发 Release）。

### 6.2 游戏内使用

1. 将构建好的 `.jar` 放入客户端的 `mods` 文件夹（需 NeoForge 1.21.1）；
2. 进入单人游戏世界；
3. 按 `Esc` -> "对局域网开放" -> 选择游戏模式 -> 确定；
4. 此时 Mod 自动在后台启动 Web API；
5. 在同一网络（或具备 IPv6 连通性的网络）的其他设备上访问：
   ```
   http://[你的IPv6地址]:24016/
   ```
   或
   ```
   http://[你的IPv6地址]:24016/ip
   ```
   返回示例：
   ```json
   {"ip":"240e:123:456:789::1"}
   ```

### 6.3 防火墙要求

确保客户端主机的防火墙放行 **TCP 24016** 端口，否则外部设备无法访问。

---

## 七、设计要点说明

| 设计决策 | 原因 |
|---------|------|
| **Mixin 注入 `publishToLAN`** | 这是 Minecraft 中"开放局域网"的唯一标准入口，能精确捕获玩家操作时机，避免轮询检测的性能开销 |
| **JDK 内置 `HttpServer`** | 零额外依赖，启动速度极快，资源占用极低（单线程即可处理） |
| **多 API 轮询 + 故障转移** | 公网 IPv6 探测服务可能因网络环境或服务商故障不可用，多源保障可靠性 |
| **30 分钟更新周期** | 家庭宽带 IPv6 前缀通常不会频繁变化，30 分钟是平衡实时性与网络开销的合理值 |
| **只在进入游戏后启动** | 通过 `ServerStoppedEvent` 确保退出世界即关闭服务，避免后台常驻进程 |
| **Daemon 线程** | 防止定时任务线程阻止 JVM 正常退出 |

---

## 八、注意事项

1. **IPv6 连通性**：本 Mod 仅获取并暴露 IPv6 地址。如果玩家网络没有 IPv6，返回的 IP 字段将为空字符串。其他设备需同样具备 IPv6 能力才能访问。
2. **端口冲突**：如果 24016 端口已被占用，Web API 启动会失败并在日志中报错。
3. **安全风险**：开放 Web API 意味着局域网内（或 IPv6 公网可达范围内）任何设备都能访问你的 IP 地址。虽然本 Mod 仅返回 IP 字符串，但请确保你理解开放端口的安全含义。
4. **API 可用性**：`ifconfig.co` 默认可能返回 HTML，若遇到解析失败会自动切换下一个 API。如长期失败，可在 `IPv6Fetcher.API_URLS` 中替换为更稳定的端点。

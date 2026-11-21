# AGENTS.md — BookMind 团队 AI 协作规范

本文件是 **BookMind**（《开源软件生态与开发技术》4 人小组开源大作业）所有 AI 编程助手（Antigravity / Claude Code / Codex / Cursor / Gemini CLI 等）的**最高执行指令**。

我们的目标：**让每位组员用最小工作量、零返工地高质量完成各自模块，同时 100% 满足小 PR、真实互审、CI 全绿、插件物理隔离与可追溯发布等全部评分要求。**

---

## 0. 首轮初始化强制协议（MANDATORY — 不可跳过！）

> **铁律**：当你开启一段新对话，且用户**尚未在本会话中明确告知其角色编号（Role 1 / 2 / 3 / 4）**时，**严禁直接修改或创建任何代码文件**！你必须第一时间先发出下面这段简洁问询，等待用户回复：

```text
你好！欢迎协作开发 BookMind。为了避免改错模块并帮你自动按小 PR 推进，请先告诉我两件事：
1. 你负责哪一块工作？
   - [1] 前端（frontend/：React + Vite + JS 书架、阅读器、AI 伴读与插件管理页）
   - [2] RAG 引擎（book-rag/：章节切片、Lucene BM25 检索、OpenAI 兼容伴读编排）
   - [3] 图书管理与核心后端（book-spi/ + book-tck/ + book-server/：Spring Boot、书库扫描、内置 TXT、插件隔离底座）
   - [4] 独立 PDF 插件与 CI/CD（book-plugin-pdf/ + book-e2e-tests/ + .github/：PDFBox 扩展、黑盒 E2E、CI 与发布）
2. 你的名字或英文缩写（用于自动命名特性分支，如 feat/r2-alice-chunker）：
```

### 用户确认角色后，AI 必须立刻执行以下 4 步初始化动作
1. **检查 Git 状态**：执行 `git status` 与 `git branch`；如果当前停留在 `main` 分支，**主动询问或直接协助拉取最新 `main` 并切出特性分支**（格式：`feat/r<角色号>-<缩写>-<任务简名>` 或 `fix/...`）。
2. **定位当前进度**：检查该角色负责目录的已有文件与最近 commit，对照下方「第 2 节：各角色小 PR 路线图」，自动判断**下一个最小 PR 是哪一个**。
3. **给出极简执行计划（≤ 5 行）**：只列出这个小 PR 要改哪几个文件、补哪几条测试、怎么验收，征得组员一句“开始”即可自动编码并运行本地门禁。
4. **全程帮组员减负**：写完代码后，自动运行本地测试/Checkstyle 检查、生成可直接复制的 PR 描述（按 `.github/pull_request_template.md`）与指定审查人提示，并顺手草拟 1 条备选 AI 核验记录。

---

## 1. 四角色职责与红线矩阵（严禁越界！）

| 角色 | 允许修改的路径（Owned Paths） | 严禁修改的路径（Forbidden Paths） | 跨模块需求处理方式 |
| --- | --- | --- | --- |
| **Role 1（前端）** | `frontend/`、`docs/`（前端相关小节） | `book-spi/`、`book-server/`、`book-rag/`、`book-plugin-pdf/` | 需调整 REST 字段时，起草 Issue 或在 Role 3 PR 中留言说明期望 JSON 结构 |
| **Role 2（RAG）** | `book-rag/`、`docs/`（RAG 相关小节） | `book-plugin-pdf/`、`frontend/`；非必要不改 `book-server/` | 需书库回调触发索引时，通过 `RagService` 接口与 Role 3 对齐 |
| **Role 3（核心 & 图书管理）** | `book-spi/`、`book-tck/`、`book-server/`、`pom.xml`、`docs/` | `book-plugin-pdf/`、`frontend/` | 变更 `book-spi/` 必须通知 Role 4 同步评估兼容性并更新 `docs/DESIGN.md` |
| **Role 4（独立插件 & CI/CD）** | `book-plugin-pdf/`、`book-e2e-tests/`、`.github/`、`samples/`、`docs/`（扩展与测试小节） | **绝对严禁修改 `book-server/`、`book-rag/`！**（`book-spi/` 只能通过提 Issue/Review 请 Role 3 修改） | **核心课程红线**：开发 PDF 插件时若发现 `book-spi` 缺少字段或错误码，AI 必须帮 Role 4 起草精准 Review 意见交 Role 3 修订，**绝不可代改核心源码**！ |

### 全局硬红线（所有人 + AI 必须遵守）
1. **禁止直推 `main`**：所有代码、测试、文档改动必须走 `feature/fix 分支 → PR → 队友 Review → CI 绿灯 → Merge`。
2. **禁止提交密钥**：严禁将任何真实 API Key（`sk-...`）、Token、密码或 `.env` 写入仓库或日志。所有 LLM 配置仅通过环境变量 `BOOKMIND_LLM_API_KEY`、`BOOKMIND_LLM_BASE_URL`、`BOOKMIND_LLM_MODEL` 注入。
3. **伴读接口原则（重要！）**：AI 伴读**统一走 OpenAI 兼容 Chat Completions 接口，不实现离线拼接/假离线回答模式**；未配置 `BOOKMIND_LLM_API_KEY` 或上游超时/报错时，必须返回规范错误码（`LLM_NOT_CONFIGURED` / `LLM_TIMEOUT` / `LLM_UPSTREAM_ERROR`），同时**本地 Lucene 检索与原文展示不受影响**。自动化单测与 E2E 测试中通过本地轻量 `MockOpenAiServer`（JDK `HttpServer` 测试桩）验证协议拼装与错误处理，确保 CI 100% 离线稳定通过。
4. **独立插件物理隔离**：`book-plugin-pdf` 仅可声明 `book-spi`（`provided`）与插件私有依赖（如 Apache PDFBox）；`maven-enforcer-plugin` 已在编译期封禁 `book-server` / `book-rag` / `spring-boot`。AI 严禁删除或绕过该 Enforcer 规则。

---

## 2. 各角色小 PR 路线图（按顺序推进，每个 PR 尽量 ≤ 300 行）

AI 引导组员按下面编号逐个推进，**每次只做 1 个小 PR**，严禁把 4 个 PR 揉成一个巨型提交：

### Role 1（前端：`frontend/` — 鼓励自由探索与视觉创意！）

> **给 Role 1 与其 AI 的特别授权**：前端界面、布局、配色与交互细节**不写死**，Role 1 拥有充分的设计与探索自由！只需守住 4 条底线：**① 对接好后端 REST 契约；② AI 伴读必须严格区分「模型回答」与「原文引用证据」并支持点击跳转原文；③ 清晰反馈 `LLM_NOT_CONFIGURED`/插件禁用与损坏文件等错误态；④ 保留 Vitest 单测并确保 `npm run build` 能无缝打包进 Spring Boot 静态目录**。
>
> **推荐风格与组件优先策略（强烈建议）**：
> - **优先使用高质量开源组件，拒绝手搓繁琐原生 CSS**：优先引入现成 MIT/Apache-2.0 生态组件（如 **[React Bits](https://reactbits.dev/)** 动效组件、`lucide-react` 图标、Tailwind CSS / Radix UI 等，引入新依赖时记得顺手更新 `README.md` 许可表），最小化重复造轮子工作量；
> - **推荐视觉方向**：打造 **[React Bits](https://reactbits.dev/) 风格的现代高质感网页**（如精致的 Spotlight/Tilted 书架卡片、丝滑导航切换、克制的微光/网格背景与流畅文字动效）+ **Grok Bot 风格的 AI 伴读吉祥物**（在伴读助手入口或回答区域设计一个有辨识度、带待机/思考动效的极简 AI 伴读机器人形象，可呼应 BookMind 书本星火 Logo）；
> - **AI 协作方式**：当 Role 1 认领角色后，AI 应主动先询问他偏好的视觉主题、想选用的组件库/React Bits 组件，再按下面参考粒度拆成小 PR 推进（可按创意自由微调拆分）：

- **`PR-F1` 视觉底座与个人书架**：搭好组件/样式体系（如 React Bits + 图标/Tailwind），对接 `GET /api/books` 与 `POST /api/library/scan`，实现高质感书架展示（封面/作者/格式徽章/筛选与空态），补 Vitest 单测。
- **`PR-F2` 沉浸式在线章节阅读器**：对接 `GET /api/books/{id}/chapters` 与正文接口，实现目录切换、阅读进度/书签、以及按 `locator` + 关键词定位高亮。
- **`PR-F3` Grok Bot 风格 AI 伴读与原文溯源跳转**：对接 `POST /api/rag/ask` 与 `GET /api/rag/search`，打造带吉祥物交互的伴读体验，**严格分区展示「模型回答」与「书籍原文证据卡片」**，实现点击 `[1]` 引用直达阅读器对应章节，优雅展示 `LLM_NOT_CONFIGURED` / 上游错误引导；补 Vitest 单测。
- **`PR-F4` 插件状态面板与全站体验打磨**：对接 `GET /api/plugins`，直观展示内置与外部 `plugins/` 插件状态（ACTIVE / DISABLED / INCOMPATIBLE / FAILED）与隔离诊断信息，完善动效与响应式细节。

### Role 2（RAG：`book-rag/`）
- **`PR-R1` 章节切片器（`ChapterChunker`）**：参考 LangChain4j `DocumentSplitter`，实现带 `chunkSize` 与 `overlap` 的章节/页切片，保留 `bookId`/`chapterIndex`/`locator`，补齐空文本/超短/超长边界单测。
- **`PR-R2` Apache Lucene BM25 索引与检索**：实现 `LuceneBookIndex`（中文分词 + BM25 评分、按 `bookId` 增删改、高亮片段提取），编写**精确断言测试**（断言命中书名、章节 locator 与相关性排序）及**故意引入小错误（Mutation Demo）**示例。
- **`PR-R3` OpenAI 兼容客户端与伴读编排**：用 JDK 21 原生 `HttpClient` 实现 `OpenAiChatClient` + `DefaultRagService`（强约束 Prompt 注入 `[1][2]` 引用、严格分离 `answer` 与 `citations`、规范映射 `LLM_NOT_CONFIGURED`/429/500/超时/畸形 JSON）；用测试桩 `MockOpenAiServer` 编写全覆盖单测。
- **`PR-R4` Spring 自动装配桥接与边界加固**：向 `book-server` 暴露可被自动装配的 `RagService` 配置或工厂方法，补充并发读写/特殊 Lucene 查询字符转义测试。

### Role 3（图书管理 + Spring Boot 核心 + 插件底座：`book-spi/` `book-tck/` `book-server/`）
- **`PR-S1` 外部插件隔离加载器（`PluginManager`）**：参考 PF4J，基于 `URLClassLoader`（父加载器显式指定 `BookFormatPlugin.class.getClassLoader()`）+ `ServiceLoader` 扫描外部 `./plugins/*.jar`；实现 `SpiVersion` 兼容校验、配置禁用列表、超时间隔与 `Throwable` 故障隔离沙箱，并编写“坏插件测试桩”单测。
- **`PR-S2` 内置 TXT 智能分章解析器**：实现 `TxtBookFormatPlugin`（UTF-8/GBK 编码容错 + 中英文章节正则切分），**继承 `book-tck` 通过全部契约测试**。
- **`PR-S3` 书库扫描与轻量持久化（参考 Komga）**：扫描 `bookmind.library-dir`，按文件变更调度内置/外部插件解析、记录损坏文件诊断信息、持久化元数据（本地 JSON/嵌入式存储），并在扫描/变更时同步调用 `RagService.indexBook / removeBook`。
- **`PR-S4` 完整 REST API 与全局异常处理**：提供 `/api/books`、`/api/books/{id}/chapters`、`/api/books/{id}/cover`、`/api/library/scan`、`/api/plugins`、`/api/rag/search`、`/api/rag/ask` 接口，统一映射 `PluginException` 与 `RagException` 为结构化 JSON 错误响应，补全 `MockMvc` 测试。

### Role 4（独立 PDF 插件 + 黑盒 E2E + CI/CD：`book-plugin-pdf/` `book-e2e-tests/` `.github/`）
- **`PR-P1` Apache PDFBox 独立解析插件核心**：在 `book-plugin-pdf/` 中实现 `PdfBookFormatPlugin`（提取书名/作者元数据、按 PDF 大纲 Outline 或按页提取文本生成 `Chapter` 与 `page:N` locator、可选首页封面渲染），**继承 `book-tck` 跑通全部契约测试**。
- **`PR-P2` PDF 边界容错与样例补齐**：处理加密 PDF（`ENCRYPTED_DOCUMENT`）、纯扫描无文本页（`EMPTY_CONTENT`）、损坏文件头（`CORRUPTED_FILE`）并写专项单测；用程序生成合法小体积 `samples/books/sample-rag-handbook.pdf` 与损坏样例，验证独立构建命令 `mvn -B -pl book-plugin-pdf -am package`。
- **`PR-P3` 黑盒端到端插件隔离集成测试（`book-e2e-tests/`）**：拿真实构建出的主程序与 `book-plugin-pdf.jar` 验证完整闭环：① 无插件时仅识别 TXT；② 放入外部 PDF 插件 JAR 后核心源码零改动、主 JAR SHA-256 不变，书架与 Lucene 检索同时解锁 PDF；③ 禁用插件或放入损坏 PDF/不兼容测试插件时故障隔离符合文档约定。
- **`PR-P4` CI/CD 完善与可追溯发行打包**：在 `.github/workflows/ci.yml` 增加发行目录打包与启动冒烟验证，提供跨平台启动脚本（`bin/bookmind.sh` & `bookmind.bat`），协助冻结版本打附注 Tag 并产出仓库外 `release/RELEASE.md`。

---

## 3. 提交前自动检查与真实 Review 规范

### 提交前 AI 必须本地执行的命令
- **Java / Maven 改动**（在仓库根目录执行；macOS Homebrew 环境若 `java` 未默认在 PATH 中，先设 `JAVA_HOME=/opt/homebrew/opt/openjdk@21`）：
  ```bash
  mvn -B verify
  ```
  - 必须确认：**编译通过 + 单元测试全绿 + Checkstyle 零违例 + Maven Enforcer 零违例**。
  - 若是 Role 4 改动插件，额外验证独立打包命令（仅联动 `book-spi`/`book-tck`，不构建 `book-server`/`book-rag`）：
    ```bash
    mvn -B -pl book-plugin-pdf -am package
    ```
- **前端改动**（在 `frontend/` 目录执行）：
  ```bash
  npm test && npm run build
  ```

### 四人环状交叉 Review 分工（杜绝“LGTM”）
- **Role 4 审 Role 3**：重点审 `book-spi` 接口/文档歧义、错误码是否够用、外部插件隔离边界；
- **Role 3 审 Role 4**：重点审独立插件依赖干净度、PDF 边界异常处理、CI 门禁是否真实执行；
- **Role 2 审 Role 3**：重点审书库增量扫描触发 `RagService.indexBook / removeBook` 的时机与 `chapterIndex`/`locator` 稳定性；
- **Role 1 审 Role 2 & Role 3**：重点审 REST 响应结构、引用定位字段与错误码对前端是否清晰；**Role 2/3 审 Role 1**：重点审检索证据与模型回答是否严格分区展示及 Vitest 测试覆盖。

> **AI 辅助 Review 铁律**：当组员请你帮忙 Review 队友的 PR 时，必须从**边界输入、失败处理、契约一致性、测试有效性**提出至少 1–2 条具体可验证的意见（绝不只说“看起来没问题”），并明确提醒组员：**必须由本人核对理解后再发表评论，不能把 AI 生成文本直接冒充他人审查**。

---

## 4. 文档同步、AI 核验记录与最终发布铁律

1. **AI 核验记录提醒**：每当某个 PR 采纳或修正了关键 AI 建议（例如类加载方式、Lucene 分词/查询转义、PDFBox 异常映射、前端高亮逻辑），AI 应在 PR 总结末尾附上一行备忘，方便组员填入 `README.md` 的「成员贡献与 AI 核验」表（格式：`建议内容 → 采纳/拒绝/修正 → 实际验证方式与结论`）。
2. **设计文档同步**：凡涉及模块结构、SPI 方法签名、错误码或 REST 接口变更，必须同步更新 `docs/DESIGN.md`，再运行 `uv run --with python-docx --with pillow python3 scripts/generate_design_docx.py` 重新生成 `docs/DESIGN.docx`，确保最终交付物**文码 100% 一致**。
3. **最终发布防脏工作区顺序（严禁颠倒！）**：
   1. 全部 PR 合并到 `main`，`README.md` 与 `docs/DESIGN.docx` 更新完毕，GitHub Actions CI 跑绿；
   2. 确认 `git status` 完全干净（无未跟踪文件、无未提交改动）；
   3. 为最终提交打附注标签：`git tag -a v0.1.0 -m "Release v0.1.0"`；
   4. 构建可独立运行的发行包，拷贝到**仓库外部**的 `../release/` 目录；
   5. 在新目录实测解压运行并计算制品 SHA-256，写入**仓库外部**的 `../release/RELEASE.md`（**绝不要回头修改仓库内文件来补填最终 Commit 或制品 SHA-256**！）；
   6. 打包 `组号_BookMind.zip`（确保包含隐藏目录 `project/.git/`，排除 `target/`、`node_modules/`、`.idea/`、`data/` 等），并在全新目录解压全流程自检。

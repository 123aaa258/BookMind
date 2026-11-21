#!/usr/bin/env python3
"""Generate crisp, high-DPI BookMind architecture and sequence diagrams plus PNG logos."""

from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent.parent
IMG_DIR = ROOT / "docs" / "images"
IMG_DIR.mkdir(parents=True, exist_ok=True)
FRONTEND_PUB = ROOT / "frontend" / "public"
FRONTEND_PUB.mkdir(parents=True, exist_ok=True)

FONT_BOLD_CANDIDATES = [
    "/System/Library/Fonts/Hiragino Sans GB.ttc",
    "/System/Library/Fonts/STHeiti Medium.ttc",
    "/Library/Fonts/Arial Unicode.ttf",
]
FONT_REG_CANDIDATES = [
    "/System/Library/Fonts/Hiragino Sans GB.ttc",
    "/System/Library/Fonts/STHeiti Light.ttc",
    "/Library/Fonts/Arial Unicode.ttf",
]


def load_font(size: int, bold: bool = False) -> ImageFont.FreeTypeFont:
    candidates = FONT_BOLD_CANDIDATES if bold else FONT_REG_CANDIDATES
    for path in candidates:
        p = Path(path)
        if p.exists():
            idx = 1 if (bold and path.endswith("Hiragino Sans GB.ttc")) else 0
            try:
                return ImageFont.truetype(str(p), size=size, index=idx)
            except Exception:
                return ImageFont.truetype(str(p), size=size)
    return ImageFont.load_default()


def draw_rounded_box(draw, xy, fill, outline, radius=18, width=3, dashed=False):
    x1, y1, x2, y2 = xy
    if not dashed:
        draw.rounded_rectangle([x1, y1, x2, y2], radius=radius, fill=fill, outline=outline, width=width)
        return
    draw.rounded_rectangle([x1, y1, x2, y2], radius=radius, fill=fill, outline=None)
    step = 18
    for x in range(x1 + radius, x2 - radius, step):
        draw.line([(x, y1), (min(x + 10, x2 - radius), y1)], fill=outline, width=width)
        draw.line([(x, y2), (min(x + 10, x2 - radius), y2)], fill=outline, width=width)
    for y in range(y1 + radius, y2 - radius, step):
        draw.line([(x1, y), (x1, min(y + 10, y2 - radius))], fill=outline, width=width)
        draw.line([(x2, y), (x2, min(y + 10, y2 - radius))], fill=outline, width=width)


def draw_centered_text(draw, box, lines, fonts, fills, line_gap=10):
    x1, y1, x2, y2 = box
    cx = (x1 + x2) / 2
    heights = []
    for text, font in zip(lines, fonts):
        bbox = draw.textbbox((0, 0), text, font=font)
        heights.append(bbox[3] - bbox[1])
    total_h = sum(heights) + line_gap * max(0, len(lines) - 1)
    y = y1 + (y2 - y1 - total_h) / 2
    for text, font, fill, h in zip(lines, fonts, fills, heights):
        bbox = draw.textbbox((0, 0), text, font=font)
        tw = bbox[2] - bbox[0]
        draw.text((cx - tw / 2, y - bbox[1]), text, font=font, fill=fill)
        y += h + line_gap


def draw_arrow(draw, start, end, color="#334155", width=4, head_len=16, head_w=10, dashed=False):
    x1, y1 = start
    x2, y2 = end
    dx, dy = x2 - x1, y2 - y1
    dist = (dx * dx + dy * dy) ** 0.5
    if dist < 1:
        return
    ux, uy = dx / dist, dy / dist
    ex, ey = x2 - ux * (head_len - 2), y2 - uy * (head_len - 2)
    if not dashed:
        draw.line([(x1, y1), (ex, ey)], fill=color, width=width)
    else:
        pos = 0.0
        while pos < dist - head_len:
            seg_end = min(pos + 12, dist - head_len)
            draw.line(
                [(x1 + ux * pos, y1 + uy * pos), (x1 + ux * seg_end, y1 + uy * seg_end)],
                fill=color,
                width=width,
            )
            pos += 22
    px, py = -uy, ux
    p1 = (x2, y2)
    p2 = (x2 - ux * head_len + px * head_w, y2 - uy * head_len + py * head_w)
    p3 = (x2 - ux * head_len - px * head_w, y2 - uy * head_len - py * head_w)
    draw.polygon([p1, p2, p3], fill=color)


def draw_pill_label(draw, center, text, font, bg="#FFFFFF", fg="#334155", border="#CBD5E1"):
    lines = text.split("\n")
    widths, heights = [], []
    for line in lines:
        bbox = draw.textbbox((0, 0), line, font=font)
        widths.append(bbox[2] - bbox[0])
        heights.append(bbox[3] - bbox[1])
    gap = 4
    tw = max(widths)
    th = sum(heights) + gap * max(0, len(lines) - 1)
    px, py = 12, 7
    x1 = int(center[0] - tw / 2 - px)
    y1 = int(center[1] - th / 2 - py)
    x2 = int(center[0] + tw / 2 + px)
    y2 = int(center[1] + th / 2 + py)
    draw.rounded_rectangle([x1, y1, x2, y2], radius=12, fill=bg, outline=border, width=2)
    y = center[1] - th / 2
    for line, w, h in zip(lines, widths, heights):
        bbox = draw.textbbox((0, 0), line, font=font)
        draw.text((center[0] - w / 2 - bbox[0], y - bbox[1]), line, font=font, fill=fg)
        y += h + gap


def build_architecture_diagram():
    W, H = 1920, 1220
    img = Image.new("RGB", (W, H), "#F8FAFC")
    draw = ImageDraw.Draw(img)

    f_title = load_font(38, bold=True)
    f_sub = load_font(22, bold=False)
    f_sec = load_font(24, bold=True)
    f_card_t = load_font(22, bold=True)
    f_card_b = load_font(18, bold=False)
    f_pill = load_font(17, bold=True)
    f_small = load_font(16, bold=False)

    # Header
    draw.text((64, 34), "BookMind 总体分层架构与插件物理隔离图", font=f_title, fill="#0F172A")
    draw.text(
        (64, 86),
        "Java 21 · Spring Boot 3 · Maven 多模块编译期防耦合 · URLClassLoader 外部插件隔离 · Lucene BM25 + OpenAI 兼容伴读",
        font=f_sub,
        fill="#475569",
    )

    # Layer 1: Presentation (Frontend)
    draw_rounded_box(draw, (64, 140, 1856, 300), fill="#EEF2FF", outline="#6366F1", radius=20, width=3)
    draw.text((92, 156), "表现层 · frontend/（Role 1：React + Vite + JS，发布期构建打包进 Spring Boot 静态资源，运行零 Node 依赖）", font=f_sec, fill="#312E81")

    fe_cards = [
        (92, 204, 492, 282, "个人书架视图", "封面展示 / 格式筛选 / 扫描刷新"),
        (522, 204, 922, 282, "在线章节阅读器", "目录切换 / 书签进度 / 原文跳转高亮"),
        (952, 204, 1392, 282, "AI 伴读问答（证据分离）", "OpenAI 模型回答 + 可点击原文引用卡片"),
        (1422, 204, 1828, 282, "插件与健康状态页", "已加载/禁用/失败插件诊断与版本查看"),
    ]
    for x1, y1, x2, y2, t, b in fe_cards:
        draw_rounded_box(draw, (x1, y1, x2, y2), fill="#FFFFFF", outline="#A5B4FC", radius=14, width=2)
        draw_centered_text(draw, (x1, y1, x2, y2), [t, b], [f_card_t, f_small], ["#1E1B4B", "#475569"], line_gap=6)

    # Arrow Layer 1 -> Layer 2
    draw_arrow(draw, (620, 300), (620, 366), color="#4F46E5", width=4)
    draw_pill_label(draw, (620, 333), "REST API (/api/books, /api/plugins)", f_pill, bg="#FFFFFF", fg="#3730A3", border="#A5B4FC")
    draw_arrow(draw, (1320, 300), (1320, 366), color="#0891B2", width=4)
    draw_pill_label(draw, (1320, 333), "REST API (/api/rag/search, /api/rag/ask)", f_pill, bg="#FFFFFF", fg="#155E75", border="#67E8F9")

    # Layer 2 Left: book-server (Role 3)
    draw_rounded_box(draw, (64, 368, 1080, 740), fill="#FFFFFF", outline="#3B49DF", radius=20, width=3)
    draw.text((92, 388), "宿主核心 · book-server/（Role 3：Spring Boot 3 + 图书管理 + 插件底座）", font=f_sec, fill="#1E3A8A")

    s_cards = [
        (92, 438, 560, 566, "书库扫描与元数据服务（参考 Komga）", "扫描 samples/books · 变更增量同步\n书籍/章节/封面 API · 轻量本地存储"),
        (590, 438, 1052, 566, "内置基础解析器（TxtBookFormatPlugin）", "UTF-8/GBK 容错 · 中英文章节切分\n遵循统一 BookFormatPlugin 契约"),
        (92, 594, 1052, 716, "外部插件动态加载与故障隔离底座（参考 PF4J）",
         "独立 URLClassLoader + ServiceLoader 扫描 ./plugins/*.jar · SpiVersion 语义化版本校验\n禁用开关（disabled-plugins） · 超时与 Throwable 沙箱隔离（单插件异常绝不拖垮宿主）"),
    ]
    for x1, y1, x2, y2, t, b in s_cards:
        fill = "#EFF6FF" if y1 > 580 else "#F8FAFC"
        border = "#3B82F6" if y1 > 580 else "#CBD5E1"
        draw_rounded_box(draw, (x1, y1, x2, y2), fill=fill, outline=border, radius=14, width=2)
        lines = [t] + b.split("\n")
        fonts = [f_card_t] + [f_small] * (len(lines) - 1)
        fills = ["#1E3A8A"] + ["#334155"] * (len(lines) - 1)
        draw_centered_text(draw, (x1, y1, x2, y2), lines, fonts, fills, line_gap=7)

    # Layer 2 Right: book-rag (Role 2)
    draw_rounded_box(draw, (1120, 368, 1856, 740), fill="#ECFEFF", outline="#0891B2", radius=20, width=3)
    draw.text((1148, 388), "RAG 伴读引擎 · book-rag/（Role 2）", font=f_sec, fill="#164E63")

    r_cards = [
        (1148, 438, 1828, 526, "ChapterChunker 切片器（参考 LangChain4j）", "按章节/页码切分 + Overlap 重叠窗口 · 保留精确 locator"),
        (1148, 544, 1828, 632, "Apache Lucene BM25 本地检索引擎", "中文分词 + 毫秒级跨书召回 + 原文片段高亮（100% 确定性）"),
        (1148, 650, 1828, 722, "OpenAiChatClient 伴读编排（JDK HttpClient）", "仅走 OpenAI 兼容接口（环境变量注入 Key）· 严格分离回答与引用"),
    ]
    for x1, y1, x2, y2, t, b in r_cards:
        draw_rounded_box(draw, (x1, y1, x2, y2), fill="#FFFFFF", outline="#67E8F9", radius=14, width=2)
        draw_centered_text(draw, (x1, y1, x2, y2), [t, b], [f_card_t, f_small], ["#155E75", "#334155"], line_gap=5)

    # Arrow book-server <-> book-rag
    draw_arrow(draw, (1080, 502), (1120, 502), color="#0E7490", width=4)
    draw_pill_label(draw, (1100, 452), "RagService", f_small, bg="#FFFFFF", fg="#155E75", border="#67E8F9")

    # Layer 3: book-spi & book-tck (Zero-dependency Contract Boundary)
    draw_rounded_box(draw, (64, 804, 1650, 926), fill="#FEF3C7", outline="#D97706", radius=20, width=3)
    draw_centered_text(
        draw,
        (64, 804, 1650, 926),
        [
            "公开契约层 · book-spi/（零第三方运行时依赖） + book-tck/（抽象契约测试套件）",
            "BookFormatPlugin  |  ParsedBook / Chapter(locator) / BookCover  |  PluginMetadata  |  PluginException(PluginErrorCode)  |  SpiVersion(1.0)",
        ],
        [f_sec, f_card_b],
        ["#78350F", "#92400E"],
        line_gap=12,
    )

    # Arrows Layer 2 -> Layer 3 (depends on SPI)
    draw_arrow(draw, (572, 740), (572, 804), color="#D97706", width=4)
    draw_pill_label(draw, (418, 772), "依赖公开 SPI（编译/运行共享）", f_pill, bg="#FFFFFF", fg="#92400E", border="#FCD34D")
    draw_arrow(draw, (1430, 740), (1430, 804), color="#D97706", width=4)
    draw_pill_label(draw, (1560, 772), "仅消费 ParsedBook/Chapter", f_pill, bg="#FFFFFF", fg="#92400E", border="#FCD34D")

    # Layer 4 Left: External Plugin (Role 4)
    draw_rounded_box(draw, (64, 988, 1220, 1170), fill="#ECFDF5", outline="#059669", radius=20, width=3, dashed=True)
    draw.text((92, 1006), "外部独立扩展 · ./plugins/book-plugin-pdf.jar（Role 4 独占，严禁修改宿主源码）", font=f_sec, fill="#065F46")
    draw_centered_text(
        draw,
        (92, 1044, 1192, 1158),
        [
            "book-plugin-pdf/（基于 Apache PDFBox 3 · Maven Shade 独立胖 JAR · META-INF/services 注册）",
            "Maven Enforcer 编译期强制封禁 book-server / book-rag / spring-boot · 仅以 provided 依赖 book-spi",
            "放入 ./plugins/ 重启即生效：书架阅读与 Lucene/RAG 跨书伴读同步解锁 PDF，主 JAR SHA-256 零变化",
        ],
        [f_card_t, f_small, f_small],
        ["#064E3B", "#047857", "#065F46"],
        line_gap=8,
    )

    # Arrow Plugin -> SPI (implements)
    draw_arrow(draw, (640, 988), (640, 926), color="#059669", width=4)
    draw_pill_label(draw, (876, 957), "实现 BookFormatPlugin + 继承 TCK（零核心依赖）", f_pill, bg="#FFFFFF", fg="#065F46", border="#6EE7B7")

    # Layer 4 Right: External Services & Quality Verification
    draw_rounded_box(draw, (1260, 988, 1856, 1170), fill="#FFF1F2", outline="#E11D48", radius=20, width=3)
    draw.text((1288, 1006), "外部模型接口 & 黑盒验证（Role 2 / 4）", font=f_sec, fill="#881337")
    draw_centered_text(
        draw,
        (1288, 1044, 1828, 1158),
        [
            "OpenAI 兼容 API（DeepSeek / Qwen / Ollama）",
            "环境变量注入 Key · 测试期由 MockOpenAiServer 验证",
            "book-e2e-tests + GitHub Actions CI + Checkstyle 门禁",
        ],
        [f_card_t, f_small, f_small],
        ["#9F1239", "#475569", "#881337"],
        line_gap=8,
    )

    # Arrow RAG -> External LLM
    draw_arrow(draw, (1760, 740), (1760, 988), color="#E11D48", width=4, dashed=True)
    draw_pill_label(draw, (1760, 864), "HTTPS\n/v1/chat/completions", f_small, bg="#FFFFFF", fg="#9F1239", border="#FDA4AF")

    out = IMG_DIR / "architecture.png"
    img.save(out, format="PNG")
    print(f"Saved {out}")


def build_sequence_diagram():
    W, H = 1920, 1340
    img = Image.new("RGB", (W, H), "#FFFFFF")
    draw = ImageDraw.Draw(img)

    f_title = load_font(36, bold=True)
    f_sub = load_font(20, bold=False)
    f_actor = load_font(20, bold=True)
    f_step = load_font(18, bold=True)
    f_note = load_font(17, bold=False)
    f_badge = load_font(18, bold=True)

    draw.text((56, 30), "BookMind 核心业务与故障隔离时序图", font=f_title, fill="#0F172A")
    draw.text(
        (56, 78),
        "覆盖主路径（外部 PDF 插件加载 → 书库解析 → Lucene 建索引 → OpenAI 兼容伴读 → 原文跳转）与 3 条关键失败隔离分支",
        font=f_sub,
        fill="#475569",
    )

    actors = [
        ("读者 / Frontend", 170, "#EEF2FF", "#4F46E5"),
        ("BookMind Server\n(Host + 书库)", 520, "#EFF6FF", "#2563EB"),
        ("PluginManager\n(隔离加载沙箱)", 880, "#FEF3C7", "#D97706"),
        ("book-plugin-pdf\n(外部 JAR)", 1220, "#ECFDF5", "#059669"),
        ("book-rag\n(Lucene + 编排)", 1540, "#ECFEFF", "#0891B2"),
        ("OpenAI 兼容 API\n(环境变量配置)", 1800, "#FFF1F2", "#E11D48"),
    ]

    top_y = 136
    box_h = 74
    bottom_y = 1290

    for label, cx, fill, border in actors:
        bw = 236 if cx != 1800 else 210
        draw_rounded_box(draw, (cx - bw // 2, top_y, cx + bw // 2, top_y + box_h), fill=fill, outline=border, radius=14, width=3)
        lines = label.split("\n")
        fonts = [f_actor] * len(lines)
        fills = ["#0F172A"] * len(lines)
        draw_centered_text(draw, (cx - bw // 2, top_y, cx + bw // 2, top_y + box_h), lines, fonts, fills, line_gap=4)
        # Lifeline
        for y in range(top_y + box_h + 6, bottom_y, 20):
            draw.line([(cx, y), (cx, min(y + 10, bottom_y))], fill="#CBD5E1", width=3)

    def seq_msg(y, x1, x2, text, color="#1E293B", dashed=False):
        draw_arrow(draw, (x1, y), (x2, y), color=color, width=3, head_len=14, head_w=8, dashed=dashed)
        mid = (x1 + x2) / 2
        bbox = draw.textbbox((0, 0), text, font=f_step)
        tw, th = bbox[2] - bbox[0], bbox[3] - bbox[1]
        tx = max(68, min(W - 68 - tw, mid - tw / 2))
        draw.rectangle([tx - 8, y - th - 12, tx + tw + 8, y - 4], fill="#FFFFFF")
        draw.text((tx - bbox[0], y - th - 10 - bbox[1]), text, font=f_step, fill=color)

    # Phase 1: Startup & Plugin Loading
    draw_rounded_box(draw, (46, 232, 1874, 412), fill="#F8FAFC", outline="#CBD5E1", radius=14, width=2, dashed=True)
    draw.text((66, 244), "阶段一：启动发现外部 PDF 插件与兼容校验", font=f_badge, fill="#334155")
    seq_msg(300, 520, 880, "1. 扫描 ./plugins/*.jar（创建独立 URLClassLoader）", "#2563EB")
    seq_msg(352, 880, 1220, "2. ServiceLoader 发现 PdfBookFormatPlugin 并读 metadata()", "#059669")
    seq_msg(394, 1220, 880, "3. 返回 spiApiVersion=1.0, extensions=[pdf]", "#059669", dashed=True)

    # Failure branch A
    draw_rounded_box(draw, (320, 424, 1620, 484), fill="#FFF7ED", outline="#EA580C", radius=10, width=2)
    draw.text((344, 442), "失败分支 A（版本不兼容 / 已禁用）：SpiVersion 校验不通过或命中 disabled-plugins → 拒绝加载，记录诊断态，宿主正常启动", font=f_note, fill="#9A3412")

    # Phase 2: Library Scan & Parsing + Indexing
    draw_rounded_box(draw, (46, 502, 1874, 782), fill="#F8FAFC", outline="#CBD5E1", radius=14, width=2, dashed=True)
    draw.text((66, 514), "阶段二：书库扫描、隔离解析与 Lucene BM25 建索引", font=f_badge, fill="#334155")
    seq_msg(568, 170, 520, "4. POST /api/library/scan（扫描书库）", "#4F46E5")
    seq_msg(616, 520, 880, "5. 按扩展名路由 .pdf 文件至隔离执行沙箱", "#2563EB")
    seq_msg(664, 880, 1220, "6. parse(filePath)（PDFBox 提取元数据/大纲/页文本）", "#059669")
    seq_msg(710, 1220, 520, "7. 返回 ParsedBook（含 page:N locator）", "#059669", dashed=True)
    seq_msg(758, 520, 1540, "8. RagService.indexBook(bookId, parsedBook) → 切片写入 Lucene", "#0891B2")

    # Failure branch B
    draw_rounded_box(draw, (480, 794, 1640, 854), fill="#FFF1F2", outline="#E11D48", radius=10, width=2)
    draw.text((502, 812), "失败分支 B（损坏/加密 PDF 或插件异常/超时）：沙箱捕获 PluginException / Throwable → 仅标记该书解析失败，其余书籍与检索不受影响", font=f_note, fill="#9F1239")

    # Phase 3: RAG Companion & Jump-to-Source
    draw_rounded_box(draw, (46, 872, 1874, 1188), fill="#F8FAFC", outline="#CBD5E1", radius=14, width=2, dashed=True)
    draw.text((66, 884), "阶段三：跨书 RAG 伴读问答（OpenAI 兼容接口）与原文定位跳转", font=f_badge, fill="#334155")
    seq_msg(936, 170, 520, "9. POST /api/rag/ask { question }", "#4F46E5")
    seq_msg(982, 520, 1540, "10. RagService.ask() → Lucene BM25 跨书召回 Top-K 原文证据", "#0891B2")
    seq_msg(1030, 1540, 1800, "11. POST /v1/chat/completions（注入证据 Prompt）", "#E11D48")
    seq_msg(1078, 1800, 1540, "12. 返回带 [1][2] 引用角标的伴读回答", "#E11D48", dashed=True)
    seq_msg(1126, 1540, 170, "13. 返回 RagAnswer（严格分离 answer 与 citations 列表）", "#0891B2", dashed=True)
    seq_msg(1170, 170, 520, "14. 点击 [1] 引用 → GET /api/books/{id}/chapters/{idx} 原文高亮", "#4F46E5")

    # Failure branch C
    draw_rounded_box(draw, (140, 1200, 1840, 1268), fill="#FEFCE8", outline="#CA8A04", radius=10, width=2)
    draw.text((166, 1218), "失败分支 C（未配置 BOOKMIND_LLM_API_KEY 或上游超时/429/500）：返回规范 RagErrorCode（如 LLM_NOT_CONFIGURED），前端清晰提示配置方式；\n本地 /api/rag/search 跨书检索与阅读器功能持续可用（不伪造离线回答）", font=f_note, fill="#854D0E")

    out = IMG_DIR / "sequence-flow.png"
    img.save(out, format="PNG")
    print(f"Saved {out}")


def convert_logo(src_jpg: Path):
    im = Image.open(src_jpg).convert("RGBA")
    # Save crisp 512x512 for docs/README and 256x256 for frontend favicon/header
    im.resize((512, 512), Image.Resampling.LANCZOS).save(IMG_DIR / "logo.png", format="PNG")
    im.resize((256, 256), Image.Resampling.LANCZOS).save(FRONTEND_PUB / "logo.png", format="PNG")
    print(f"Converted logo -> {IMG_DIR / 'logo.png'} & {FRONTEND_PUB / 'logo.png'}")


if __name__ == "__main__":
    import sys
    if len(sys.argv) > 1:
        src_logo = Path(sys.argv[1])
        if src_logo.exists():
            convert_logo(src_logo)
    build_architecture_diagram()
    build_sequence_diagram()

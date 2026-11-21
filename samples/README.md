# 本地小样例书库说明

本目录用于存放体积小、版权清晰的样例书籍，供本机快速启动、自动化测试与演示「接入 PDF 插件前后对比」使用：

- `books/sample-oss-guide.txt`：原创无版权风险的 3 章节中文测试样书，供内置 TXT 解析器与 Lucene 检索开箱验证。
- `books/sample-rag-handbook.pdf`（计划由 Role 4 在 PR-P2 中以程序生成的合法测试 PDF 补充）：用于验证接入 `book-plugin-pdf` 后，书架与 RAG 伴读同时解锁 PDF 内容。
- `corrupted/`（计划由 Role 4 在 PR-P2/PR-P3 中补充）：存放损坏 PDF 样例（非合法 PDF 头字节），用于验证故障隔离行为。

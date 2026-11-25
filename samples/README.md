# 本地小样例书库说明

本目录用于存放体积小、版权清晰的样例书籍，供本机快速启动、自动化测试与演示「接入 PDF 插件前后对比」使用：

- `books/sample-oss-guide.txt`：原创无版权风险的 3 章节中文测试样书，供内置 TXT 解析器与 Lucene 检索开箱验证。
- `books/sample-rag-handbook.pdf` 是程序生成的两页原创中文手册。安装 PDF 插件后，书架应显示《RAG 检索手册》，作者为 BookMind。“建立索引”和“核对引用”两个书签分别指向第 1、2 页。检索“蓝鹭校验词”可核对引用是否指向 `page:2`。
- `corrupted/invalid-header.pdf` 是故意使用非法文件头的测试文件。将它复制到临时测试书库并扫描，应得到 `CORRUPTED_FILE`。默认书库只包含正常样书。

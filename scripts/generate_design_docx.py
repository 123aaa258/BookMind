#!/usr/bin/env python3
"""Build a publication-grade docs/DESIGN.docx from docs/DESIGN.md and docs/images/*."""

import re
from pathlib import Path

from docx import Document
from docx.enum.table import WD_TABLE_ALIGNMENT, WD_ALIGN_VERTICAL
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.oxml import OxmlElement, parse_xml
from docx.oxml.ns import nsdecls, qn
from docx.shared import Inches, Pt, RGBColor

ROOT = Path(__file__).resolve().parent.parent
DOCS_DIR = ROOT / "docs"
MD_PATH = DOCS_DIR / "DESIGN.md"
DOCX_PATH = DOCS_DIR / "DESIGN.docx"
LOGO_PATH = DOCS_DIR / "images" / "logo.png"

COLOR_NAVY = RGBColor(0x0F, 0x17, 0x2A)
COLOR_INDIGO = RGBColor(0x3B, 0x49, 0xDF)
COLOR_SLATE = RGBColor(0x33, 0x41, 0x55)
COLOR_MUTED = RGBColor(0x64, 0x74, 0x8B)
COLOR_CODE = RGBColor(0x1E, 0x29, 0x3B)

FONT_CN = "PingFang SC"
FONT_MONO = "Menlo"


def set_run_font(run, font_name=FONT_CN, size=10.5, bold=False, italic=False, color=COLOR_NAVY):
    run.font.name = font_name
    run.font.size = Pt(size)
    run.bold = bold
    run.italic = italic
    run.font.color.rgb = color
    rpr = run._r.get_or_add_rPr()
    rfonts = rpr.find(qn("w:rFonts"))
    if rfonts is None:
        rfonts = OxmlElement("w:rFonts")
        rpr.insert(0, rfonts)
    rfonts.set(qn("w:eastAsia"), font_name)
    rfonts.set(qn("w:ascii"), font_name)
    rfonts.set(qn("w:hAnsi"), font_name)


def shade_cell(cell, hex_color):
    shading = parse_xml(f'<w:shd {nsdecls("w")} w:fill="{hex_color}"/>')
    cell._tc.get_or_add_tcPr().append(shading)


def set_cell_margins(cell, top=90, bottom=90, left=130, right=130):
    tc_pr = cell._tc.get_or_add_tcPr()
    tc_mar = OxmlElement("w:tcMar")
    for m, val in [("top", top), ("bottom", bottom), ("left", left), ("right", right)]:
        node = OxmlElement(f"w:{m}")
        node.set(qn("w:w"), str(val))
        node.set(qn("w:type"), "dxa")
        tc_mar.append(node)
    tc_pr.append(tc_mar)


def set_table_borders(table, color="CBD5E1"):
    tbl_pr = table._tbl.tblPr
    borders = parse_xml(
        f'<w:tblBorders {nsdecls("w")}>'
        f'<w:top w:val="single" w:sz="6" w:space="0" w:color="{color}"/>'
        f'<w:bottom w:val="single" w:sz="6" w:space="0" w:color="{color}"/>'
        f'<w:insideH w:val="single" w:sz="4" w:space="0" w:color="E2E8F0"/>'
        f'<w:left w:val="none"/><w:right w:val="none"/><w:insideV w:val="none"/>'
        f"</w:tblBorders>"
    )
    tbl_pr.append(borders)


def add_inline_formatted(paragraph, text, base_size=10.5, base_color=COLOR_NAVY):
    pattern = re.compile(r"(\*\*[^*]+\*\*|`[^`]+`)")
    pos = 0
    for m in pattern.finditer(text):
        if m.start() > pos:
            r = paragraph.add_run(text[pos:m.start()])
            set_run_font(r, FONT_CN, base_size, color=base_color)
        token = m.group(0)
        if token.startswith("**"):
            r = paragraph.add_run(token[2:-2])
            set_run_font(r, FONT_CN, base_size, bold=True, color=base_color)
        elif token.startswith("`"):
            r = paragraph.add_run(token[1:-1])
            set_run_font(r, FONT_MONO, max(base_size - 1.0, 8.5), color=COLOR_INDIGO)
        pos = m.end()
    if pos < len(text):
        r = paragraph.add_run(text[pos:])
        set_run_font(r, FONT_CN, base_size, color=base_color)


def add_callout(doc, text):
    tbl = doc.add_table(rows=1, cols=1)
    tbl.alignment = WD_TABLE_ALIGNMENT.CENTER
    cell = tbl.cell(0, 0)
    shade_cell(cell, "EEF2FF")
    set_cell_margins(cell, top=120, bottom=120, left=180, right=160)
    tc_pr = cell._tc.get_or_add_tcPr()
    borders = parse_xml(
        f'<w:tcBorders {nsdecls("w")}>'
        f'<w:left w:val="single" w:sz="24" w:space="0" w:color="3B49DF"/>'
        f'<w:top w:val="none"/><w:right w:val="none"/><w:bottom w:val="none"/>'
        f"</w:tcBorders>"
    )
    tc_pr.append(borders)
    p = cell.paragraphs[0]
    p.paragraph_format.space_before = Pt(0)
    p.paragraph_format.space_after = Pt(0)
    p.paragraph_format.line_spacing = 1.35
    add_inline_formatted(p, text, base_size=9.8, base_color=COLOR_SLATE)
    spacer = doc.add_paragraph()
    spacer.paragraph_format.space_before = Pt(0)
    spacer.paragraph_format.space_after = Pt(4)


def add_code_block(doc, code_lines, lang=""):
    tbl = doc.add_table(rows=1, cols=1)
    tbl.alignment = WD_TABLE_ALIGNMENT.CENTER
    cell = tbl.cell(0, 0)
    shade_cell(cell, "F1F5F9")
    set_cell_margins(cell, top=110, bottom=110, left=150, right=150)
    tc_pr = cell._tc.get_or_add_tcPr()
    borders = parse_xml(
        f'<w:tcBorders {nsdecls("w")}>'
        f'<w:top w:val="single" w:sz="4" w:space="0" w:color="CBD5E1"/>'
        f'<w:bottom w:val="single" w:sz="4" w:space="0" w:color="CBD5E1"/>'
        f'<w:left w:val="single" w:sz="4" w:space="0" w:color="CBD5E1"/>'
        f'<w:right w:val="single" w:sz="4" w:space="0" w:color="CBD5E1"/>'
        f"</w:tcBorders>"
    )
    tc_pr.append(borders)
    p = cell.paragraphs[0]
    p.paragraph_format.space_before = Pt(0)
    p.paragraph_format.space_after = Pt(0)
    p.paragraph_format.line_spacing = 1.15
    if lang:
        r_lang = p.add_run(f"// {lang}\n")
        set_run_font(r_lang, FONT_MONO, 8.2, italic=True, color=COLOR_MUTED)
    r = p.add_run("\n".join(code_lines))
    set_run_font(r, FONT_MONO, 8.8, color=COLOR_CODE)
    spacer = doc.add_paragraph()
    spacer.paragraph_format.space_before = Pt(0)
    spacer.paragraph_format.space_after = Pt(4)


def add_styled_table(doc, rows):
    if not rows:
        return
    num_cols = len(rows[0])
    tbl = doc.add_table(rows=len(rows), cols=num_cols)
    tbl.alignment = WD_TABLE_ALIGNMENT.CENTER
    set_table_borders(tbl)
    for r_idx, row_vals in enumerate(rows):
        row = tbl.rows[r_idx]
        for c_idx, val in enumerate(row_vals[:num_cols]):
            cell = row.cells[c_idx]
            cell.vertical_alignment = WD_ALIGN_VERTICAL.CENTER
            set_cell_margins(cell, top=80, bottom=80, left=110, right=110)
            p = cell.paragraphs[0]
            p.paragraph_format.space_before = Pt(0)
            p.paragraph_format.space_after = Pt(0)
            p.paragraph_format.line_spacing = 1.25
            if r_idx == 0:
                shade_cell(cell, "1E293B")
                r = p.add_run(val.replace("**", ""))
                set_run_font(r, FONT_CN, 9.5, bold=True, color=RGBColor(0xFF, 0xFF, 0xFF))
            else:
                if r_idx % 2 == 0:
                    shade_cell(cell, "F8FAFC")
                add_inline_formatted(p, val, base_size=9.2, base_color=COLOR_SLATE)
    spacer = doc.add_paragraph()
    spacer.paragraph_format.space_before = Pt(0)
    spacer.paragraph_format.space_after = Pt(6)


def add_page_number_footer(section):
    section.different_first_page_header_footer = True
    header = section.header
    hp = header.paragraphs[0]
    hp.alignment = WD_ALIGN_PARAGRAPH.RIGHT
    hr = hp.add_run("BookMind — 系统设计文档（《开源软件生态与开发技术》课程大作业）")
    set_run_font(hr, FONT_CN, 8.5, color=COLOR_MUTED)

    footer = section.footer
    fp = footer.paragraphs[0]
    fp.alignment = WD_ALIGN_PARAGRAPH.CENTER
    r1 = fp.add_run("第 ")
    set_run_font(r1, FONT_CN, 8.5, color=COLOR_MUTED)
    fld1 = OxmlElement("w:fldSimple")
    fld1.set(qn("w:instr"), "PAGE")
    fp._p.append(fld1)
    r2 = fp.add_run(" 页")
    set_run_font(r2, FONT_CN, 8.5, color=COLOR_MUTED)


def add_cover_page(doc):
    sp = doc.add_paragraph()
    sp.paragraph_format.space_before = Pt(48)

    if LOGO_PATH.exists():
        lp = doc.add_paragraph()
        lp.alignment = WD_ALIGN_PARAGRAPH.CENTER
        lp.add_run().add_picture(str(LOGO_PATH), width=Inches(1.55))

    tp = doc.add_paragraph()
    tp.alignment = WD_ALIGN_PARAGRAPH.CENTER
    tp.paragraph_format.space_before = Pt(18)
    tp.paragraph_format.space_after = Pt(8)
    tr = tp.add_run("BookMind 系统设计文档")
    set_run_font(tr, FONT_CN, 26, bold=True, color=COLOR_NAVY)

    sub = doc.add_paragraph()
    sub.alignment = WD_ALIGN_PARAGRAPH.CENTER
    sub.paragraph_format.space_after = Pt(28)
    sr = sub.add_run("本地私人电子书库与 AI 伴读服务（可扩展开源产品工程）")
    set_run_font(sr, FONT_CN, 13, color=COLOR_INDIGO)

    meta_rows = [
        ["项目属性", "详细信息"],
        ["课程名称", "《开源软件生态与开发技术》大作业（OSS Portfolio Project）"],
        ["技术栈", "Java 21 · Spring Boot 3.3 · Apache Lucene 9.12 · Apache PDFBox 3 · React + Vite"],
        ["开源许可证", "Apache License 2.0"],
        ["核心扩展机制", "公开 SPI（book-spi v1.0）+ URLClassLoader/ServiceLoader + 独立 PDF 插件"],
        ["文档版本", "v0.1（初始架构设计稿，随各小 PR 持续迭代并与最终代码保持一致）"],
        ["4 人分工架构", "Role 1 前端 · Role 2 RAG 引擎 · Role 3 图书管理/核心底座 · Role 4 独立 PDF 插件/CI"],
    ]
    add_styled_table(doc, meta_rows)
    doc.add_page_break()


def parse_markdown_table(lines, start_idx):
    raw = []
    idx = start_idx
    while idx < len(lines) and lines[idx].strip().startswith("|"):
        raw.append(lines[idx].strip())
        idx += 1
    rows = []
    for i, line in enumerate(raw):
        cells = [c.strip() for c in line.strip("|").split("|")]
        if i == 1 and all(re.fullmatch(r":?-+:?", c) for c in cells):
            continue
        rows.append(cells)
    return rows, idx


def build_docx():
    doc = Document()
    for section in doc.sections:
        section.top_margin = Inches(0.85)
        section.bottom_margin = Inches(0.85)
        section.left_margin = Inches(0.9)
        section.right_margin = Inches(0.9)
        add_page_number_footer(section)

    add_cover_page(doc)

    md_lines = MD_PATH.read_text(encoding="utf-8").splitlines()
    i = 0
    while i < len(md_lines):
        line = md_lines[i]
        stripped = line.strip()

        if not stripped or stripped == "---":
            i += 1
            continue

        # Skip top H1 (already on cover page)
        if stripped.startswith("# ") and i < 5:
            i += 1
            continue

        # Code block
        if stripped.startswith("```"):
            lang = stripped[3:].strip()
            i += 1
            code_lines = []
            while i < len(md_lines) and not md_lines[i].strip().startswith("```"):
                code_lines.append(md_lines[i])
                i += 1
            add_code_block(doc, code_lines, lang)
            i += 1
            continue

        # Image ![alt](path)
        img_match = re.fullmatch(r"!\[([^\]]*)\]\(([^)]+)\)", stripped)
        if img_match:
            alt, rel_path = img_match.group(1), img_match.group(2)
            img_file = (DOCS_DIR / rel_path).resolve()
            if img_file.exists():
                p_img = doc.add_paragraph()
                p_img.alignment = WD_ALIGN_PARAGRAPH.CENTER
                p_img.paragraph_format.space_before = Pt(8)
                p_img.paragraph_format.space_after = Pt(4)
                p_img.add_run().add_picture(str(img_file), width=Inches(6.45))

                p_cap = doc.add_paragraph()
                p_cap.alignment = WD_ALIGN_PARAGRAPH.CENTER
                p_cap.paragraph_format.space_after = Pt(10)
                rc = p_cap.add_run(f"图：{alt}")
                set_run_font(rc, FONT_CN, 9.0, italic=True, color=COLOR_MUTED)
            i += 1
            continue

        # Table
        if stripped.startswith("|") and i + 1 < len(md_lines) and md_lines[i + 1].strip().startswith("|"):
            rows, i = parse_markdown_table(md_lines, i)
            add_styled_table(doc, rows)
            continue

        # Blockquote -> Callout
        if stripped.startswith("> "):
            quote_lines = []
            while i < len(md_lines) and md_lines[i].strip().startswith(">"):
                quote_lines.append(md_lines[i].strip().lstrip(">").strip())
                i += 1
            add_callout(doc, " ".join(quote_lines))
            continue

        # Headings
        if stripped.startswith("## "):
            p = doc.add_paragraph()
            p.paragraph_format.space_before = Pt(16)
            p.paragraph_format.space_after = Pt(6)
            p.paragraph_format.keep_with_next = True
            pPr = p._p.get_or_add_pPr()
            pBdr = parse_xml(
                f'<w:pBdr {nsdecls("w")}><w:bottom w:val="single" w:sz="10" w:space="4" w:color="3B49DF"/></w:pBdr>'
            )
            pPr.append(pBdr)
            r = p.add_run(stripped[3:].strip())
            set_run_font(r, FONT_CN, 15.5, bold=True, color=COLOR_INDIGO)
            i += 1
            continue

        if stripped.startswith("### "):
            p = doc.add_paragraph()
            p.paragraph_format.space_before = Pt(11)
            p.paragraph_format.space_after = Pt(4)
            p.paragraph_format.keep_with_next = True
            r = p.add_run(stripped[4:].strip())
            set_run_font(r, FONT_CN, 12.5, bold=True, color=COLOR_NAVY)
            i += 1
            continue

        if stripped.startswith("#### "):
            p = doc.add_paragraph()
            p.paragraph_format.space_before = Pt(8)
            p.paragraph_format.space_after = Pt(3)
            p.paragraph_format.keep_with_next = True
            r = p.add_run(stripped[5:].strip())
            set_run_font(r, FONT_CN, 11.0, bold=True, color=COLOR_SLATE)
            i += 1
            continue

        # Bullet / numbered list
        if re.match(r"^([-*]|\d+\.)\s+", stripped):
            is_ordered = bool(re.match(r"^\d+\.\s+", stripped))
            p = doc.add_paragraph(style="List Number" if is_ordered else "List Bullet")
            p.paragraph_format.space_before = Pt(1)
            p.paragraph_format.space_after = Pt(3)
            p.paragraph_format.line_spacing = 1.35
            content = re.sub(r"^([-*]|\d+\.)\s+", "", stripped)
            add_inline_formatted(p, content, base_size=10.2, base_color=COLOR_NAVY)
            i += 1
            continue

        # Normal paragraph
        p = doc.add_paragraph()
        p.paragraph_format.space_before = Pt(2)
        p.paragraph_format.space_after = Pt(6)
        p.paragraph_format.line_spacing = 1.4
        add_inline_formatted(p, stripped, base_size=10.3, base_color=COLOR_NAVY)
        i += 1

    doc.save(DOCX_PATH)
    print(f"Generated {DOCX_PATH} ({DOCX_PATH.stat().st_size} bytes)")


if __name__ == "__main__":
    build_docx()

# -*- coding: utf-8 -*-
"""
生成《寒假实习准备清单》docx。

数据源：同目录 checklist.json（纯文本内容 + 字号/加粗/表宽等排版参数）
产物：  仓库根目录 寒假实习准备清单.docx

用法（在仓库根或任意目录都行）：
    python tools/build_docs/build_checklist.py

改稿流程：改 checklist.json（或让 AI 改）-> 跑本脚本 -> git 提交 json 和 docx。
不要再用 WPS 直接改 docx，否则 json 与 docx 会再次不一致。
"""
import json
import os
import sys
from datetime import datetime

from docx import Document
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.oxml.ns import qn
from docx.shared import Cm, Pt

# ---- 路径：脚本在 <repo>/tools/build_docs/ 下 ----
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
REPO_ROOT = os.path.dirname(os.path.dirname(SCRIPT_DIR))
JSON_PATH = os.path.join(SCRIPT_DIR, "checklist.json")
OUT_PATH = os.path.join(REPO_ROOT, "寒假实习准备清单.docx")


def build(data, out_path):
    page = data["page"]
    blocks = data["blocks"]

    doc = Document()

    # 页面尺寸与页边距（A4，左右 2.0cm，上下 1.9cm）
    sec = doc.sections[0]
    sec.page_width, sec.page_height = Cm(page["pw"]), Cm(page["ph"])
    sec.left_margin, sec.right_margin = Cm(page["l"]), Cm(page["r"])
    sec.top_margin, sec.bottom_margin = Cm(page["t"]), Cm(page["b"])
    sec.header_distance, sec.footer_distance = Cm(page["head"]), Cm(page["foot"])

    # 正文默认样式：西文 Times New Roman + 中文宋体，10.5pt
    normal = doc.styles["Normal"]
    normal.font.name = "Times New Roman"
    normal.font.size = Pt(10.5)
    normal.element.rPr.rFonts.set(qn("w:eastAsia"), "宋体")

    def set_run(run, info):
        """按 JSON 里的 b(加粗) / z(字号) / f(西文字体) / ea(中文字体) 设置一段文字。"""
        if not info:
            return
        run.bold = info["b"]
        if info["z"]:
            run.font.size = Pt(info["z"])
        if info["f"]:
            run.font.name = info["f"]
        run._element.get_or_add_rPr().get_or_add_rFonts().set(
            qn("w:eastAsia"), info.get("ea") or "宋体"
        )

    for b in blocks:
        if b["k"] == "p":
            # 普通段落：套样式名（Normal / List Number），居中则居中，再写文字
            p = doc.add_paragraph(style=b["s"])
            if b["align"] and "CENTER" in b["align"]:
                p.alignment = WD_ALIGN_PARAGRAPH.CENTER
            set_run(p.add_run(b["t"]), b["r"])
        else:
            # 表格：Table Grid 边框样式，按 JSON 里的列宽逐列设置
            rows, widths = b["rows"], b["widths"]
            tbl = doc.add_table(rows=len(rows), cols=len(rows[0]))
            tbl.style = "Table Grid"
            for j, w in enumerate(widths):
                if w:
                    for row in tbl.rows:
                        row.cells[j].width = Cm(w)
            for i, row in enumerate(rows):
                for j, txt in enumerate(row):
                    cell = tbl.cell(i, j)
                    cell.text = ""
                    # 第一行按表头格式（加粗），其余行按正文格式
                    set_run(cell.paragraphs[0].add_run(txt),
                            b["head"] if i == 0 else b["cell"])

    doc.save(out_path)


def main():
    if not os.path.exists(JSON_PATH):
        sys.exit("找不到数据源：%s" % JSON_PATH)

    # 防呆：docx 比 json 还新，说明有人直接用 Word/WPS 改过 docx，改稿会丢
    if os.path.exists(OUT_PATH) and os.path.getmtime(OUT_PATH) > os.path.getmtime(JSON_PATH):
        print("警告：%s 的修改时间比 checklist.json 新。" % os.path.basename(OUT_PATH))
        print("      说明 docx 可能被 Word/WPS 直接改过，本次生成会覆盖那些改动。")
        print("      如确认要以 json 为准，直接重跑即可；否则先把手改内容搬进 json。")

    data = json.load(open(JSON_PATH, encoding="utf-8"))
    build(data, OUT_PATH)
    n_p = sum(1 for b in data["blocks"] if b["k"] == "p")
    n_t = sum(1 for b in data["blocks"] if b["k"] == "t")
    print("生成完成：%s" % OUT_PATH)
    print("段落 %d 个、表格 %d 张、体积 %d 字节、时间 %s"
          % (n_p, n_t, os.path.getsize(OUT_PATH),
             datetime.now().strftime("%Y-%m-%d %H:%M:%S")))


if __name__ == "__main__":
    main()

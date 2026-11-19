# 文档生成（tools/build_docs）

这里只放《寒假实习准备清单》的生成链路，用来替代之前散落在对话目录里的 `build_docs.py`。

## 文件

| 文件 | 作用 |
|---|---|
| `checklist.json` | **唯一内容源**：段落、表格、字号、加粗、表宽全在这里 |
| `build_checklist.py` | 渲染脚本：读 json → 生成 docx |
| `../../寒假实习准备清单.docx` | 产物，提交进仓库 |

## 改稿流程（重要）

```
改 checklist.json  →  跑脚本  →  git 提交 json + docx
```

跑脚本：

```powershell
python tools/build_docs/build_checklist.py
```

**不要再用 WPS / Word 直接改 docx。** 一旦手改 docx，json 和 docx 就会不一致，下次跑脚本会把手改的内容覆盖掉。脚本开头有防呆：如果发现 docx 比 json 新，会打印警告。

## 排版约定（脚本按 JSON 里的字段渲染）

| 用途 | 样式名 | 字号 | 加粗 |
|---|---|---|---|
| 文档标题 | Normal | 22 | 是 |
| 副标题 | Normal | 11 | 否 |
| 三级说明（基线信息） | Normal | 9.5 | 否 |
| 使用说明 | Normal | 9 | 否 |
| 大节标题（一、二、……） | Normal | 14 | 是 |
| 小节标题 | Normal | 11.5 | 是 |
| 正文 | Normal | 10.5 | 否 |
| 清单项（☐ 开头） | Normal | 10 | 否 |
| 问答列表 | List Number | 10 | 否 |
| 表格 | Table Grid | 表头 9 加粗 / 正文 9 | — |

页面：A4，左右边距 2.0cm，上下边距 1.9cm；正文西文 Times New Roman、中文宋体。

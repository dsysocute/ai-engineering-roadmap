from datetime import datetime
import os
import re

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
PROGRESS_FILE = os.path.join(BASE_DIR, "PROGRESS.md")

FIELDS = [
    ("完成的功能", "今天做完了什么事"),
    ("新学会的概念", "学到了什么新知识点"),
    ("新增或修改的文件", "改了哪些代码文件"),
    ("测试命令", "跑了什么命令验证"),
    ("测试结果", "运行结果怎么样"),
    ("Git commit", "提交信息"),
    ("遇到的问题", "踩了什么坑"),
    ("如何定位", "怎么找到问题的"),
    ("最终如何修复", "怎么解决的"),
    ("哪些问题仍未解决", "还有什么遗留问题"),
    ("明天必须交付的功能", "明天要做什么"),
    ("验收标准", "怎么算完成"),
    ("需要复习的概念", "哪些要回头看"),
]

def print_header():
    print("=" * 40)
    print("  每日进度记录工具 v2.0")
    print("=" * 40)
    print()

def gen_template():
    print("📋 空白模板已生成，复制下面内容填充后，用「批量模式」粘贴：")
    print()
    print("-" * 40)
    print("### 完成的功能")
    print("（在这里写）")
    for name, _ in FIELDS[1:]:
        print(f"### {name}")
        print("（在这里写）")
    print("-" * 40)
    print()

def parse_raw(raw_text):
    fields = {}
    pattern = r"###\s*(.+?)\n([\s\S]*?)(?=###|$)"
    matches = re.findall(pattern, raw_text)
    for name, value in matches:
        fields[name.strip()] = value.strip()
    return fields

def collect_batch():
    print("📥 批量模式：把所有内容一次性粘贴进来")
    print("   格式：### 字段名 换行 内容")
    print("   结束：粘贴完直接敲一次回车（空行结束）")
    print()
    lines = []
    while True:
        line = input()
        if line.strip() == "":
            break
        lines.append(line)
    return parse_raw("\n".join(lines))

def collect_interactive():
    print("⌨️ 逐项模式：一个一个填")
    print()
    fields = {}
    for name, hint in FIELDS:
        val = input(f"{name}（{hint}）：").strip()
        fields[name] = val
    return fields

def build_content(fields, date_str):
    lines = [
        "",
        "---",
        "## Daily Progress",
        f"### Today",
        f"- 日期：{date_str}",
        f"- 完成的功能：{fields.get('完成的功能', '')}",
        f"- 新学会的概念：{fields.get('新学会的概念', '')}",
        f"- 新增或修改的文件：{fields.get('新增或修改的文件', '')}",
        f"- 测试命令：{fields.get('测试命令', '')}",
        f"- 测试结果：{fields.get('测试结果', '')}",
        f"- Git commit：{fields.get('Git commit', '')}",
        "",
        f"### Problems",
        f"- 遇到的问题：{fields.get('遇到的问题', '')}",
        f"- 如何定位：{fields.get('如何定位', '')}",
        f"- 最终如何修复：{fields.get('最终如何修复', '')}",
        f"- 哪些问题仍未解决：{fields.get('哪些问题仍未解决', '')}",
        "",
        f"### Tomorrow",
        f"- 明天必须交付的功能：{fields.get('明天必须交付的功能', '')}",
        f"- 验收标准：{fields.get('验收标准', '')}",
        f"- 需要复习的概念：{fields.get('需要复习的概念', '')}",
        "",
    ]
    return "\n".join(lines)

def preview(content):
    print()
    print("👀 预览即将写入的内容：")
    print("-" * 40)
    print(content)
    print("-" * 40)
    print()

def main():
    print_header()
    print("请选择模式：")
    print("  1. 批量粘贴（推荐，一次性贴一大段）")
    print("  2. 逐项输入（一个一个填）")
    print("  3. 生成空白模板")
    print()
    choice = input("输入数字（默认1）：").strip() or "1"

    if choice == "3":
        gen_template()
        return

    if choice == "2":
        fields = collect_interactive()
    else:
        fields = collect_batch()

    # 日期
    date_str = fields.get("日期", "").strip()
    if not date_str:
        date_str = datetime.now().strftime("%Y-%m-%d")

    # 校验必填
    missing = [name for name, _ in FIELDS if not fields.get(name)]
    if missing:
        print(f"⚠️  以下字段为空：{', '.join(missing)}")
        confirm = input("仍然写入？(y/n)：").strip().lower()
        if confirm != "y":
            print("已取消")
            return

    content = build_content(fields, date_str)
    preview(content)

    confirm = input("确认写入？(y/n)：").strip().lower()
    if confirm == "y":
        with open(PROGRESS_FILE, "a", encoding="utf-8") as f:
            f.write(content)
        print(f"\n✅ 写入成功！已保存到 {PROGRESS_FILE}")
    else:
        print("已取消")

if __name__ == "__main__":
    main()
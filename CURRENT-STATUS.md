# 当前进度交接（开新对话先读这个）

> 更新时间：2026-11-19。本文件是当前阶段的进度落点；详细规划见 `ROADMAP.md`，事实口径见用户技能 `internship-prep-algoarena`。

## 一、现在处在哪一步

- **阶段：M0 已完成，准备进入 M1。**
- M0（11/17–11/19）环境搭建与上云已收尾：
  - `algoarena-ai-service/.venv` 已建，Python 3.13；
  - 已装 fastapi、uvicorn[standard]、pytest、httpx，导入验证 `ok`；
  - `requirements.txt` 已生成；
  - `.gitignore` 已补全（.venv、缓存、数据文件、Word 锁文件、acl-report）；
  - 10-30 后产出分 4 次提交并 push 到 GitHub（master，与 origin 同步）；
  - `daily progress/PROGRESS.md` 已补 10-31→11-17 断更说明（期中考试 + 调整实习方向）与新排期。
- 仓库 `git status` 现已干净；`寒假实习准备清单.docx` 的 ✅ 已提交（`749bf88`）。

### 11/19 晚：M1 已提前起步

- 目录骨架已建（`app/api`、`app/services`、`app/models`、`app/core`、`tests`，各含空 `__init__.py`）。
- `GET /api/health` 已手写并跑通（`app/main.py`），已在 8000 端口浏览器验证，`/docs` 已看。
- 清单文档改由脚本生成：`tools/build_docs/checklist.json`（内容源）+ `build_checklist.py`（渲染）→ 根目录 docx；**以后改清单不再手改 docx**。旧 `build_docs.py` 已改为只产出 `寒假实习准备清单-旧版存档.docx`，不再覆盖。
- 提交：`b5562a4`（生成链路）、`38da75f`（docx 重生成），均已 push。
- **每日未解决困惑台账：`daily progress/OPEN-QUESTIONS.md`** —— 开新对话先读它，逐条用代码验证。

## 二、下一步：M1 从哪开始（11/20–11/30，约 35h）

M1 九项里已完成 2 项（建目录、`GET /api/health`），**当前卡在 `POST /api/analyze`**：

- 已完成/进行中的分层约定：`models` 放入参模型、`services` 放纯业务规则（不 import fastapi）、`main.py` 只做"路由登记 + 搬运"。
- 待手搓：`app/models/analyze.py`（五个字段：problem_id / source_code / language / verdict / error_message，后者 `Field(default="", max_length=500)`）→ `app/services/diagnose.py`（`build_conclusion`：verdict 归一化 + 未知兜底、源码截断 2000、报错截断 500，均带 truncated 标记）→ `POST /api/analyze`（空白源码主动 `raise HTTPException(422)`）。
- 练习用的 `grade` 三个文件（models/services/main 内路由）在 analyze 全绿后删除，单独提交。

**手搓/命令边界（红线）**：
- 命令项：建目录、venv、装依赖、Git、跑测试、环境排障。
- 手搓项：接口代码、规则逻辑、Pydantic 校验、测试断言、SQL 语句——必须本人写，AI 只答疑/review/查文档。
- 每步先标明是命令还是手搓；做完能脱稿讲清链路才算验收。

## 三、已固化的关键事实（避免新对话重查）

### 老平台 sort_platform（真实技术栈，核对过源码）

- JDK 17；后端纯 Java，基于 JDK 内置 `com.sun.net.httpserver.HttpServer`，**无 Spring、无 Maven**；前端原生 HTML/JS/CSS；数据 TSV/CSV 文件存储，**无数据库**；端口 8080。
- HTTP 层代码主要为 AI 主导，用户配合复现、验证、梳理链路。简历/面试口径：**参与、配合、借助 AI 辅助**，不写成独立手写 HTTP 服务。
- 用户角色：提需求、测试、找漏洞、验收；代码实现由 AI 辅助完成。面试统一答："需求、验收标准、缺陷定位是我；代码实现是我指挥 AI 完成的。"

### 新服务 algoarena-ai-service（规划，未落地部分勿写成已完成）

- 技术栈：Python 3.13 + FastAPI + Uvicorn + pytest + httpx。
- 架构：只读消费老平台 TSV 数据，Java 判题核心零改动。
- 规划接口：`/api/health`、`/api/analyze`、`/api/problem/{id}`、`/api/submission/{id}`。
- 未落地：DeepSeek 接入、三张表（ai_explanation / llm_call_log / cache_record）、Agent 三工具（get_problem_detail / get_submission_history / read_source_code）、RAG、LangGraph。

## 四、环境已验证事实

- Windows PowerShell 5.1；Python 用 `python`，pip 用 `python -m pip`。
- 镜像源：默认 PyPI 卡死；清华源报找不到 fastapi；**阿里云可用** `https://mirrors.aliyun.com/pypi/simple/`。
- venv 激活：先 `Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass`，再 `.venv\Scripts\Activate.ps1`；行首出现 `(.venv)`。
- 新开终端必须重新激活 venv，否则 `No module named fastapi`。
- Git 远程 `git@github.com:dsysocute/ai-engineering-roadmap.git`，master；SSH 走 `ssh.github.com:443`；代理 10808 未启动时注释 config 里 ProxyCommand 直连。

## 五、材料与占位

- 生成脚本：`C:\Users\Administrator\Doubao\chats\2026-11-17\new-chat\build_docs.py`；改稿统一改脚本后重跑，不直接手改 docx。
- 产物：同目录 `寒假实习准备清单.docx`、`简历初稿.docx`、`AlgoArena项目技术方案.docx`。
- 简历待补 XX：电话、邮箱、GitHub、项目演示链接、项目起止时间；相关课程以成绩单为准。
- 简历红线：不主动写挂科/补考/重修；技术栈与实际掌握一致；未落地的大模型/Agent/RAG 只标学习中或规划中。

## 六、待确认 / 未决

- 寒假准确起止日期（现按 01/11 起估算）；
- 数分重修考试时间；
- 每周可到岗天数、可承诺最短实习时长；
- DeepSeek 预算上限；
- 新服务目录名是否最终定为 `algoarena-ai-service`（用户曾想自定，英文小写连字符、不用空格中文）；
- `build_docs.py` 与仓库 docx 已漂移（章节从"阶段"重写为"里程碑"），M4 前统一改脚本后重跑三份（清单已改由新链路生成，简历与技术方案仍走旧脚本）。

## 七、开新对话怎么接

1. 先读本文件 + `ROADMAP.md` 第四节 M1 清单；
2. 需要项目事实口径时查用户技能 `internship-prep-algoarena` 的 references；
3. 从 M1 第一步建目录骨架继续，先讲清每个目录职责再动手。

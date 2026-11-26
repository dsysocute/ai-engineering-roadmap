# 当前进度交接（开新对话先读这个）

> 更新时间：2026-11-26。本文件是当前阶段的进度落点；详细规划见 `ROADMAP.md`，搁置项目事实口径见用户技能 `internship-prep-algoarena`。

## 一、路线已调整（重要）

2026-11-26 修订为 **AI 全栈开发**全周期路线（主轨就业，备选考研兜底），从当前学期排到毕业：

1. 2026.11 – 2027.06（大二上末 – 大二下）：工程底座 + 电商 Java 后端 + 前端联调 + AI 全栈 Demo，2027 春末投第一段实习；
2. 2027 暑假：第一段实习破零；
3. 大三上：OS/计网内功 + 后端深化（Java SSM/Spring Boot/Redis，Python FastAPI），10 月起投第二段实习；
4. 大三下：1–2 个高质量项目收口，3–5 月再投递；
5. 2028 暑假：第二段实习；大四上 8–11 月秋招，12 月底考研初试作兜底。

技术口径：后端 Python 主线 + Java 二线打底（按 SE → JDBC → Spring Boot 阶梯补）；前端补到能做页面和联调；AI 应用接 LLM / RAG / 工具调用。**不单独设 Agent 开发岗位方向**，Agent 只是全栈里的一项技术能力。

**项目取舍（2026-11-26 拍板）**：

- 电商 Java 项目继续做：既是课堂作业，也属学长推荐项目范畴，作为主线练手。
- AI 全栈 Demo：Python 后端 + 前端页面 + AI 链路，作为 AI 能力证据。
- **AlgoArena 排序平台搁置备查**：代码保留、不推进、不占主线时间；未跑通、未理解的部分不进简历。

旧 ROADMAP 里的 12/14 首批投递、泛联新安 / 新星 / 万兴等寒假目标岗位，**全部归档，不再按此投递**。

## 二、现在处在哪一步

**阶段：阶段 0 · 底座起步（进行中）。** AlgoArena 的 M1 工作随项目搁置暂停，不再作为主线卡点。

### 阶段 0 当前落点

- [ ] Python 基础回补：函数、异常、模块、虚拟环境；FastAPI 跑通一个 health 接口。
- [ ] Java SE 起步：类、对象、OOP、集合、方法封装；配合电商作业。
- [ ] SQL：两张表 + CRUD + join + 执行计划；聚合/HAVING、窗口函数、数仓分层各一例。
- [ ] 电商项目梳理：业务实体、表结构、核心流程（下单 / 库存 / 校验），先出需求与表设计。
- [ ] 数据清洗脚本启动：缺失 / 异常 / 聚合 / 边界测试。

### AlgoArena 搁置时的代码现状（备查，不推进）

- `algoarena-ai-service/`：`app/main.py` 存在；`app/api/analyze.py` 空文件；`app/api/health.py` 仍是旧 `app=FastAPI(...)` + health 路由；`app/api/__init__.py`、`app/models/__init__.py` 被删。
- `app/models/analyze.py`（AnalyzeRequest 五字段，`error_message` 默认空串、上限 500）、`app/services/diagnose.py`（截断、verdict 归一化、五类 + UNKNOWN 模板）完好。
- `tests/` 为空；`.env` / `.env.example` 未做；SQL 两表与加权查询未做；Java 四文件阅读笔记未写。
- 这些待办随项目搁置暂停；重启时再按备查口径接回，不占主线时间。

### Git 状态（截至本次核对）

- `app/main.py` 已修改未提交；`app/services/diagnose.py` 未跟踪。
- 业务代码必须本人手写，AI 只答疑 / review / 查文档；跑通验收后再分批提交。

## 三、下一步：阶段 0 从哪开始

1. **Python 底座**：在 `algoarena-ai-service` 之外另起独立练手目录或直接用电商项目后端；FastAPI 跑通一个 health 接口并自测，不把搁置服务的 M1 路由当主线任务。
2. **Java SE + 电商梳理**：跟学长 JavaSE 课程补类 / 对象 / 集合；把电商作业的实体、表结构、下单 / 库存 / 校验流程先写成设计草稿。
3. **SQL**：两张表 + CRUD + join + 执行计划，聚合 / HAVING / 窗口函数 / 数仓分层各写一例。
4. **数据清洗脚本**：缺失值、异常值、聚合、边界测试各一例。

## 四、已固化的关键事实

### 老平台 sort_platform（真实技术栈，核对过源码）

- JDK 17；后端纯 Java，基于 JDK 内置 `com.sun.net.httpserver.HttpServer`，**无 Spring、无 Maven**；前端原生 HTML/JS/CSS；数据 TSV/CSV 文件存储，**无数据库**；端口 8080。
- HTTP 层代码主要为 AI 主导，用户配合复现、验证、梳理链路。简历/面试口径：**参与、配合、借助 AI 辅助**，不写成独立手写 HTTP 服务。
- 用户角色：提需求、测试、找漏洞、验收；代码实现由 AI 辅助完成。面试统一答：「需求、验收标准、缺陷定位是我；代码实现是我指挥 AI 完成的。」

### 新服务 algoarena-ai-service（搁置，未落地部分勿写成已完成）

- 技术栈：Python 3.13 + FastAPI + Uvicorn + pytest + httpx。
- 规划接口：`/api/health`、`/api/analyze`、`/api/problem/{id}`、`/api/submission/{id}`。
- 未落地：模型接入、三张表（ai_explanation / llm_call_log / cache_record）、工具调用、RAG、LangGraph。

## 五、环境已验证事实

- Windows PowerShell；Python 用 `python`，pip 用 `python -m pip`。
- 镜像源：默认 PyPI 卡死；清华源报找不到 fastapi；**阿里云可用** `https://mirrors.aliyun.com/pypi/simple/`。
- venv 激活：先 `Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass`，再 `.venv\Scripts\Activate.ps1`；行首出现 `(.venv)`。
- 新开终端必须重新激活 venv，否则 `No module named fastapi`。
- Git 远程 `git@github.com:dsysocute/ai-engineering-roadmap.git`，master；SSH 走 `ssh.github.com:443`；代理 10808 未启动时注释 config 里 ProxyCommand 直连。Git 可执行路径：`C:\Program Files\Git\cmd\git.exe`（PowerShell 里 `git` 不在 PATH，用 `& $git ...`）。

## 六、材料与占位

- 清单 docx 由 `tools/build_docs/checklist.json` + `build_checklist.py` 生成；改清单先改 json，再跑脚本，不手改 docx。
- 旧口径的 `简历初稿.docx`、`AlgoArena项目技术方案.docx` 本地保留，已在 `.gitignore` 中排除，不进新仓库；后续简历按 AI 全栈主线重新生成。旧生成脚本在仓库外 `C:\Users\Administrator\Doubao\chats\2026-11-17\new-chat\build_docs.py`，改 docx 必须改脚本后重跑，不手改。
- 详细执行规划（技术栈程度、项目拆解、面试 / 笔试题、过关清单）见 `PLAN-2027暑假实习.md`。
- 简历待补 XX：电话、邮箱、GitHub、项目演示链接、项目起止时间；相关课程以成绩单为准。
- 简历红线：不主动写挂科 / 补考 / 重修；技术栈与实际掌握一致；未落地的模型 / RAG / 框架只标学习中或规划中；搁置项目未跑通部分不进简历。

## 七、待确认 / 未决

- 数分重修考试时间与通过情况；
- 2027 暑假可到岗天数、可承诺最短实习时长；
- 模型 API 预算上限；
- AI 全栈 Demo 的具体数据集与场景（商品问答 / 评论分析 / 知识库问答 / 个人工具助手）；
- 前端框架最终选型（Vue3 起步 / 直接 React，按目标岗位再定）；
- 是否报名蓝桥杯 / CSP（核对当年官方通知与校内目录后再定）；
- 考研倾向程度与目标方向（决定大三下就业 / 升学投入比例）；
- 目标城市与远程接受度。

## 八、开新对话怎么接

1. 先读本文件 + `ROADMAP.md`；
2. 需要搁置项目事实口径时查用户技能 `internship-prep-algoarena` 的 references；
3. 从阶段 0 底座起步接：Python 接口自测、Java SE + 电商梳理、SQL 练习；先讲清每个目录 / 模块职责再动手。

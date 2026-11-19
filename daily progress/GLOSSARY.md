# 名词表 · 我遇到过的所有专业词

> 用途：对话里反复出现、我总记不住的词，一个都不漏。每个词三件事——**一句话是什么** / **在我们项目里对应哪块** / **我栽过的坑**。
> 维护方式：遇到新词，往对应分组末尾追加一行，**不要重写全文**。分组不够就新开一组。

---

## 一、网络与 HTTP（服务是怎么被访问到的）

| 名词 | 一句话是什么 | 项目里对应哪块 | 我栽过的坑 |
|---|---|---|---|
| HTTP | 客户端和服务器之间"说话"的协议 | 浏览器 ↔ 127.0.0.1:8000 | — |
| 请求 request | 客户端发过去的那一包东西 | 浏览器访问 `/api/health` 就是一次 GET 请求 | — |
| 响应 response | 服务端回给客户端的那一包东西 | `{"status":"ok",...}` 就是响应体 | — |
| GET | 只取数据，参数放在地址里 | `GET /api/health` | 一开始以为 GET/POST 的区别是"要不要写函数"，其实区别在"数据放哪" |
| POST | 提交数据，数据放在**请求体**里 | `POST /api/analyze` | — |
| 端点 / 接口 / API / 路径 | 一个能被访问的地址 | `/api/health`、`/api/analyze`、`/docs` | 曾以为是函数名决定地址，实际是装饰器里那串字符串决定 |
| 路由 router | "地址 → 函数"的登记表 | `app/main.py` 里的 `@app.get` / `@app.post` | 手敲时把 `/api/health` 写成 `/app/health`，结果 404 |
| 请求体 body | POST 时随请求一起带的数据 | `AnalyzeRequest` 那五个字段 | — |
| JSON | 跨语言的文本数据格式，`{}` 包起来，键必须是双引号字符串 | 请求体是 JSON，响应也是 JSON | 以为 dict 和 JSON 是同一个东西——**不是**：dict 是 Python 里的对象，JSON 是文本 |
| 状态码 status code | 服务器回复里的"结果编号" | 200 成功 / 404 没这个地址 / 422 数据校验不过 / 500 代码崩了 | — |
| 422 | 请求格式对，但内容不合规 | 缺字段、`error_message` 超 500 字都会出 422 | 曾不知道 422 是谁产生的，其实是 Pydantic 校验失败被 FastAPI 翻译成 422 |
| 端口 port | 一台机器上某个服务的门牌号 | `8000` | 曾不理解"没有 uvicorn 为什么就访问不到"——没人监听端口，地址就是空的 |
| 127.0.0.1 / localhost | 本机自己 | `http://127.0.0.1:8000` | — |
| 查询参数 query | 地址里 `?` 后面的部分 | 本项目暂未使用 | — |

## 二、FastAPI 和它的搭档（工具层）

| 名词 | 一句话是什么 | 项目里对应哪块 | 我栽过的坑 |
|---|---|---|---|
| FastAPI | 写 HTTP 服务的 Python 框架 | `app/main.py` 里的 `app = FastAPI()` | — |
| 装饰器 decorator | 写在函数上方、`@` 开头的"贴标签"，用来登记这个函数归谁管 | `@app.get("/api/health")` | 一开始以为要"调用"装饰器，其实它只是登记 |
| uvicorn | 真正监听端口、把收到的请求转交 FastAPI 的程序 | `python -m uvicorn app.main:app --reload` | 以为写完代码就能访问，其实必须有进程在监听 |
| `--reload` | 改代码后自动重启服务 | 启动命令末尾 | — |
| `/docs` | FastAPI 自带的接口说明书，还能直接在网页里试发请求 | `http://127.0.0.1:8000/docs` | — |
| Pydantic | 校验数据格式的库 | `app/models/analyze.py` | — |
| `BaseModel` | 声明"一条数据长什么样"的父类 | `class AnalyzeRequest(BaseModel)` | — |
| `Field` | 给字段加约束（默认值、长度上限、数值范围） | `Field(default="", max_length=500)` | — |
| `ValidationError` | Pydantic 校验失败时抛的错 | 命令行里直接看到它 | 同一个校验，在命令行叫 `ValidationError`，走 HTTP 就变成 `422` |
| `HTTPException` | 你在代码里**主动**抛出的带状态码的错误 | 路由里源码空白时 `raise HTTPException(422, ...)` | — |
| `.env` | 放密钥/配置的文件，**不进 git** | M1 待做 | — |
| 环境变量 | 程序运行时从外部读的配置 | 以后 `API_KEY` 从 `.env` 读 | — |
| pytest | 自动跑测试用例的工具 | M1 待做（10 条） | — |
| httpx | 测试里用来"假装发请求"的客户端 | pytest 里配合用 | — |
| venv | 项目专属的 Python 环境，装依赖装在这里面 | `algoarena-ai-service\.venv` | 点 VS Code ▶ 用了全局 Python312，报 `No module named 'pydantic'` |
| 依赖 dependency | 项目要用到的第三方库 | `fastapi`、`uvicorn`、`pytest`、`httpx` | — |
| 虚拟环境激活 | 让终端用 venv 里的 Python | `.\.venv\Scripts\Activate.ps1`，成功后行首出现 `(.venv)` | 没激活就等于用全局环境 |

## 三、Python 语言本身（语法层）

| 名词 | 一句话是什么 | 项目里对应哪块 | 我栽过的坑 |
|---|---|---|---|
| 模块 module | 一个 `.py` 文件 | `app/models/analyze.py` 就是一个模块 | — |
| import | 把别的模块里的东西拿过来用 | `from pydantic import BaseModel, Field` | — |
| 类 class | 一类事物的模板 | `AnalyzeRequest` | 曾把"类"和"对象"混了——类是模板，对象是按模板造出来的那一个 |
| 实例 / 对象 | 按类造出来的具体东西 | `A(problem_id='p1', ...)` 造出的那个 | — |
| `self` | 指"当前这个对象自己" | 类方法第一个参数（本项目模型里没用到） | — |
| 类型注解 | `problem_id: str` 里的 `: str`，是标注不是强制 | 模型五字段 | 以为写了 `: str` 就真的会强制，其实真正校验是 Pydantic 干的 |
| 函数 def | 一段可以被调用的代码 | `build_conclusion()` | — |
| 参数 / 返回值 | 传进去的 / `return` 出来的 | service 层入参出参 | — |
| `None` | "空" | `.get()` 查不到时的默认 | — |
| 字典 dict | `{键: 值}` 的容器 | `VERDICT_MAP`、`TEMPLATES` | — |
| `.get(键, 默认值)` | 查字典，查不到就给默认值，不报错 | `VERDICT_MAP.get(x, "UNKNOWN")` | 这是"兜底"的实现方式 |
| `.keys()` / `.values()` | 字典的所有键 / 所有值 | 验收时打印键名 | — |
| `.strip()` | 去掉字符串两端空白 | 判断源码是不是空的 | `"   "` 不是空字符串，`len()` 是 3，必须 `strip()` 后判断 |
| `.upper()` | 全转大写 | 归一化 verdict 前 | 表里的键也要大写，否则永远查不到 |
| 切片 `s[:n]` | 取前 n 个字符 | 源码截断到 2000 字 | — |
| `len()` | 长度 | 判断超长 | — |
| `raise` | 主动抛错误 | `raise HTTPException(...)` | — |
| `try / except` | 抓错误、别让程序崩 | 以后坏 JSON 用 | — |

## 四、架构与工程词（这些词最容易被说成"高大上"）

| 名词 | 一句话是什么 | 项目里对应哪块 | 我栽过的坑 |
|---|---|---|---|
| 分层 | 把"数据长什么样／怎么算／怎么被访问"拆到三个文件夹 | `app/models`、`app/services`、`app/api` | — |
| model 层 | 只负责"数据长什么样、合不合规" | `app/models/analyze.py` | — |
| service 层 | 只负责"业务怎么算"，不碰 HTTP | `app/services/diagnose.py`（在写） | — |
| api / router 层 | 只负责"接请求、调 service、返响应" | `app/main.py` 里的路由函数 | — |
| 归一化 normalization | 把各种写法统一成一个标准值 | `VERDICT_MAP` | — |
| 兜底 fallback | 遇到不认识的值也不崩，给个默认 | `.get(键, "UNKNOWN")` | — |
| 截断 truncate | 太长就砍掉，并标记"我砍过" | `MAX_SOURCE = 2000` + `truncated` 标记 | 只砍不标记 = 下游以为拿到的是全文 |
| 常量 constant | 全大写、写在文件顶部、不中途改的值 | `MAX_SOURCE`、`MAX_ERROR` | — |
| 契约 / 约定 | 双方约好的输入输出格式 | `AnalyzeRequest` 就是给上游的契约 | — |
| 验收 acceptance | 自己定几条命令/操作，跑过了才算完成 | 每条命令验证一件事 | 曾经"写完了"就当完成，其实没验证 |
| 里程碑 milestone | 阶段目标，M0 / M1 / M2 … | `ROADMAP.md` 里的 M0–M5 | — |

## 五、Agent / LLM（简历要讲的那条线）

| 名词 | 一句话是什么 | 项目里对应哪块 | 我栽过的坑 |
|---|---|---|---|
| LLM | 大语言模型，一个"根据上文续写下文"的模型 | 以后 `/api/analyze` 里可选接 DeepSeek | — |
| Prompt | 给模型的指令文本 | 以后构造的提示词 | — |
| system prompt | 给模型设定的"身份+规矩"，比用户消息优先级高 | 以后 prompt 的第一段 | — |
| Token | 模型计费和长度计算的最小单位（大致=字/词片段） | 预算控制 | — |
| 上下文窗口 | 模型一次最多能看多少 token | 源码截断 2000 字的现实理由之一 | — |
| 幻觉 hallucination | 模型编出看起来合理但不真实的内容 | 所以结论要"规则兜底"，不能全信模型 | — |
| Agent | 会自己决定下一步、会调工具、分多步完成任务的程序 | 简历主线叙事 | 以为"调一次模型"就叫 Agent，其实关键在"多步 + 调工具 + 自己判断" |
| 工作流 workflow | 把任务拆成固定步骤依次执行 | 本项目"模型 → 规则 → 路由"三层就是最小工作流 | — |
| 工具调用 tool calling / function calling | 让模型输出"我要调用哪个函数、参数是什么"，由程序去执行 | 以后可加 | — |
| 编排 orchestration | 决定谁先干、谁后干、失败了怎么办 | 多 Agent 场景的词 | — |
| RAG | 先检索资料，再把资料塞进 prompt 让模型回答 | 本项目暂不涉及 | — |

## 六、AlgoArena 业务词（自己的项目）

| 名词 | 一句话是什么 | 项目里对应哪块 | 我栽过的坑 |
|---|---|---|---|
| verdict | 判题结论的原始值 | `AnalyzeRequest.verdict` | 曾不知道这词什么意思 |
| AC | Accepted，全部测试点通过 | `VERDICT_MAP` 的目标值 | — |
| WA | Wrong Answer，跑完了但答案错 | 同上 | — |
| TLE | Time Limit Exceeded，超时 | 同上 | — |
| RE | Runtime Error，运行时崩了 | 同上 | — |
| CE | Compile Error，编译没过 | 同上 | — |
| UNKNOWN | 无法识别的结论（兜底值） | `VERDICT_MAP.get()` 的默认 | — |
| 测试点 testcase | 一组输入 + 期望输出，判题就是拿它比对 | Java 老平台跑的 | — |
| 判题 judge / judge engine | 编译、跑测试点、出结论的那套东西 | `JudgeEngine.java`（M1 要读懂） | — |
| 提交 submission | 选手交的一次代码 | 一次提交对应一个 verdict | — |
| 排行榜 rank | 按分数/用时排序 | SQL 加权要练的 `ROW_NUMBER/RANK` | — |
| TSV / CSV | 用制表符 / 逗号分隔的纯文本表格 | 老平台的数据存储格式，新服务**只读**消费 | — |
| HttpServer | Java 自带的最简 HTTP 服务，不依赖框架 | `sort_platform` 就靠它，端口 8080 | — |
| Spring / Maven | Java 主流框架 / 构建工具 | **老平台没用**，别在简历里说用了 | — |
| JDK 17 | Java 运行环境版本 | `sort_platform` 的版本 | — |

## 七、Git（提交这条线）

| 名词 | 一句话是什么 | 项目里对应哪块 | 我栽过的坑 |
|---|---|---|---|
| 仓库 repository | 被 git 管理的文件夹 | `D:\ai-engineering-roadmap` | — |
| 分支 branch | 一条开发线，本项目只有 `master` | `master` | — |
| 工作区 | 你改过但还没登记的改动 | `git status` 里的 M / ?? | — |
| 暂存区 stage | `git add` 之后、"提交"之前的缓冲区 | `git add <文件>` | — |
| commit | 把暂存区的内容存成一个历史快照 | `7f7f9b1` | — |
| commit message | 一句话说明这次改了什么 | `feat: analyze 入参模型…` | — |
| push | 把本地提交推到远程仓库 | `git push origin master` | — |
| origin / remote | 远程仓库的别名 / 远程地址 | `git@github.com:dsysocute/...` | — |
| `.gitignore` | 声明哪些文件不进 git（venv、密钥） | 仓根 | — |
| diff | 两个版本的差异 | Git Graph 里点开能看 | — |
| Git Graph | VS Code 插件，图形化看提交历史 | 左侧面板 | — |

---

## 怎么用这张表

1. **遇到新词**：先在这里找。找不到就问，问明白后**自己**往对应分组加一行（加的过程就是记的过程）。
2. **复习方式**：只看第二列"一句话"，能自己说出"项目里在哪"，就算通过。说不出的那行做个标记。
3. **写简历/面试前**：重点看第五组（Agent/LLM）和第四组（架构）——面试官问的就是这些词，你能不能用自己的话讲清。

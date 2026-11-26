---

## Daily Progress

### Today

- 日期：2026-10-28
- 完成的功能：搭建项目总目录，创建python-practice；编写hello.py、day01.py练习；初始化Git仓库；学会终端切换目录运行py文件
- 新学会的概念：py源码、解释器、终端的区别；str/int类型差异；input返回字符串；CMD cd /d跨盘语法；Git仓库基础
- 新增或修改的文件：python-practice/hello.py、python-practice/day01.py、PROGRESS.md
- 测试命令：cd /d "D:\ai-engineering-roadmap\daily progress\python-practice" && python day01.py
- 测试结果：hello.py正常输出；day01.py完成输入、类型转换、计算、异常处理；cd万能命令稳定可用
- Git commit：feat: day01基础类型练习 + hello验证脚本 + 学习记录

### Problems

- 遇到的问题：CMD目录切换逻辑复杂，跨盘、带空格路径cd经常报错；容易跑错旧版本代码
- 如何定位：用dir查看当前目录文件；核对终端路径和VSCode路径；检查文件是否保存
- 最终如何修复：固定使用cd /d "完整路径"；带空格路径加英文引号；运行前核对文件名
- 哪些问题仍未解决：CMD是老旧终端，语法繁琐，后续可以尝试PowerShell简化操作

### Tomorrow

- 明天必须交付的功能：巩固类型转换，学习分支判断；熟悉git add/commit/push完整流程
- 验收标准：独立终端运行py；能解释数据类型区别；完成第一次git提交
- 需要复习的概念：Python数据类型与转换、CMD cd /d原理、Git基础



---

## Daily Progress

### Today

- 日期：2026-10-29
- 完成的功能：完成Python基础控制流与数据结构实战练习；编写day02.py实现基础数值统计；开发quality_check_v0.py数据质量校验函数；完成3组边界测试用例自测，实现空列表、非法阈值异常捕获，统一返回值数据类型。
- 新学会的概念：掌握if/elif/else分支逻辑；分清for循环与while循环适用场景；理清列表、字典、元组、集合的存储区别；掌握列表与字典增删改查操作；理解循环边界条件与无限循环成因；学会函数返回多值、空数据与非法参数的容错处理。
- 新增或修改的文件：day02.py、quality_check_v0.py
- 测试命令：直接运行py脚本；手动调用函数传入多组测试用例（全超标、全不超标、空列表、阈值传入字符串）
- 测试结果：1. day02.py可以正常找出极值、统计阈值数量、字典存储成绩、计算平均分；2. check_values函数可以正常返回数据总数、超标数量、超标比例；3. 空列表、非法阈值会打印提示并返回(0,0,0.0)，无程序崩溃；4. 三组边界用例的实际输出和预判结果完全一致。
- Git commit：quality_check_v0：完成check_values函数，增加空列表、非法阈值边界校验，统一返回值类型

### Problems

- 遇到的问题：一开始错误返回整数0，和正常分支的浮点比例类型不一致；最开始只做了空列表判断，没有对阈值做类型转换异常捕获；最开始函数只return元组，没有提前打印文字提示。
- 如何定位：对比正常分支与异常分支的返回值类型；单独传入字符串阈值触发报错；逐行调试函数分支执行逻辑。
- 最终如何修复：把异常返回值修改为0,0,0.0保证类型统一；增加try-except捕获阈值转浮点异常；在分支内提前打印文字提示，再返回默认值。
- 哪些问题仍未解决：暂无遗留问题，当前版本满足纯基础语法实现，未引入pandas与第三方库。

### Tomorrow

- 明天必须交付的功能：练习多层嵌套循环、字典嵌套；完善异常捕获逻辑；练习函数传参、局部变量与全局变量；完成下一轮数据统计类练习题。
- 验收标准：能够独立编写条件分支、循环代码；清晰区分列表与字典的使用场景；能够主动处理空列表、不存在字典键等异常；函数使用return返回结果，不只用print打印内容。
- 需要复习的概念：循环边界控制、for/while场景区分、列表与字典增删改查、函数多返回值、基础异常处理try-except。

---

## Daily Progress

### Today

- 日期：2026-10-30
- 完成的功能：将第2天代码拆分为 count_values()、calculate_ratio()、validate_input()、main() 四个函数，增加参数校验，处理除零问题；创建dataops-copilot目录，搭建虚拟环境，完成venv创建、PowerShell激活、pip安装依赖、导出依赖清单；新建app/services/quality_checker.py，把质量检查函数抽成可导入模块；完成Harness评审，核对函数拆分合理性并说明修改理由；配置SSH，成功将代码推送到GitHub仓库。
- 新学会的概念：单一职责函数拆分、参数校验与try/except异常捕获、除零防护、Python虚拟环境隔离依赖、项目服务层模块化、跨文件导入、Git SSH代理与端口排障、区分导入错误和业务异常。
- 新增或修改的文件：day02_quality_check.py、dataops-copilot/app/services/quality_checker.py、dataops-copilot/requirements.txt、.ssh/config
- 测试命令：python -m venv .venv
  .venv\Scripts\Activate.ps1
  pip freeze > requirements.txt
  python -c "from app.services.quality_checker import calculate_ratio"
  ssh -T git@github.com
  git push
- 测试结果：函数参数校验生效，除零场景可以捕获；虚拟环境创建激活正常，依赖清单生成成功；跨文件导入函数正常；SSH认证成功，代码推送至GitHub，能区分导入报错和业务计算报错，全部验收项通过。
- Git commit：feat: day02 函数拆分模块化 + 虚拟环境搭建 + ssh推送github

### Problems

- 遇到的问题：CMD下connect命令找不到；github默认22端口被拦截连接断开；Git Bash和CMD路径语法不一样；ssh config文件后缀错误导致配置不生效。
- 如何定位：根据报错信息判断connect仅Git Bash内置；Connection closed 判定22端口封禁；cd命令报错是终端路径语法差异；查看文件后缀发现config变成config.txt。
- 最终如何修复：改用Git Bash执行git/ssh操作；ssh配置改为ssh.github.com:443；删除config多余txt后缀；Git Bash使用/d/路径写法；代码增加前置参数校验+异常捕获双重防护。
- 哪些问题仍未解决：无

### Tomorrow

- 明天必须交付的功能：给quality_checker模块编写unittest单元测试；使用argparse做命令行参数入口；编写项目README，记录环境搭建与运行步骤。
- 验收标准：代码拆分为指定4个函数，带参数校验，防止除零；可以外部导入模块函数；熟练完成虚拟环境全套操作；能区分导入异常和业务逻辑异常；交付基础目录、虚拟环境、可导入质量检查模块。
- 需要复习的概念：函数单一职责、精准异常捕获、Python包导入机制、虚拟环境、Git SSH配置

---

## Daily Progress

### Today

- 日期：2026-11-17
- 断更说明：10-31 → 11-17 期间为期中考试备考与考试占用，同时在调整实习方向、梳理老平台项目与目标岗位要求，代码练习暂停，进度记录断更。11-17 重新接上，按实习准备清单恢复节奏。
- 完成的功能：在 algoarena-ai-service 下创建 Python 虚拟环境，安装 fastapi、uvicorn[standard]、pytest、httpx 四个依赖并验证可导入，生成 requirements.txt；补全 .gitignore 忽略规则。
- 新学会的概念：虚拟环境依赖隔离、第三方依赖与传递依赖、requirements.txt 依赖复现、pip 临时目录清理。
- 新增或修改的文件：algoarena-ai-service/.venv（不进 Git）、algoarena-ai-service/requirements.txt、.gitignore
- 测试命令：python -c "import fastapi, uvicorn, pytest, httpx; print('ok')"
- 测试结果：四个依赖导入成功，打印 ok。

### Tomorrow / 排期

- M0（11/17–11/19）：完成 .gitignore、Git 分主题提交并 push、补本进度记录。
- M1（11/20–11/30）：搭建 app 目录骨架，写最小 /api/health、/api/analyze 接口，Pydantic 入参校验，pytest 用例，SQL 基础练习。
- 验收标准：每个阶段做完能脱稿讲清链路，代码本人手写；没跑通、没理解的内容不写进简历。
---

## Daily Progress

### Today

- 日期：2026-11-19
- 完成的功能：在 algoarena-ai-service 下建 app/api、app/services、app/models、app/core、tests 五个分层目录（各含 __init__.py）；手写 app/main.py，跑通 GET /api/health，返回 200 + JSON；打开 /docs 确认 FastAPI 自动生成的接口文档里登记了这条路由；恢复被误删的 CURRENT-STATUS.md。
- 新学会的概念：一次 HTTP 请求到响应的完整链路（浏览器 → 端口 → uvicorn 解析 → 查路由表 → 调函数 → 转 JSON 回传）；端口与路由表的关系；@app.get 是"登记动作"而不是普通语法；uvicorn 与 FastAPI 的分工（uvicorn 占端口收请求，FastAPI 负责算）；health check 探活接口的行业用途；Git 里"未追踪/暂存/提交"三层状态；VS Code 打开子目录时与父级仓库的关系。
- 新增或修改的文件：algoarena-ai-service/app/main.py；app/__init__.py、app/api/__init__.py、app/core/__init__.py、app/models/__init__.py、app/services/__init__.py、tests/__init__.py；恢复 CURRENT-STATUS.md
- 测试命令：python -m uvicorn app.main:app --reload
  Invoke-WebRequest http://127.0.0.1:8010/api/health
- 测试结果：/api/health 返回状态码 200，响应体 {"status":"ok","service":"algoarena-ai-service"}；/docs 页面可打开并列出该路由。
- 卡在哪里：手敲时踩了三个错——from 写成 form、@app 写成 @qpp、路径写成 /app/health，分别对应"启动即炸""启动即炸""能启动但访问 404"，改完后通过；VS Code 解释器仍是全局 Python 3.12，未切到 .venv。
- 未完成：POST /api/analyze、Pydantic 五字段校验、三个易漏点、.env 配置、pytest 用例、SQL、Pandas 全部未开始；本次改动尚未提交。

### Tomorrow

- 明天第一件事：把 main.py 拆成分层——路由搬到 app/api/health.py，main.py 只保留"创建 app + 挂载 router"。
- 验收标准：拆完仍能在 8000 端口访问 /api/health；main.py 里不再出现任何接口函数体；能脱稿说清"接口层只负责搬运、不负责决策"。
---

## Daily Progress

### Today

- 日期：2026-11-19（夜，跨到 11-20 凌晨）
- 完成的功能：清单文档从"手改 docx"改为"json 数据源 + 渲染脚本"，重生成的 docx 与原文件 126 块逐块一致；建立每日困惑台账 OPEN-QUESTIONS.md；写完 app/models/analyze.py 五字段入参模型并通过三条验收；建立名词表 GLOSSARY.md（7 组约 100 词）
- 新学会的概念：docx 可以被"数据源 + 渲染器"完整还原；Pydantic 的 ValidationError 在命令行与 HTTP 422 是同一套校验的两种表现；Field(default=/max_length=) 的写法；verdict = 判题结论（AC/WA/TLE/RE/CE）；路由地址由装饰器里的字符串决定，函数名不参与路由
- 新增或修改的文件：tools/build_docs/checklist.json、build_checklist.py、README.md；寒假实习准备清单.docx；algoarena-ai-service/app/models/analyze.py；daily progress/OPEN-QUESTIONS.md；daily progress/GLOSSARY.md；CURRENT-STATUS.md
- 测试命令：python -c 三条——正常构造 / 缺 verdict / error_message 传 501 字
- 测试结果：正常输出 error_message=''；缺字段报 Field required；501 字报 String should have at most 500 characters
- Git commit：b5562a4、38da75f、b65cac2、9bde722、7f7f9b1、6df9e0f（均已 push）

### Problems

- 遇到的问题：点 VS Code 右上角运行按钮跑 analyze.py 用了全局 Python 3.12，报 No module named 'pydantic'；另外直接运行 analyze.py 本身就不会有任何输出（文件里只有 import 和 class，没有 print）
- 如何定位：看终端提示符有没有 (.venv)；用 python -c "import sys; print(sys.executable)" 打印解释器真实路径
- 最终如何修复：激活 .venv 后用 python -c 主动构造对象来验证
- 哪些问题仍未解决：VS Code 默认解释器仍是全局 Python312 未永久切换；check-commit.ps1 仍未注册进计划任务；OPEN-QUESTIONS 六条里仍五条待验证

### Tomorrow

- 明天必须交付的功能：diagnose.py（VERDICT_MAP + TEMPLATES + build_conclusion）；main.py 加 POST /api/analyze 并完成四项验收
- 验收标准：命令行能对三种输入出三种不同结论；/docs 里能发请求拿到结论；空白源码返回 422；未知 verdict 归成 UNKNOWN 不崩
- 需要复习的概念：归一化与兜底、截断与 truncated 标记、装饰器是登记动作、dict 与 JSON 的区别
- 遗留待办：把 health 路由拆到 app/api/health.py，main.py 只保留"创建 app + 挂载 router"——这条是上一节排的，今晚未做

---

## Daily Progress

### Today

- 日期：2026-11-24
- 完成的功能：实习路线重排。旧「寒假投大模型 / Agent 实习」口径作废，改为：2027 暑假第一份实习破零 → 大三第二段 AI 应用 / Data Agent 实习 → 2028 暑假第二段实习争取 return → 2028 秋招与转正并行。仓库内规划文档同步改写：ROADMAP.md、CURRENT-STATUS.md、README.md、checklist.json 全部对齐新路线；清单 docx 改由新链路重新生成为《项目与实习准备清单.docx》。
- 新学会的概念：数据开发 / 数据分析、AI 应用 / Agent、Data Agent 三类在招 JD 的能力要求差异；底座（Python/SQL/API/调试）可跨岗迁移，纯套壳 Demo 竞争力最弱。
- 新增或修改的文件：ROADMAP.md、CURRENT-STATUS.md、README.md、tools/build_docs/checklist.json、tools/build_docs/build_checklist.py、tools/build_docs/README.md；产物 项目与实习准备清单.docx。旧《寒假实习准备清单.docx》仅作历史留存，不再更新。
- 测试命令：python tools/build_docs/build_checklist.py
- 测试结果：生成成功，85 段落、7 张表格、44544 字节。
- 卡在哪里：无。
- 未完成：M1 代码侧待办不变——POST /api/analyze 路由接入、pytest 10 条、.env 配置隔离、SQL 两表与加权查询、Java 阅读笔记、Pandas 第二项目。

### Tomorrow

- 第一件事：接 POST /api/analyze（手搓）：main.py 引入 AnalyzeRequest 与 build_conclusion，收请求 → 调 service → 返回结论字典；纯空白源码 raise 422。
- 验收标准：/docs 手动测一遍正常 / 缺字段 / 空白 / 未知 verdict；脱稿讲清请求链路每一跳。

---

## Daily Progress（续）

### Today（2026-11-24，第二轮）

- 完成的功能：补齐新路线下的材料与详细规划。简历、技术方案两份 docx 按新口径经旧生成脚本重跑，落到仓库根 `简历初稿.docx`、`AlgoArena项目技术方案.docx`；求职方向改为数据 / 后端为主、AI 应用 / Agent 兜底；旧排期（12/10、12/18、12/14 首投、寒假投递）全部清除。新增 `PLAN-2027暑假实习.md` 完整规划：路线总览、技术栈程度与验收、项目拆解、面试题、笔试题、阶段动作、名词与流程过关清单、投递前 Go/No-Go 总表。
- 新学会的概念：三类岗位（数据 / 后端 / Agent）共用底座与差异化要求；笔试是否存在因厂而异，按"可能有笔试"准备算法与 SQL。
- 新增或修改的文件：PLAN-2027暑假实习.md；ROADMAP.md、README.md、CURRENT-STATUS.md 补规划文档链接；旧生成脚本 build_docs.py 简历与技术方案部分改写；产物 简历初稿.docx、AlgoArena项目技术方案.docx。
- 测试命令：python build_docs.py；python -c 扫描两份新 docx 无旧口径残留。
- 未完成：M1 代码侧待办不变；本轮已按确认删除旧口径存档 docx、废弃旧前端、旧 .bak 备份、__pycache__、.idea、Word 锁文件与 ACL 报告目录；36 个 .class 编译产物按要求保留。

---

## Daily Progress（续）

### Today（2026-11-24，第三轮 · 路线最终修订为 AI 全栈）

- 完成的功能：听取在岗员工建议后，路线做最后一次修改，全面转向 **AI 全栈开发**。后端语言定为 Python（FastAPI 主线）+ Java（读老平台、电商作业练后端）；新增前端目标：HTML/CSS/JS/TS 基础 + Vue3（或 React）一种框架，做到页面可交互、能调通后端接口并联调；AI 应用方向保留 LLM API、Prompt、RAG、Function Calling/Agent。重新检索了字节、OPPO、安克、万兴、拓维、博世、Accenture 等在招 AI 全栈 / 全栈 JD，提炼出五层要求（主语言 + 后端 + 前端 + AI 应用 + 交付协作），实习不要求模型训练/微调/高并发/微服务/K8s/多端。
- 新学会的概念：AI 全栈实习的真实口径是"一门后端扎牢 + 前端能做页面联调 + 能把大模型接进业务"，不是前后端都精通；JD 反复出现 React/Vue、HTTP/WebSocket、SQL、Redis/消息队列概念、AI Coding 工具使用经验。
- 新增或修改的文件：PLAN-2027暑假实习.md 整体重写为全栈口径；ROADMAP.md 路线定位、JD 对标、里程碑（新增 M1.5 前端入门、M3 全栈页面）、风险降级改写；README.md 路线表与红线更新；CURRENT-STATUS.md 路线段与待确认项更新；checklist.json 全量对齐并重生成《项目与实习准备清单.docx》；build_docs.py 简历与技术方案部分改写，重生成《简历初稿.docx》《AlgoArena项目技术方案.docx》。
- 测试命令：python tools/build_docs/build_checklist.py；python build_docs.py。
- 测试结果：清单生成 93 段落、7 张表格；简历与技术方案两份 docx 均重跑成功。
- 未完成：M1 代码侧待办不变——POST /api/analyze 路由接入、pytest 10 条、.env 配置隔离、SQL 两表与加权查询、Java 阅读笔记、数据清洗第二项目；前端框架最终选型（Vue3 起步 / 直接 React）待按目标岗位再定。

---

## Daily Progress（续）

### Today（2026-11-24，第四轮 · 仓库清理）

- 完成的功能：按确认清理仓库冗余。删除 Word 锁文件 `~$*.docx`；36 个 `.class` 编译产物（21 个 `.java` 源码保留，启动时重新编译）；3 个旧数据备份目录（`data.backup-*`、`data.reset-backup-*`，现行 `data/` 4 个 TSV 保留）；早期练习脚本（`dailyprogress.py`、`function.py`、`python-practice/`）与过期分析文档（代码体检报告、维护记录、类图 dot/png）。`.gitignore` 补入 `*.class`、`out/`、`backend/out/`；README 目录树同步去掉 `python-practice`。
- 保留未动：`.venv`（27.9MB 本地虚拟环境，已 gitignore）；现行 `data/`；`_tool/` 维护脚本；三份 docx 与规划文档。
- 新增或修改的文件：`.gitignore`、`README.md`；删除项见提交 `8f1bc82`。
- 未完成：本地 M1 重构改动（`api/analyze.py` 空、`api/health.py` 旧写法、两个 `__init__.py` 待补）仍未提交，继续接 `POST /api/analyze`。

---

### Today（2026-11-25，第五轮 · 学长 Java 全栈路线合并为全周期规划）

- 完成的功能：细读学长给的《Java学习攻略详解.pdf》13 阶段路线、7 张项目/简历截图与文字版时间线，和现有 AI 全栈规划做最后一次合并。规划从"只盯 2027 暑假实习"改成**从当前学期到毕业的全周期分阶段路线**：主轨就业 AI 全栈，备选考研兜底。
- 吸收进规划的内容：Java SE → 核心 → MySQL/JDBC → 前端基础 → 原生 Web → SSM → Spring Boot → Redis/MQ 的阶梯（近期只到 SE 核心 + JDBC + Boot 入门，SSM/中间件大三实践，Spring Cloud/分布式/ES/K8s/大数据后置）；长期刷题 + 竞赛备选线（蓝桥杯/CSP/PAT，待核验）；OS/计网/计组课程排期；项目渠道与避坑（避开商城外卖，优先管理系统/博客论坛/网盘/评测/AI 应用）；中间件与设计模式内功；考研兜底分支与秋招/考研时间节点；投递渠道补拉钩/智联/offer show/官网。
- 保留的我方内容：手写红线、未跑通不进简历、AlgoArena 真实基线与角色口径、分层与测试验收、降本兜底、SQL 数据管线、面试深挖 8 问与过关清单。
- 新学会的概念：全栈实习看端到端交付，不是两端精通；竞赛/证书在没核对官方通知和校内目录前只标待核验，不写"含金量高/普遍认可"；同一学期只保留 1 个主线硬任务 + 1–2 个高价值增强任务。
- 新增或修改的文件：PLAN-2027暑假实习.md 重写为全周期路线图（画像、学期级节点表、Java 阶梯、竞赛待核验、兜底路径）；ROADMAP.md 里程碑改为阶段 0–9、时间账/渠道/风险/待确认对齐；README.md 路线表更新；CURRENT-STATUS.md 路线段与待确认更新；checklist.json 对齐并重生成《项目与实习准备清单.docx》（107 段落、7 表）。
- 测试命令：python -c json 校验；python tools/build_docs/build_checklist.py。
- 未完成：M1 代码侧待办不变——`POST /api/analyze` 路由接入、pytest 10 条、.env 配置隔离、SQL 两表与加权查询、Java 阅读笔记、数据清洗第二项目；前端框架选型、是否报竞赛、考研倾向待确认。

---

### Today（2026-11-26，第六轮 · 口径纠偏：AI 全栈主轨，AlgoArena 搁置）

- 用户拍板的口径调整：主轨就是 **AI 全栈开发**，不是 Java 全栈，也不单设 Agent 开发岗位方向；Agent / 工具调用只是全栈里的一项 AI 能力。后端 Python 主线 + Java 二线打底，前端联调够用，叠加大模型 / RAG / 工具调用。
- 项目取舍：**电商 Java 项目继续做**（课堂作业 + 学长推荐项目，主线练手）；**AI 全栈 Demo**（Python 后端 + 前端页面 + AI 链路）作为 AI 能力证据；**AlgoArena 排序平台搁置备查**——代码保留、不推进、不占主线时间，未跑通 / 未理解部分不进简历。
- 文档同步：PLAN-2027暑假实习.md 岗位口径统一为 AI 全栈，附录 A 换成在招全栈 / 后端 JD 样本，Agent 专项 JD 移出；ROADMAP.md 里程碑重排为阶段 0 底座起步 + 电商梳理，阶段 3/4 改为 AI Demo 通用场景，第六节改为搁置备查；README.md、CURRENT-STATUS.md、tools/build_docs/README.md、checklist.json 全部对齐；清单 docx 重生成（108 段落、6 表）。
- 保留未动：AlgoArena 事实基线与角色口径（HTTP 层 AI 主导、本人配合复现验证，简历写参与 / 配合 / 借助 AI 辅助）；OPEN-QUESTIONS 6 条 M1 困惑标注搁置，重启时再接回。
- 未完成 / 待确认：前端框架选型（Vue3 / React）、是否报蓝桥杯 / CSP、考研倾向、数分重修时间、2027 暑假到岗天数、模型 API 预算、AI Demo 数据集与场景、目标城市；M1 代码侧待办随搁置暂停。

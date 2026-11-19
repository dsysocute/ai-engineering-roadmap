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

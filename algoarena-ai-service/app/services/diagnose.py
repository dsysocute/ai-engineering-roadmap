MAX_SOURCE = 2000
MAX_ERROR = 500

# 上游（Java 判题端）给出来的判题结果写法不统一，
# 这里把各种写法统一成五个标准值；UNKNOWN 不写进表，靠 .get 的默认值兜底。
VERDICT_MAP = {
    # 通过
    "AC": "AC",
    "OK": "AC",
    "OKAY": "AC",
    "ACCEPTED": "AC",
    "PASS": "AC",
    "PASSED": "AC",
    "通过": "AC",
    "答案正确": "AC",
    # 答案错误
    "WA": "WA",
    "WRONG": "WA",
    "WRONG_ANSWER": "WA",
    "WRONGANSWER": "WA",
    "答案错误": "WA",
    # 超时
    "TLE": "TLE",
    "TIMEOUT": "TLE",
    "TIME_LIMIT_EXCEEDED": "TLE",
    "TIME LIMIT EXCEEDED": "TLE",
    "超时": "TLE",
    # 运行错误
    "RE": "RE",
    "RUNTIME_ERROR": "RE",
    "RUNTIMEERROR": "RE",
    "CRASH": "RE",
    "运行错误": "RE",
    "崩溃": "RE",
    # 编译错误
    "CE": "CE",
    "COMPILE_ERROR": "CE",
    "COMPILATION_ERROR": "CE",
    "编译错误": "CE",
}

# 每种结论对应的固定结论句；UNKNOWN 必须在表里，否则兜底时会 KeyError
TEMPLATES = {
    "AC": "全部测试点通过。可进一步关注时间复杂度与代码风格。",
    "WA": "程序能编译能运行，但输出与期望答案不一致，多为逻辑或边界条件问题。",
    "TLE": "运行时间超过限制，常见原因是算法复杂度过高或存在死循环。",
    "RE": "运行过程中异常退出，常见原因是空指针、数组越界或除零。",
    "CE": "编译未通过，需先查看编译输出并修正语法或类型错误。",
    "UNKNOWN": "无法识别的判题结果，建议人工核对提交记录与判题日志。",
}


def normalize_verdict(raw: str) -> str:
    """把各种写法的判题结果统一成标准值，认不出的一律归 UNKNOWN"""
    return VERDICT_MAP.get(raw.strip().upper(), "UNKNOWN")


def truncate(text: str, limit: int) -> tuple[str, bool]:
    """超长就截断，第二个返回值表示有没有截断过"""
    if len(text) <= limit:
        return text, False
    return text[:limit], True


def build_conclusion(req) -> dict:
    """根据入参生成一条固定模板结论（先不接模型）"""
    verdict = normalize_verdict(req.verdict)
    source, source_truncated = truncate(req.source_code, MAX_SOURCE)
    error, error_truncated = truncate(req.error_message, MAX_ERROR)

    return {
        "problem_id": req.problem_id,
        "language": req.language,
        "verdict": verdict,
        "conclusion": TEMPLATES.get(verdict, TEMPLATES["UNKNOWN"]),
        "source_truncated": source_truncated,
        "error_truncated": error_truncated,
        "source_length": len(req.source_code),
        "error_length": len(req.error_message),
    }

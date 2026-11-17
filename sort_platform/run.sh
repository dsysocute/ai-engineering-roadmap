#!/usr/bin/env bash
set -e
cd "$(dirname "$0")"

echo "========================================"
echo "  排序算法测评平台 - 启动"
echo "========================================"

if ! command -v javac >/dev/null 2>&1; then
    if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/javac" ]; then
        export PATH="$JAVA_HOME/bin:$PATH"
    else
        echo "[错误] 找不到 javac，说明装的是 JRE，不是 JDK。"
        echo "       判题必须要 JDK（要调编译器）。"
        echo "       装一个 JDK 17：https://adoptium.net/"
        exit 1
    fi
fi

mkdir -p out/classes

echo "[1/2] 正在编译后端..."
javac -encoding UTF-8 -d out/classes backend/src/*.java
echo "      编译完成。"

echo "[2/2] 正在启动服务..."
java -Dfile.encoding=UTF-8 -cp out/classes Main

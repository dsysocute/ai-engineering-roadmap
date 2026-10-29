import random

# 获取阈值a，捕获非法输入
a_str = input("输入一个数字：")
while True:
    try:
        a = int(a_str)
        break
    except ValueError:
        a_str = input("输入非法，请重新输入：")

# 生成10个[-10,10]的随机浮点数
nums = []
for i in range(10):
    nums.insert(i, random.uniform(-10, 10))
print(f"随机生成的列表为:{nums}")

# 练习：try-except捕获空列表访问异常
while True:
    try:
        print(nums[0])
        break
    except IndexError:
        nums.append(a)

# 统计大于阈值a的数量
count_above = 0
for num in nums:
    if num > a:
        count_above += 1
print(f"其中大于a的数为:{count_above}")

# 对比输出（内置max/min，保留用来对照结果）
print(f"数组中最大值为:{max(nums)},最小值为:{min(nums)}")

# 学生成绩数据
grade = [
    {"姓名": "张三", "语文成绩": 89, "数学成绩": 76, "英语成绩": 90},
    {"姓名": "李四", "语文成绩": 83, "数学成绩": 120, "英语成绩": 118}
]

# 语文平均分计算，做好容错
Chinese_grade_total = 0
for stu in grade:
    Chinese_grade_total += stu.get("语文成绩", 0)
if len(grade) == 0:
    print("暂无学生成绩数据，无法计算平均分")
else:
    Chinese_grade_average = Chinese_grade_total / len(grade)
    print(f"班内语文成绩平均分为{Chinese_grade_average}")

# 手写循环求最值，增加空列表判断
if not nums:
    print("列表为空，无法计算最值")
else:
    min_val = nums[0]
    max_val = nums[0]
    for num in nums:
        if num < min_val:
            min_val = num
        if num > max_val:
            max_val = num
    print(f"最大值为:{max_val},最小值为:{min_val}")
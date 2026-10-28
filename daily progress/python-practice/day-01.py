name=input("请输入你的名字:")
age_str=input("请输入你的年龄:")
while True:
    try:
        age=int(age_str)
        print(f"姓名:{name},年龄:{age}")
        break
    except ValueError:
        age_str=input("输入非法,请重新输入您的年龄:")
a_str=input("请输入两个数字来计算和差积商，第一个:")
b_str=input("第二个:")
while True:
    try:
        a=float(a_str)
        b=float(b_str)
        break
    except ValueError:
        a_str=input("输入非法，现请重新输入第一个数字:")
        b_str=input("第二个数字:")
while True:
    try:
        print(f"和为{a+b}，差为{a-b}，积为{a*b}，商为{a/b}")
        break
    except ZeroDivisionError:
        print(f"和为{a+b}，差为{a-b}，积为{a*b}，因除数为零，故商不存在")
print(f"变量类型：姓名为{type(name)},年龄为{type(age_str)}转{type(age)},第一个数字为{type(a_str)}转{type(a)},第二个数字为{type(b_str)}转{type(b)}")
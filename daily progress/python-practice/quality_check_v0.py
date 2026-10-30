import random
values=[]
values_num=random.randint(1,20)
for i in range(values_num):
    values.append(random.randint(-100,100))
print(values)
if not values:
    while True:
        re_values=(input("列表为空，请至少添加一个数据,请输入"))
        try:
            values.append(float(re_values))
            break
        except ValueError:
            print("输入非法,请重新输入正确数据")  
def check_values(values,threshold):
    if not values:
        print(f"列表为空，无法进行函数计算")
        return 0,0,0
    try:
        threshold=float(threshold)
    except ValueError:
        print(f"阈值非数，无法计算")
        return 0,0,0
    over_threshold_total=0
    for val in values:
        if val>threshold:
            over_threshold_total+=1  
    over_threshold_rate=float(over_threshold_total/len(values)) 
    return len(values),over_threshold_total,over_threshold_rate
print(check_values(values,45))
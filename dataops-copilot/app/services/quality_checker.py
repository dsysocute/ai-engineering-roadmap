def count_values(values_list,threshold):
    over_threshold_num=0
    for val in values_list:
        if val>threshold:
            over_threshold_num+=1
    return len(values_list),over_threshold_num
def validate_input(values_list,threshold):
    if not values_list:
        print(f"列表为空，请检查")
        return False
    try:
        threshold=float(threshold)
    except ValueError:
        print("阈值非数，请检查")
        return False
    return threshold
def calculate_ratio(total,over_threshold):
    if total==0:
        return 0.0 
    over_threshold_rate=over_threshold/total
    return over_threshold_rate
def main(values_list,threshold):
    print(f"列表:{values_list},阈值:{threshold}")
    valid_th= validate_input(values_list,threshold)
    if valid_th is not False:
        total,over_threshold=count_values(values_list,valid_th)  
        over_threshold_rate=calculate_ratio(total,over_threshold)
        return total,over_threshold,over_threshold_rate
    
    else:print("列表为空或阈值非数，请检查")
    return 0,0,0.0
if __name__ == "__main__":
    # 自测代码
    res = main([2,6,9,14], "8")
    print(f"总数、超标数、超标比例：{res}")

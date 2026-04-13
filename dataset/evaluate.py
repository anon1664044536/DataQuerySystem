import os

def load_reference(file_path):
    """加载参考答案，返回字典: {id: (question, expected_answer)}"""
    reference_data = {}
    with open(file_path, 'r', encoding='utf-8') as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            parts = line.split('|')
            if len(parts) == 3:
                q_id, question, expected_ans = parts
                # 统一转换为字符串并去除空格，防止格式干扰
                reference_data[q_id.strip()] = {
                    "question": question.strip(),
                    "expected": expected_ans.strip()
                }
    return reference_data

def load_results(file_path):
    """加载模型预测结果，返回字典: {id: actual_answer}"""
    results_data = {}
    if not os.path.exists(file_path):
        print(f"Error: 找不到结果文件 {file_path}")
        return results_data
        
    with open(file_path, 'r', encoding='utf-8') as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            parts = line.split('|')
            if len(parts) == 2:
                q_id, actual_ans = parts
                results_data[q_id.strip()] = actual_ans.strip()
    return results_data

def evaluate(ref_file, res_file):
    """评测主函数"""
    print("=" * 50)
    print("NL2SQL 智能问数准确率自动化评测报告")
    print("=" * 50)
    
    reference_data = load_reference(ref_file)
    results_data = load_results(res_file)
    
    if not reference_data:
        print("错误: 对照答案文件为空或格式不正确。")
        return
        
    total_questions = len(reference_data)
    correct_count = 0
    errors = []

    for q_id, ref_info in reference_data.items():
        expected = ref_info['expected']
        actual = results_data.get(q_id, "未提供答案/执行失败")
        
        # 将结果转为浮点数比较（防止 "180" 和 "180.0" 的误判）
        try:
            is_match = float(expected) == float(actual)
        except ValueError:
            # 如果不是纯数字（比如 Error 字段），则直接进行字符串比较
            is_match = str(expected).lower() == str(actual).lower()
            
        if is_match:
            correct_count += 1
        else:
            errors.append({
                "id": q_id,
                "question": ref_info['question'],
                "expected": expected,
                "actual": actual
            })

    accuracy = (correct_count / total_questions) * 100
    
    # 打印概览信息
    print(f"总测试题目数 : {total_questions}")
    print(f"回答正确题数 : {correct_count}")
    print(f"回答错误题数 : {len(errors)}")
    print(f"✅ 模型准确率 (Accuracy): {accuracy:.2f}%\n")
    
    # 打印错误明细，方便排查模型在哪类 SQL 上犯错
    if errors:
        print("❌ 错误明细 (需重点排查的 SQL 解析失败场景):")
        print("-" * 50)
        for error in errors:
            print(f"问题 ID: {error['id']}")
            print(f"自然语言: {error['question']}")
            print(f"预期结果: {error['expected']}")
            print(f"模型结果: {error['actual']}")
            print("-" * 50)

if __name__ == "__main__":
    # 文件路径配置
    REFERENCE_FILE = "./reference.txt"
    RESULTS_FILE = "./results.txt"
    
    # 确保存放这两个文件在同一目录下，或者修改上面的路径
    evaluate(REFERENCE_FILE, RESULTS_FILE)
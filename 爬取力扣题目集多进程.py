import json
import os
import re
import time
import multiprocessing
from selenium import webdriver
from selenium.webdriver.chrome.service import Service
from selenium.webdriver.common.by import By
from selenium.common.exceptions import TimeoutException
from selenium.webdriver.support.wait import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC
from selenium.webdriver.chrome.options import Options
from selenium.webdriver.common.action_chains import ActionChains
from bs4 import BeautifulSoup
from webdriver_manager.chrome import ChromeDriverManager
import threading
from concurrent.futures import ProcessPoolExecutor, as_completed


def setup_driver():
    """设置并返回配置好的driver实例 - 进程安全版本"""
    chrome_options = Options()
    chrome_options.add_argument('--disable-blink-features=AutomationControlled')
    chrome_options.add_argument('--disable-infobars')
    chrome_options.add_argument('--disable-gpu')
    chrome_options.add_argument('--no-sandbox')
    chrome_options.add_argument('--disable-dev-shm-usage')
    chrome_options.add_argument('--disable-extensions')
    chrome_options.add_argument('--disable-notifications')
    chrome_options.add_argument('--start-maximized')
    chrome_options.add_argument('--headless')  # 无头模式适合多进程
    chrome_options.add_argument('--disable-images')  # 禁用图片加速加载

    service = Service(ChromeDriverManager().install())
    driver = webdriver.Chrome(service=service, options=chrome_options)
    return driver


def minimal_safe_clean(text):
    """最小化安全清理"""
    if not text:
        return ""
    text = re.sub(r'10\n5', '10^5', text)
    text = re.sub(r'10\n9', '10^9', text)
    text = re.sub(r'\n+', '\n', text)
    return text.strip()


def extract_problem_number(title):
    """从标题中提取题号"""
    match = re.search(r'^(\d+)\.\s', title.strip())
    if match:
        return match.group(1)
    return None


def safe_click_element(driver, element_name, timeout=10):
    """安全点击元素，使用多种策略"""
    print(f"[进程 {os.getpid()}] 🖱️ 尝试点击 {element_name}...")

    if element_name == "题目描述":
        selectors = [
            "//div[@id='description_tab']",
            "//div[contains(@class, 'flexlayout__tab_button') and contains(., '题目描述')]",
            "//div[text()='题目描述']",
            "//div[contains(text(), '题目描述')]"
        ]
        content_selector = "div[data-track-load='description_content']"
    elif element_name == "题解":
        selectors = [
            "//div[@id='solutions_tab']",
            "//div[contains(@class, 'flexlayout__tab_button') and contains(., '题解')]",
            "//div[text()='题解']",
            "//div[contains(text(), '题解')]"
        ]
        content_selector = "div.break-words"
    else:
        print(f"[进程 {os.getpid()}] ❌ 未知元素类型: {element_name}")
        return False

    for selector in selectors:
        try:
            element = WebDriverWait(driver, timeout).until(
                EC.element_to_be_clickable((By.XPATH, selector))
            )

            driver.execute_script("arguments[0].scrollIntoView({block: 'center', behavior: 'smooth'});", element)
            driver.execute_script("arguments[0].click();", element)

            # 等待对应内容加载
            try:
                WebDriverWait(driver, 8).until(
                    EC.presence_of_element_located((By.CSS_SELECTOR, content_selector))
                )
                print(f"[进程 {os.getpid()}] ✅ {element_name} 内容加载完成")
                return True
            except TimeoutException:
                print(f"[进程 {os.getpid()}] ⚠️ {element_name} 内容未加载，但点击操作完成")
                return True

        except Exception as e:
            continue

    print(f"[进程 {os.getpid()}] ❌ 所有选择器都失败了")
    return False


def append_to_json_file(output_file, data):
    """线程安全的增量保存数据到JSON文件"""
    lock = multiprocessing.Lock()
    with lock:
        try:
            existing_data = []
            if os.path.exists(output_file):
                with open(output_file, 'r', encoding='utf-8') as f:
                    try:
                        existing_data = json.load(f)
                        if not isinstance(existing_data, list):
                            existing_data = []
                    except json.JSONDecodeError:
                        existing_data = []

            existing_data.append(data)

            with open(output_file, 'w', encoding='utf-8') as f:
                json.dump(existing_data, f, ensure_ascii=False, indent=2)

            print(f"[进程 {os.getpid()}] ✅ 数据已保存到: {output_file}")
            return True
        except Exception as e:
            print(f"[进程 {os.getpid()}] ❌ 保存JSON文件失败: {e}")
            return False


def is_problem_exists(output_file, problem_number):
    """检查题目是否已存在 - 进程安全版本"""
    lock = multiprocessing.Lock()
    with lock:
        if not os.path.exists(output_file):
            return False

        try:
            with open(output_file, 'r', encoding='utf-8') as f:
                existing_data = json.load(f)
                if not isinstance(existing_data, list):
                    return False

                pattern = rf'"题目：{problem_number}\.'
                for item in existing_data:
                    if 'input' in item and re.search(pattern, item['input']):
                        return True
        except Exception:
            return False

        return False


def format_time(seconds):
    """格式化时间显示"""
    if seconds < 60:
        return f"{seconds:.1f}秒"
    elif seconds < 3600:
        return f"{seconds / 60:.1f}分钟"
    else:
        return f"{seconds / 3600:.1f}小时"


def process_single_url(url_info, output_file="./datasets/leetcode/solutions.json"):
    """处理单个URL - 多进程工作函数"""
    pid = os.getpid()
    start_time = time.time()
    print(f"[进程 {pid}] 🚀 开始处理: {url_info['title']}")

    # 提取题号并检查是否已存在
    problem_number = extract_problem_number(url_info['title'])
    if problem_number:
        if is_problem_exists(output_file, problem_number):
            process_time = time.time() - start_time
            print(f"[进程 {pid}] ⏭️ 题目 {problem_number} 已存在，跳过处理 (用时: {process_time:.2f}秒)")
            return "skipped"

    driver = None
    try:
        # 确保输出目录存在
        os.makedirs(os.path.dirname(output_file), exist_ok=True)

        # 每个进程创建自己的driver实例
        driver = setup_driver()

        # 访问URL
        page_start_time = time.time()
        driver.get(url_info['url'])
        WebDriverWait(driver, 10).until(
            EC.presence_of_element_located((By.TAG_NAME, "body"))
        )
        page_load_time = time.time() - page_start_time
        print(f"[进程 {pid}] ⏱️ 页面加载时间: {page_load_time:.2f}秒")

        # 点击题目描述
        if not safe_click_element(driver, "题目描述"):
            return "failed"

        # 获取题目内容
        problem_content = ""
        try:
            problem_element = WebDriverWait(driver, 15).until(
                EC.presence_of_element_located((By.CSS_SELECTOR, "div[data-track-load='description_content']"))
            )
            problem_html = driver.execute_script("return arguments[0].innerHTML;", problem_element)
            soup = BeautifulSoup(problem_html, 'html.parser')
            problem_content = soup.get_text(separator='\n').strip()
            problem_content = minimal_safe_clean(problem_content)
            print(f"[进程 {pid}] ✅ 题目内容获取成功，长度: {len(problem_content)} 字符")
        except Exception as e:
            print(f"[进程 {pid}] ❌ 获取题目内容失败: {e}")
            problem_content = "获取题目内容失败"

        # 点击题解
        if not safe_click_element(driver, "题解"):
            return "failed"

        # 获取题解内容
        solution_content = ""
        try:
            solution_element = WebDriverWait(driver, 10).until(
                EC.presence_of_element_located((By.CSS_SELECTOR, "div.break-words"))
            )
            solution_content = solution_element.text
            solution_content = minimal_safe_clean(solution_content)
            print(f"[进程 {pid}] ✅ 题解内容获取成功，长度: {len(solution_content)} 字符")
        except Exception as e:
            print(f"[进程 {pid}] ❌ 获取题解内容失败: {e}")
            solution_content = "获取题解内容失败"

        # 构建JSON数据
        json_data = {
            "instruction": "请解析以下算法题解，理解解题思路和代码实现：",
            "input": f"题目：{url_info['title']}\n\n{problem_content}",
            "output": solution_content
        }

        # 保存数据
        if append_to_json_file(output_file, json_data):
            process_time = time.time() - start_time
            print(f"[进程 {pid}] ✅ 处理完成，总用时: {process_time:.2f}秒")
            return "success"
        else:
            return "failed"

    except Exception as e:
        print(f"[进程 {pid}] ❌ 处理失败: {e}")
        return "failed"
    finally:
        if driver:
            driver.quit()
        total_time = time.time() - start_time
        print(f"[进程 {pid}] 🎯 处理完成: {url_info['title']} (总用时: {total_time:.2f}秒)")


class TimeTracker:
    """时间跟踪器"""

    def __init__(self):
        self.start_time = time.time()
        self.processed_count = 0
        self.success_count = 0
        self.skip_count = 0
        self.fail_count = 0
        self.lock = multiprocessing.Lock()

    def update_stats(self, result):
        """更新统计信息"""
        with self.lock:
            self.processed_count += 1
            if result == "success":
                self.success_count += 1
            elif result == "skipped":
                self.skip_count += 1
            elif result == "failed":
                self.fail_count += 1

    def get_progress_info(self, total_urls):
        """获取进度信息"""
        elapsed_time = time.time() - self.start_time
        processed = self.processed_count

        if processed > 0:
            avg_time_per_url = elapsed_time / processed
            remaining_urls = total_urls - processed
            estimated_remaining = avg_time_per_url * remaining_urls

            progress_percent = (processed / total_urls) * 100

            return {
                'elapsed': elapsed_time,
                'processed': processed,
                'total': total_urls,
                'progress_percent': progress_percent,
                'avg_time': avg_time_per_url,
                'estimated_remaining': estimated_remaining,
                'success': self.success_count,
                'skip': self.skip_count,
                'fail': self.fail_count
            }
        return None


def process_urls_with_multiprocessing(start_problem_number=2454, max_workers=None):
    """使用多进程处理URL列表"""
    url_file = "./datasets/leetcode/missing_urls.json"
    output_file = "./datasets/leetcode/solutions.json"

    # 加载URL列表
    urls = load_urls_from_file(url_file)
    if not urls:
        print("❌ 没有找到URL数据，请先运行收集功能")
        return

    # 找到起始索引
    start_index = find_start_index(urls, start_problem_number)
    urls_to_process = urls[start_index:]
    total_urls = len(urls_to_process)

    print(f"🚀 开始多进程处理")
    print(f"📊 总共 {len(urls)} 个URL，从索引 {start_index} 开始处理 {total_urls} 个")
    print(f"🔧 使用 {max_workers or multiprocessing.cpu_count()} 个进程")

    # 初始化时间跟踪器
    time_tracker = TimeTracker()

    # 统计结果
    results = {
        'success': 0,
        'skipped': 0,
        'failed': 0
    }

    # 创建进程池
    with ProcessPoolExecutor(max_workers=max_workers) as executor:
        # 提交所有任务
        future_to_url = {
            executor.submit(process_single_url, url_info, output_file): url_info
            for url_info in urls_to_process
        }

        # 处理完成的任务
        for i, future in enumerate(as_completed(future_to_url)):
            url_info = future_to_url[future]
            try:
                result = future.result()
                results[result] += 1
                time_tracker.update_stats(result)

                # 每处理10个URL显示一次进度
                if (i + 1) % 10 == 0 or (i + 1) == total_urls:
                    progress_info = time_tracker.get_progress_info(total_urls)
                    if progress_info:
                        print(f"\n📈 进度报告 ({i + 1}/{total_urls})")
                        print(f"⏱️  已用时间: {format_time(progress_info['elapsed'])}")
                        print(f"📊 完成进度: {progress_info['progress_percent']:.1f}%")
                        print(f"⚡ 平均每个URL: {progress_info['avg_time']:.1f}秒")
                        print(f"⏳ 预计剩余: {format_time(progress_info['estimated_remaining'])}")
                        print(
                            f"✅ 成功: {progress_info['success']} | ⏭️ 跳过: {progress_info['skip']} | ❌ 失败: {progress_info['fail']}")
                        print("-" * 50)

            except Exception as e:
                print(f"❌ 处理 {url_info['title']} 时发生异常: {e}")
                results['failed'] += 1
                time_tracker.update_stats("failed")

    # 输出最终统计
    total_time = time.time() - time_tracker.start_time
    print(f"\n🎉 多进程处理完成!")
    print(f"⏱️  总用时: {format_time(total_time)}")
    print(f"✅ 成功: {results['success']}")
    print(f"⏭️  跳过: {results['skipped']}")
    print(f"❌ 失败: {results['failed']}")
    print(f"📊 总计处理: {total_urls} 个URL")
    print(f"⚡ 平均每个URL: {total_time / total_urls:.1f}秒")

    # 性能对比
    expected_single_thread_time = (total_time / total_urls) * total_urls * max_workers
    speedup = expected_single_thread_time / total_time if total_time > 0 else 1
    print(f"🚀 相比单线程加速: {speedup:.1f}x")


def load_urls_from_file(filename="./datasets/leetcode/missing_urls.json"):
    """从文件加载URL列表"""
    try:
        if os.path.exists(filename):
            with open(filename, 'r', encoding='utf-8') as f:
                urls = json.load(f)
            print(f"✅ 从文件加载了 {len(urls)} 个URL")
            return urls
        else:
            print(f"❌ URL文件不存在: {filename}")
            return []
    except Exception as e:
        print(f"❌ 加载URL文件失败: {e}")
        return []


def find_start_index(urls, start_problem_number=2454):
    """找到从指定题号开始的索引位置"""
    for i, url_info in enumerate(urls):
        problem_number = extract_problem_number(url_info['title'])
        if problem_number and int(problem_number) == start_problem_number:
            print(f"🔍 找到起始位置: 索引 {i}, 题号 {problem_number} - {url_info['title']}")
            return i

    for i, url_info in enumerate(urls):
        problem_number = extract_problem_number(url_info['title'])
        if problem_number and int(problem_number) >= start_problem_number:
            print(f"🔍 找到起始位置: 索引 {i}, 题号 {problem_number} - {url_info['title']}")
            return i

    print(f"⚠️ 未找到题号 {start_problem_number} 或之后的题目，将从开始处理")
    return 0


def main():
    """主函数"""
    print("🚀 LeetCode题解采集器 - 多进程版本 (带时间统计)")
    print("=" * 50)

    # 检查URL文件是否存在
    url_file = "./datasets/leetcode/missing_urls.json"

    if os.path.exists(url_file):
        print("📁 检测到URL文件，开始多进程处理...")

        # 配置参数
        start_problem_number = 2588  # 起始题号
        max_workers = 2  # 进程数，可根据CPU核心数调整

        print(f"🔧 配置参数:")
        print(f"  起始题号: {start_problem_number}")
        print(f"  进程数: {max_workers}")
        print(f"  CPU核心数: {multiprocessing.cpu_count()}")

        process_urls_with_multiprocessing(
            start_problem_number=start_problem_number,
            max_workers=max_workers
        )
    else:
        print("❌ 未检测到URL文件，请先运行收集功能")
        print("💡 提示: 需要先运行单进程版本收集URL，然后再使用多进程处理")


if __name__ == "__main__":
    # 注意：在Windows上运行多进程时需要保护入口点
    if os.name == 'nt':  # Windows
        multiprocessing.freeze_support()

    main()
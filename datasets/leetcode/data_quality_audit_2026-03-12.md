# datasets/leetcode 数据体检报告（2026-03-12）

## 1. 总览

- 目录文件数：30
- `solutions*.json*` 相关文件：23
- 当前主文件：
  - `solutions.json` 仅 4 条（明显不是可训练主数据）
  - `solutions_sorted.json` 791 条

## 2. 关键发现

## 2.1 JSON 完整性问题

- 以下文件存在尾逗号导致 JSON 解析失败（可修复）：
  - `solutions.json.backup_20251029_003210`
  - `solutions.json.backup_20251029_004135`
  - `solutions.json.backup_20251029_004201`
  - `solutions.json.backup_repair`（仍有尾逗号问题）

## 2.2 内容完整性问题（以 `solutions_sorted.json` 为准）

- 总记录：791
- `output` 非空：492
- `output` 为空：299
- 非空但很短（<=100 字符）：5（多为失败占位文本）

结论：当前可直接用于推荐/训练的“有效题解”大约 **492 条**。

## 2.3 覆盖率问题

- `urls.json` 共 2590 行，其中可提取数值题号约 2361
- `solutions_sorted.json` 覆盖题号约 767
- 题号覆盖率约 **32.49%**

说明：当前题解覆盖不足，不建议直接作为最终推荐题库。

## 2.4 备份可挽回空间

- 对所有 `solutions*.json*` 做“宽松解析 + 合并去重 + 保留最长 output”后：
  - 总题目键：864
  - 非空 output：512
  - 有效 output（长度>=30）：506
- 相比 `solutions_sorted.json` 非空 492，最多只多约 **20 条**。

结论：仅靠备份合并，提升有限；若目标是高覆盖，仍需继续爬取。

## 2.5 文本编码

- 抽样检查显示 `solutions_sorted.json` 中文可正常读取。
- PowerShell 直接 `Get-Content` 显示的“乱码样式”主要是终端编码问题，不代表文件本体损坏。

---

## 3. 是否需要“重新清洗”

需要，但分两层：

1. **必须做的轻清洗（现在就可做）**
- 修复尾逗号损坏 JSON。
- 合并备份去重（同题保留更长 output）。
- 过滤空 output 和失败占位文本。
- 统一空白字符（如 `NBSP`）与换行。

2. **必须做的补数（否则覆盖不足）**
- 继续爬取/补抓缺失题解（当前覆盖仅约 1/3）。

---

## 4. 建议的清洗基线

- 不要用 `solutions.json`（当前只有 4 条）。
- 可先以 `solutions_sorted.json` 为主，再从较新备份补齐增量。
- 最终目标文件建议拆两份：
  - `solutions_merged_raw.json`（合并后原始）
  - `solutions_cleaned.json`（过滤+规范化后）

---

## 5. 下一步建议

1. 先执行“轻清洗”生成 `solutions_cleaned.json`。  
2. 再根据 `missing_urls.json` 做增量爬取。  
3. 爬取完成后再次跑体检，目标：
- 非空 output 占比 >= 80%
- 题号覆盖率 >= 70%


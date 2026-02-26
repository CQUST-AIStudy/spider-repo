"""Report builder: generates HTML-based PDF reports using WeasyPrint."""
from weasyprint import HTML


def build_report_html(student_name: str, scores: list[dict],
                      evidence_blocks: list[dict], total_score: float) -> str:
    """Build HTML report content."""
    rows = ""
    for s in scores:
        status_badge = ""
        if s.get("status") == "NEED_MORE_EVIDENCE":
            status_badge = '<span style="color:orange;font-weight:bold">⚠ 证据不足</span>'

        evidence_refs = ", ".join(s.get("evidence_ids", []))
        rows += f"""
        <tr>
            <td>{s.get('dimension_name', '')}</td>
            <td>{s.get('score', 'N/A')} / {s.get('max_score', '')}</td>
            <td>{s.get('weight', '')}%</td>
            <td>{s.get('comment', '')} {status_badge}</td>
            <td style="font-size:0.8em">{evidence_refs}</td>
        </tr>"""

    evidence_section = ""
    for eb in evidence_blocks[:20]:  # Limit to 20 evidence blocks in report
        content_preview = (eb.get("content", "") or "")[:300]
        evidence_section += f"""
        <div style="border:1px solid #ddd;padding:8px;margin:4px 0;border-radius:4px">
            <strong>[{eb.get('evidence_id', '')}]</strong>
            <span style="color:#666">类型: {eb.get('kind', '')} | 页码: {eb.get('page', '')}</span>
            <pre style="white-space:pre-wrap;font-size:0.85em;background:#f5f5f5;padding:4px;margin:4px 0">{content_preview}</pre>
        </div>"""

    return f"""<!DOCTYPE html>
<html><head><meta charset="utf-8">
<style>
  body {{ font-family: "Microsoft YaHei", sans-serif; margin: 20px; font-size: 12px; }}
  h1 {{ color: #333; font-size: 18px; }}
  h2 {{ color: #555; font-size: 14px; border-bottom: 1px solid #ccc; padding-bottom: 4px; }}
  table {{ width: 100%; border-collapse: collapse; margin: 10px 0; }}
  th, td {{ border: 1px solid #ddd; padding: 6px 8px; text-align: left; }}
  th {{ background: #f0f0f0; }}
  .total {{ font-size: 16px; font-weight: bold; color: #1a73e8; }}
</style></head><body>
<h1>实验报告批改结果</h1>
<p><strong>学生:</strong> {student_name or '未知'}</p>
<p class="total">总分: {total_score}</p>

<h2>评分详情</h2>
<table>
  <tr><th>评分维度</th><th>得分</th><th>权重</th><th>评语</th><th>证据引用</th></tr>
  {rows}
</table>

<h2>证据材料</h2>
{evidence_section}

<p style="color:#999;font-size:0.8em;margin-top:20px">本报告由AI辅助批改系统自动生成</p>
</body></html>"""


def generate_pdf(student_name: str, scores: list[dict],
                 evidence_blocks: list[dict], total_score: float) -> bytes:
    """Generate a PDF report and return bytes."""
    html_content = build_report_html(student_name, scores, evidence_blocks, total_score)
    return HTML(string=html_content).write_pdf()

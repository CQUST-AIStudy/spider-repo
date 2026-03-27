import {
  AlignmentType,
  BorderStyle,
  Document,
  ImageRun,
  Packer,
  Paragraph,
  Table,
  TableCell,
  TableRow,
  TextRun,
  VerticalAlign,
  WidthType,
} from 'docx'
import { saveAs } from 'file-saver'
import { convertCommentToImageForDocx } from './commentToImage'

const thickBorder = { style: BorderStyle.THICK, size: 8, color: '000000' }
const thinBorder = { style: BorderStyle.SINGLE, size: 2, color: '000000' }
const noneBorder = { style: BorderStyle.NONE, size: 0, color: 'FFFFFF' }

function text(value) {
  return value == null || value === '' ? '' : String(value)
}

function paragraph(content, options = {}) {
  return new Paragraph({
    alignment: options.alignment,
    spacing: options.spacing,
    indent: options.indent,
    children: [
      new TextRun({
        text: text(content),
        font: options.font || 'SimSun',
        size: options.size || 24,
        bold: options.bold || false,
        color: options.color,
      }),
    ],
  })
}

function splitLines(content, fallback = '待补充。') {
  const source = text(content).trim() || fallback
  return source.split(/\r?\n/).filter(Boolean)
}

export class DocxGenerator {
  async generateReport(data) {
    return this.generateStandardReport(data)
  }

  async generateStandardReport(data = {}) {
    const teacherCommentBlocks = data.teacherComment
      ? await this._generateTeacherCommentImage(text(data.teacherComment))
      : []

    const doc = new Document({
      sections: [
        {
          properties: {},
          children: [
            new Paragraph({
              alignment: AlignmentType.CENTER,
              spacing: { after: 80 },
              children: [new TextRun({ text: '重庆科技大学', font: 'SimHei', size: 32, bold: true })],
            }),
            new Paragraph({
              alignment: AlignmentType.CENTER,
              spacing: { after: 220 },
              children: [new TextRun({ text: '上机实验报告', font: 'SimHei', size: 32, bold: true })],
            }),
            ...(data.score || data.teacherComment ? [this._createTeacherReviewTable(data, teacherCommentBlocks)] : []),
            this._createInfoTable(data),
            ...this._createSectionTables(data),
          ],
        },
      ],
    })

    return Packer.toBlob(doc)
  }

  _createTeacherReviewTable(data, teacherCommentBlocks) {
    const reviewChildren = [
      paragraph(`教师评分：${text(data.score) || '待评分'}`, {
        font: 'SimHei',
        size: 28,
        bold: true,
        color: 'B91C1C',
      }),
    ]

    if (teacherCommentBlocks.length > 0) {
      reviewChildren.push(
        paragraph('教师评语', {
          font: 'SimHei',
          size: 22,
          bold: true,
          color: '991B1B',
          spacing: { before: 120, after: 120 },
        }),
      )
      reviewChildren.push(...teacherCommentBlocks)
    }

    return new Table({
      width: { size: 100, type: WidthType.PERCENTAGE },
      margins: { top: 120, bottom: 120, left: 120, right: 120 },
      borders: {
        top: { style: BorderStyle.SINGLE, size: 4, color: 'E5A5A5' },
        bottom: { style: BorderStyle.SINGLE, size: 4, color: 'E5A5A5' },
        left: { style: BorderStyle.SINGLE, size: 4, color: 'E5A5A5' },
        right: { style: BorderStyle.SINGLE, size: 4, color: 'E5A5A5' },
        insideHorizontal: { style: BorderStyle.NONE, size: 0, color: 'FFFFFF' },
        insideVertical: { style: BorderStyle.NONE, size: 0, color: 'FFFFFF' },
      },
      rows: [
        new TableRow({
          children: [
            new TableCell({
              verticalAlign: VerticalAlign.CENTER,
              shading: { fill: 'FFF5F5' },
              children: reviewChildren,
            }),
          ],
        }),
      ],
    })
  }

  _createInfoTable(data) {
    const rows = [
      ['课程名称', text(data.courseName) || '课程待补充', '实验项目', text(data.experimentName) || '实验待补充'],
      ['机房名称', text(data.labName) || '实验机房', '上机时间', text(data.labTime) || new Date().toLocaleDateString()],
      ['指导教师', text(data.teacherName) || '指导教师', '上机成绩', text(data.score)],
      ['学生姓名', text(data.studentName), '学号', text(data.studentId), '专业班级', text(data.className)],
    ]

    return new Table({
      width: { size: 100, type: WidthType.PERCENTAGE },
      borders: {
        top: thickBorder,
        bottom: thickBorder,
        left: thickBorder,
        right: thickBorder,
        insideHorizontal: thinBorder,
        insideVertical: thinBorder,
      },
      rows: [
        new TableRow({
          children: [
            this._labelCell(rows[0][0]),
            this._valueCell(rows[0][1], 2),
            this._labelCell(rows[0][2]),
            this._valueCell(rows[0][3], 2),
          ],
        }),
        new TableRow({
          children: [
            this._labelCell(rows[1][0]),
            this._valueCell(rows[1][1], 2),
            this._labelCell(rows[1][2]),
            this._valueCell(rows[1][3], 2),
          ],
        }),
        new TableRow({
          children: [
            this._labelCell(rows[2][0]),
            this._valueCell(rows[2][1], 2),
            this._labelCell(rows[2][2]),
            this._valueCell(rows[2][3], 2),
          ],
        }),
        new TableRow({
          children: [
            this._labelCell(rows[3][0]),
            this._valueCell(rows[3][1], 1),
            this._labelCell(rows[3][2]),
            this._valueCell(rows[3][3], 1),
            this._labelCell(rows[3][4]),
            this._valueCell(rows[3][5], 1),
          ],
        }),
      ],
    })
  }

  _labelCell(content) {
    return new TableCell({
      verticalAlign: VerticalAlign.CENTER,
      children: [paragraph(content, { alignment: AlignmentType.CENTER })],
    })
  }

  _valueCell(content, colSpan = 1) {
    return new TableCell({
      columnSpan: colSpan,
      verticalAlign: VerticalAlign.CENTER,
      children: [paragraph(content, { alignment: AlignmentType.CENTER })],
    })
  }

  _createSectionTables(data) {
    const sections = [
      ['一、实验目的和要求', data.purpose],
      ['二、实验环境', data.requirements],
      ['三、实验内容', data.tasks],
      ['四、实验步骤与关键代码', data.steps],
      ['五、实验结果与问题分析', data.results],
      ['六、实验总结', data.summary],
    ]

    return sections.map(([title, content]) =>
      new Table({
        width: { size: 100, type: WidthType.PERCENTAGE },
        borders: {
          top: { ...thinBorder, size: 4 },
          bottom: { ...thinBorder, size: 4 },
          left: thickBorder,
          right: thickBorder,
          insideHorizontal: noneBorder,
          insideVertical: noneBorder,
        },
        margins: { top: 120, bottom: 120, left: 120, right: 120 },
        rows: [
          new TableRow({
            children: [
              new TableCell({
                children: [
                  paragraph(title, { bold: true }),
                  ...splitLines(content).map(line =>
                    paragraph(line, { spacing: { after: 80 }, indent: { firstLine: 420 } }),
                  ),
                ],
              }),
            ],
          }),
        ],
      }),
    )
  }

  async _generateTeacherCommentImage(teacherComment) {
    try {
      const imageData = await convertCommentToImageForDocx(teacherComment, {
        width: 900,
        fontSize: 28,
        fontFamily: 'ZiYouLangManTi',
        textColor: '#C81E1E',
        backgroundColor: '#FFF8F8',
        watermark: '教师评语',
      })

      if (!imageData?.buffer) {
        return splitLines(teacherComment).map(line =>
          paragraph(line, {
            font: 'KaiTi',
            size: 28,
            color: 'C81E1E',
            indent: { firstLine: 420 },
          }),
        )
      }

      return [
        new Paragraph({
          spacing: { before: 60, after: 60 },
          children: [
            new ImageRun({
              data: imageData.buffer,
              transformation: {
                width: Math.min(imageData.width, 520),
                height: Math.round((Math.min(imageData.width, 520) / imageData.width) * imageData.height),
              },
            }),
          ],
        }),
      ]
    } catch {
      return splitLines(teacherComment).map(line =>
        paragraph(line, {
          font: 'KaiTi',
          size: 28,
          color: 'C81E1E',
          indent: { firstLine: 420 },
        }),
      )
    }
  }

  static async downloadReport(blob, filename) {
    saveAs(blob, filename)
  }
}

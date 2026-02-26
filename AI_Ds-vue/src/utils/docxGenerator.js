import { 
  Document, 
  Packer, 
  Paragraph, 
  TextRun, 
  HeadingLevel, 
  Table, 
  TableRow, 
  TableCell, 
  WidthType, 
  AlignmentType, 
  BorderStyle, 
  VerticalAlign, 
  ImageRun 
} from 'docx'
import { convertCommentToImageForDocx } from './commentToImage'
import { saveAs } from 'file-saver'

export class DocxGenerator {
  constructor(template) {
    this.template = template
  }

  async generateReport(data) {
    try {
      // 创建文档
      const doc = new Document({
        sections: [{
          properties: {},
          children: this._generateDocumentContent(data)
        }]
      })

      // 生成 blob
      const blob = await Packer.toBlob(doc)
      return blob
    } catch (error) {
      console.error('生成报告失败:', error)
      throw new Error(`生成报告失败: ${error.message}`)
    }
  }
  // 生成标准格式报告（来自wordGenerator.js的整合功能）
  async generateStandardReport(data) {
    try {
      // 如果存在教师评语，预先处理评语生成图片数据
      let teacherCommentParagraphs = [];
      if (data.teacherComment) {
        teacherCommentParagraphs = await this._generateTeacherCommentImage(data.teacherComment);
      }
      // 定义统一边框样式 - 加粗的黑色边框
      const thickBorders = {
        top: { style: BorderStyle.THICK, size: 10, color: '#000000' },
        bottom: { style: BorderStyle.THICK, size: 10, color: '#000000' },
        left: { style: BorderStyle.THICK, size: 10, color: '#000000' },
        right: { style: BorderStyle.THICK, size: 10, color: '#000000' },
      };

      // 定义内部分隔线边框样式 - 适当加粗
      const insideBorders = {
        style: BorderStyle.SINGLE,
        size: 1,
        color: '#000000'
      };

      // 定义无边框样式
      const noBorders = {
        top: { style: BorderStyle.NONE },
        bottom: { style: BorderStyle.NONE },
        left: { style: BorderStyle.NONE },
        right: { style: BorderStyle.NONE },
      };

      const doc = new Document({
        sections: [{
          properties: {},
          children: [
            // 标题
            new Paragraph({
              children: [
                new TextRun({
                  text: "……大学",
                  font: "黑体",
                  size: 32, // 三号字体 = 16pt = 32半磅
                })
              ],
              alignment: AlignmentType.CENTER,
              spacing: {
                after: 0, // 减少段落后的间距
                before: 200 // 标题上方保留一些间距
              }
            }),
            new Paragraph({
              children: [
                new TextRun({
                  text: "上机实验报告（上机操作类）",
                  font: "黑体",
                  size: 32,
                })
              ],
              alignment: AlignmentType.CENTER,
              spacing: {
                after: 200, // 标题下方保留一些间距
                before: 0  // 减少段落前的间距
              }
            }),

            // 创建一个主容器，不设边框
            new Table({
              width: {
                size: 9000,
                type: 'dxa',
              },
              borders: noBorders,
              rows: [
                new TableRow({
                  children: [
                    new TableCell({
                      width: {
                        size: 9000,
                        type: 'dxa',
                      },
                      children: [
                        // 基本信息表格 - 应用加粗边框
                        new Table({
                          width: {
                            size: 9000,
                            type: 'dxa',
                          },
                          borders: {
                            top: thickBorders.top,
                            bottom: thickBorders.bottom,
                            left: thickBorders.left,
                            right: thickBorders.right,
                            insideHorizontal: insideBorders,
                            insideVertical: insideBorders,
                          },
                          // margins: {
                          //   top: 120,
                          //   bottom: 120,
                          //   left: 120,
                          //   right: 120,
                          // },
                          rows: [
                            // 第一行：课程名称（1+2列）| 实验项目（1+2列）
                            new TableRow({

                              height: {
                                value: 567, // 1cm = 567 / 20 = 28.35 points
                                rule: 'exact' // 固定值
                              },
                              children: [
                                // 课程名称
                                new TableCell({
                                  children: [
                                    new Paragraph({
                                      children: [
                                        new TextRun({
                                          text: "课程名称",
                                          font: "宋体",
                                          size: 24, // 小四号字体 = 12pt = 24半磅
                                        })
                                      ],
                                      alignment: AlignmentType.CENTER,
                                    })
                                  ],
                                  width: {
                                    size: 1134,  // 2cm 对应的 dxa 值
                                    type: 'dxa',
                                  },
                                  verticalAlign: VerticalAlign.CENTER,
                                }),
                                // 课程名称值（占2列）
                                new TableCell({

                                  children: [
                                    new Paragraph({
                                      children: [
                                        new TextRun({
                                          text: data.courseName || "数据结构",
                                          font: "宋体",
                                          size: 24,
                                        })
                                      ],
                                      alignment: AlignmentType.CENTER,
                                    })
                                  ],
                                  columnSpan: 2,
                                  width: {
                                    size: 2835,  // 5cm 对应的 dxa 值
                                    type: 'dxa',
                                  },
                                  verticalAlign: VerticalAlign.CENTER,
                                }),
                                // 实验项目
                                new TableCell({
                                  children: [
                                    new Paragraph({
                                      children: [
                                        new TextRun({
                                          text: "实验项目",
                                          font: "宋体",
                                          size: 24,
                                        })
                                      ],
                                      alignment: AlignmentType.CENTER,
                                    })
                                  ],
                                  width: {
                                    size: 1134,  // 2cm 对应的 dxa 值
                                    type: 'dxa',
                                  },
                                  verticalAlign: VerticalAlign.CENTER,
                                }),
                                // 实验项目值（占2列）
                                new TableCell({
                                  children: [
                                    new Paragraph({
                                      children: [
                                        new TextRun({
                                          text: data.experimentName || "数据结构实验",
                                          font: "宋体",
                                          size: 24,
                                        })
                                      ],
                                      alignment: AlignmentType.CENTER,
                                    })
                                  ],
                                  columnSpan: 2,
                                  width: {
                                    size: 2835,  // 5cm 对应的 dxa 值
                                    type: 'dxa',
                                  },
                                  verticalAlign: VerticalAlign.CENTER,
                                }),
                              ],
                            }),

                            // 第二行：机房名称（1+2列）| 上机时间（1+2列）
                            new TableRow({
                              height: {
                                value: 567,
                                rule: 'exact'
                              },
                              children: [
                                // 机房名称
                                new TableCell({
                                  children: [
                                    new Paragraph({
                                      children: [
                                        new TextRun({
                                          text: "机房名称",
                                          font: "宋体",
                                          size: 24,
                                        })
                                      ],
                                      alignment: AlignmentType.CENTER,
                                    })
                                  ],
                                  width: {
                                    size: 1134,  // 2cm 对应的 dxa 值
                                    type: 'dxa',
                                  },
                                  verticalAlign: VerticalAlign.CENTER,
                                }),
                                // 机房名称值（占2列）
                                new TableCell({
                                  children: [
                                    new Paragraph({
                                      children: [
                                        new TextRun({
                                          text: data.labName || "计算机实验室",
                                          font: "宋体",
                                          size: 24,
                                        })
                                      ],
                                      alignment: AlignmentType.CENTER,
                                    })
                                  ],
                                  width: {
                                    size: 2835,  // 5cm 对应的 dxa 值
                                    type: 'dxa',
                                  },
                                  columnSpan: 2,
                                  verticalAlign: VerticalAlign.CENTER,
                                }),
                                // 上机时间
                                new TableCell({
                                  children: [
                                    new Paragraph({
                                      children: [
                                        new TextRun({
                                          text: "上机时间",
                                          font: "宋体",
                                          size: 24,
                                        })
                                      ],
                                      alignment: AlignmentType.CENTER,
                                    })
                                  ],
                                  width: {
                                    size: 1134,  // 2cm 对应的 dxa 值
                                    type: 'dxa',
                                  },
                                  verticalAlign: VerticalAlign.CENTER,
                                }),
                                // 上机时间值（占2列）
                                new TableCell({
                                  children: [
                                    new Paragraph({
                                      children: [
                                        new TextRun({
                                          text: data.labTime || new Date().toLocaleDateString(),
                                          font: "宋体",
                                          size: 24,
                                        })
                                      ],
                                      alignment: AlignmentType.CENTER,
                                    })
                                  ],
                                  width: {
                                    size: 2835,  // 5cm 对应的 dxa 值
                                    type: 'dxa',
                                  },
                                  columnSpan: 2,
                                  verticalAlign: VerticalAlign.CENTER,
                                }),
                              ],
                            }),

                            // 第三行：指导老师（1+2列）| 上机成绩（1+2列）
                            new TableRow({
                              height: {
                                value: 567,
                                rule: 'exact'
                              },
                              children: [
                                // 指导老师
                                new TableCell({
                                  children: [
                                    new Paragraph({
                                      children: [
                                        new TextRun({
                                          text: "指导老师",
                                          font: "宋体",
                                          size: 24,
                                        })
                                      ],
                                      alignment: AlignmentType.CENTER,
                                    })
                                  ],
                                  width: {
                                    size: 1134,  // 2cm 对应的 dxa 值
                                    type: 'dxa',
                                  },
                                  verticalAlign: VerticalAlign.CENTER,
                                }),
                                // 指导老师值（占2列）
                                new TableCell({
                                  children: [
                                    new Paragraph({
                                      children: [
                                        new TextRun({
                                          text: data.teacherName || "未知",
                                          font: "宋体",
                                          size: 24,
                                        })
                                      ],
                                      alignment: AlignmentType.CENTER,
                                    })
                                  ],
                                  width: {
                                    size: 2835,  // 5cm 对应的 dxa 值
                                    type: 'dxa',
                                  },
                                  columnSpan: 2,
                                  verticalAlign: VerticalAlign.CENTER,
                                }),
                                // 上机成绩
                                new TableCell({
                                  children: [
                                    new Paragraph({
                                      children: [
                                        new TextRun({
                                          text: "上机成绩",
                                          font: "宋体",
                                          size: 24,
                                        })
                                      ],
                                      alignment: AlignmentType.CENTER,
                                    })
                                  ],
                                  width: {
                                    size: 1134,  // 2cm 对应的 dxa 值
                                    type: 'dxa',
                                  },
                                  verticalAlign: VerticalAlign.CENTER,
                                }),
                                // 上机成绩值（占2列）
                                new TableCell({
                                  children: [
                                    new Paragraph({
                                      children: [
                                        new TextRun({
                                          text: data.score || "",
                                          font: "宋体",
                                          size: 24,
                                        })
                                      ],
                                      alignment: AlignmentType.CENTER,
                                    })
                                  ],
                                  width: {
                                    size: 2835,  // 5cm 对应的 dxa 值
                                    type: 'dxa',
                                  },
                                  columnSpan: 2,
                                  verticalAlign: VerticalAlign.CENTER,
                                }),
                              ],
                            }),

                            // 第四行：学生姓名（1+1列）| 学号（1+1列）| 专业班级（1+1列）
                            new TableRow({
                              height: {
                                value: 567,
                                rule: 'exact'
                              },
                              children: [
                                // 学生姓名
                                new TableCell({
                                  children: [
                                    new Paragraph({
                                      children: [
                                        new TextRun({
                                          text: "学生姓名",
                                          font: "宋体",
                                          size: 24,
                                        })
                                      ],
                                      alignment: AlignmentType.CENTER,
                                    })
                                  ],
                                  width: {
                                    size: 1134,
                                    type: 'dxa',
                                  },
                                  verticalAlign: VerticalAlign.CENTER,
                                }),
                                // 学生姓名值（占1列）
                                new TableCell({
                                  children: [
                                    new Paragraph({
                                      children: [
                                        new TextRun({
                                          text: data.studentName || "未知",
                                          font: "宋体",
                                          size: 24,
                                        })
                                      ],
                                      alignment: AlignmentType.CENTER,
                                    })
                                  ],
                                  width: {
                                    size: 1418,
                                    type: 'dxa',
                                  },
                                  verticalAlign: VerticalAlign.CENTER,
                                }),
                                // 学号
                                new TableCell({
                                  children: [
                                    new Paragraph({
                                      children: [
                                        new TextRun({
                                          text: "学号",
                                          font: "宋体",
                                          size: 24,
                                        })
                                      ],
                                      alignment: AlignmentType.CENTER,
                                    })
                                  ],
                                  width: {
                                    size: 1418,
                                    type: 'dxa',
                                  },
                                  verticalAlign: VerticalAlign.CENTER,
                                }),
                                // 学号值
                                new TableCell({
                                  children: [
                                    new Paragraph({
                                      children: [
                                        new TextRun({
                                          text: data.studentId || "未知学号",
                                          font: "宋体",
                                          size: 24,
                                        })
                                      ],
                                      alignment: AlignmentType.CENTER,
                                    })
                                  ],
                                  width: {
                                    size: 1134,
                                    type: 'dxa',
                                  },
                                  verticalAlign: VerticalAlign.CENTER,
                                }),
                                // 专业班级
                                new TableCell({
                                  children: [
                                    new Paragraph({
                                      children: [
                                        new TextRun({
                                          text: "专业班级",
                                          font: "宋体",
                                          size: 24,
                                        })
                                      ],
                                      alignment: AlignmentType.CENTER,
                                    })
                                  ],
                                  width: {
                                    size: 1418,
                                    type: 'dxa',
                                  },
                                  verticalAlign: VerticalAlign.CENTER,
                                }),
                                // 专业班级值
                                new TableCell({
                                  children: [
                                    new Paragraph({
                                      children: [
                                        new TextRun({
                                          text: data.className || "未知班级",
                                          font: "宋体",
                                          size: 24,
                                        })
                                      ],
                                      alignment: AlignmentType.CENTER,
                                    })
                                  ],
                                  width: {
                                    size: 1418,
                                    type: 'dxa',
                                  },
                                  verticalAlign: VerticalAlign.CENTER,
                                }),
                              ],
                            }),

                            // 一、上机操作目的和要求 - 带边框的表格
                            new TableRow({
                              height: {
                                value: 1134,
                                rule: 'atLeast'
                              },
                              children: [
                                new TableCell({
                                  width: {
                                    size: 7938,  // 14cm 对应的 dxa 值
                                    type: 'dxa',
                                  },
                                  columnSpan: 6,
                                  children: [
                                    new Paragraph({
                                      children: [
                                        new TextRun({
                                          text: "一、上机操作目的和要求",
                                          size: 24,
                                          font: "宋体",
                                          // bold: true,
                                        })
                                      ],
                                      spacing: {
                                        after: 0,
                                        before: 0,
                                        line: 360, // 1.5倍行距 (240 * 1.5 = 360)
                                        lineRule: 'exact'
                                      }
                                    }),
                                    // 将单个段落替换为多个段落，每行一个段落
                                    ...(data.purpose ? data.purpose.split('\n').map(line =>
                                      new Paragraph({
                                        children: [
                                          new TextRun({
                                            text: line,
                                            font: "宋体",
                                            size: 24,
                                          })
                                        ],
                                        spacing: {
                                          line: 360, // 1.5倍行距
                                          lineRule: 'exact'
                                        },
                                        indent: { firstLine: 480 }, // 首行缩进2字符
                                      })
                                    ) : [
                                      new Paragraph({
                                        children: [
                                          new TextRun({
                                            text: "实现顺序表的基本操作 实现链表的基本操作 完成示例应用程序 撰写实验报告分析性能",
                                            font: "宋体",
                                            size: 24,
                                          })
                                        ],
                                        spacing: {
                                          line: 360, // 1.5倍行距
                                          lineRule: 'exact'
                                        },
                                        indent: { firstLine: 480 }, // 首行缩进2字符
                                      })
                                    ]),
                                  ],
                                  // borders: {
                                  //   top: { style: BorderStyle.NONE },
                                  //   bottom: { style: BorderStyle.NONE },
                                  //   left: { style: BorderStyle.NONE },
                                  //   right: { style: BorderStyle.NONE },
                                  // },
                                }),
                              ],
                            }),



                            // 二、上机操作需要的软、硬件 - 带边框的表格
                            new TableRow({
                              height: {
                                value: 701,
                                rule: 'atLeast'
                              },
                              children: [
                                new TableCell({
                                  width: {
                                    size: 7938,  // 14cm 对应的 dxa 值
                                    type: 'dxa',
                                  },
                                  columnSpan: 6,
                                  children: [
                                    new Paragraph({
                                      children: [
                                        new TextRun({
                                          text: "二、上机操作需要的软、硬件",
                                          size: 24,
                                          font: "宋体",
                                          // bold: true,
                                        })
                                      ],
                                      spacing: {
                                        after: 0,
                                        before: 0,
                                        line: 360, // 1.5倍行距 (240 * 1.5 = 360)
                                        lineRule: 'exact'
                                      }
                                    }),
                                    // 将单个段落替换为多个段落，每行一个段落
                                    ...(data.requirements ? data.requirements.split('\n').map(line =>
                                      new Paragraph({
                                        children: [
                                          new TextRun({
                                            text: line,
                                            font: "宋体",
                                            size: 24,
                                          })
                                        ],
                                        spacing: {
                                          line: 360, // 1.5倍行距
                                          lineRule: 'exact'
                                        },
                                        indent: { firstLine: 480 }, // 首行缩进2字符
                                      })
                                    ) : [
                                      new Paragraph({
                                        children: [
                                          new TextRun({
                                            text: "Windows11,Visual Studio 2022",
                                            font: "宋体",
                                            size: 24,
                                          })
                                        ],
                                        spacing: {
                                          line: 360, // 1.5倍行距
                                          lineRule: 'exact'
                                        },
                                        indent: { firstLine: 480 }, // 首行缩进2字符
                                      })
                                    ]),
                                  ],
                                  // borders: {
                                  //   top: { style: BorderStyle.NONE },
                                  //   bottom: { style: BorderStyle.NONE },
                                  //   left: { style: BorderStyle.NONE },
                                  //   right: { style: BorderStyle.NONE },
                                  // },
                                }),
                              ],
                            }),

                            // 三、上机操作内容（老师布置的具体任务） - 带边框的表格
                            new TableRow({
                              height: {
                                value: 1457,
                                rule: 'atLeast'
                              },
                              children: [
                                new TableCell({
                                  width: {
                                    size: 7938,  // 14cm 对应的 dxa 值
                                    type: 'dxa',
                                  },
                                  columnSpan: 6,
                                  children: [
                                    new Paragraph({
                                      children: [
                                        new TextRun({
                                          text: "三、上机操作内容（老师布置的具体任务）",
                                          size: 24,
                                          font: "宋体",
                                          // bold: true,
                                        })
                                      ],
                                      spacing: {
                                        after: 0,
                                        before: 0,
                                        line: 360, // 1.5倍行距 (240 * 1.5 = 360)
                                        lineRule: 'exact'
                                      }
                                    }),
                                    // 将单个段落替换为多个段落，每行一个段落
                                    ...(data.tasks ? data.tasks.split('\n').map(line =>
                                      new Paragraph({
                                        children: [
                                          new TextRun({
                                            text: line,
                                            font: "宋体",
                                            size: 24,
                                          })
                                        ],
                                        spacing: {
                                          line: 360, // 1.5倍行距
                                          lineRule: 'exact'
                                        },
                                        indent: { firstLine: 480 }, // 首行缩进2字符
                                      })
                                    ) : [
                                      new Paragraph({
                                        children: [
                                          new TextRun({
                                            text: "实验内容：线性表基础操作，包括顺序表的初始化、插入、删除、查找和遍历实现",
                                            font: "宋体",
                                            size: 24,
                                          })
                                        ],
                                        spacing: {
                                          line: 360, // 1.5倍行距
                                          lineRule: 'exact'
                                        },
                                        indent: { firstLine: 480 }, // 首行缩进2字符
                                      })
                                    ]),
                                  ],
                                  // borders: {
                                  //   top: { style: BorderStyle.NONE },
                                  //   bottom: { style: BorderStyle.NONE },
                                  //   left: { style: BorderStyle.NONE },
                                  //   right: { style: BorderStyle.NONE },
                                  // },
                                }),
                              ],
                            }),
                            // 四、上机操作的基本步骤 - 带边框的表格
                            new TableRow({
                              height: {
                                value: 1457,
                                rule: 'atLeast'
                              },
                              children: [
                                new TableCell({
                                  width: {
                                    size: 7938,  // 14cm 对应的 dxa 值
                                    type: 'dxa',
                                  },
                                  columnSpan: 6,
                                  children: [
                                    new Paragraph({
                                      children: [
                                        new TextRun({
                                          text: "四、上机操作的基本步骤(每个题目的关键代码及注释)",
                                          size: 24,
                                          font: "宋体",
                                        })
                                      ],
                                      spacing: {
                                        after: 200, // 增加标题下方间距
                                        before: 0,
                                        line: 360,
                                        lineRule: 'exact'
                                      }
                                    }),
                                    // 使用新函数处理steps内容
                                    ...(data.steps ? this._processStepsContent(data.steps) : [
                                      new Paragraph({
                                        children: [
                                          new TextRun({
                                            text: "未获取到实验内容",
                                            font: "宋体",
                                            size: 24,
                                          })
                                        ],
                                        spacing: {
                                          line: 360,
                                          lineRule: 'exact'
                                        },
                                        indent: { firstLine: 480 },
                                      })
                                    ]),
                                  ],
                                }),
                              ],
                            }),
                            // 五、上机操作的结果截图及还存在的问题 - 带边框的表格
                            new TableRow({
                              height: {
                                value: 3402,
                                rule: 'atLeast'
                              },
                              children: [
                                new TableCell({
                                  width: {
                                    size: 7938,  // 14cm 对应的 dxa 值
                                    type: 'dxa',
                                  },
                                  columnSpan: 6,
                                  children: [
                                    new Paragraph({
                                      children: [
                                        new TextRun({
                                          text: "五、上机操作的结果截图及还存在的问题",
                                          size: 24,
                                          font: "宋体",
                                          // bold: true,
                                        })
                                      ],
                                      spacing: {
                                        after: 0,
                                        before: 0,
                                        line: 360, // 1.5倍行距 (240 * 1.5 = 360)
                                        lineRule: 'exact'
                                      }
                                    }),
                                    // 将单个段落替换为多个段落，每行一个段落
                                    ...(data.results ? data.results.split('\n').map(line =>
                                      new Paragraph({
                                        children: [
                                          new TextRun({
                                            text: line,
                                            font: "宋体",
                                            size: 24,
                                          })
                                        ],
                                        spacing: {
                                          line: 360, // 1.5倍行距
                                          lineRule: 'exact'
                                        },
                                        indent: { firstLine: 480 }, // 首行缩进2字符
                                      })
                                    ) : [
                                      new Paragraph({
                                        children: [
                                          new TextRun({
                                            text: "成功实现了各项功能，测试通过。",
                                            font: "宋体",
                                            size: 24,
                                          })
                                        ],
                                        spacing: {
                                          line: 360, // 1.5倍行距
                                          lineRule: 'exact'
                                        },
                                        indent: { firstLine: 480 }, // 首行缩进2字符
                                      })
                                    ]),
                                  ],
                                  // borders: {
                                  //   top: { style: BorderStyle.NONE },
                                  //   bottom: { style: BorderStyle.NONE },
                                  //   left: { style: BorderStyle.NONE },
                                  //   right: { style: BorderStyle.NONE },
                                  // },
                                }),
                              ],
                            }),
                            // 六、上机操作的收获及心得 - 带边框的表格
                            new TableRow({
                              height: {
                                value: 4536,
                                rule: 'atLeast'
                              },
                              children: [
                                new TableCell({
                                  width: {
                                    size: 7938,  // 14cm 对应的 dxa 值
                                    type: 'dxa',
                                  },
                                  columnSpan: 6,
                                  children: [
                                    new Paragraph({
                                      children: [
                                        new TextRun({
                                          text: "六、上机操作的收获及心得",
                                          size: 24,
                                          font: "宋体",
                                          // bold: true,
                                        })
                                      ],
                                      spacing: {
                                        after: 0,
                                        before: 0,
                                        line: 360, // 1.5倍行距 (240 * 1.5 = 360)
                                        lineRule: 'exact'
                                      }
                                    }),
                                    // 将单个段落替换为多个段落，每行一个段落
                                    ...(data.summary ? data.summary.split('\n').map(line =>
                                      new Paragraph({
                                        children: [
                                          new TextRun({
                                            text: line,
                                            font: "宋体",
                                            size: 24,
                                          })
                                        ],
                                        spacing: {
                                          line: 360, // 1.5倍行距
                                          lineRule: 'exact'
                                        },
                                        indent: { firstLine: 480 }, // 首行缩进2字符
                                      })
                                    ) : [
                                      new Paragraph({
                                        children: [
                                          new TextRun({
                                            text: "通过本次实验，我深入理解了线性表的工作原理和实现方法。",
                                            font: "宋体",
                                            size: 24,
                                          })
                                        ],
                                        spacing: {
                                          line: 360, // 1.5倍行距
                                          lineRule: 'exact'
                                        },
                                        indent: { firstLine: 480 }, // 首行缩进2字符
                                      })
                                    ]),
                                  ],
                                  // borders: {
                                  //   top: { style: BorderStyle.NONE },
                                  //   bottom: { style: BorderStyle.NONE },
                                  //   left: { style: BorderStyle.NONE },
                                  //   right: { style: BorderStyle.NONE },
                                  // },
                                }),
                              ],
                            })],
                        }),

                        // // 一、上机操作目的和要求 - 带边框的表格
                        // new Table({
                        //   width: {
                        //     size: 7938,
                        //     type: 'dxa',
                        //   },
                        //   borders: {
                        //     top: thickBorders.top,
                        //     bottom: thickBorders.bottom,
                        //     left: thickBorders.left,
                        //     right: thickBorders.right,
                        //     insideHorizontal: insideBorders,
                        //     insideVertical: insideBorders,
                        //   },
                        //   margins: {
                        //     top: 120,
                        //     bottom: 120,
                        //     left: 120,
                        //     right: 120,
                        //   },
                        //   rows: [
                        //     // 一、上机操作目的和要求 - 带边框的表格
                        //     new TableRow({
                        //       height: {
                        //         value: 1134,
                        //         rule: 'atLeast'
                        //       },
                        //       children: [
                        //         new TableCell({
                        //           width: {
                        //             size: 7938,  // 14cm 对应的 dxa 值
                        //             type: 'dxa',
                        //           },
                        //           columnSpan: 6,
                        //           children: [
                        //             new Paragraph({
                        //               children: [
                        //                 new TextRun({
                        //                   text: "一、上机操作目的和要求",
                        //                   size: 24,
                        //                   font: "宋体",
                        //                   // bold: true,
                        //                 })
                        //               ],
                        //               spacing: {
                        //                 after: 0,
                        //                 before: 0,
                        //                 line: 360, // 1.5倍行距 (240 * 1.5 = 360)
                        //                 lineRule: 'exact'
                        //               }
                        //             }),
                        //             // 将单个段落替换为多个段落，每行一个段落
                        //             ...(data.purpose ? data.purpose.split('\n').map(line =>
                        //               new Paragraph({
                        //                 children: [
                        //                   new TextRun({
                        //                     text: line,
                        //                     font: "宋体",
                        //                     size: 24,
                        //                   })
                        //                 ],
                        //                 spacing: {
                        //                   line: 360, // 1.5倍行距
                        //                   lineRule: 'exact'
                        //                 },
                        //                 indent: { firstLine: 480 }, // 首行缩进2字符
                        //               })
                        //             ) : [
                        //               new Paragraph({
                        //                 children: [
                        //                   new TextRun({
                        //                     text: "未获取到实验内容",
                        //                     font: "宋体",
                        //                     size: 24,
                        //                   })
                        //                 ],
                        //                 spacing: {
                        //                   line: 360, // 1.5倍行距
                        //                   lineRule: 'exact'
                        //                 },
                        //                 indent: { firstLine: 480 }, // 首行缩进2字符
                        //               })
                        //             ]),
                        //           ],
                        //           // borders: {
                        //           //   top: { style: BorderStyle.NONE },
                        //           //   bottom: { style: BorderStyle.NONE },
                        //           //   left: { style: BorderStyle.NONE },
                        //           //   right: { style: BorderStyle.NONE },
                        //           // },
                        //         }),
                        //       ],
                        //     }),

                        //     // 二、上机操作需要的软、硬件 - 带边框的表格
                        //     new TableRow({
                        //       height: {
                        //         value: 701,
                        //         rule: 'atLeast'
                        //       },
                        //       children: [
                        //         new TableCell({
                        //           width: {
                        //             size: 7938,  // 14cm 对应的 dxa 值
                        //             type: 'dxa',
                        //           },
                        //           columnSpan: 6,
                        //           children: [
                        //             new Paragraph({
                        //               children: [
                        //                 new TextRun({
                        //                   text: "二、上机操作需要的软、硬件",
                        //                   size: 24,
                        //                   font: "宋体",
                        //                   // bold: true,
                        //                 })
                        //               ],
                        //               spacing: {
                        //                 after: 0,
                        //                 before: 0,
                        //                 line: 360, // 1.5倍行距 (240 * 1.5 = 360)
                        //                 lineRule: 'exact'
                        //               }
                        //             }),
                        //             // 将单个段落替换为多个段落，每行一个段落
                        //             ...(data.requirements ? data.requirements.split('\n').map(line =>
                        //               new Paragraph({
                        //                 children: [
                        //                   new TextRun({
                        //                     text: line,
                        //                     font: "宋体",
                        //                     size: 24,
                        //                   })
                        //                 ],
                        //                 spacing: {
                        //                   line: 360, // 1.5倍行距
                        //                   lineRule: 'exact'
                        //                 },
                        //                 indent: { firstLine: 480 }, // 首行缩进2字符
                        //               })
                        //             ) : [
                        //               new Paragraph({
                        //                 children: [
                        //                   new TextRun({
                        //                     text: "未获取到实验内容",
                        //                     font: "宋体",
                        //                     size: 24,
                        //                   })
                        //                 ],
                        //                 spacing: {
                        //                   line: 360, // 1.5倍行距
                        //                   lineRule: 'exact'
                        //                 },
                        //                 indent: { firstLine: 480 }, // 首行缩进2字符
                        //               })
                        //             ]),
                        //           ],
                        //           borders: {
                        //             top: { style: BorderStyle.NONE },
                        //             bottom: { style: BorderStyle.NONE },
                        //             left: { style: BorderStyle.NONE },
                        //             right: { style: BorderStyle.NONE },
                        //           },
                        //         }),
                        //       ],
                        //     }),

                        //     // 三、上机操作内容（老师布置的具体任务） - 带边框的表格
                        //     new TableRow({
                        //       height: {
                        //         value: 1457,
                        //         rule: 'atLeast'
                        //       },
                        //       children: [
                        //         new TableCell({
                        //           width: {
                        //             size: 7938,  // 14cm 对应的 dxa 值
                        //             type: 'dxa',
                        //           },
                        //           columnSpan: 6,
                        //           children: [
                        //             new Paragraph({
                        //               children: [
                        //                 new TextRun({
                        //                   text: "三、上机操作内容（老师布置的具体任务）",
                        //                   size: 24,
                        //                   font: "宋体",
                        //                   // bold: true,
                        //                 })
                        //               ],
                        //               spacing: {
                        //                 after: 0,
                        //                 before: 0,
                        //                 line: 360, // 1.5倍行距 (240 * 1.5 = 360)
                        //                 lineRule: 'exact'
                        //               }
                        //             }),
                        //             // 将单个段落替换为多个段落，每行一个段落
                        //             ...(data.tasks ? data.tasks.split('\n').map(line =>
                        //               new Paragraph({
                        //                 children: [
                        //                   new TextRun({
                        //                     text: line,
                        //                     font: "宋体",
                        //                     size: 24,
                        //                   })
                        //                 ],
                        //                 spacing: {
                        //                   line: 360, // 1.5倍行距
                        //                   lineRule: 'exact'
                        //                 },
                        //                 indent: { firstLine: 480 }, // 首行缩进2字符
                        //               })
                        //             ) : [
                        //               new Paragraph({
                        //                 children: [
                        //                   new TextRun({
                        //                     text: "未获取到实验内容",
                        //                     font: "宋体",
                        //                     size: 24,
                        //                   })
                        //                 ],
                        //                 spacing: {
                        //                   line: 360, // 1.5倍行距
                        //                   lineRule: 'exact'
                        //                 },
                        //                 indent: { firstLine: 480 }, // 首行缩进2字符
                        //               })
                        //             ]),
                        //           ],
                        //           borders: {
                        //             top: { style: BorderStyle.NONE },
                        //             bottom: { style: BorderStyle.NONE },
                        //             left: { style: BorderStyle.NONE },
                        //             right: { style: BorderStyle.NONE },
                        //           },
                        //         }),
                        //       ],
                        //     }),
                        //     // 四、上机操作的基本步骤 - 带边框的表格
                        //     new TableRow({
                        //       height: {
                        //         value: 1457,
                        //         rule: 'atLeast'
                        //       },
                        //       children: [
                        //         new TableCell({
                        //           width: {
                        //             size: 7938,  // 14cm 对应的 dxa 值
                        //             type: 'dxa',
                        //           },
                        //           columnSpan: 6,
                        //           children: [
                        //             new Paragraph({
                        //               children: [
                        //                 new TextRun({
                        //                   text: "四、上机操作的基本步骤(每个题目的关键代码及注释)",
                        //                   size: 24,
                        //                   font: "宋体",
                        //                   // bold: true,
                        //                 })
                        //               ],
                        //               spacing: {
                        //                 after: 0,
                        //                 before: 0,
                        //                 line: 360, // 1.5倍行距 (240 * 1.5 = 360)
                        //                 lineRule: 'exact'
                        //               }
                        //             }),
                        //             // 将单个段落替换为多个段落，每行一个段落
                        //             ...(data.steps ? data.steps.split('\n').map(line =>
                        //               new Paragraph({
                        //                 children: [
                        //                   new TextRun({
                        //                     text: line,
                        //                     font: "宋体",
                        //                     size: 24,
                        //                   })
                        //                 ],
                        //                 spacing: {
                        //                   line: 360, // 1.5倍行距
                        //                   lineRule: 'exact'
                        //                 },
                        //                 indent: { firstLine: 480 }, // 首行缩进2字符
                        //               })
                        //             ) : [
                        //               new Paragraph({
                        //                 children: [
                        //                   new TextRun({
                        //                     text: "未获取到实验内容",
                        //                     font: "宋体",
                        //                     size: 24,
                        //                   })
                        //                 ],
                        //                 spacing: {
                        //                   line: 360, // 1.5倍行距
                        //                   lineRule: 'exact'
                        //                 },
                        //                 indent: { firstLine: 480 }, // 首行缩进2字符
                        //               })
                        //             ]),
                        //           ],
                        //           borders: {
                        //             top: { style: BorderStyle.NONE },
                        //             bottom: { style: BorderStyle.NONE },
                        //             left: { style: BorderStyle.NONE },
                        //             right: { style: BorderStyle.NONE },
                        //           },
                        //         }),
                        //       ],
                        //     }),
                        //     // 五、上机操作的结果截图及还存在的问题 - 带边框的表格
                        //     new TableRow({
                        //       height: {
                        //         value: 3402,
                        //         rule: 'atLeast'
                        //       },
                        //       children: [
                        //         new TableCell({
                        //           width: {
                        //             size: 7938,  // 14cm 对应的 dxa 值
                        //             type: 'dxa',
                        //           },
                        //           columnSpan: 6,
                        //           children: [
                        //             new Paragraph({
                        //               children: [
                        //                 new TextRun({
                        //                   text: "五、上机操作的结果截图及还存在的问题",
                        //                   size: 24,
                        //                   font: "宋体",
                        //                   // bold: true,
                        //                 })
                        //               ],
                        //               spacing: {
                        //                 after: 0,
                        //                 before: 0,
                        //                 line: 360, // 1.5倍行距 (240 * 1.5 = 360)
                        //                 lineRule: 'exact'
                        //               }
                        //             }),
                        //             // 将单个段落替换为多个段落，每行一个段落
                        //             ...(data.results ? data.results.split('\n').map(line =>
                        //               new Paragraph({
                        //                 children: [
                        //                   new TextRun({
                        //                     text: line,
                        //                     font: "宋体",
                        //                     size: 24,
                        //                   })
                        //                 ],
                        //                 spacing: {
                        //                   line: 360, // 1.5倍行距
                        //                   lineRule: 'exact'
                        //                 },
                        //                 indent: { firstLine: 480 }, // 首行缩进2字符
                        //               })
                        //             ) : [
                        //               new Paragraph({
                        //                 children: [
                        //                   new TextRun({
                        //                     text: "未获取到实验内容",
                        //                     font: "宋体",
                        //                     size: 24,
                        //                   })
                        //                 ],
                        //                 spacing: {
                        //                   line: 360, // 1.5倍行距
                        //                   lineRule: 'exact'
                        //                 },
                        //                 indent: { firstLine: 480 }, // 首行缩进2字符
                        //               })
                        //             ]),
                        //           ],
                        //           borders: {
                        //             top: { style: BorderStyle.NONE },
                        //             bottom: { style: BorderStyle.NONE },
                        //             left: { style: BorderStyle.NONE },
                        //             right: { style: BorderStyle.NONE },
                        //           },
                        //         }),
                        //       ],
                        //     }),
                        //     // 六、上机操作的收获及心得 - 带边框的表格
                        //     new TableRow({
                        //       height: {
                        //         value: 4536,
                        //         rule: 'atLeast'
                        //       },
                        //       children: [
                        //         new TableCell({
                        //           width: {
                        //             size: 7938,  // 14cm 对应的 dxa 值
                        //             type: 'dxa',
                        //           },
                        //           columnSpan: 6,
                        //           children: [
                        //             new Paragraph({
                        //               children: [
                        //                 new TextRun({
                        //                   text: "六、上机操作的收获及心得",
                        //                   size: 24,
                        //                   font: "宋体",
                        //                   // bold: true,
                        //                 })
                        //               ],
                        //               spacing: {
                        //                 after: 0,
                        //                 before: 0,
                        //                 line: 360, // 1.5倍行距 (240 * 1.5 = 360)
                        //                 lineRule: 'exact'
                        //               }
                        //             }),
                        //             // 将单个段落替换为多个段落，每行一个段落
                        //             ...(data.summary ? data.summary.split('\n').map(line =>
                        //               new Paragraph({
                        //                 children: [
                        //                   new TextRun({
                        //                     text: line,
                        //                     font: "宋体",
                        //                     size: 24,
                        //                   })
                        //                 ],
                        //                 spacing: {
                        //                   line: 360, // 1.5倍行距
                        //                   lineRule: 'exact'
                        //                 },
                        //                 indent: { firstLine: 480 }, // 首行缩进2字符
                        //               })
                        //             ) : [
                        //               new Paragraph({
                        //                 children: [
                        //                   new TextRun({
                        //                     text: "未获取到实验内容",
                        //                     font: "宋体",
                        //                     size: 24,
                        //                   })
                        //                 ],
                        //                 spacing: {
                        //                   line: 360, // 1.5倍行距
                        //                   lineRule: 'exact'
                        //                 },
                        //                 indent: { firstLine: 480 }, // 首行缩进2字符
                        //               })
                        //             ]),
                        //           ],
                        //           borders: {
                        //             top: { style: BorderStyle.NONE },
                        //             bottom: { style: BorderStyle.NONE },
                        //             left: { style: BorderStyle.NONE },
                        //             right: { style: BorderStyle.NONE },
                        //           },
                        //         }),
                        //       ],
                        //     }),
                        //     // 七、教师评语 - 带边框的表格 (仅当有教师评语时添加)
                        //     ...(data.teacherComment ? [
                        //       new TableRow({
                        //         height: {
                        //           value: 4536,
                        //           rule: 'atLeast'
                        //         },
                        //         children: [
                        //           new TableCell({
                        //             width: {
                        //               size: 7938,  // 14cm 对应的 dxa 值
                        //               type: 'dxa',
                        //             },
                        //             columnSpan: 6,
                        //             children: [
                        //               new Paragraph({
                        //                 children: [
                        //                   new TextRun({
                        //                     text: "教师评语",
                        //                     size: 24,
                        //                     font: "宋体",
                        //                     // bold: true,
                        //                   })
                        //                 ],
                        //                 spacing: {
                        //                   after: 0,
                        //                   before: 0,
                        //                   line: 360, // 1.5倍行距 (240 * 1.5 = 360)
                        //                   lineRule: 'exact'
                        //                 }
                        //               }),
                        //               // 将单个段落替换为多个段落，每行一个段落
                        //               ...(data.teacherComment.split('\n').map(line =>
                        //                 new Paragraph({
                        //                   children: [
                        //                     new TextRun({
                        //                       text: line,
                        //                       font: "宋体",
                        //                       size: 24,
                        //                     })
                        //                   ],
                        //                   spacing: {
                        //                     line: 360, // 1.5倍行距
                        //                     lineRule: 'exact'
                        //                   },
                        //                   indent: { firstLine: 480 }, // 首行缩进2字符
                        //                 })
                        //               )),
                        //             ],
                        //             borders: {
                        //               top: { style: BorderStyle.NONE },
                        //               bottom: { style: BorderStyle.NONE },
                        //               left: { style: BorderStyle.NONE },
                        //               right: { style: BorderStyle.NONE },
                        //             },
                        //           }),
                        //         ],
                        //       }),
                        //     ] : []),
                        //   ]
                        // }),
                      ],
                    }),
                  ],
                }),
              ],
            }),
          ],
        }],
      });

      // 生成并返回文档 blob
      const blob = await Packer.toBlob(doc);
      return blob;
    } catch (error) {
      console.error('生成标准报告失败:', error)
      throw new Error(`生成标准报告失败: ${error.message}`)
    }
  }

  // 辅助方法：创建报告章节表格
  _createSectionTable(title, content, thickBorders, insideBorders) {
    return new Table({
      width: {
        size: 100,
        type: 'pct',
      },
      borders: {
        top: thickBorders.top,
        bottom: thickBorders.bottom,
        left: thickBorders.left,
        right: thickBorders.right,
        insideHorizontal: insideBorders,
        insideVertical: insideBorders,
      },
      margins: {
        top: 120,
        bottom: 120,
        left: 120,
        right: 120,
      },
      rows: [
        new TableRow({
          height: {
            value: 3000,
            rule: 'atLeast'
          },
          children: [
            new TableCell({
              columnSpan: 6,
              children: [
                new Paragraph({
                  children: [
                    new TextRun({
                      text: title,
                      size: 24,
                      font: "宋体",
                    })
                  ],
                  spacing: {
                    after: 0,
                    before: 0,
                    line: 360, // 1.5倍行距
                    lineRule: 'exact'
                  }
                }),
                ...(content ? content.split('\n').map(line =>
                  new Paragraph({
                    children: [
                      new TextRun({
                        text: line,
                        font: "宋体",
                        size: 24,
                      })
                    ],
                    spacing: {
                      line: 360, // 1.5倍行距
                      lineRule: 'exact'
                    },
                    indent: { firstLine: 480 }, // 首行缩进2字符
                  })
                ) : [
                  new Paragraph({
                    children: [
                      new TextRun({
                        text: "未获取到实验内容",
                        font: "宋体",
                        size: 24,
                      })
                    ],
                    spacing: {
                      line: 360, // 1.5倍行距
                      lineRule: 'exact'
                    },
                    indent: { firstLine: 480 }, // 首行缩进2字符
                  })
                ]),
              ],
              borders: {
                top: { style: BorderStyle.NONE },
                bottom: { style: BorderStyle.NONE },
                left: { style: BorderStyle.NONE },
                right: { style: BorderStyle.NONE },
              },
            }),
          ],
        }),
      ]
    });
  }

  _generateDocumentContent(data) {
    const children = []

    // 标题
    children.push(
      new Paragraph({
        text: `${data.experimentName}实验报告`,
        heading: HeadingLevel.TITLE,
        alignment: AlignmentType.CENTER
      })
    )

    // 基本信息表格
    children.push(
      new Table({
        width: {
          size: 100,
          type: WidthType.PERCENTAGE
        },
        rows: [
          new TableRow({
            children: [
              new TableCell({
                children: [new Paragraph('学生姓名')],
                width: { size: 20, type: WidthType.PERCENTAGE }
              }),
              new TableCell({
                children: [new Paragraph(data.studentName || '')],
                width: { size: 30, type: WidthType.PERCENTAGE }
              }),
              new TableCell({
                children: [new Paragraph('学号')],
                width: { size: 20, type: WidthType.PERCENTAGE }
              }),
              new TableCell({
                children: [new Paragraph(data.studentId || '')],
                width: { size: 30, type: WidthType.PERCENTAGE }
              })
            ]
          }),
          new TableRow({
            children: [
              new TableCell({
                children: [new Paragraph('班级')]
              }),
              new TableCell({
                children: [new Paragraph(data.className || '')]
              }),
              new TableCell({
                children: [new Paragraph('日期')]
              }),
              new TableCell({
                children: [new Paragraph(new Date().toLocaleDateString())]
              })
            ]
          })
        ]
      })
    )

    // 实验目的
    children.push(
      new Paragraph({
        text: '1. 实验目的',
        heading: HeadingLevel.HEADING_1
      }),
      new Paragraph({
        text: data.purpose || '本实验旨在加深对相关知识点的理解。'
      })
    )

    // 实验内容
    children.push(
      new Paragraph({
        text: '2. 实验内容',
        heading: HeadingLevel.HEADING_1
      }),
      new Paragraph({
        text: data.content || `实现${data.experimentName}的基本操作。`
      })
    )

    // 实验代码
    if (data.code) {
      children.push(
        new Paragraph({
          text: '3. 实验代码',
          heading: HeadingLevel.HEADING_1
        }),
        new Paragraph({
          text: data.code,
          style: 'Code'
        })
      )
    }

    // 代码分析
    children.push(
      new Paragraph({
        text: '4. 代码分析',
        heading: HeadingLevel.HEADING_1
      }),
      new Paragraph({
        text: data.codeAnalysis || '代码分析内容'
      })
    )

    // 实验结果
    children.push(
      new Paragraph({
        text: '5. 实验结果',
        heading: HeadingLevel.HEADING_1
      }),
      new Paragraph({
        text: this._generateResult(data)
      })
    )

    // 实验总结
    children.push(
      new Paragraph({
        text: '6. 实验总结',
        heading: HeadingLevel.HEADING_1
      }),
      new Paragraph({
        text: this._generateSummary(data)
      })
    )

    return children
  }

  _generateResult(data) {
    let result = `实验实现了${data.experimentName}的基本功能，成功完成了数据的存储、查询和修改操作。`

    if (data.score) {
      result += `\n\n本次实验获得了${data.score}分的成绩，`
      if (data.score >= 90) {
        result += '表现优秀，实验完成度高，代码质量好。'
      } else if (data.score >= 80) {
        result += '表现良好，基本掌握了相关知识点。'
      } else if (data.score >= 60) {
        result += '基本完成实验要求，但仍有改进空间。'
      } else {
        result += '未能很好地完成实验要求，需要加强对基础知识的理解。'
      }
    }

    if (data.plagiarismRate !== undefined) {
      result += `\n\n代码查重率为${data.plagiarismRate}%，`
      if (data.plagiarismRate < 10) {
        result += '代码具有很高的独创性。'
      } else if (data.plagiarismRate < 20) {
        result += '代码独创性较好。'
      } else if (data.plagiarismRate < 30) {
        result += '代码有一定程度的重复，建议进一步思考优化方案。'
      } else {
        result += '代码重复率较高，请注意提高代码的独创性。'
      }
    }

    return result
  }

  _generateSummary(data) {
    let summary = `通过本次实验，我加深了对${data.experimentName}的理解，掌握了相关算法的实现方法以及优化技巧。`

    if (data.aiComment) {
      summary += `\n\n根据AI评价：${data.aiComment}`
    }

    return summary
  }
  /**
   * 将教师评语转换为图像段落
   * @param {string} teacherComment 教师评语
   * @returns {Promise<Array<Paragraph>>} 返回包含图像的段落数组
   */
  async _generateTeacherCommentImage(teacherComment) {
    try {
      // 使用我们创建的 commentToImage 功能将文本转换为图像
      const imageData = await convertCommentToImageForDocx(teacherComment, {
        width: 700, // 适合表格宽度的图像宽度
        fontSize: 16,
        fontFamily: '宋体',
        watermark: '教师评语',
      });

      if (!imageData || !imageData.buffer) {
        // 如果图像生成失败，回退到文本模式
        return teacherComment.split('\n').map(line =>
          new Paragraph({
            children: [
              new TextRun({
                text: line,
                font: "宋体",
                size: 24,
              })
            ],
            spacing: {
              line: 360, // 1.5倍行距
              lineRule: 'exact'
            },
            indent: { firstLine: 480 }, // 首行缩进2字符
          })
        );
      }

      // 将图像添加到段落中
      return [
        new Paragraph({
          children: [
            new ImageRun({
              data: imageData.buffer,
              transformation: {
                width: imageData.width / 2, // 调整为合适的宽度（单位：dxa）
                height: imageData.height / 2, // 调整高度保持比例
              }
            })
          ],
          spacing: {
            after: 0,
            before: 0
          }
        })
      ];
    } catch (error) {
      console.error('生成评语图片失败:', error);

      // 如果出错，回退到文本模式
      return teacherComment.split('\n').map(line =>
        new Paragraph({
          children: [
            new TextRun({
              text: line,
              font: "宋体",
              size: 24,
            })
          ],
          spacing: {
            line: 360,
            lineRule: 'exact'
          },
          indent: { firstLine: 480 }, // 首行缩进2字符
        })
      );
    }
  }

  /**
   * 生成教师评语表格行，支持异步生成图片
   * @param {string} teacherComment 教师评语
   * @returns {Promise<TableRow>} 包含教师评语的表格行
   */
  async _generateTeacherCommentRow(teacherComment) {
    // 生成教师评语图片段落
    const commentParagraphs = await this._generateTeacherCommentImage(teacherComment);

    // 创建表格行
    return new TableRow({
      height: {
        value: 4536,
        rule: 'atLeast'
      },
      children: [
        new TableCell({
          width: {
            size: 7938,  // 14cm 对应的 dxa 值
            type: 'dxa',
          },
          columnSpan: 6,
          children: [
            new Paragraph({
              children: [
                new TextRun({
                  text: "教师评语",
                  size: 24,
                  font: "宋体",
                })
              ],
              spacing: {
                after: 120,
                before: 0,
                line: 360, // 1.5倍行距
                lineRule: 'exact'
              }
            }),
            ...commentParagraphs,
          ],
        }),
      ],
    });
  }


    // filepath: [docxGenerator.js](http://_vscodecontentref_/1)
    /**
     * 处理steps内容中的标记，解析文本和评语图片
     * @param {string} stepsContent steps内容
     * @returns {Array<Paragraph>} 段落数组
     */
    _processStepsContent(stepsContent) {
      if (!stepsContent) return [];
      
      // 结果数组，将包含文本段落和图片
      const result = [];
      
      // 查找所有图片标记 - 使用与前端组件相同的pattern
      const imagePattern = /<div class="comment-image-container" data-image="(.*?)"><\/div>/g;
      
      // 分割文本，保留分隔位置
      let lastIndex = 0;
      let match;
      
      while ((match = imagePattern.exec(stepsContent)) !== null) {
        // 处理图片前的文本
        if (match.index > lastIndex) {
          const textBefore = stepsContent.substring(lastIndex, match.index);
          // 将文本分行并创建段落
          this._addTextParagraphs(result, textBefore);
        }
        
        // 处理图片
        const imageDataUrl = match[1];
        if (imageDataUrl) {
          try {
            console.log('找到评语图片，开始处理');
            
            // 提取Base64数据 - 使用更健壮的方法
            const imageBuffer = this._dataUrlToBuffer(imageDataUrl);
            console.log('Base64数据转换为Buffer成功，大小:', imageBuffer.length);
            
            // 添加图片段落
            result.push(
              new Paragraph({
                alignment: AlignmentType.CENTER,
                children: [
                  new ImageRun({
                    data: imageBuffer,
                    type: "png", // 明确指定图片类型为PNG
                    transformation: {
                      width: 350,     // 固定宽度
                      height: 200,    // 固定高度，确保评语图片能完整显示
                    },
                  }),
                ],
                spacing: {
                  before: 240,
                  after: 240
                }
              })
            );
            console.log('评语图片处理成功，已添加到文档');
          } catch (error) {
            console.error('图片处理失败:', error);
            // 添加错误提示作为回退
            result.push(
              new Paragraph({
                children: [
                  new TextRun({
                    text: "【教师评语图片处理失败】",
                    font: "宋体",
                    size: 24,
                    bold: true,
                    color: "FF0000"
                  })
                ],
              })
            );
          }
        }
        
        lastIndex = match.index + match[0].length;
      }
      
      // 处理剩余文本
      if (lastIndex < stepsContent.length) {
        const remainingText = stepsContent.substring(lastIndex);
        this._addTextParagraphs(result, remainingText);
      }
      
      return result;
    }
  
  /**
   * 辅助函数：将文本分行并创建段落
   * @param {Array<Paragraph>} result 段落数组
   * @param {string} text 文本内容
   */
  _addTextParagraphs(result, text) {
    // 处理Markdown代码块
    const processedText = text.replace(/```[a-z]*\n([\s\S]*?)```/g, (match, codeContent) => {
      return codeContent.split('\n').map(line => {
        // 为代码添加适当的缩进和格式
        return `    ${line}`;
      }).join('\n');
    });
    
    // 处理Markdown标题
    const lines = processedText.split('\n');
    for (const line of lines) {
      if (line.trim()) {
        // 检查是否是Markdown标题
        if (line.startsWith('### ')) {
          result.push(
            new Paragraph({
              children: [
                new TextRun({
                  text: line.substring(4),
                  font: "宋体",
                  size: 24,
                  bold: true,
                })
              ],
              spacing: {
                before: 240,
                after: 120,
                line: 360,
                lineRule: 'exact'
              }
            })
          );
        } else {
          result.push(
            new Paragraph({
              children: [
                new TextRun({
                  text: line,
                  font: "宋体",
                  size: 24,
                })
              ],
              spacing: {
                line: 360, // 1.5倍行距
                lineRule: 'exact'
              },
              indent: { firstLine: 480 }, // 首行缩进2字符
            })
          );
        }
      }
    }
  }
  
  /**
   * 根据图片内容计算适当的高度
   * @param {Buffer} imageBuffer 图片数据
   * @param {number} defaultWidth 默认宽度
   * @returns {Object} 宽高对象
   */
  _calculateImageDimensions(imageBuffer, defaultWidth = 400) {
    try {
      // 这里理想情况下应该分析图片实际尺寸
      // 为简化实现，我们假设评语图片的宽高比约为3:1
      return {
        width: defaultWidth,
        height: Math.round(defaultWidth / 3)
      };
    } catch (error) {
      console.error('计算图片尺寸失败:', error);
      return { width: defaultWidth, height: 150 };
    }
  }
  
  /**
   * 辅助函数：将data URL转换为Uint8Array - 浏览器兼容版
   * @param {string} dataUrl 数据URL
   * @returns {Uint8Array} 图片buffer
   */
  _dataUrlToBuffer(dataUrl) {
    try {
      // 确保输入是有效的dataUrl
      if (!dataUrl || typeof dataUrl !== 'string') {
        throw new Error('无效的data URL');
      }
      
      // 首先尝试分割data URL
      const parts = dataUrl.split(',');
      if (parts.length !== 2) {
        throw new Error('无效的data URL格式');
      }
      
      // 检查MIME类型和base64标记
      const mimeMatch = parts[0].match(/^data:(image\/[^;]+);base64$/);
      if (!mimeMatch) {
        throw new Error('无效的图片MIME类型');
      }
      
      console.log('图片MIME类型:', mimeMatch[1]);
      
      // 解码base64
      const base64 = parts[1];
      const binary = atob(base64);
      
      // 转换为Uint8Array (浏览器中的替代Buffer)
      const bytes = new Uint8Array(binary.length);
      for (let i = 0; i < binary.length; i++) {
        bytes[i] = binary.charCodeAt(i);
      }
      
      console.log('图片数据处理成功，大小:', bytes.length, '字节');
      return bytes;
    } catch (error) {
      console.error('转换data URL到Buffer失败:', error, '数据URL前20个字符:', dataUrl?.substring(0, 20));
      throw error;
    }
  }
  


  // 预览模板的实现
  async generatePreview(previewType) {
    try {
      // 根据不同的预览类型生成不同的预览数据
      let previewData = {
        courseName: '数据结构',
        experimentName: '未知实验',
        labName: '计算机实验室A101',
        labTime: new Date().toLocaleDateString(),
        teacherName: '王教授',
        studentName: '张三',
        studentId: '2023001001',
        className: '计算机科学与技术1班',
        purpose: '本次实验旨在理解并掌握数据结构的基本概念和实现方法。',
        requirements: '硬件：计算机\n软件：Visual Studio Code、C++编译器',
        tasks: '根据实验要求完成相关程序的编写和测试。',
        steps: '// 这里是示例代码\n#include <iostream>\nusing namespace std;\n\nint main() {\n  cout << "Hello, World!" << endl;\n  return 0;\n}',
        results: '程序运行正常，输出符合预期。',
        summary: '通过本次实验，我学会了如何应用数据结构解决实际问题，提高了编程能力。'
      };

      // 根据预览类型调整数据
      switch (previewType) {
        case 'linear_list':
          previewData.experimentName = '线性表的实现与应用';
          previewData.purpose = '了解线性表的基本概念，掌握线性表的顺序存储和链式存储实现，以及各种基本操作的实现方法。';
          previewData.tasks = '1. 实现顺序表的基本操作\n2. 实现单链表的基本操作\n3. 完成应用题：约瑟夫环问题';
          previewData.steps = '// 单链表节点定义\ntypedef struct Node {\n  int data;\n  struct Node* next;\n} Node, *LinkList;\n\n// 初始化链表\nvoid InitList(LinkList &L) {\n  L = new Node;\n  L->next = NULL;\n}\n\n// 头插法建立链表\nvoid CreateListHead(LinkList &L, int n) {\n  for(int i = 0; i < n; i++) {\n    Node *p = new Node;\n    p->data = i;\n    p->next = L->next;\n    L->next = p;\n  }\n}';
          break;
        case 'stack_queue':
          previewData.experimentName = '栈与队列的实现与应用';
          previewData.purpose = '掌握栈和队列的基本概念，实现栈和队列的基本操作，了解栈和队列的应用。';
          previewData.tasks = '1. 实现顺序栈的基本操作\n2. 实现链式队列的基本操作\n3. 完成应用题：表达式求值';
          previewData.steps = '// 顺序栈的实现\n#define MAXSIZE 100\ntypedef struct {\n  int data[MAXSIZE];\n  int top;\n} SqStack;\n\n// 初始化栈\nvoid InitStack(SqStack &S) {\n  S.top = -1;\n}\n\n// 入栈操作\nbool Push(SqStack &S, int e) {\n  if(S.top == MAXSIZE - 1) {\n    return false;\n  }\n  S.data[++S.top] = e;\n  return true;\n}';
          break;
        case 'tree':
          previewData.experimentName = '树与二叉树的实现与应用';
          previewData.purpose = '掌握二叉树的基本概念，实现二叉树的创建和遍历，理解二叉树在实际应用中的价值。';
          previewData.tasks = '1. 实现二叉树的链式存储结构\n2. 实现二叉树的三种遍历方式\n3. 完成应用题：哈夫曼编码';
          previewData.steps = '// 二叉树节点定义\ntypedef struct BiNode {\n  char data;\n  struct BiNode *lchild, *rchild;\n} BiNode, *BiTree;\n\n// 创建二叉树\nvoid CreateBiTree(BiTree &T) {\n  char ch;\n  cin >> ch;\n  if(ch == \'#\') {\n    T = NULL;\n  } else {\n    T = new BiNode;\n    T->data = ch;\n    CreateBiTree(T->lchild);\n    CreateBiTree(T->rchild);\n  }\n}\n\n// 前序遍历\nvoid PreOrder(BiTree T) {\n  if(T) {\n    cout << T->data << " ";\n    PreOrder(T->lchild);\n    PreOrder(T->rchild);\n  }\n}';
          break;
      }

      // 生成标准格式报告
      return await this.generateStandardReport(previewData);
    } catch (error) {
      console.error('生成预览失败:', error);
      throw new Error(`生成预览失败: ${error.message}`);
    }
  }

  static async downloadReport(blob, filename) {
    saveAs(blob, filename)
  }
}
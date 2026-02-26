// 这里是模拟数据，实际项目中会替换为API调用

// 学生信息
export const studentInfo = {
  id: 'S2023001',
  name: '张三',
  avatar: 'https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png',
  class: '计算机科学与技术2班',
  grade: '2023级',
  email: 'zhangsan@example.com',
  phone: '13800001111',
  role: 'student'
}

// 学生1信息 (计算机科学与技术1班)
export const student1Info = {
  id: 'S2023101',
  name: '学生1',
  avatar: 'https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png',
  class: '计算机科学与技术1班',
  grade: '2023级',
  email: 'student1@example.com',
  phone: '13866667777',
  role: 'student'
}

// 教师信息
export const teacherInfo = {
  id: 'T2023001',
  name: '李教授',
  avatar: 'https://cube.elemecdn.com/3/7c/3ea6beec64369c2642b92c6726f1epng.png',
  department: '计算机科学与工程学院',
  title: '副教授',
  email: 'liteacher@example.com',
  phone: '13800138000',
  role: 'teacher'
}

// 管理员信息
export const adminInfo = {
  id: 'A2023001',
  name: '王管理',
  avatar: 'https://cube.elemecdn.com/9/c2/f0ee8a3c7c9638a54940382568c9cpng.png',
  department: '教务处',
  email: 'admin@example.com',
  phone: '13900139000',
  role: 'admin'
}

// 实验列表
export const experimentList = [
  {
    id: 1,
    name: '线性表的实现与应用',
    deadline: '2023-12-15',
    submitCount: 28,
    averageScore: 85.6,
    status: 'completed',
    submitTime: '2023-11-10 14:30:00',
    score: 85.6,
    plagiarismRate: 10.2,
    description: '本实验要求学生掌握线性表的基本概念和实现方法，包括顺序表和链表两种存储结构。',
    requirements: [
      '实现顺序表和链表的基本操作，包括插入、删除、查找等',
      '编写测试程序验证实现的操作正确性',
      '分析两种存储结构的优缺点和适用场景',
      '完成实验报告'
    ],
    report: `# 线性表的实现与应用 - 实验报告

## 1. 实验目的
本实验旨在通过实现线性表数据结构，加深对线性表的顺序存储和链式存储方式的理解，掌握线性表的各种基本操作算法及其应用。

## 2. 实验内容
本实验主要实现了链表的基本操作，包括创建、插入、删除和查找等功能。

主要实现以下功能：
1. 实现链表的基本数据结构
2. 完成链表的初始化、插入、删除、查找等基本操作
3. 分析操作的时间复杂度
4. 测试各项功能的正确性

## 3. 实验环境
- 操作系统：Windows/Linux/MacOS
- 编程语言：C/C++/Java/Python
- 开发工具：Visual Studio Code

## 4. 实验过程
### 4.1 链表的实现
\`\`\`
// 链表实现代码
#include <stdio.h>
#include <stdlib.h>

// 链表节点结构
typedef struct Node {
    int data;
    struct Node *next;
} Node;

// 创建新节点
Node* createNode(int data) {
    Node* newNode = (Node*)malloc(sizeof(Node));
    newNode->data = data;
    newNode->next = NULL;
    return newNode;
}

// 插入节点
void insert(Node** head, int data) {
    Node* newNode = createNode(data);
    newNode->next = *head;
    *head = newNode;
}

// 打印链表
void printList(Node* head) {
    while (head != NULL) {
        printf("%d -> ", head->data);
        head = head->next;
    }
    printf("NULL\\n");
}
\`\`\`

### 4.2 实验分析
实验采用了结构体来表示节点结构，采用指针操作实现链表功能。代码中正确使用了内存分配和释放函数，避免了内存泄漏问题。

### 4.3 算法复杂度分析
| 操作 | 时间复杂度 | 空间复杂度 |
|------|--------|--------|
| 查找 | O(n) | O(1) |
| 插入 | O(1)（已知位置）/ O(n)（需查找） | O(1) |
| 删除 | O(1)（已知位置）/ O(n)（需查找） | O(1) |

## 5. 实验结果
实验实现了链表的基本功能，成功完成了数据的存储、查询和修改操作。

本次实验获得了85.6分的成绩，表现良好，基本掌握了相关知识点。

代码查重率为10.2%，代码独创性较好。

## 6. 实验总结
通过本次实验，我加深了对链表数据结构的理解，掌握了其基本操作的实现方法和应用场景。

在实验过程中，我遇到的主要问题是：
1. 指针操作的边界条件处理
2. 内存管理和释放问题
3. 特殊情况（如空表操作）的处理逻辑

这些问题通过反复测试和调试得到了解决，加深了我对数据结构实现细节的理解。

## 7. 参考资料
1. 《数据结构》教材第三章线性表
2. 《算法导论》相关章节
3. 网络资源与课堂笔记

---
*作者信息*
- 姓名：张三
- 学号：S2023001
- 班级：计算机科学与技术2班
- 日期：2023-11-10`,
    code: '#include <stdio.h>\n#include <stdlib.h>\n\n// 链表节点结构\ntypedef struct Node {\n    int data;\n    struct Node *next;\n} Node;\n\n// 创建新节点\nNode* createNode(int data) {\n    Node* newNode = (Node*)malloc(sizeof(Node));\n    newNode->data = data;\n    newNode->next = NULL;\n    return newNode;\n}\n\n// 插入节点\nvoid insert(Node** head, int data) {\n    Node* newNode = createNode(data);\n    newNode->next = *head;\n    *head = newNode;\n}\n\n// 打印链表\nvoid printList(Node* head) {\n    while (head != NULL) {\n        printf("%d -> ", head->data);\n        head = head->next;\n    }\n    printf("NULL\\n");\n}\n\nint main() {\n    Node* head = NULL;\n    insert(&head, 3);\n    insert(&head, 2);\n    insert(&head, 1);\n    printList(head);\n    return 0;\n}',
    aiComment: '这次线性表实验完成得很好，代码结构清晰且实现了完整的功能。你对顺序表和链表的实现都很规范，特别是顺序表的扩容机制和链表的反转实现值得肯定。实验报告中的性能分析也很到位，清晰地对比了两种结构的优缺点。建议在后续实验中可以考虑实现双向链表和循环链表，以及更多实际应用场景的测试。你在代码中的错误处理也很完善，这是很好的编程习惯。总体来说，这是一个出色的实验成果。'
  },
  {
    id: 2,
    name: '栈与队列的实现与应用',
    deadline: '2023-12-25',
    submitCount: 25,
    averageScore: 88.3,
    status: 'completed',
    submitTime: '2023-12-02 16:45:30',
    score: 92.0,
    plagiarismRate: 5.5,
    description: '本实验要求学生实现栈和队列数据结构，并利用它们解决实际问题。',
    requirements: [
      '实现栈的基本操作，包括压栈、出栈等',
      '实现队列的基本操作，包括入队、出队等',
      '使用栈实现括号匹配算法',
      '使用队列实现层次遍历算法'
    ]
  },
  {
    id: 3,
    name: '树与二叉树的实现与应用',
    deadline: '2024-01-10',
    submitCount: 0,
    averageScore: null,
    status: 'in_progress',
    description: '本实验要求学生掌握树和二叉树的基本概念，实现相关操作和算法。',
    requirements: [
      '实现二叉树的基本操作',
      '实现二叉树的三种遍历方式',
      '实现二叉搜索树的插入和查找操作',
      '分析各种操作的时间复杂度'
    ]
  }
]

// 教师创建的实验列表
export let teacherExperimentList = [
  {
    id: 1,
    name: '线性表的实现与应用',
    deadline: '2023-12-15',
    createdTime: '2023-11-01 10:23:45',
    status: 'active',
    submissionCount: 28,
    averageScore: 85.6
  },
  {
    id: 2,
    name: '栈与队列的实现与应用',
    deadline: '2023-12-25',
    createdTime: '2023-11-05 14:30:22',
    status: 'active',
    submissionCount: 0,
    averageScore: null
  },
  {
    id: 3,
    name: '树与二叉树的实现与应用',
    deadline: '2024-01-10',
    createdTime: '2023-11-10 09:15:36',
    status: 'draft',
    submissionCount: 0,
    averageScore: null
  }
]

// 学生提交列表
export const studentSubmissionsList = [
  {
    id: 1,
    experimentId: 1,
    experimentName: '线性表的实现与应用',
    studentId: 'S2023001',
    studentName: '张三',
    class: '计算机科学与技术2班',
    submitTime: '2023-11-10 14:30:00',
    score: 85.6,
    plagiarismRate: 10.2,
    status: 'graded'
  },
  {
    id: 2,
    experimentId: 1,
    experimentName: '线性表的实现与应用',
    studentId: 'S2023002',
    studentName: '李四',
    class: '计算机科学与技术2班',
    submitTime: '2023-11-11 10:15:20',
    score: 92.5,
    plagiarismRate: 5.0,
    status: 'graded'
  },
  {
    id: 3,
    experimentId: 1,
    experimentName: '线性表的实现与应用',
    studentId: 'S2023003',
    studentName: '王五',
    class: '计算机科学与技术2班',
    submitTime: '2023-11-11 16:40:12',
    score: null,
    plagiarismRate: null,
    status: 'submitted'
  },
  {
    id: 4,
    experimentId: 1,
    experimentName: '线性表的实现与应用',
    studentId: 'S2023101',
    studentName: '学生1',
    class: '计算机科学与技术1班',
    submitTime: '2023-11-08 15:20:30',
    score: 88.5,
    plagiarismRate: 3.5,
    status: 'graded'
  }
]

// 班级列表
export const classList = [
  {
    id: 'C2023001',
    name: '计算机科学与技术1班',
    grade: '2023级',
    studentCount: 42,
    teacherId: 'T2023001',
    teacherName: '李教授'
  },
  {
    id: 'C2023002',
    name: '计算机科学与技术2班',
    grade: '2023级',
    studentCount: 45,
    teacherId: 'T2023002',
    teacherName: '王老师'
  },
  {
    id: 'C2022001',
    name: '软件工程1班',
    grade: '2022级',
    studentCount: 38,
    teacherId: 'T2023003',
    teacherName: '张教授'
  }
]

// 班级分析数据
export const classAnalysisData = {
  id: 'C2023002',
  name: '计算机科学与技术2班',
  grade: '2023级',
  studentCount: 45,
  teacherName: '王老师',
  experimentCompletion: [
    { name: '线性表的实现与应用', completion: 85 },
    { name: '栈与队列的实现与应用', completion: 0 },
    { name: '树与二叉树的实现与应用', completion: 0 }
  ],
  scoreDistribution: {
    '90-100': 10,
    '80-89': 18,
    '70-79': 8,
    '60-69': 6,
    '<60': 3
  },
  topStudents: [
    { id: 'S2023002', name: '李四', averageScore: 92.5 },
    { id: 'S2023004', name: '赵六', averageScore: 91.2 },
    { id: 'S2023008', name: '陈一', averageScore: 90.8 }
  ],
  learningProblems: [
    '约15%的学生对链表的理解和实现存在困难',
    '部分学生在指针操作方面表现欠佳',
    '建议加强算法复杂度分析的训练'
  ]
}

// 实验详情
export const experimentDetails = {
  id: 1,
  name: '线性表的实现与应用',
  description: '本实验要求学生掌握线性表的基本概念和实现方法，包括顺序表和链表两种存储结构。',
  deadline: '2023-12-15',
  submitCount: 28,
  averageScore: 85.6,
  status: 'completed',
  submitTime: '2023-11-10 14:30:00',
  score: 85.6,
  plagiarismRate: 10.2,
  requirements: [
    '实现顺序表和链表的基本操作，包括插入、删除、查找等',
    '编写测试程序验证实现的操作正确性',
    '分析两种存储结构的优缺点和适用场景',
    '完成实验报告'
  ],
  testCases: [
    {
      input: '[1, 2, 3], insert(1, 4)',
      expectedOutput: '[1, 4, 2, 3]'
    },
    {
      input: '[1, 2, 3, 4], delete(2)',
      expectedOutput: '[1, 2, 4]'
    }
  ],
  report: `# 线性表的实现与应用 - 实验报告

## 1. 实验目的
本实验旨在通过实现线性表数据结构，加深对线性表的顺序存储和链式存储方式的理解，掌握线性表的各种基本操作算法及其应用。

## 2. 实验内容
本实验主要实现了链表的基本操作，包括创建、插入、删除和查找等功能。

主要实现以下功能：
1. 实现链表的基本数据结构
2. 完成链表的初始化、插入、删除、查找等基本操作
3. 分析操作的时间复杂度
4. 测试各项功能的正确性

## 3. 实验环境
- 操作系统：Windows/Linux/MacOS
- 编程语言：C/C++/Java/Python
- 开发工具：Visual Studio Code
- 提交时间：2023-11-10 14:30:00
- 截止时间：2023-12-15

## 4. 实验过程
### 4.1 链表的实现
\`\`\`
// 链表实现代码
#include <stdio.h>
#include <stdlib.h>

// 链表节点结构
typedef struct Node {
    int data;
    struct Node *next;
} Node;

// 创建新节点
Node* createNode(int data) {
    Node* newNode = (Node*)malloc(sizeof(Node));
    newNode->data = data;
    newNode->next = NULL;
    return newNode;
}

// 插入节点
void insert(Node** head, int data) {
    Node* newNode = createNode(data);
    newNode->next = *head;
    *head = newNode;
}

// 打印链表
void printList(Node* head) {
    while (head != NULL) {
        printf("%d -> ", head->data);
        head = head->next;
    }
    printf("NULL\\n");
}
\`\`\`

### 4.2 实验分析
实验采用了结构体来表示节点结构，采用指针操作实现链表功能。代码中正确使用了内存分配和释放函数，避免了内存泄漏问题。

### 4.3 算法复杂度分析
| 操作 | 时间复杂度 | 空间复杂度 |
|------|--------|--------|
| 查找 | O(n) | O(1) |
| 插入 | O(1)（已知位置）/ O(n)（需查找） | O(1) |
| 删除 | O(1)（已知位置）/ O(n)（需查找） | O(1) |

## 5. 实验结果
实验实现了链表的基本功能，成功完成了数据的存储、查询和修改操作。

本次实验获得了85.6分的成绩，表现良好，基本掌握了相关知识点。

代码查重率为10.2%，代码独创性较好。

## 6. 实验总结
通过本次实验，我加深了对链表数据结构的理解，掌握了其基本操作的实现方法和应用场景。

在实验过程中，我遇到的主要问题是：
1. 指针操作的边界条件处理
2. 内存管理和释放问题
3. 特殊情况（如空表操作）的处理逻辑

这些问题通过反复测试和调试得到了解决，加深了我对数据结构实现细节的理解。

## 7. 参考资料
1. 《数据结构》教材第三章线性表
2. 《算法导论》相关章节
3. 网络资源与课堂笔记

---
*作者信息*
- 姓名：张三
- 学号：S2023001
- 班级：计算机科学与技术2班
- 日期：2023-11-10`,
  code: '#include <stdio.h>\n#include <stdlib.h>\n\n// 链表节点结构\ntypedef struct Node {\n    int data;\n    struct Node *next;\n} Node;\n\n// 创建新节点\nNode* createNode(int data) {\n    Node* newNode = (Node*)malloc(sizeof(Node));\n    newNode->data = data;\n    newNode->next = NULL;\n    return newNode;\n}\n\n// 插入节点\nvoid insert(Node** head, int data) {\n    Node* newNode = createNode(data);\n    newNode->next = *head;\n    *head = newNode;\n}\n\n// 打印链表\nvoid printList(Node* head) {\n    while (head != NULL) {\n        printf("%d -> ", head->data);\n        head = head->next;\n    }\n    printf("NULL\\n");\n}\n\nint main() {\n    Node* head = NULL;\n    insert(&head, 3);\n    insert(&head, 2);\n    insert(&head, 1);\n    printList(head);\n    return 0;\n}',
  aiComment: '这次线性表实验完成得很好，代码结构清晰且实现了完整的功能。你对顺序表和链表的实现都很规范，特别是顺序表的扩容机制和链表的反转实现值得肯定。实验报告中的性能分析也很到位，清晰地对比了两种结构的优缺点。建议在后续实验中可以考虑实现双向链表和循环链表，以及更多实际应用场景的测试。你在代码中的错误处理也很完善，这是很好的编程习惯。总体来说，这是一个出色的实验成果。'
}

// 学习分析数据
export const learningAnalysisData = {
  completedExperiments: 2,
  totalExperiments: 3,
  averageScore: 88.8,
  ranking: 10,
  totalStudents: 45,
  scoreDistribution: {
    '90-100': 10,
    '80-89': 18,
    '70-79': 8,
    '60-69': 6,
    '<60': 3
  },
  recentSubmissions: [
    {
      id: 2,
      experimentName: '栈与队列的实现与应用',
      submitTime: '2023-12-02 16:45:30',
      score: 92.0,
      status: 'graded'
    },
    {
      id: 1,
      experimentName: '线性表的实现与应用',
      submitTime: '2023-11-10 14:30:00',
      score: 85.6,
      status: 'graded'
    }
  ],
  overall: {
    averageScore: 87,
    completionRate: 100,
    strengthAreas: ['线性表', '栈与队列', '时间复杂度分析', '基本算法设计'],
    weaknessAreas: ['平衡树', '图算法', '高级数据结构'],
    suggestionTopics: [
      '加强平衡树相关知识学习',
      '复习图的基本概念和算法',
      '学习高级数据结构的实现原理',
      '多做算法练习，提高编程能力',
      '参与小组讨论，分享学习心得'
    ]
  },
  knowledgeRadar: [
    { name: '数据结构基础', value: 85 },
    { name: '算法设计', value: 80 },
    { name: '时间复杂度', value: 90 },
    { name: '空间复杂度', value: 85 },
    { name: '编程实现', value: 75 },
    { name: '问题分析', value: 82 }
  ],
  timeDistribution: [
    { date: '2023-10-01', hours: 2.5 },
    { date: '2023-10-08', hours: 3.0 },
    { date: '2023-10-15', hours: 4.5 },
    { date: '2023-10-22', hours: 3.5 },
    { date: '2023-10-29', hours: 5.0 },
    { date: '2023-11-05', hours: 4.0 },
    { date: '2023-11-12', hours: 3.5 },
    { date: '2023-11-19', hours: 5.5 },
    { date: '2023-11-26', hours: 4.5 },
    { date: '2023-12-03', hours: 6.0 }
  ],
  experimentPerformance: [
    {
      id: 2,
      name: '栈与队列的实现与应用',
      score: 92.0,
      averageClassScore: 84.7
    },
    {
      id: 1,
      name: '线性表的实现与应用',
      score: 85.6,
      averageClassScore: 78.2
    },
    {
      id: 3,
      name: '树与二叉树的实现与应用',
      score: null,
      averageClassScore: null
    }
  ]
}

// 推荐练习
export const recommendedPractices = [
  {
    id: 1,
    title: '单链表反转',
    difficulty: 'medium',
    tags: ['链表', '指针操作'],
    acceptance: '65%',
    url: 'https://example.com/practice1'
  },
  {
    id: 2,
    title: '循环队列实现',
    difficulty: 'medium',
    tags: ['队列', '数组'],
    acceptance: '72%'
  },
  {
    id: 3,
    title: '栈的应用：括号匹配',
    difficulty: 'easy',
    tags: ['栈', '字符串'],
    acceptance: '85%'
  },
  {
    id: 4,
    title: '二叉树层序遍历',
    difficulty: 'medium',
    tags: ['二叉树', '队列'],
    acceptance: '68%'
  },
  {
    id: 5,
    title: '哈希表实现',
    difficulty: 'hard',
    tags: ['哈希表', '数组'],
    acceptance: '58%'
  }
]

// 这里暴露延迟函数，以便其他文件可以使用
export const delay = (ms) => new Promise(resolve => setTimeout(resolve, ms))

// 班级学生列表
export const classStudents = {
  'se2101': [
    { id: 'S2023001', name: '张三', averageScore: 87.5 },
    { id: 'S2023002', name: '李四', averageScore: 92.0 },
    { id: 'S2023003', name: '王五', averageScore: 76.5 },
    { id: 'S2023004', name: '赵六', averageScore: 81.2 },
    { id: 'S2023005', name: '孙七', averageScore: 88.7 }
  ],
  'cs2101': [
    { id: 'S2023006', name: '周八', averageScore: 85.4 },
    { id: 'S2023007', name: '吴九', averageScore: 79.8 },
    { id: 'S2023008', name: '郑十', averageScore: 91.2 },
    { id: 'S2023009', name: '钱十一', averageScore: 83.6 },
    { id: 'S2023010', name: '陈十二', averageScore: 77.9 },
    { id: 'S2023101', name: '学生1', averageScore: 88.5 }
  ]
}

// 班级详细分析数据
export const classDetailAnalysis = {
  'se2101': {
    id: 'se2101',
    name: '软件工程2101',
    studentCount: 42,
    experimentCompletion: [
      { name: '线性表实验', completion: 95 },
      { name: '栈与队列实验', completion: 88 },
      { name: '树与二叉树实验', completion: 76 },
      { name: '图论基础实验', completion: 65 }
    ],
    scoreDistribution: {
      '90-100': 8,
      '80-89': 15,
      '70-79': 12,
      '60-69': 5,
      '<60': 2
    },
    topStudents: [
      { id: 'S2023002', name: '李四', averageScore: 92.0 },
      { id: 'S2023005', name: '孙七', averageScore: 88.7 },
      { id: 'S2023001', name: '张三', averageScore: 87.5 }
    ],
    learningProblems: [
      '图算法理解困难，仅65%的学生完成相关实验',
      '高级数据结构掌握不足，需要加强训练',
      '代码实现能力参差不齐，部分学生需要额外指导'
    ],
    knowledgePoints: [
      { name: '数组与链表', mastery: 88 },
      { name: '栈与队列', mastery: 85 },
      { name: '树的基础', mastery: 76 },
      { name: '图的表示', mastery: 65 },
      { name: '排序算法', mastery: 72 }
    ],
    weeklyPerformance: [
      { week: '第1周', attendance: 98, homework: 92, participation: 85 },
      { week: '第2周', attendance: 95, homework: 90, participation: 88 },
      { week: '第3周', attendance: 96, homework: 85, participation: 82 },
      { week: '第4周', attendance: 92, homework: 80, participation: 78 },
      { week: '第5周', attendance: 94, homework: 82, participation: 80 }
    ]
  },
  'cs2101': {
    id: 'cs2101',
    name: '计算机科学2101',
    studentCount: 38,
    experimentCompletion: [
      { name: '线性表实验', completion: 92 },
      { name: '栈与队列实验', completion: 85 },
      { name: '树与二叉树实验', completion: 78 },
      { name: '图论基础实验', completion: 70 }
    ],
    scoreDistribution: {
      '90-100': 6,
      '80-89': 14,
      '70-79': 10,
      '60-69': 7,
      '<60': 1
    },
    topStudents: [
      { id: 'S2023008', name: '郑十', averageScore: 91.2 },
      { id: 'S2023006', name: '周八', averageScore: 85.4 },
      { id: 'S2023009', name: '钱十一', averageScore: 83.6 }
    ],
    learningProblems: [
      '复杂数据结构理解不深入，需要加强实践',
      '算法优化能力不足，效率提升空间大',
      '理论与实践脱节，需要更多实际应用案例'
    ],
    knowledgePoints: [
      { name: '数组与链表', mastery: 86 },
      { name: '栈与队列', mastery: 82 },
      { name: '树的基础', mastery: 75 },
      { name: '图的表示', mastery: 68 },
      { name: '排序算法', mastery: 77 }
    ],
    weeklyPerformance: [
      { week: '第1周', attendance: 96, homework: 90, participation: 82 },
      { week: '第2周', attendance: 94, homework: 85, participation: 80 },
      { week: '第3周', attendance: 95, homework: 82, participation: 78 },
      { week: '第4周', attendance: 90, homework: 78, participation: 75 },
      { week: '第5周', attendance: 92, homework: 80, participation: 76 }
    ]
  }
}

// 学生1的线性表实验详情
export const student1ExperimentDetail = {
  id: 1,
  name: '线性表的实现与应用',
  description: '本实验要求学生掌握线性表的基本概念和实现方法，包括顺序表和链表两种存储结构。',
  deadline: '2023-12-15',
  submitCount: 28,
  averageScore: 85.6,
  status: 'completed',
  submitTime: '2023-11-08 15:20:30',
  score: 88.5,
  plagiarismRate: 3.5,
  studentId: 'S2023101',
  studentName: '学生1',
  class: '计算机科学与技术1班',
  requirements: [
    '实现顺序表和链表的基本操作，包括插入、删除、查找等',
    '编写测试程序验证实现的操作正确性',
    '分析两种存储结构的优缺点和适用场景',
    '完成实验报告'
  ],
  testCases: [
    {
      input: '[1, 2, 3], insert(1, 4)',
      expectedOutput: '[1, 4, 2, 3]'
    },
    {
      input: '[1, 2, 3, 4], delete(2)',
      expectedOutput: '[1, 2, 4]'
    }
  ],
  report: `# 线性表实现与应用实验报告

## 1. 实验目的
- 深入理解线性表的基本概念和特性
- 掌握顺序表和链表的实现原理与适用场景
- 分析比较两种存储结构的优缺点
- 通过实际编程实现线性表的各种基本操作

## 2. 实验环境
- 操作系统: Windows 11
- 编译器: Visual Studio 2022
- 编程语言: C语言

## 3. 实验内容与实现
### 3.1 顺序表实现
顺序表基于数组实现，具有随机访问快速、存储密度高的优点。我主要实现了以下功能：
- 顺序表的初始化
- 插入元素（包括尾部插入和中间位置插入）
- 删除元素
- 按值查找和按位置查找
- 顺序表的扩容机制

代码实现摘要：
\`\`\`c
typedef struct {
    int *data;         // 存储数组
    int length;        // 当前长度
    int capacity;      // 最大容量
} SeqList;

// 初始化顺序表
SeqList* initSeqList(int capacity) {
    SeqList *list = (SeqList*)malloc(sizeof(SeqList));
    list->data = (int*)malloc(capacity * sizeof(int));
    list->length = 0;
    list->capacity = capacity;
    return list;
}

// 顺序表插入元素
bool insertSeqList(SeqList *list, int pos, int elem) {
    // 检查位置是否合法及是否需要扩容
    if (pos < 0 || pos > list->length || list->length >= list->capacity) {
        return false;
    }
    
    // 移动元素
    for (int i = list->length; i > pos; i--) {
        list->data[i] = list->data[i-1];
    }
    
    // 插入元素并更新长度
    list->data[pos] = elem;
    list->length++;
    return true;
}

// 顺序表删除元素
bool deleteSeqList(SeqList *list, int pos) {
    // 检查位置是否合法
    if (pos < 0 || pos >= list->length) {
        return false;
    }
    
    // 移动元素
    for (int i = pos; i < list->length - 1; i++) {
        list->data[i] = list->data[i+1];
    }
    
    // 更新长度
    list->length--;
    return true;
}
\`\`\`

### 3.2 链表实现
链表采用节点链接方式实现，具有动态增长、插入删除方便的特点。我实现了单链表的以下功能：
- 链表的初始化
- 头部、尾部和中间位置插入节点
- 删除节点
- 按值查找和按位置查找节点
- 链表反转
- 链表排序

代码实现摘要：
\`\`\`c
typedef struct Node {
    int data;
    struct Node *next;
} Node;

typedef struct {
    Node *head;
    int length;
} LinkedList;

// 初始化链表
LinkedList* initLinkedList() {
    LinkedList *list = (LinkedList*)malloc(sizeof(LinkedList));
    list->head = NULL;
    list->length = 0;
    return list;
}

// 头部插入节点
void insertHead(LinkedList *list, int elem) {
    Node *newNode = (Node*)malloc(sizeof(Node));
    newNode->data = elem;
    newNode->next = list->head;
    list->head = newNode;
    list->length++;
}

// 按位置插入节点
bool insertPos(LinkedList *list, int pos, int elem) {
    if (pos < 0 || pos > list->length) {
        return false;
    }
    
    if (pos == 0) {
        insertHead(list, elem);
        return true;
    }
    
    Node *current = list->head;
    for (int i = 0; i < pos - 1; i++) {
        current = current->next;
    }
    
    Node *newNode = (Node*)malloc(sizeof(Node));
    newNode->data = elem;
    newNode->next = current->next;
    current->next = newNode;
    list->length++;
    return true;
}

// 删除节点
bool deleteNode(LinkedList *list, int pos) {
    if (pos < 0 || pos >= list->length || list->head == NULL) {
        return false;
    }
    
    Node *temp;
    if (pos == 0) {
        temp = list->head;
        list->head = list->head->next;
        free(temp);
        list->length--;
        return true;
    }
    
    Node *current = list->head;
    for (int i = 0; i < pos - 1; i++) {
        current = current->next;
    }
    
    temp = current->next;
    current->next = temp->next;
    free(temp);
    list->length--;
    return true;
}
\`\`\`

## 4. 性能分析与比较

| 操作 | 顺序表 | 链表 |
|------|-------|------|
| 查找 | O(1)（已知位置）/ O(n)（按值查找）| O(n) |
| 插入 | O(n) | O(1)（已知位置）/ O(n)（需查找） |
| 删除 | O(n) | O(1)（已知位置）/ O(n)（需查找） |

顺序表和链表各有优势：
- 顺序表适合于需要频繁随机访问的场景，存储密度高，对缓存友好
- 链表适合于需要频繁插入删除的场景，空间利用率高，不需要预先分配内存

## 5. 实验结果
实验实现了顺序表和链表的基本功能，成功完成了数据的存储、查询和修改操作。通过测试用例验证了各项操作的正确性，并对两种结构进行了详细对比分析。

实验测试结果表明，在数据量较小时，顺序表的整体性能略优于链表；而在大量数据需要频繁插入删除的场景中，链表的性能优势明显。在随机访问频繁的应用中，顺序表仍然是更好的选择。

## 6. 实验总结
通过本次实验，我加深了对线性表数据结构的理解，掌握了顺序表和链表的基本操作的实现方法和应用场景。在实验过程中，遇到的主要问题是链表的指针操作容易出错，特别是在删除节点时需要特别注意头节点的处理和内存的释放。

本次实验使我认识到了不同数据结构的选择对算法性能的重要影响，学会了根据具体应用场景选择合适的数据结构。在后续学习中，我将进一步研究更复杂的数据结构，如循环链表、双向链表等，并尝试解决实际应用问题。

## 7. 参考资料
1. 《数据结构》（C语言版）, 严蔚敏等
2. 《算法导论》, Thomas H. Cormen等
3. 《数据结构与算法分析》, Mark Allen Weiss

---
*作者信息*
- 姓名：学生1
- 学号：S2023101
- 班级：计算机科学与技术1班
- 日期：2023-11-08`,
  code: `#include <stdio.h>
#include <stdlib.h>
#include <stdbool.h>

// ===== 顺序表实现 =====
typedef struct {
    int *data;         // 存储数组
    int length;        // 当前长度
    int capacity;      // 最大容量
} SeqList;

// 初始化顺序表
SeqList* initSeqList(int capacity) {
    SeqList *list = (SeqList*)malloc(sizeof(SeqList));
    list->data = (int*)malloc(capacity * sizeof(int));
    list->length = 0;
    list->capacity = capacity;
    return list;
}

// 顺序表插入元素
bool insertSeqList(SeqList *list, int pos, int elem) {
    // 检查位置是否合法及是否需要扩容
    if (pos < 0 || pos > list->length) {
        return false;
    }
    
    // 如果需要扩容
    if (list->length >= list->capacity) {
        int newCapacity = list->capacity * 2;
        int *newData = (int*)realloc(list->data, newCapacity * sizeof(int));
        if (newData == NULL) {
            return false;
        }
        list->data = newData;
        list->capacity = newCapacity;
    }
    
    // 移动元素
    for (int i = list->length; i > pos; i--) {
        list->data[i] = list->data[i-1];
    }
    
    // 插入元素并更新长度
    list->data[pos] = elem;
    list->length++;
    return true;
}

// 顺序表删除元素
bool deleteSeqList(SeqList *list, int pos) {
    // 检查位置是否合法
    if (pos < 0 || pos >= list->length) {
        return false;
    }
    
    // 移动元素
    for (int i = pos; i < list->length - 1; i++) {
        list->data[i] = list->data[i+1];
    }
    
    // 更新长度
    list->length--;
    return true;
}

// 按位置查找元素
int getElem(SeqList *list, int pos) {
    if (pos < 0 || pos >= list->length) {
        return -1; // 假设-1为无效值
    }
    return list->data[pos];
}

// 按值查找元素
int locateElem(SeqList *list, int elem) {
    for (int i = 0; i < list->length; i++) {
        if (list->data[i] == elem) {
            return i;
        }
    }
    return -1; // 未找到
}

// 打印顺序表
void printSeqList(SeqList *list) {
    printf("SeqList: ");
    for (int i = 0; i < list->length; i++) {
        printf("%d ", list->data[i]);
    }
    printf("\\n");
}

// 释放顺序表
void freeSeqList(SeqList *list) {
    free(list->data);
    free(list);
}

// ===== 链表实现 =====
typedef struct Node {
    int data;
    struct Node *next;
} Node;

typedef struct {
    Node *head;
    int length;
} LinkedList;

// 初始化链表
LinkedList* initLinkedList() {
    LinkedList *list = (LinkedList*)malloc(sizeof(LinkedList));
    list->head = NULL;
    list->length = 0;
    return list;
}

// 头部插入节点
void insertHead(LinkedList *list, int elem) {
    Node *newNode = (Node*)malloc(sizeof(Node));
    newNode->data = elem;
    newNode->next = list->head;
    list->head = newNode;
    list->length++;
}

// 尾部插入节点
void insertTail(LinkedList *list, int elem) {
    Node *newNode = (Node*)malloc(sizeof(Node));
    newNode->data = elem;
    newNode->next = NULL;
    
    if (list->head == NULL) {
        list->head = newNode;
    } else {
        Node *current = list->head;
        while (current->next != NULL) {
            current = current->next;
        }
        current->next = newNode;
    }
    list->length++;
}

// 按位置插入节点
bool insertPos(LinkedList *list, int pos, int elem) {
    if (pos < 0 || pos > list->length) {
        return false;
    }
    
    if (pos == 0) {
        insertHead(list, elem);
        return true;
    }
    
    Node *current = list->head;
    for (int i = 0; i < pos - 1; i++) {
        current = current->next;
    }
    
    Node *newNode = (Node*)malloc(sizeof(Node));
    newNode->data = elem;
    newNode->next = current->next;
    current->next = newNode;
    list->length++;
    return true;
}

// 删除节点
bool deleteNode(LinkedList *list, int pos) {
    if (pos < 0 || pos >= list->length || list->head == NULL) {
        return false;
    }
    
    Node *temp;
    if (pos == 0) {
        temp = list->head;
        list->head = list->head->next;
        free(temp);
        list->length--;
        return true;
    }
    
    Node *current = list->head;
    for (int i = 0; i < pos - 1; i++) {
        current = current->next;
    }
    
    temp = current->next;
    current->next = temp->next;
    free(temp);
    list->length--;
    return true;
}

// 反转链表
void reverseList(LinkedList *list) {
    if (list->head == NULL || list->head->next == NULL) {
        return;
    }
    
    Node *prev = NULL;
    Node *current = list->head;
    Node *next = NULL;
    
    while (current != NULL) {
        next = current->next;
        current->next = prev;
        prev = current;
        current = next;
    }
    
    list->head = prev;
}

// 打印链表
void printLinkedList(LinkedList *list) {
    printf("LinkedList: ");
    Node *current = list->head;
    while (current != NULL) {
        printf("%d -> ", current->data);
        current = current->next;
    }
    printf("NULL\\n");
}

// 释放链表内存
void freeLinkedList(LinkedList *list) {
    Node *current = list->head;
    Node *next = NULL;
    
    while (current != NULL) {
        next = current->next;
        free(current);
        current = next;
    }
    
    free(list);
}

// ===== 测试函数 =====
void testSeqList() {
    printf("=== Testing Sequential List ===\\n");
    SeqList *list = initSeqList(5);
    
    // 测试插入
    insertSeqList(list, 0, 10);
    insertSeqList(list, 1, 20);
    insertSeqList(list, 2, 30);
    insertSeqList(list, 1, 15); // 在中间插入
    printSeqList(list);
    
    // 测试删除
    deleteSeqList(list, 2);
    printSeqList(list);
    
    // 测试查找
    int pos = locateElem(list, 10);
    printf("Element 10 found at position: %d\\n", pos);
    
    // 测试扩容
    for (int i = 0; i < 10; i++) {
        insertSeqList(list, list->length, i * 5);
    }
    printSeqList(list);
    
    // 释放内存
    freeSeqList(list);
}

void testLinkedList() {
    printf("\\n=== Testing Linked List ===\\n");
    LinkedList *list = initLinkedList();
    
    // 测试插入
    insertHead(list, 30);
    insertHead(list, 20);
    insertHead(list, 10);
    insertTail(list, 40);
    insertPos(list, 2, 25); // 在中间插入
    printLinkedList(list);
    
    // 测试删除
    deleteNode(list, 1);
    printLinkedList(list);
    
    // 测试反转
    printf("Reversing the linked list...\\n");
    reverseList(list);
    printLinkedList(list);
    
    // 释放内存
    freeLinkedList(list);
}

int main() {
    testSeqList();
    testLinkedList();
    
    printf("\\n=== Performance Comparison ===\\n");
    printf("Sequential List:\\n");
    printf("  Advantages: Fast random access, memory efficient\\n");
    printf("  Disadvantages: Slow insertion/deletion in middle\\n\\n");
    
    printf("Linked List:\\n");
    printf("  Advantages: Fast insertion/deletion, dynamic size\\n");
    printf("  Disadvantages: Slow random access, extra memory for pointers\\n");
    
    return 0;
}`
}

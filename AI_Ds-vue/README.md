# 数据结构课程AI测评系统

这是一个基于Vue3+SpringBoot的数据结构课程AI测评系统前端项目，主要用于课程实验的管理、评测和学习分析。

## 项目特点

- 基于Vue3, Vite, Vue Router, Pinia和Element Plus开发
- 使用ECharts实现多种数据可视化
- AI评测系统对学生提交的实验代码进行评估和反馈
- 个性化的学习分析和练习推荐

## 技术栈

- **前端框架**: Vue3 (使用组合式API)
- **状态管理**: Pinia
- **路由管理**: Vue Router
- **UI组件库**: Element Plus
- **数据可视化**: ECharts
- **HTTP请求**: Axios
- **构建工具**: Vite

## 主要功能

### 学生角色
- 实验列表与详情查看
- 代码提交与评测结果查看
- 学习情况分析
- 自我评估
- 个性化推荐练习
- 个人信息管理

### 教师角色 (待开发)
- 学生管理
- 实验管理
- 教学情况分析
- 实验评审与批注

### 管理员角色 (待开发)
- 系统管理
- 用户管理
- 实验设置
- 评测规则配置

## 项目结构

```
src/
├── api/             # API请求
├── assets/          # 静态资源
├── components/      # 公共组件
├── mock/            # 模拟数据
├── router/          # 路由配置
├── store/           # 状态管理
├── utils/           # 工具函数
└── views/           # 页面视图
    ├── Login.vue    # 登录页
    └── student/     # 学生相关页面
        ├── Dashboard.vue        # 学生仪表盘
        ├── ExperimentList.vue   # 实验列表
        ├── ExperimentDetail.vue # 实验详情
        ├── LearningAnalysis.vue # 学习分析
        ├── Practice.vue         # 推荐练习
        ├── SelfAssessment.vue   # 自我评估
        └── Profile.vue          # 个人信息
```

## 安装与运行

```bash
# 安装依赖
npm install

# 启动开发服务器
npm run serve

# 构建生产版本
npm run build
```

## 开发注意事项

- 本项目使用Element Plus UI组件库，请参考其官方文档
- 数据可视化部分使用ECharts，请熟悉其配置项
- 当前版本使用模拟数据，实际项目中应连接后端API
- 请遵循项目的代码规范和文件组织结构

## 后续规划

- 完善教师和管理员角色功能
- 集成实时代码编辑器
- 添加实时消息通知
- 引入人工智能辅助教学功能
- 优化移动端适配
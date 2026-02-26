<template>
  <div class="system-log">
    <page-header
        class="my-page-header"
      title="系统日志"
      description="系统操作和事件记录"
    />

    <div class="system-log-content">
      <el-card>
        <template #header>
          <div class="card-header">
            <div class="left">
              <span>日志列表</span>
            </div>
            <div class="right">
              <el-input
                placeholder="搜索日志内容"
                v-model="searchKeyword"
                class="search-input"
                clearable
              >
                <template #prefix>
                  <el-icon><Search /></el-icon>
                </template>
              </el-input>
              <el-select v-model="logLevel" placeholder="日志级别" clearable class="level-select">
                <el-option label="全部" value="" />
                <el-option label="信息" value="INFO" />
                <el-option label="警告" value="WARNING" />
                <el-option label="错误" value="ERROR" />
              </el-select>
              <el-button type="danger" @click="clearLogs">清空日志</el-button>
              <el-button type="primary" @click="exportLogs">导出日志</el-button>
            </div>
          </div>
        </template>

        <el-table :data="filteredLogs" v-loading="loading" border style="width: 100%">
          <el-table-column prop="timestamp" label="时间" width="180" sortable />
          <el-table-column label="级别" width="100">
            <template #default="scope">
              <el-tag
                :type="getLogLevelType(scope.row.level)"
                size="small"
              >
                {{ scope.row.level }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="category" label="分类" width="120" />
          <el-table-column prop="message" label="消息内容" min-width="400" show-overflow-tooltip />
          <el-table-column prop="user" label="相关用户" width="120" />
          <el-table-column prop="ip" label="IP地址" width="140" />
          <el-table-column label="操作" width="120" fixed="right">
            <template #default="scope">
              <el-button type="primary" link @click="viewLogDetail(scope.row)">详情</el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="pagination-container">
          <el-pagination
            background
            layout="total, sizes, prev, pager, next, jumper"
            :total="total"
            :page-size="pageSize"
            :current-page="currentPage"
            @size-change="handleSizeChange"
            @current-change="handleCurrentChange"
          />
        </div>
      </el-card>
    </div>

    <!-- 日志详情对话框 -->
    <el-dialog v-model="logDetailVisible" title="日志详情" width="60%">
      <el-descriptions :column="1" border>
        <el-descriptions-item label="时间">{{ selectedLog.timestamp }}</el-descriptions-item>
        <el-descriptions-item label="级别">
          <el-tag :type="getLogLevelType(selectedLog.level)">{{ selectedLog.level }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="分类">{{ selectedLog.category }}</el-descriptions-item>
        <el-descriptions-item label="消息内容">{{ selectedLog.message }}</el-descriptions-item>
        <el-descriptions-item label="相关用户">{{ selectedLog.user }}</el-descriptions-item>
        <el-descriptions-item label="IP地址">{{ selectedLog.ip }}</el-descriptions-item>
        <el-descriptions-item label="浏览器">{{ selectedLog.userAgent || '未知' }}</el-descriptions-item>
        <el-descriptions-item label="完整请求地址" v-if="selectedLog.url">
          {{ selectedLog.url }}
        </el-descriptions-item>
        <el-descriptions-item label="请求参数" v-if="selectedLog.params">
          <pre>{{ selectedLog.params }}</pre>
        </el-descriptions-item>
        <el-descriptions-item label="错误堆栈" v-if="selectedLog.stackTrace">
          <el-collapse>
            <el-collapse-item title="查看错误堆栈信息">
              <pre class="stack-trace">{{ selectedLog.stackTrace }}</pre>
            </el-collapse-item>
          </el-collapse>
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>

    <!-- 清空日志确认对话框 -->
    <el-dialog v-model="clearConfirmVisible" title="警告" width="30%">
      <span>确定要清空所有日志吗？此操作不可撤销!</span>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="clearConfirmVisible = false">取消</el-button>
          <el-button type="danger" @click="confirmClearLogs">确定</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import PageHeader from '../../components/PageHeader.vue'

// 数据加载状态
const loading = ref(false)

// 搜索和筛选
const searchKeyword = ref('')
const logLevel = ref('')

// 分页参数
const total = ref(0)
const pageSize = ref(10)
const currentPage = ref(1)

// 对话框控制
const logDetailVisible = ref(false)
const clearConfirmVisible = ref(false)
const selectedLog = ref({})

// 模拟日志数据
const logs = ref([
  {
    id: 1,
    timestamp: '2023-07-01 10:23:45',
    level: 'INFO',
    category: '用户管理',
    message: '用户登录成功',
    user: '王管理',
    ip: '192.168.0.1',
    userAgent: 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36',
    url: '/api/auth/login'
  },
  {
    id: 2,
    timestamp: '2023-07-01 09:18:22',
    level: 'WARNING',
    category: '权限控制',
    message: '用户尝试访问未授权页面: /admin/system-log',
    user: '张三',
    ip: '192.168.0.2',
    url: '/admin/system-log'
  },
  {
    id: 3,
    timestamp: '2023-07-01 08:45:12',
    level: 'ERROR',
    category: '数据库',
    message: '数据库连接失败',
    user: '系统',
    ip: '127.0.0.1',
    stackTrace: `Error: Connection refused at Database.connect (db.js:42:23)
at async Server.start (server.js:28:5)
at async bootstrap (app.js:15:3)`
  },
  {
    id: 4,
    timestamp: '2023-06-30 22:34:56',
    level: 'INFO',
    category: '系统维护',
    message: '系统备份完成',
    user: '系统',
    ip: '127.0.0.1'
  },
  {
    id: 5,
    timestamp: '2023-06-30 16:28:41',
    level: 'INFO',
    category: '用户管理',
    message: '新用户注册: user123',
    user: '李四',
    ip: '192.168.0.3'
  },
  {
    id: 6,
    timestamp: '2023-06-30 14:12:39',
    level: 'WARNING',
    category: '安全',
    message: '多次失败的登录尝试',
    user: 'unknown',
    ip: '203.0.113.42'
  },
  {
    id: 7,
    timestamp: '2023-06-30 11:35:21',
    level: 'ERROR',
    category: 'API',
    message: '外部API调用超时: 支付服务',
    user: '系统',
    ip: '127.0.0.1',
    url: '/api/payment/process',
    params: JSON.stringify({orderId: 'ORD-123456', amount: 199.99}, null, 2)
  },
  {
    id: 8,
    timestamp: '2023-06-30 10:03:15',
    level: 'INFO',
    category: '实验管理',
    message: '创建了新实验: 数据结构基础',
    user: '王教师',
    ip: '192.168.0.4'
  },
  {
    id: 9,
    timestamp: '2023-06-29 16:42:33',
    level: 'INFO',
    category: '用户管理',
    message: '修改用户信息: zhangsan',
    user: '王管理',
    ip: '192.168.0.1'
  },
  {
    id: 10,
    timestamp: '2023-06-29 15:12:05',
    level: 'ERROR',
    category: '文件系统',
    message: '文件上传失败: 磁盘空间不足',
    user: '张三',
    ip: '192.168.0.2',
    stackTrace: `Error: No space left on device at FileSystem.write (fs.js:75:11)
at async UploadService.saveFile (upload.js:42:8)
at async Router.handleUpload (routes.js:28:12)`
  }
])

// 过滤日志
const filteredLogs = computed(() => {
  let result = logs.value

  if (searchKeyword.value) {
    const keyword = searchKeyword.value.toLowerCase()
    result = result.filter(log =>
      log.message.toLowerCase().includes(keyword) ||
      log.user.toLowerCase().includes(keyword)
    )
  }

  if (logLevel.value) {
    result = result.filter(log => log.level === logLevel.value)
  }

  // 简单的客户端分页
  const start = (currentPage.value - 1) * pageSize.value
  const end = start + pageSize.value
  return result.slice(start, end)
})

// 添加一个新的计算属性来计算总数
const filteredTotal = computed(() => {
  let result = logs.value

  if (searchKeyword.value) {
    const keyword = searchKeyword.value.toLowerCase()
    result = result.filter(log =>
      log.message.toLowerCase().includes(keyword) ||
      log.user.toLowerCase().includes(keyword)
    )
  }

  if (logLevel.value) {
    result = result.filter(log => log.level === logLevel.value)
  }

  return result.length
})

// 监听过滤条件变化更新总数
watch([searchKeyword, logLevel], () => {
  total.value = filteredTotal.value
})

// 获取日志级别类型
const getLogLevelType = (level) => {
  const typeMap = {
    'INFO': 'info',
    'WARNING': 'warning',
    'ERROR': 'danger'
  }
  return typeMap[level] || 'info'
}

// 查看日志详情
const viewLogDetail = (log) => {
  selectedLog.value = { ...log }
  logDetailVisible.value = true
}

// 清空日志
const clearLogs = () => {
  clearConfirmVisible.value = true
}

// 确认清空日志
const confirmClearLogs = () => {
  // 在实际环境中，这里应该调用API
  logs.value = []
  clearConfirmVisible.value = false
  ElMessage.success('日志已清空')
}

// 导出日志
const exportLogs = () => {
  ElMessage.success('日志导出功能已触发（演示）')
  // 实际实现可能涉及后端API调用或前端导出逻辑
}

// 页码改变
const handleCurrentChange = (val) => {
  currentPage.value = val
}

// 每页条数改变
const handleSizeChange = (val) => {
  pageSize.value = val
  currentPage.value = 1
}

// 组件挂载时加载数据
onMounted(() => {
  loading.value = true
  // 模拟API请求延迟
  setTimeout(() => {
    // 使用计算属性获取总数，而不是直接使用logs.value.length
    total.value = filteredTotal.value
    loading.value = false
  }, 500)
})
</script>

<style scoped>
.system-log {
  height: 100%;
}

.system-log-content {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.right {
  display: flex;
  gap: 10px;
  align-items: center;
}

.search-input {
  width: 200px;
}

.level-select {
  width: 120px;
}

.pagination-container {
  margin-top: 20px;
  display: flex;
  justify-content: center;
}

.stack-trace {
  font-family: monospace;
  white-space: pre-wrap;
  background-color: #f5f5f5;
  padding: 10px;
  border-radius: 4px;
  color: #666;
  font-size: 12px;
  max-height: 300px;
  overflow-y: auto;
}

.my-page-header {
  padding: 20px;
}

</style>

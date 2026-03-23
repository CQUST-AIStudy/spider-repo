<template>
  <div class="admin-dashboard">
    <page-header
      class="my-page-header"
      title="管理员控制台"
      description="统一查看 API 资源、PTA 爬虫状态和教师侧数据更新情况"
    />

    <el-skeleton v-if="loading" :rows="12" animated />

    <div v-else class="dashboard-body">
      <section class="hero-grid">
        <el-card class="hero-card hero-card-ai" shadow="hover">
          <div class="hero-label">今日 AI 请求</div>
          <div class="hero-value">{{ formatNumber(stats.aiRequestsUsedToday) }}</div>
          <div class="hero-meta">上限 {{ formatNumber(stats.aiRequestsLimit) }}</div>
          <el-progress :percentage="quotaPercent('ai')" :stroke-width="8" :show-text="false" />
        </el-card>

        <el-card class="hero-card hero-card-tr" shadow="hover">
          <div class="hero-label">今日翻译字符</div>
          <div class="hero-value">{{ formatNumber(stats.translationCharsUsedToday) }}</div>
          <div class="hero-meta">上限 {{ formatNumber(stats.translationCharsLimit) }}</div>
          <el-progress :percentage="quotaPercent('translation')" :stroke-width="8" :show-text="false" status="success" />
        </el-card>

        <el-card class="hero-card hero-card-sync" shadow="hover">
          <div class="hero-label">开启同步班级</div>
          <div class="hero-value">{{ formatNumber(stats.syncEnabledClassCount) }}</div>
          <div class="hero-meta">运行中 {{ formatNumber(stats.runningClassCount) }} 个</div>
          <div class="hero-chip-row">
            <el-tag effect="plain" type="warning">待关注 {{ formatNumber(stats.attentionClassCount) }}</el-tag>
          </div>
        </el-card>

        <el-card class="hero-card hero-card-spider" shadow="hover">
          <div class="hero-label">爬虫与 Cookie</div>
          <div class="hero-status-row">
            <el-tag :type="spider.healthy ? 'success' : 'danger'" effect="dark">
              {{ spider.healthy ? '爬虫在线' : '爬虫离线' }}
            </el-tag>
            <el-tag :type="cookieStatusType(spider.cookieStatus)" effect="plain">
              Cookie {{ cookieStatusText(spider.cookieStatus) }}
            </el-tag>
          </div>
          <div class="hero-meta">{{ spider.baseUrl || '未配置' }}</div>
          <div class="hero-meta" v-if="spider.cookieLastUpdated">
            上次更新 {{ formatDateTime(spider.cookieLastUpdated) }}
          </div>
        </el-card>
      </section>

      <section class="content-grid">
        <div class="main-column">
          <el-card class="panel-card" shadow="never">
            <template #header>
              <div class="panel-header">
                <div>
                  <div class="panel-title">API 资源池</div>
                  <div class="panel-desc">查看 Key 状态、来源、当日用量和更换建议</div>
                </div>
                <el-button text @click="loadDashboard">刷新</el-button>
              </div>
            </template>

            <el-table :data="apiServices" stripe>
              <el-table-column prop="name" label="服务" min-width="120" />
              <el-table-column label="状态" width="110">
                <template #default="{ row }">
                  <el-tag :type="serviceStatusType(row.status)" effect="dark">
                    {{ serviceStatusText(row.status) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="provider" label="Provider" width="120" />
              <el-table-column prop="model" label="模型/用途" min-width="150" />
              <el-table-column label="Key" min-width="180">
                <template #default="{ row }">
                  <div class="mono">{{ row.maskedKey || '未配置' }}</div>
                  <div class="muted">{{ row.envName }} / {{ row.source }}</div>
                </template>
              </el-table-column>
              <el-table-column label="今日用量" min-width="180">
                <template #default="{ row }">
                  <div v-if="row.limit > 0">
                    {{ formatUsage(row.usedToday, row.usageUnit) }} / {{ formatUsage(row.limit, row.usageUnit) }}
                  </div>
                  <div v-else class="muted">未接入统计</div>
                </template>
              </el-table-column>
              <el-table-column prop="actionHint" label="建议动作" min-width="220" />
            </el-table>
          </el-card>

          <el-card class="panel-card" shadow="never">
            <template #header>
              <div class="panel-header">
                <div>
                  <div class="panel-title">教师侧数据时效性</div>
                  <div class="panel-desc">管理员可按班级查看 PTA 增量同步状态并直接触发</div>
                </div>
                <el-button text @click="loadDashboard">刷新班级状态</el-button>
              </div>
            </template>

            <el-table :data="classes" stripe>
              <el-table-column prop="name" label="班级" min-width="150" />
              <el-table-column prop="teacherName" label="教师" width="130" />
              <el-table-column prop="ptaKeyword" label="PTA 关键词" min-width="140" />
              <el-table-column label="同步开关" width="100">
                <template #default="{ row }">
                  <el-tag :type="row.syncEnabled ? 'success' : 'info'" effect="plain">
                    {{ row.syncEnabled ? '已开启' : '未开启' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="状态" width="110">
                <template #default="{ row }">
                  <el-tag :type="syncStatusType(row.syncStatus)" effect="dark">
                    {{ syncStatusText(row.syncStatus) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="上次成功更新" min-width="165">
                <template #default="{ row }">
                  {{ row.lastSyncAt ? formatDateTime(row.lastSyncAt) : '未同步' }}
                </template>
              </el-table-column>
              <el-table-column label="关注项" min-width="170">
                <template #default="{ row }">
                  <el-tag v-if="row.attention" type="warning" effect="plain">{{ row.attentionReason }}</el-tag>
                  <span v-else class="muted">正常</span>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="190" fixed="right">
                <template #default="{ row }">
                  <div class="action-row">
                    <el-button
                      type="primary"
                      link
                      :disabled="!row.syncEnabled || !row.ptaKeyword || syncingClassId === row.id"
                      @click="triggerSync(row, 'incremental')"
                    >
                      增量同步
                    </el-button>
                    <el-button
                      type="danger"
                      link
                      :disabled="!row.syncEnabled || !row.ptaKeyword || syncingClassId === row.id"
                      @click="confirmFullSync(row)"
                    >
                      全量同步
                    </el-button>
                  </div>
                </template>
              </el-table-column>
            </el-table>
          </el-card>

          <el-card class="panel-card" shadow="never">
            <template #header>
              <div class="panel-header">
                <div>
                  <div class="panel-title">最近 PTA 任务</div>
                  <div class="panel-desc">查看增量更新了哪些题集、提交记录和导出数据</div>
                </div>
                <el-button text @click="loadDashboard">刷新任务</el-button>
              </div>
            </template>

            <el-table :data="recentTasks" stripe>
              <el-table-column prop="taskId" label="任务 ID" min-width="110" />
              <el-table-column prop="keyword" label="关键词" min-width="120" />
              <el-table-column label="模式" width="100">
                <template #default="{ row }">
                  <el-tag :type="taskModeType(row.mode)" effect="plain">{{ modeText(row.mode) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="状态" width="100">
                <template #default="{ row }">
                  <el-tag :type="syncStatusType(row.status)" effect="dark">{{ syncStatusText(row.status) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="增量结果" min-width="240">
                <template #default="{ row }">
                  新题集 {{ row.newSetsCount || 0 }}，刷新 {{ row.refreshedCount || 0 }}，提交 {{ row.submissionsCount || 0 }}
                </template>
              </el-table-column>
              <el-table-column label="创建时间" min-width="160">
                <template #default="{ row }">
                  {{ formatDateTime(row.createdAt) }}
                </template>
              </el-table-column>
            </el-table>
          </el-card>
        </div>

        <div class="side-column">
          <el-card class="panel-card side-status-card" shadow="never">
            <template #header>
              <div class="panel-header">
                <div>
                  <div class="panel-title">爬虫运行状态</div>
                  <div class="panel-desc">管理员可优先处理 Cookie 失效和爬虫离线</div>
                </div>
              </div>
            </template>

            <div class="status-list">
              <div class="status-item">
                <span class="status-label">爬虫服务</span>
                <el-tag :type="spider.healthy ? 'success' : 'danger'" effect="dark">
                  {{ spider.healthy ? '在线' : '离线' }}
                </el-tag>
              </div>
              <div class="status-item">
                <span class="status-label">Cookie 状态</span>
                <el-tag :type="cookieStatusType(spider.cookieStatus)" effect="plain">
                  {{ cookieStatusText(spider.cookieStatus) }}
                </el-tag>
              </div>
              <div class="status-item">
                <span class="status-label">Cookie 更新时间</span>
                <span>{{ spider.cookieLastUpdated ? formatDateTime(spider.cookieLastUpdated) : '未知' }}</span>
              </div>
              <div class="status-item">
                <span class="status-label">爬虫地址</span>
                <span class="mono compact">{{ spider.baseUrl || '未配置' }}</span>
              </div>
            </div>

            <el-alert
              v-if="spider.cookieError"
              class="inline-alert"
              type="warning"
              :closable="false"
              show-icon
              :title="spider.cookieError"
            />
            <el-alert
              v-if="spider.healthError"
              class="inline-alert"
              type="error"
              :closable="false"
              show-icon
              :title="spider.healthError"
            />
          </el-card>

          <el-card class="panel-card" shadow="never">
            <template #header>
              <div class="panel-header">
                <div>
                  <div class="panel-title">Cookie 维护</div>
                  <div class="panel-desc">管理员可直接提交新的 PTA Cookie 恢复同步</div>
                </div>
              </div>
            </template>

            <el-form label-position="top">
              <el-form-item label="Cookie JSON">
                <el-input
                  v-model="cookieInput"
                  type="textarea"
                  :rows="8"
                  placeholder='例如：[{"name":"PTASession","value":"xxx","domain":".pintia.cn"}]'
                />
              </el-form-item>
            </el-form>
            <el-button type="primary" :loading="cookieSubmitting" :disabled="!cookieInput.trim()" @click="submitCookieForm">
              验证并保存 Cookie
            </el-button>
            <div v-if="cookieResult" class="cookie-result">
              <el-tag :type="cookieResult.valid ? 'success' : 'danger'" effect="dark">
                {{ cookieResult.valid ? '验证成功' : '验证失败' }}
              </el-tag>
              <span class="cookie-message">{{ cookieResult.message }}</span>
            </div>
          </el-card>

          <el-card class="panel-card" shadow="never">
            <template #header>
              <div class="panel-header">
                <div>
                  <div class="panel-title">当日用量 Top 用户</div>
                  <div class="panel-desc">便于判断哪些教师最依赖 AI/翻译资源</div>
                </div>
              </div>
            </template>

            <el-table :data="topUsers" size="small">
              <el-table-column prop="username" label="账号" min-width="110" />
              <el-table-column label="AI 请求" width="80">
                <template #default="{ row }">{{ formatNumber(row.aiRequests) }}</template>
              </el-table-column>
              <el-table-column label="翻译字符" min-width="110">
                <template #default="{ row }">{{ formatNumber(row.translationChars) }}</template>
              </el-table-column>
            </el-table>
          </el-card>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '../../components/PageHeader.vue'
import api from '../../api'
import { submitPtaCookie } from '../../api/tap'

const loading = ref(false)
const syncingClassId = ref(null)
const cookieSubmitting = ref(false)
const cookieInput = ref('')
const cookieResult = ref(null)
const dashboard = ref({
  stats: {},
  quota: { topUsers: [] },
  apiServices: [],
  spider: {},
  classes: [],
  recentTasks: []
})

const stats = computed(() => dashboard.value.stats || {})
const apiServices = computed(() => dashboard.value.apiServices || [])
const spider = computed(() => dashboard.value.spider || {})
const classes = computed(() => dashboard.value.classes || [])
const recentTasks = computed(() => dashboard.value.recentTasks || [])
const topUsers = computed(() => dashboard.value.quota?.topUsers || [])

onMounted(() => {
  loadDashboard()
})

async function loadDashboard() {
  loading.value = true
  try {
    const res = await api.getAdminDashboardOverview()
    dashboard.value = res?.data ?? res ?? dashboard.value
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || error.message || '管理员数据加载失败')
  } finally {
    loading.value = false
  }
}

async function submitCookieForm() {
  cookieSubmitting.value = true
  cookieResult.value = null
  try {
    const res = await submitPtaCookie(cookieInput.value.trim())
    const data = res?.data ?? res ?? {}
    cookieResult.value = {
      valid: !!data.valid,
      message: data.message || (data.valid ? 'Cookie 已更新' : 'Cookie 无效')
    }
    if (data.valid) {
      ElMessage.success('Cookie 更新成功')
      cookieInput.value = ''
      await loadDashboard()
    } else {
      ElMessage.warning(cookieResult.value.message)
    }
  } catch (error) {
    cookieResult.value = { valid: false, message: error.message || 'Cookie 更新失败' }
    ElMessage.error(cookieResult.value.message)
  } finally {
    cookieSubmitting.value = false
  }
}

async function triggerSync(row, mode, force = false) {
  syncingClassId.value = row.id
  try {
    const res = await api.triggerAdminClassSync(row.id, { mode, force })
    const data = res?.data ?? res ?? {}
    if (data.blocked) {
      ElMessage.warning(data.message || '任务被系统拦截')
    } else {
      ElMessage.success(data.message || `${row.name} 已提交${modeText(mode)}`)
    }
    await loadDashboard()
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || error.message || '同步触发失败')
  } finally {
    syncingClassId.value = null
  }
}

async function confirmFullSync(row) {
  try {
    await ElMessageBox.confirm(
      `确认对班级“${row.name}”执行全量同步？该操作会重新抓取内容、提交记录和导出数据。`,
      '全量同步确认',
      {
        confirmButtonText: '确认同步',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    await triggerSync(row, 'full', true)
  } catch (error) {
    void error
  }
}

function quotaPercent(kind) {
  if (kind === 'ai') {
    return percent(stats.value.aiRequestsUsedToday, stats.value.aiRequestsLimit)
  }
  return percent(stats.value.translationCharsUsedToday, stats.value.translationCharsLimit)
}

function percent(used, limit) {
  if (!limit || limit <= 0) return 0
  return Math.min(100, Math.round((Number(used || 0) / Number(limit)) * 100))
}

function formatNumber(value) {
  return Number(value || 0).toLocaleString('zh-CN')
}

function formatUsage(value, unit) {
  if (unit === 'chars') return `${formatNumber(value)} 字`
  if (unit === 'requests') return `${formatNumber(value)} 次`
  return formatNumber(value)
}

function formatDateTime(value) {
  if (!value) return '未知'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value)
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

function serviceStatusType(status) {
  if (status === 'OK') return 'success'
  if (status === 'WARN') return 'warning'
  if (status === 'CRITICAL') return 'danger'
  return 'info'
}

function serviceStatusText(status) {
  return {
    OK: '正常',
    WARN: '预警',
    CRITICAL: '紧急',
    MISSING: '缺失'
  }[status] || status
}

function syncStatusType(status) {
  return {
    SUCCESS: 'success',
    RUNNING: 'warning',
    FAILED: 'danger',
    IDLE: 'info',
    QUEUED: 'info'
  }[status] || 'info'
}

function syncStatusText(status) {
  return {
    SUCCESS: '成功',
    RUNNING: '运行中',
    FAILED: '失败',
    IDLE: '空闲',
    QUEUED: '排队中'
  }[status] || status || '未知'
}

function cookieStatusType(status) {
  return {
    OK: 'success',
    EXPIRED: 'danger',
    UNKNOWN: 'warning'
  }[status] || 'info'
}

function cookieStatusText(status) {
  return {
    OK: '正常',
    EXPIRED: '已过期',
    UNKNOWN: '未知'
  }[status] || status || '未知'
}

function modeText(mode) {
  return {
    incremental: '增量',
    submissions: '提交',
    refresh: '刷新导出',
    full: '全量'
  }[mode] || mode
}

function taskModeType(mode) {
  return {
    incremental: 'primary',
    submissions: 'success',
    refresh: 'warning',
    full: 'danger'
  }[mode] || 'info'
}
</script>

<style scoped>
.admin-dashboard {
  min-height: 100%;
}

.my-page-header {
  padding: 20px 20px 0;
}

.dashboard-body {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.hero-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.hero-card {
  border: none;
  overflow: hidden;
}

.hero-card :deep(.el-card__body) {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 150px;
}

.hero-card-ai {
  background: linear-gradient(135deg, #16324f, #245c7a);
  color: #f3f7fb;
}

.hero-card-tr {
  background: linear-gradient(135deg, #17463a, #1d6f59);
  color: #effbf6;
}

.hero-card-sync {
  background: linear-gradient(135deg, #5b3a16, #8d5f1f);
  color: #fff8ef;
}

.hero-card-spider {
  background: linear-gradient(135deg, #3c234f, #6d3e92);
  color: #faf5ff;
}

.hero-label {
  font-size: 14px;
  opacity: 0.88;
}

.hero-value {
  font-size: 34px;
  font-weight: 700;
  line-height: 1;
}

.hero-meta {
  font-size: 13px;
  opacity: 0.8;
  word-break: break-all;
}

.hero-chip-row,
.hero-status-row {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.content-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.9fr) minmax(320px, 0.95fr);
  gap: 16px;
  align-items: start;
}

.main-column,
.side-column {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.panel-card {
  border-radius: 18px;
  border: 1px solid #e7ebf0;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.panel-title {
  font-size: 16px;
  font-weight: 700;
  color: #1f2937;
}

.panel-desc {
  margin-top: 4px;
  font-size: 12px;
  color: #667085;
}

.status-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.status-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.status-label {
  color: #667085;
}

.inline-alert {
  margin-top: 12px;
}

.action-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.mono {
  font-family: Consolas, 'Courier New', monospace;
}

.compact {
  font-size: 12px;
}

.muted {
  color: #98a2b3;
  font-size: 12px;
}

.cookie-result {
  margin-top: 12px;
  display: flex;
  align-items: center;
  gap: 10px;
}

.cookie-message {
  color: #344054;
  font-size: 13px;
}

@media (max-width: 1280px) {
  .hero-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .content-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .hero-grid {
    grid-template-columns: 1fr;
  }

  .my-page-header {
    padding-left: 0;
    padding-right: 0;
  }
}
</style>

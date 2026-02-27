<template>
  <div class="sync-panel">
    <page-header title="PTA 数据同步" description="管理 PTA 平台数据爬取、同步状态和 Cookie 维护" />

    <el-alert v-if="cookieStatus === 'EXPIRED'" title="PTA Cookie 已过期"
      description="自动爬取已暂停，请在下方提交新 Cookie 恢复同步。管理员已收到告警通知。"
      type="error" show-icon :closable="false" style="margin-bottom:16px" />
    <el-alert v-if="cookieStatus === 'UNKNOWN'" title="Cookie 状态未知"
      description="爬虫服务可能未启动，或尚未检测到 Cookie。"
      type="warning" show-icon :closable="false" style="margin-bottom:16px" />

    <el-row :gutter="16">
      <el-col :span="14">
        <el-card class="g-card">
          <template #header><span>数据同步操作</span></template>
          <div class="status-row">
            <div class="status-item">
              <span class="status-label">同步关键词</span>
              <el-tag v-if="currentKeyword" type="primary" effect="plain" size="small">{{ currentKeyword }}</el-tag>
              <el-tag v-else type="warning" effect="plain" size="small">未设置（请在班级管理中配置PTA关键词）</el-tag>
            </div>
            <div class="status-item">
              <span class="status-label">Cookie</span>
              <el-tag :type="cookieTagType" effect="dark" size="small">{{ cookieStatusText }}</el-tag>
            </div>
            <div class="status-item">
              <span class="status-label">爬虫服务</span>
              <el-tag :type="spiderAlive ? 'success' : 'danger'" effect="dark" size="small">
                {{ spiderAlive ? '运行中' : '未启动' }}
              </el-tag>
            </div>
            <div class="status-item" v-if="lastSync">
              <span class="status-label">上次更新</span>
              <span class="status-val">{{ lastSync }}</span>
            </div>
          </div>
          <div v-if="cooldownInfo" class="cooldown-bar">
            <div class="cooldown-item" v-for="(info, key) in cooldownInfo" :key="key">
              <el-icon :color="info.allowed ? '#1e8e3e' : '#e37400'">
                <CircleCheck v-if="info.allowed" /><Clock v-else />
              </el-icon>
              <span class="cooldown-label">{{ {submissions:'提交记录',exports:'导出数据'}[key] }}</span>
              <span v-if="info.allowed" class="cooldown-ok">可执行</span>
              <span v-else class="cooldown-wait">冷却中 {{ info.remaining_human }}（上次 {{ info.last_time }}）</span>
            </div>
          </div>
          <el-divider />
          <div class="force-row">
            <el-switch v-model="forceMode" active-text="强制更新" inactive-text="正常模式" />
            <span class="force-hint" v-if="forceMode">跳过冷却限制，请谨慎使用以保护 PTA 平台</span>
          </div>
          <div class="sync-actions">
            <div class="sync-action-item">
              <div class="action-info">
                <div class="action-title">增量同步</div>
                <div class="action-desc">检测新题目集，爬取内容+提交+导出（仅新增）</div>
              </div>
              <el-button type="primary" :loading="syncLoading === 'incremental'"
                :disabled="!!syncLoading || cookieStatus === 'EXPIRED'" @click="triggerSync('incremental')">
                开始同步
              </el-button>
            </div>
            <div class="sync-action-item">
              <div class="action-info">
                <div class="action-title">拉取提交记录</div>
                <div class="action-desc">拉取已有题目集的最新提交（轻量，冷却 4h）</div>
              </div>
              <el-button type="success" :loading="syncLoading === 'submissions'"
                :disabled="!!syncLoading || cookieStatus === 'EXPIRED'" @click="triggerSync('submissions')">
                拉取提交
              </el-button>
            </div>
            <div class="sync-action-item">
              <div class="action-info">
                <div class="action-title">刷新导出</div>
                <div class="action-desc">重新导出成绩单/答题卡/代码（较重，冷却 24h）</div>
              </div>
              <el-button type="warning" :loading="syncLoading === 'refresh'"
                :disabled="!!syncLoading || cookieStatus === 'EXPIRED'" @click="triggerSync('refresh')">
                刷新导出
              </el-button>
            </div>
            <div class="sync-action-item">
              <div class="action-info">
                <div class="action-title">全量同步</div>
                <div class="action-desc">增量 + 提交 + 导出，耗时较长</div>
              </div>
              <el-button type="danger" :loading="syncLoading === 'full'"
                :disabled="!!syncLoading || cookieStatus === 'EXPIRED'" @click="triggerSync('full')">
                全量同步
              </el-button>
            </div>
          </div>
          <!-- 当前任务进度 -->
          <div v-if="currentTask" class="task-progress">
            <el-divider />
            <div class="task-info">
              <span>任务 {{ currentTask.task_id }}</span>
              <el-tag :type="taskTagType" size="small">{{ taskStatusText }}</el-tag>
              <span v-if="currentTask.force" class="force-badge">强制</span>
            </div>
            <el-progress v-if="currentTask.status === 'RUNNING'" :percentage="50" :indeterminate="true" status="warning" />
            <div v-if="currentTask.status === 'SUCCESS'" class="task-result">
              新增 {{ currentTask.new_sets_count }} 个题目集，
              刷新 {{ currentTask.refreshed_count }} 个，
              提交记录 {{ currentTask.submissions_count }} 条
            </div>
            <div v-if="currentTask.skipped_cooldown && currentTask.skipped_cooldown.length" class="task-skipped">
              跳过（冷却中）: {{ currentTask.skipped_cooldown.join('、') }}
            </div>
            <div v-if="currentTask.error" class="task-error">{{ currentTask.error }}</div>
          </div>
        </el-card>

        <!-- 同步记录 -->
        <el-card class="g-card" style="margin-top:16px" v-if="taskHistory.length">
          <template #header><span>最近同步记录</span></template>
          <el-table :data="taskHistory" size="small" stripe max-height="240">
            <el-table-column prop="task_id" label="任务ID" width="110" />
            <el-table-column prop="keyword" label="关键词" width="100" />
            <el-table-column prop="mode" label="模式" width="90">
              <template #default="{ row }">
                <el-tag size="small" :type="modeTagType(row.mode)">{{ modeCn(row.mode) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="80">
              <template #default="{ row }">
                <el-tag size="small" :type="row.status==='SUCCESS'?'success':row.status==='FAILED'?'danger':'warning'">
                  {{ row.status }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="强制" width="50">
              <template #default="{ row }">{{ row.force ? '是' : '' }}</template>
            </el-table-column>
            <el-table-column prop="created_at" label="创建时间" min-width="140" />
          </el-table>
        </el-card>
      </el-col>

      <!-- 右: Cookie 管理 -->
      <el-col :span="10">
        <el-card class="g-card">
          <template #header><span>Cookie 管理</span></template>
          <div class="cookie-help">
            <p>当自动爬取因 Cookie 过期而失败时，需要手动更新 Cookie。</p>
            <el-collapse>
              <el-collapse-item title="如何获取 Cookie？">
                <ol class="cookie-steps">
                  <li>在浏览器中登录 <a href="https://pintia.cn" target="_blank">PTA 平台</a></li>
                  <li>按 F12 打开开发者工具，切换到「应用」标签</li>
                  <li>在左侧找到「Cookie」→「https://pintia.cn」</li>
                  <li>找到 <code>PTASession</code>，复制其值</li>
                  <li>或安装「EditThisCookie」扩展，导出全部 Cookie 为 JSON</li>
                </ol>
              </el-collapse-item>
            </el-collapse>
          </div>
          <el-divider />
          <el-form label-position="top">
            <el-form-item label="Cookie JSON">
              <el-input v-model="cookieInput" type="textarea" :rows="8"
                placeholder='粘贴 Cookie JSON 数组，格式如:
[{"name":"PTASession","value":"xxx","domain":".pintia.cn"}]' />
            </el-form-item>
            <el-button type="primary" :loading="cookieSubmitting" @click="submitCookieHandler"
              :disabled="!cookieInput.trim()">
              验证并保存 Cookie
            </el-button>
          </el-form>
          <div v-if="cookieResult" class="cookie-result" :class="cookieResult.valid ? 'valid' : 'invalid'">
            <el-icon v-if="cookieResult.valid"><CircleCheck /></el-icon>
            <el-icon v-else><CircleClose /></el-icon>
            <span>{{ cookieResult.message }}</span>
          </div>
        </el-card>

        <!-- 频率保护说明 -->
        <el-card class="g-card" style="margin-top:16px">
          <template #header><span>频率保护策略</span></template>
          <div class="freq-info">
            <div class="freq-item">
              <span class="freq-type">题目内容</span>
              <span class="freq-desc">只爬一次，发布后不变</span>
            </div>
            <div class="freq-item">
              <span class="freq-type">提交记录</span>
              <span class="freq-desc">冷却 4 小时，拉取最新提交</span>
            </div>
            <div class="freq-item">
              <span class="freq-type">导出数据</span>
              <span class="freq-desc">冷却 24 小时，重新导出成绩单/代码</span>
            </div>
            <div class="freq-item">
              <span class="freq-type">API 限速</span>
              <span class="freq-desc">令牌桶 20 请求/分钟</span>
            </div>
            <p class="freq-note">开启「强制更新」可跳过冷却，但请谨慎使用。</p>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { CircleCheck, CircleClose, Clock } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '../../components/PageHeader.vue'
import { getPtaCookieStatus, submitPtaCookie } from '../../api/tap'
import { useUserStore } from '../../store'
import axios from 'axios'

const SPIDER_URL = 'http://localhost:8100'
const userStore = useUserStore()

const currentKeyword = computed(() => userStore.selectedClass?.ptaKeyword || userStore.selectedClass?.name || '')

const cookieStatus = ref('UNKNOWN')
const spiderAlive = ref(false)
const lastSync = ref('')
const syncLoading = ref('')
const currentTask = ref(null)
const taskHistory = ref([])
const cookieInput = ref('')
const cookieSubmitting = ref(false)
const cookieResult = ref(null)
const forceMode = ref(false)
const cooldownInfo = ref(null)
let pollTimer = null

const cookieTagType = computed(() => {
  if (cookieStatus.value === 'OK') return 'success'
  if (cookieStatus.value === 'EXPIRED') return 'danger'
  return 'warning'
})
const cookieStatusText = computed(() => {
  return { OK: '正常', EXPIRED: '已过期', UNKNOWN: '未知' }[cookieStatus.value] || cookieStatus.value
})
const taskTagType = computed(() => {
  if (!currentTask.value) return 'info'
  const s = currentTask.value.status
  return s === 'SUCCESS' ? 'success' : s === 'FAILED' ? 'danger' : s === 'RUNNING' ? 'warning' : 'info'
})
const taskStatusText = computed(() => {
  if (!currentTask.value) return ''
  return { QUEUED:'排队中', RUNNING:'运行中', SUCCESS:'完成', FAILED:'失败' }[currentTask.value.status] || currentTask.value.status
})

const modeCn = (m) => ({ incremental:'增量', submissions:'提交', refresh:'刷新', full:'全量' }[m] || m)
const modeTagType = (m) => ({ full:'danger', refresh:'warning', submissions:'success', incremental:'primary' }[m] || '')

async function loadCookieStatus() {
  try {
    const res = await getPtaCookieStatus()
    const d = res?.data || res
    cookieStatus.value = d?.status || 'UNKNOWN'
    lastSync.value = d?.lastUpdated || d?.updated_at || ''
    spiderAlive.value = true
  } catch {
    try {
      const r = await axios.get(`${SPIDER_URL}/health`, { timeout: 3000 })
      spiderAlive.value = r.status === 200
    } catch { spiderAlive.value = false }
  }
}

async function loadCooldown() {
  const keyword = userStore.selectedClass?.ptaKeyword || userStore.selectedClass?.name || '数据结构'
  try {
    const r = await axios.get(`${SPIDER_URL}/cooldown/${encodeURIComponent(keyword)}`, { timeout: 5000 })
    cooldownInfo.value = r.data
  } catch { /* spider not running */ }
}

async function loadTaskHistory() {
  try {
    const r = await axios.get(`${SPIDER_URL}/tasks`, { timeout: 5000 })
    taskHistory.value = r.data || []
  } catch { /* spider not running */ }
}

async function triggerSync(mode) {
  const keyword = userStore.selectedClass?.ptaKeyword || userStore.selectedClass?.name || '数据结构'

  // 强制模式需二次确认
  if (forceMode.value) {
    try {
      await ElMessageBox.confirm(
        '强制更新将跳过冷却时间限制，频繁请求可能影响 PTA 平台。确定继续？',
        '强制更新确认', { confirmButtonText: '确定强制', cancelButtonText: '取消', type: 'warning' }
      )
    } catch { return }
  }

  syncLoading.value = mode
  try {
    const r = await axios.post(`${SPIDER_URL}/crawl`, {
      keyword, mode, force: forceMode.value,
      class_id: userStore.selectedClass?.id || null
    }, { timeout: 10000 })

    // 冷却拦截
    if (r.data?.blocked) {
      ElMessage.warning(r.data.message)
      syncLoading.value = ''
      return
    }

    const taskId = r.data?.task_id
    if (taskId) {
      currentTask.value = { task_id: taskId, status: 'QUEUED', new_sets_count: 0,
        refreshed_count: 0, submissions_count: 0, error: null, skipped_cooldown: [], force: forceMode.value }
      pollTaskStatus(taskId)
    }
    ElMessage.success(r.data?.message || '任务已提交')
  } catch (e) {
    ElMessage.error('提交失败: ' + (e.response?.data?.detail || e.message))
  } finally {
    syncLoading.value = ''
  }
}

function pollTaskStatus(taskId) {
  if (pollTimer) clearInterval(pollTimer)
  pollTimer = setInterval(async () => {
    try {
      const r = await axios.get(`${SPIDER_URL}/status/${taskId}`, { timeout: 5000 })
      currentTask.value = r.data
      if (r.data.status === 'SUCCESS' || r.data.status === 'FAILED') {
        clearInterval(pollTimer)
        pollTimer = null
        loadTaskHistory()
        loadCooldown()
        if (r.data.status === 'SUCCESS') ElMessage.success('数据同步完成')
        else ElMessage.error('同步失败: ' + (r.data.error || '未知错误'))
      }
    } catch { /* ignore */ }
  }, 3000)
}

async function submitCookieHandler() {
  cookieSubmitting.value = true
  cookieResult.value = null
  try {
    const res = await submitPtaCookie(cookieInput.value.trim())
    const d = res?.data || res
    cookieResult.value = { valid: d?.valid, message: d?.message || (d?.valid ? 'Cookie 有效' : 'Cookie 无效') }
    if (d?.valid) {
      cookieStatus.value = 'OK'
      ElMessage.success('Cookie 更新成功')
    }
  } catch (e) {
    cookieResult.value = { valid: false, message: '提交失败: ' + e.message }
  } finally {
    cookieSubmitting.value = false
  }
}

onMounted(() => { loadCookieStatus(); loadTaskHistory(); loadCooldown() })
onBeforeUnmount(() => { if (pollTimer) clearInterval(pollTimer) })
</script>

<style scoped>
.sync-panel { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif; }
.g-card { border-radius: 12px; border: 1px solid #dadce0; }
.g-card :deep(.el-card__header) { font-size: 14px; font-weight: 600; color: #202124; }

.status-row { display: flex; gap: 24px; align-items: center; flex-wrap: wrap; }
.status-item { display: flex; align-items: center; gap: 8px; }
.status-label { font-size: 13px; color: #5f6368; }
.status-val { font-size: 13px; color: #202124; font-weight: 500; }

.cooldown-bar { display: flex; gap: 20px; margin-top: 12px; flex-wrap: wrap; }
.cooldown-item { display: flex; align-items: center; gap: 6px; font-size: 12.5px; }
.cooldown-label { color: #3c4043; font-weight: 500; }
.cooldown-ok { color: #1e8e3e; }
.cooldown-wait { color: #e37400; }

.force-row { display: flex; align-items: center; gap: 12px; margin-bottom: 14px; }
.force-hint { font-size: 12px; color: #d93025; }

.sync-actions { display: flex; flex-direction: column; gap: 12px; }
.sync-action-item {
  display: flex; align-items: center; justify-content: space-between;
  padding: 12px 16px; border-radius: 10px; background: #f8f9fa;
  border: 1px solid #e8eaed; transition: box-shadow 0.2s;
}
.sync-action-item:hover { box-shadow: 0 2px 8px rgba(0,0,0,0.06); }
.action-info { flex: 1; }
.action-title { font-size: 14px; font-weight: 600; color: #202124; margin-bottom: 2px; }
.action-desc { font-size: 12px; color: #5f6368; }

.task-progress { margin-top: 4px; }
.task-info { display: flex; align-items: center; gap: 10px; margin-bottom: 8px; font-size: 13px; color: #202124; }
.force-badge { font-size: 11px; color: #d93025; background: #fce8e6; padding: 1px 6px; border-radius: 4px; }
.task-result { font-size: 13px; color: #1e8e3e; margin-top: 6px; }
.task-skipped { font-size: 12px; color: #e37400; margin-top: 4px; }
.task-error { font-size: 13px; color: #d93025; margin-top: 6px; }

.cookie-help p { font-size: 13px; color: #5f6368; margin: 0 0 10px; }
.cookie-steps { padding-left: 18px; font-size: 12.5px; color: #3c4043; line-height: 1.8; }
.cookie-steps code { background: #f1f3f4; padding: 1px 5px; border-radius: 4px; font-size: 12px; }
.cookie-result {
  display: flex; align-items: center; gap: 6px; margin-top: 12px;
  padding: 10px 14px; border-radius: 8px; font-size: 13px;
}
.cookie-result.valid { background: #e6f4ea; color: #1e8e3e; }
.cookie-result.invalid { background: #fce8e6; color: #d93025; }

.freq-info { font-size: 13px; }
.freq-item { display: flex; gap: 10px; padding: 6px 0; border-bottom: 1px solid #f1f3f4; }
.freq-item:last-of-type { border-bottom: none; }
.freq-type { font-weight: 600; color: #202124; min-width: 70px; }
.freq-desc { color: #5f6368; }
.freq-note { font-size: 12px; color: #e37400; margin-top: 8px; }
</style>

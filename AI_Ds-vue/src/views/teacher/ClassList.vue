<template>
  <div class="class-list">
    <page-header class="my-page-header" title="班级管理" description="管理教学班级和学生信息">
      <el-button type="primary" @click="openCreateDialog">
        <el-icon><Plus /></el-icon> 新增班级
      </el-button>
    </page-header>

    <!-- PTA Cookie 过期告警 -->
    <el-alert
      v-if="cookieStatus === 'EXPIRED'"
      title="PTA 登录凭证已过期"
      type="warning"
      :closable="false"
      show-icon
      style="margin-bottom: 16px; border-radius: 12px"
    >
      <template #default>
        <div style="display: flex; align-items: center; gap: 12px; flex-wrap: wrap">
          <span style="font-size: 13px; color: #5f6368">
            系统自动登录失败，PTA数据同步暂停。请手动更新 Cookie 以恢复同步。
          </span>
          <el-button type="warning" size="small" @click="openCookieDialog" style="border-radius: 100px">
            更新 Cookie
          </el-button>
        </div>
      </template>
    </el-alert>

    <!-- 班级卡片列表 -->
    <div class="class-cards" v-loading="loading">
      <el-empty v-if="classes.length === 0 && !loading" description="暂无班级，点击上方按钮创建">
        <el-button type="primary" @click="openCreateDialog">创建第一个班级</el-button>
      </el-empty>

      <el-row :gutter="16">
        <el-col :span="8" v-for="cls in classes" :key="cls.id" style="margin-bottom: 16px">
          <el-card shadow="hover" class="class-card">
            <template #header>
              <div class="card-header">
                <span class="class-name">{{ cls.name }}</span>
                <el-dropdown trigger="click">
                  <el-icon style="cursor: pointer"><MoreFilled /></el-icon>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item @click="editClass(cls)">编辑班级</el-dropdown-item>
                      <el-dropdown-item @click="manageStudents(cls)">学生管理</el-dropdown-item>
                      <el-dropdown-item divided @click="confirmDelete(cls)" style="color: #f56c6c">删除班级</el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </div>
            </template>
            <div class="class-info">
              <div class="info-row">
                <span class="label">班级号</span>
                <el-tag size="small" type="info" effect="plain">{{ cls.classCode }}</el-tag>
                <el-button link size="small" @click="copyCode(cls.classCode)" style="margin-left: 4px">复制</el-button>
              </div>
              <div class="info-row">
                <span class="label">加入密码</span>
                <span>{{ cls.joinPassword }}</span>
              </div>
              <div class="info-row" v-if="cls.grade">
                <span class="label">年级</span>
                <span>{{ cls.grade }}</span>
              </div>
              <div class="info-row" v-if="cls.courseName">
                <span class="label">课程</span>
                <span>{{ cls.courseName }}</span>
              </div>
              <div class="info-row">
                <span class="label">学生人数</span>
                <el-badge :value="cls.studentCount" type="primary" />
              </div>
              <div class="info-row" v-if="cls.ptaKeyword">
                <span class="label">PTA同步</span>
                <el-tag size="small" :type="syncTagType(cls.syncStatus)" effect="plain">
                  {{ syncStatusText(cls.syncStatus) }}
                </el-tag>
                <span v-if="cls.lastSyncAt" style="font-size: 11px; color: #b0b0b0; margin-left: 4px">
                  {{ formatTime(cls.lastSyncAt) }}
                </span>
              </div>
            </div>
            <div class="card-actions">
              <el-button type="primary" text @click="manageStudents(cls)">学生管理</el-button>
              <el-button type="info" text @click="viewAnalysis(cls)">班级分析</el-button>
              <el-button v-if="cls.ptaKeyword" type="warning" text
                         :loading="syncingMap[cls.id]"
                         :disabled="cls.syncStatus === 'RUNNING'"
                         @click="triggerSyncForClass(cls)">
                {{ cls.syncStatus === 'RUNNING' ? '同步中...' : '立即同步' }}
              </el-button>
            </div>
          </el-card>
        </el-col>
      </el-row>
    </div>

    <!-- 创建/编辑班级对话框 -->
    <el-dialog v-model="classDialogVisible" :title="editingClass ? '编辑班级' : '新增班级'" width="520px" destroy-on-close>
      <el-form :model="classForm" :rules="classRules" ref="classFormRef" label-width="90px">
        <el-form-item label="班级名称" prop="name">
          <el-input v-model="classForm.name" placeholder="如：计算机科学与技术1班" />
        </el-form-item>
        <el-form-item label="班级号" prop="classCode" v-if="!editingClass">
          <el-input v-model="classForm.classCode" placeholder="唯一标识，如 CS2023-01">
            <template #append>
              <el-button @click="generateCode">随机生成</el-button>
            </template>
          </el-input>
        </el-form-item>
        <el-form-item label="加入密码" prop="joinPassword">
          <el-input v-model="classForm.joinPassword" placeholder="学生加入班级时需要输入" show-password />
        </el-form-item>
        <el-form-item label="年级">
          <el-select v-model="classForm.grade" placeholder="选择年级" clearable style="width: 100%">
            <el-option v-for="y in ['2022','2023','2024','2025','2026']" :key="y" :label="y + '级'" :value="y" />
          </el-select>
        </el-form-item>
        <el-form-item label="课程名称">
          <el-input v-model="classForm.courseName" placeholder="如：数据结构" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="classForm.description" type="textarea" :rows="2" placeholder="班级描述（选填）" />
        </el-form-item>
        <el-divider content-position="left">PTA 数据同步</el-divider>
        <el-form-item label="PTA关键词">
          <el-input v-model="classForm.ptaKeyword" placeholder="PTA上的搜索关键词，如：计科23数据结构">
            <template #prepend>🔍</template>
          </el-input>
          <div style="font-size: 12px; color: #909399; margin-top: 4px">
            填写后可自动从PTA同步该班级的实验数据
          </div>
        </el-form-item>
        <el-form-item label="定时同步">
          <el-switch v-model="classForm.syncEnabled" :disabled="!classForm.ptaKeyword" />
          <span style="margin-left: 8px; font-size: 13px; color: #909399">
            {{ classForm.syncEnabled ? '已开启，每天凌晨自动同步一次' : '关闭' }}
          </span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="classDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitClassForm" :loading="submitting">确认</el-button>
      </template>
    </el-dialog>

    <!-- 学生管理对话框 -->
    <el-dialog v-model="studentDialogVisible" :title="'学生管理 - ' + (currentClass?.name || '')" width="700px" destroy-on-close>
      <div style="display: flex; justify-content: space-between; margin-bottom: 12px">
        <el-input v-model="studentSearch" placeholder="搜索姓名或学号" clearable style="width: 240px" />
        <el-button type="primary" size="small" @click="openAddStudentDialog">添加学生</el-button>
      </div>
      <el-table :data="filteredStudents" stripe size="small" v-loading="studentsLoading" max-height="400">
        <el-table-column type="index" label="#" width="50" />
        <el-table-column prop="studentNum" label="学号" width="140" />
        <el-table-column prop="studentName" label="姓名" width="120" />
        <el-table-column prop="joinedAt" label="加入时间" min-width="160">
          <template #default="{ row }">{{ formatTime(row.joinedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="80">
          <template #default="{ row }">
            <el-button type="danger" link size="small" @click="confirmRemoveStudent(row)">移除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div style="margin-top: 12px; color: #909399; font-size: 13px">
        共 {{ students.length }} 名学生
      </div>
    </el-dialog>

    <!-- 添加学生小对话框 -->
    <el-dialog v-model="addStudentVisible" title="添加学生" width="400px" append-to-body>
      <el-form :model="addStudentForm" label-width="60px">
        <el-form-item label="姓名">
          <el-input v-model="addStudentForm.studentName" placeholder="学生姓名" />
        </el-form-item>
        <el-form-item label="学号">
          <el-input v-model="addStudentForm.studentNum" placeholder="学号（选填）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addStudentVisible = false">取消</el-button>
        <el-button type="primary" @click="doAddStudent" :loading="addingStudent">确认</el-button>
      </template>
    </el-dialog>

    <!-- 手动更新 PTA Cookie 对话框 -->
    <el-dialog v-model="cookieDialogVisible" title="手动更新 PTA Cookie" width="600px" destroy-on-close>
      <el-steps :active="1" simple style="margin-bottom: 20px">
        <el-step title="获取 Cookie" />
        <el-step title="粘贴到下方" />
        <el-step title="验证生效" />
      </el-steps>
      <el-alert type="info" :closable="false" style="margin-bottom: 16px; border-radius: 8px">
        <template #title>
          <span style="font-weight: 500">获取步骤</span>
        </template>
        <template #default>
          <ol style="margin: 8px 0 0; padding-left: 20px; font-size: 13px; color: #5f6368; line-height: 1.8">
            <li>用浏览器打开 <a href="https://pintia.cn" target="_blank" style="color: #1a73e8">pintia.cn</a> 并登录</li>
            <li>按 <code>F12</code> 打开开发者工具 → 切换到 <code>Application</code> 标签</li>
            <li>左侧找到 <code>Cookies</code> → <code>https://pintia.cn</code></li>
            <li>推荐安装浏览器插件 <strong>EditThisCookie</strong>，点击导出按钮即可复制 JSON</li>
            <li>将复制的 JSON 粘贴到下方输入框</li>
          </ol>
        </template>
      </el-alert>
      <el-input
        v-model="cookieInput"
        type="textarea"
        :rows="8"
        placeholder='粘贴 Cookie JSON，格式如：[{"name":"PTASession","value":"xxx","domain":".pintia.cn"}, ...]'
        style="font-family: monospace; font-size: 12px"
      />
      <div v-if="cookieSubmitResult" style="margin-top: 12px">
        <el-alert
          :title="cookieSubmitResult.message"
          :type="cookieSubmitResult.valid ? 'success' : 'error'"
          :closable="false"
          show-icon
          style="border-radius: 8px"
        />
      </div>
      <template #footer>
        <el-button @click="cookieDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitCookieForm" :loading="cookieSubmitting"
                   :disabled="!cookieInput.trim()" style="border-radius: 100px">
          验证并保存
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, MoreFilled } from '@element-plus/icons-vue'
import PageHeader from '../../components/PageHeader.vue'
import {
  getTeachingClasses, createTeachingClass, updateTeachingClass, deleteTeachingClass,
  getClassStudents, addClassStudent, removeClassStudent,
  triggerPtaSync, getPtaSyncStatus,
  getPtaCookieStatus, submitPtaCookie
} from '../../api/tap'

const router = useRouter()
const loading = ref(false)
const classes = ref([])

const extract = (res) => res?.data ?? res

const loadClasses = async () => {
  loading.value = true
  try {
    const res = await getTeachingClasses()
    classes.value = extract(res) || []
  } catch (e) {
    ElMessage.error('加载班级失败: ' + e.message)
  } finally {
    loading.value = false
  }
}

// --- 创建/编辑 ---
const classDialogVisible = ref(false)
const editingClass = ref(null)
const submitting = ref(false)
const classFormRef = ref(null)
const classForm = reactive({ name: '', classCode: '', joinPassword: '', grade: '', courseName: '', description: '', ptaKeyword: '', syncEnabled: false })
const classRules = {
  name: [{ required: true, message: '请输入班级名称', trigger: 'blur' }],
  classCode: [{ required: true, message: '请输入班级号', trigger: 'blur' }],
  joinPassword: [{ required: true, message: '请设置加入密码', trigger: 'blur' }]
}

const openCreateDialog = () => {
  editingClass.value = null
  Object.assign(classForm, { name: '', classCode: '', joinPassword: '', grade: '', courseName: '', description: '', ptaKeyword: '', syncEnabled: false })
  classDialogVisible.value = true
}

const editClass = (cls) => {
  editingClass.value = cls
  Object.assign(classForm, {
    name: cls.name, classCode: cls.classCode, joinPassword: cls.joinPassword,
    grade: cls.grade || '', courseName: cls.courseName || '', description: cls.description || '',
    ptaKeyword: cls.ptaKeyword || '', syncEnabled: cls.syncEnabled || false
  })
  classDialogVisible.value = true
}

const generateCode = () => {
  classForm.classCode = 'C' + Date.now().toString(36).toUpperCase().slice(-6)
}

const submitClassForm = async () => {
  const valid = await classFormRef.value.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    if (editingClass.value) {
      await updateTeachingClass(editingClass.value.id, {
        name: classForm.name, joinPassword: classForm.joinPassword,
        grade: classForm.grade, courseName: classForm.courseName, description: classForm.description,
        ptaKeyword: classForm.ptaKeyword, syncEnabled: classForm.syncEnabled
      })
      ElMessage.success('班级更新成功')
    } else {
      const res = await createTeachingClass({ ...classForm })
      const created = extract(res)
      ElMessage.success('班级创建成功')
      // 如果填写了 PTA 关键词，自动触发首次同步
      if (classForm.ptaKeyword && classForm.ptaKeyword.trim() && created?.id) {
        try {
          await triggerPtaSync(created.id)
          ElMessage.success('已自动触发 PTA 数据同步')
        } catch (syncErr) {
          ElMessage.warning('班级已创建，但自动同步失败: ' + (syncErr.message || '爬虫服务可能未启动'))
        }
      }
    }
    classDialogVisible.value = false
    loadClasses()
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    submitting.value = false
  }
}

const confirmDelete = (cls) => {
  ElMessageBox.confirm(`确定删除班级「${cls.name}」？此操作不可恢复，班级内学生关系将一并删除。`, '警告', {
    confirmButtonText: '确定删除', cancelButtonText: '取消', type: 'warning'
  }).then(async () => {
    try {
      await deleteTeachingClass(cls.id)
      ElMessage.success('删除成功')
      loadClasses()
    } catch (e) { ElMessage.error(e.message) }
  }).catch(() => {})
}

const copyCode = (code) => {
  navigator.clipboard.writeText(code).then(() => ElMessage.success('班级号已复制'))
}

const viewAnalysis = (cls) => {
  router.push(`/teacher/class-detailed-analysis/${cls.id}`)
}

// --- PTA 同步 ---
const syncingMap = reactive({})

const syncTagType = (status) => {
  const map = { SUCCESS: 'success', RUNNING: '', FAILED: 'danger', IDLE: 'info' }
  return map[status] || 'info'
}

const syncStatusText = (status) => {
  const map = { SUCCESS: '已同步', RUNNING: '同步中', FAILED: '同步失败', IDLE: '未同步' }
  return map[status] || status || '未同步'
}

const triggerSyncForClass = async (cls) => {
  syncingMap[cls.id] = true
  try {
    await triggerPtaSync(cls.id)
    ElMessage.success('同步任务已提交')
    cls.syncStatus = 'RUNNING'
  } catch (e) {
    ElMessage.error(e.message || '同步失败')
  } finally {
    syncingMap[cls.id] = false
  }
}

// --- 学生管理 ---
const studentDialogVisible = ref(false)
const currentClass = ref(null)
const students = ref([])
const studentsLoading = ref(false)
const studentSearch = ref('')

const filteredStudents = computed(() => {
  if (!studentSearch.value) return students.value
  const q = studentSearch.value.toLowerCase()
  return students.value.filter(s =>
    (s.studentName && s.studentName.toLowerCase().includes(q)) ||
    (s.studentNum && s.studentNum.toLowerCase().includes(q))
  )
})

const manageStudents = async (cls) => {
  currentClass.value = cls
  studentDialogVisible.value = true
  studentsLoading.value = true
  try {
    const res = await getClassStudents(cls.id)
    students.value = extract(res) || []
  } catch (e) {
    ElMessage.error('加载学生列表失败')
  } finally {
    studentsLoading.value = false
  }
}

const addStudentVisible = ref(false)
const addStudentForm = reactive({ studentName: '', studentNum: '' })
const addingStudent = ref(false)

const openAddStudentDialog = () => {
  addStudentForm.studentName = ''
  addStudentForm.studentNum = ''
  addStudentVisible.value = true
}

const doAddStudent = async () => {
  if (!addStudentForm.studentName.trim()) { ElMessage.warning('请输入学生姓名'); return }
  addingStudent.value = true
  try {
    await addClassStudent(currentClass.value.id, { ...addStudentForm })
    ElMessage.success('添加成功')
    addStudentVisible.value = false
    // reload
    const res = await getClassStudents(currentClass.value.id)
    students.value = extract(res) || []
    loadClasses() // refresh count
  } catch (e) { ElMessage.error(e.message) }
  finally { addingStudent.value = false }
}

const confirmRemoveStudent = (row) => {
  ElMessageBox.confirm(`确定移除学生「${row.studentName}」？`, '提示', {
    confirmButtonText: '移除', cancelButtonText: '取消', type: 'warning'
  }).then(async () => {
    try {
      await removeClassStudent(currentClass.value.id, row.id)
      students.value = students.value.filter(s => s.id !== row.id)
      ElMessage.success('已移除')
      loadClasses()
    } catch (e) { ElMessage.error(e.message) }
  }).catch(() => {})
}

const formatTime = (t) => {
  if (!t) return ''
  return new Date(t).toLocaleString('zh-CN')
}

// --- PTA Cookie 状态 ---
const cookieStatus = ref('UNKNOWN')
const cookieDialogVisible = ref(false)
const cookieInput = ref('')
const cookieSubmitting = ref(false)
const cookieSubmitResult = ref(null)

const checkCookieStatus = async () => {
  try {
    const res = await getPtaCookieStatus()
    const data = res?.data ?? res
    cookieStatus.value = data?.status || 'UNKNOWN'
  } catch {
    // 爬虫服务未启动时不显示告警
  }
}

const openCookieDialog = () => {
  cookieInput.value = ''
  cookieSubmitResult.value = null
  cookieDialogVisible.value = true
}

const submitCookieForm = async () => {
  cookieSubmitting.value = true
  cookieSubmitResult.value = null
  try {
    const res = await submitPtaCookie(cookieInput.value.trim())
    const data = res?.data ?? res
    cookieSubmitResult.value = data
    if (data?.valid) {
      cookieStatus.value = 'OK'
      ElMessage.success('Cookie 更新成功，数据同步已恢复')
      setTimeout(() => { cookieDialogVisible.value = false }, 1500)
    }
  } catch (e) {
    cookieSubmitResult.value = { valid: false, message: '提交失败: ' + (e.message || '未知错误') }
  } finally {
    cookieSubmitting.value = false
  }
}

onMounted(() => { loadClasses(); checkCookieStatus() })
</script>

<style scoped>
.class-list { height: 100%; overflow-y: auto; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif; }
.my-page-header { padding: 0; }
.my-page-header :deep(.g-primary-btn),
.class-list :deep(.el-button--primary) { border-radius: 100px; }
.class-cards { padding: 0 0 20px; }
.class-card {
  border-radius: 16px;
  border: 1px solid #dadce0;
  box-shadow: none;
  transition: box-shadow 0.2s, transform 0.2s;
}
.class-card:hover { transform: translateY(-2px); box-shadow: 0 1px 3px rgba(60,64,67,0.15), 0 4px 8px rgba(60,64,67,0.08); }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.class-name { font-size: 15px; font-weight: 500; color: #202124; }
.class-info { display: flex; flex-direction: column; gap: 10px; }
.info-row { display: flex; align-items: center; gap: 8px; font-size: 13px; color: #5f6368; }
.info-row .label { color: #9aa0a6; min-width: 64px; }
.card-actions { margin-top: 14px; display: flex; gap: 8px; border-top: 1px solid #e8eaed; padding-top: 12px; }
</style>

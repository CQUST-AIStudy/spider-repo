<template>
  <div class="class-list">
    <page-header
      class="my-page-header"
      title="班级管理"
      description="管理教学班级、学生信息与 PTA 同步设置，首屏卡片会根据内容自动伸展。"
    >
      <el-button type="primary" @click="openCreateDialog">
        <el-icon><Plus /></el-icon>
        新增班级
      </el-button>
    </page-header>

    <el-alert
      v-if="cookieStatus === 'EXPIRED'"
      class="cookie-alert"
      title="PTA 登录凭证已过期"
      type="warning"
      :closable="false"
      show-icon
    >
      <template #default>
        <div class="cookie-alert__content">
          <span>系统自动登录失败。可以手动更新 Cookie，也可以在“个人资料”绑定 PTA 账号，或在发起同步时临时输入账号密码。</span>
          <el-button type="warning" size="small" @click="openCookieDialog">更新 Cookie</el-button>
        </div>
      </template>
    </el-alert>

    <div class="class-cards" v-loading="loading">
      <el-empty v-if="classes.length === 0 && !loading" description="暂无班级，点击上方按钮创建">
        <el-button type="primary" @click="openCreateDialog">创建第一个班级</el-button>
      </el-empty>

      <el-row v-else :gutter="20" class="class-grid">
        <el-col
          v-for="cls in classes"
          :key="cls.id"
          :xs="24"
          :sm="24"
          :md="24"
          :lg="12"
          :xl="12"
          class="class-grid__item"
        >
          <el-card shadow="hover" class="class-card">
            <template #header>
              <div class="card-header">
                <div class="card-header__main">
                  <h3 class="class-name">{{ displayClassName(cls) }}</h3>
                  <div class="class-meta">
                    <span class="meta-pill">{{ displayGrade(cls) }}</span>
                    <span class="meta-pill meta-pill--soft">{{ studentCountValue(cls) }} 人</span>
                  </div>
                </div>
                <el-dropdown trigger="click">
                  <el-icon class="card-menu"><MoreFilled /></el-icon>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item @click="editClass(cls)">编辑班级</el-dropdown-item>
                      <el-dropdown-item @click="manageStudents(cls)">学生管理</el-dropdown-item>
                      <el-dropdown-item divided @click="confirmDelete(cls)" style="color: #f56c6c">
                        删除班级
                      </el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </div>
            </template>

            <div class="class-summary">
              <div class="summary-chip">
                <span class="summary-chip__label">班级号</span>
                <strong>{{ displayClassCode(cls) }}</strong>
                <el-button link size="small" @click="copyCode(displayClassCode(cls))">复制</el-button>
              </div>
              <div class="summary-chip" v-if="hasPtaConfig(cls)">
                <span class="summary-chip__label">PTA 同步</span>
                <el-tag size="small" :type="syncTagType(cls.syncStatus)" effect="plain">
                  {{ syncStatusText(cls.syncStatus) }}
                </el-tag>
                <span v-if="cls.lastSyncAt" class="summary-chip__time">{{ formatTime(cls.lastSyncAt) }}</span>
              </div>
            </div>

            <div class="info-grid">
              <div class="info-block">
                <span class="info-label">加入密码</span>
                <span class="info-value">{{ displayJoinPassword(cls) }}</span>
              </div>
              <div class="info-block">
                <span class="info-label">课程</span>
                <span class="info-value">{{ displayCourseName(cls) }}</span>
              </div>
              <div class="info-block">
                <span class="info-label">描述</span>
                <span class="info-value">{{ displayDescription(cls) }}</span>
              </div>
              <div class="info-block">
                <span class="info-label">同步关键词</span>
                <span class="info-value">{{ displayPtaKeyword(cls) }}</span>
              </div>
            </div>

            <div class="card-actions">
              <el-button type="primary" @click="enterClassSpace(cls)">进入教学班</el-button>
              <el-button @click="manageStudents(cls)">学生管理</el-button>
              <el-button
                type="success"
                plain
                :loading="importingMap[cls.id]"
                @click="importStudentsForClass(cls)"
              >
                导入 PTA 学生
              </el-button>
              <el-button @click="viewAnalysis(cls)">班级分析</el-button>
              <el-button
                v-if="hasPtaConfig(cls)"
                type="warning"
                plain
                :loading="syncingMap[cls.id]"
                :disabled="cls.syncStatus === 'RUNNING'"
                @click="openSyncDialog(cls)"
              >
                {{ cls.syncStatus === 'RUNNING' ? '同步中...' : '立即同步' }}
              </el-button>
            </div>
          </el-card>
        </el-col>
      </el-row>
    </div>

    <el-dialog
      v-model="classDialogVisible"
      :title="editingClass ? '编辑班级' : '新增班级'"
      width="520px"
      destroy-on-close
    >
      <el-form :model="classForm" :rules="classRules" ref="classFormRef" label-width="90px">
        <el-form-item label="班级名称" prop="name">
          <el-input v-model="classForm.name" placeholder="例如：计算机科学与技术 23 级 1 班" />
        </el-form-item>
        <el-form-item label="班级号" prop="classCode" v-if="!editingClass">
          <el-input v-model="classForm.classCode" placeholder="唯一标识，例如 CS2023-01">
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
            <el-option v-for="y in gradeOptions" :key="y" :label="`${y} 级`" :value="y" />
          </el-select>
        </el-form-item>
        <el-form-item label="课程名称">
          <el-input v-model="classForm.courseName" placeholder="例如：数据结构" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="classForm.description" type="textarea" :rows="3" placeholder="可选，用于补充班级说明" />
        </el-form-item>

        <el-divider content-position="left">PTA 数据同步</el-divider>

        <el-form-item label="PTA 关键词">
          <el-input
            v-model="classForm.ptaKeyword"
            placeholder="例如：计科 23 数据结构"
          />
          <div class="form-help">填写后可自动从 PTA 同步该班级的实验数据。</div>
        </el-form-item>
        <el-form-item label="定时同步">
          <el-switch v-model="classForm.syncEnabled" :disabled="!classForm.ptaKeyword.trim()" />
          <span class="switch-hint">
            {{ classForm.syncEnabled ? '已开启，每天凌晨自动同步一次。' : '关闭' }}
          </span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="classDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitClassForm" :loading="submitting">确认</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="studentDialogVisible"
      :title="`学生管理 - ${displayClassName(currentClass || {})}`"
      width="720px"
      destroy-on-close
    >
      <div class="student-toolbar">
        <el-input v-model="studentSearch" placeholder="搜索姓名或学号" clearable class="student-toolbar__search" />
        <el-button type="primary" size="small" @click="openAddStudentDialog">添加学生</el-button>
      </div>
      <el-table :data="filteredStudents" stripe size="small" v-loading="studentsLoading" max-height="420">
        <el-table-column type="index" label="#" width="56" />
        <el-table-column prop="studentNum" label="学号" width="160" />
        <el-table-column prop="studentName" label="姓名" width="140" />
        <el-table-column prop="joinedAt" label="加入时间" min-width="180">
          <template #default="{ row }">{{ formatTime(row.joinedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="90">
          <template #default="{ row }">
            <el-button type="danger" link size="small" @click="confirmRemoveStudent(row)">移除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="student-count">共 {{ students.length }} 名学生</div>
    </el-dialog>

    <el-dialog v-model="addStudentVisible" title="添加学生" width="400px" append-to-body>
      <el-form :model="addStudentForm" label-width="60px">
        <el-form-item label="姓名">
          <el-input v-model="addStudentForm.studentName" placeholder="学生姓名" />
        </el-form-item>
        <el-form-item label="学号">
          <el-input v-model="addStudentForm.studentNum" placeholder="选填" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addStudentVisible = false">取消</el-button>
        <el-button type="primary" @click="doAddStudent" :loading="addingStudent">确认</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="syncDialogVisible" title="PTA 同步账号" width="480px" destroy-on-close>
      <el-alert
        type="info"
        :closable="false"
        class="cookie-helper"
        title="可为本次同步临时输入 PTA 账号密码；若留空，则优先使用个人资料中已绑定的 PTA 账号。"
      />
      <div v-if="hasBoundPtaCredentials" class="sync-dialog__bound">
        已绑定 PTA 账号：{{ boundPtaUsername }}
      </div>
      <div v-else class="sync-dialog__bound sync-dialog__bound--warning">
        当前未绑定 PTA 账号，留空时将继续回退到现有 Cookie 方式。
      </div>
      <el-form :model="syncForm" label-width="90px" autocomplete="off">
        <el-form-item label="PTA 账号">
          <el-input v-model="syncForm.ptaUsername" autocomplete="off" name="pta-sync-username" placeholder="本次同步使用的 PTA 账号（可选）" clearable />
        </el-form-item>
        <el-form-item label="PTA 密码">
          <el-input
            v-model="syncForm.ptaPassword"
            autocomplete="new-password"
            name="pta-sync-password"
            type="password"
            show-password
            placeholder="本次同步使用的 PTA 密码（可选）"
            clearable
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="syncDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="syncDialogClass ? syncingMap[syncDialogClass.id] : false" @click="triggerSyncForClass">
          开始同步
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="cookieDialogVisible" title="手动更新 PTA Cookie" width="600px" destroy-on-close>
      <el-steps :active="1" simple class="cookie-steps">
        <el-step title="获取 Cookie" />
        <el-step title="粘贴到下方" />
        <el-step title="验证生效" />
      </el-steps>

      <el-alert type="info" :closable="false" class="cookie-helper">
        <template #title>
          <span class="cookie-helper__title">获取步骤</span>
        </template>
        <template #default>
          <ol class="cookie-helper__list">
            <li>打开 <a href="https://pintia.cn" target="_blank" rel="noopener noreferrer">pintia.cn</a> 并登录。</li>
            <li>按 `F12` 打开开发者工具，切换到 `Application`。</li>
            <li>在左侧找到 `Cookies`，选择 `https://pintia.cn`。</li>
            <li>复制导出的 Cookie JSON，粘贴到下方输入框。</li>
          </ol>
        </template>
      </el-alert>

      <el-input
        v-model="cookieInput"
        type="textarea"
        :rows="8"
        placeholder='粘贴 Cookie JSON，例如：[{"name":"PTASession","value":"xxx","domain":".pintia.cn"}]'
        class="cookie-textarea"
      />

      <div v-if="cookieSubmitResult" class="cookie-result">
        <el-alert
          :title="cookieSubmitResult.message"
          :type="cookieSubmitResult.valid ? 'success' : 'error'"
          :closable="false"
          show-icon
        />
      </div>

      <template #footer>
        <el-button @click="cookieDialogVisible = false">取消</el-button>
        <el-button
          type="primary"
          @click="submitCookieForm"
          :loading="cookieSubmitting"
          :disabled="!cookieInput.trim()"
        >
          验证并保存
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { MoreFilled, Plus } from '@element-plus/icons-vue'
import PageHeader from '../../components/PageHeader.vue'
import { useUserStore } from '../../store'
import {
  addClassStudent,
  createTeachingClass,
  deleteTeachingClass,
  getClassStudents,
  getPtaCookieStatus,
  getTeacherPtaCredentials,
  getTeachingClasses,
  importPtaStudents,
  removeClassStudent,
  submitPtaCookie,
  triggerPtaSync,
  updateTeacherPtaCredentials,
  updateTeachingClass
} from '../../api/tap'

const router = useRouter()
const userStore = useUserStore()

const loading = ref(false)
const classes = ref([])
const gradeOptions = ['2022', '2023', '2024', '2025', '2026', '2027']

const classDialogVisible = ref(false)
const editingClass = ref(null)
const submitting = ref(false)
const classFormRef = ref(null)
const classForm = reactive({
  name: '',
  classCode: '',
  joinPassword: '',
  grade: '',
  courseName: '',
  description: '',
  ptaKeyword: '',
  syncEnabled: false
})
const classRules = {
  name: [{ required: true, message: '请输入班级名称', trigger: 'blur' }],
  classCode: [{ required: true, message: '请输入班级号', trigger: 'blur' }],
  joinPassword: [{ required: true, message: '请设置加入密码', trigger: 'blur' }]
}

const syncingMap = reactive({})
const importingMap = reactive({})

const studentDialogVisible = ref(false)
const currentClass = ref(null)
const students = ref([])
const studentsLoading = ref(false)
const studentSearch = ref('')

const addStudentVisible = ref(false)
const addStudentForm = reactive({ studentName: '', studentNum: '' })
const addingStudent = ref(false)

const cookieStatus = ref('UNKNOWN')
const cookieDialogVisible = ref(false)
const cookieInput = ref('')
const cookieSubmitting = ref(false)
const cookieSubmitResult = ref(null)
const syncDialogVisible = ref(false)
const syncDialogClass = ref(null)
const syncForm = reactive({ ptaUsername: '', ptaPassword: '' })
const boundPtaUsername = ref('')
const hasBoundPtaCredentials = ref(false)

const extract = (res) => res?.data ?? res

const isCorruptedText = (value) => {
  const text = String(value || '').trim()
  if (!text) return true
  return text.includes('??') || text.includes('�')
}

const cleanText = (value, fallback = '未设置') => {
  const text = String(value || '').trim()
  if (!text || isCorruptedText(text)) return fallback
  return text
}

const studentCountValue = (cls) => Number(cls?.studentCount || 0)
const hasPtaConfig = (cls) => !isCorruptedText(cls?.ptaKeyword) && !!String(cls?.ptaKeyword || '').trim()

const displayClassCode = (cls) => cleanText(cls?.classCode, '未生成')
const displayClassName = (cls) => cleanText(cls?.name, displayClassCode(cls) === '未生成' ? '未命名班级' : `班级 ${displayClassCode(cls)}`)
const displayCourseName = (cls) => cleanText(cls?.courseName, '课程信息待补充')
const displayDescription = (cls) => cleanText(cls?.description, '暂无描述')
const displayPtaKeyword = (cls) => cleanText(cls?.ptaKeyword, '未配置')
const displayJoinPassword = (cls) => cleanText(cls?.joinPassword, '未设置')
const displayGrade = (cls) => {
  const grade = cleanText(cls?.grade, '')
  return grade ? `${grade} 级` : '未设置年级'
}

const filteredStudents = computed(() => {
  if (!studentSearch.value) return students.value
  const query = studentSearch.value.toLowerCase()
  return students.value.filter(item =>
    String(item.studentName || '').toLowerCase().includes(query) ||
    String(item.studentNum || '').toLowerCase().includes(query)
  )
})

const resolvePtaKeyword = () => (classForm.ptaKeyword || classForm.name || '').trim()

const toSelectedClass = (cls) => ({
  id: cls.id,
  name: displayClassName(cls),
  ptaKeyword: cls.ptaKeyword || cls.name || ''
})

const loadClasses = async () => {
  loading.value = true
  try {
    const res = await getTeachingClasses()
    classes.value = extract(res) || []
  } catch (error) {
    ElMessage.error(`加载班级失败：${error.message}`)
  } finally {
    loading.value = false
  }
}

const openCreateDialog = () => {
  editingClass.value = null
  Object.assign(classForm, {
    name: '',
    classCode: '',
    joinPassword: '',
    grade: '',
    courseName: '',
    description: '',
    ptaKeyword: '',
    syncEnabled: false
  })
  classDialogVisible.value = true
}

const editClass = (cls) => {
  editingClass.value = cls
  Object.assign(classForm, {
    name: cleanText(cls.name, ''),
    classCode: cleanText(cls.classCode, ''),
    joinPassword: cleanText(cls.joinPassword, ''),
    grade: cleanText(cls.grade, ''),
    courseName: cleanText(cls.courseName, ''),
    description: cleanText(cls.description, ''),
    ptaKeyword: cleanText(cls.ptaKeyword, ''),
    syncEnabled: !!cls.syncEnabled
  })
  classDialogVisible.value = true
}

const generateCode = () => {
  classForm.classCode = `C${Date.now().toString(36).toUpperCase().slice(-6)}`
}

const submitClassForm = async () => {
  const valid = await classFormRef.value?.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    const ptaKeyword = resolvePtaKeyword()
    if (editingClass.value) {
      await updateTeachingClass(editingClass.value.id, {
        name: classForm.name,
        joinPassword: classForm.joinPassword,
        grade: classForm.grade,
        courseName: classForm.courseName,
        description: classForm.description,
        ptaKeyword,
        syncEnabled: classForm.syncEnabled
      })
      ElMessage.success('班级更新成功')
    } else {
      const res = await createTeachingClass({ ...classForm, ptaKeyword })
      const created = extract(res)
      if (created?.id) {
        userStore.setSelectedClass(toSelectedClass({
          ...created,
          ptaKeyword: created.ptaKeyword || ptaKeyword || created.name
        }))
      }
      ElMessage.success('班级创建成功')
      if (ptaKeyword && created?.id) {
        try {
          await triggerPtaSync(created.id)
          ElMessage.success('已自动触发 PTA 数据同步')
        } catch (syncError) {
          ElMessage.warning(`班级已创建，但自动同步失败：${syncError.message || '爬虫服务可能未启动'}`)
        }
      }
    }

    classDialogVisible.value = false
    await loadClasses()
  } catch (error) {
    ElMessage.error(error.message || '保存班级失败')
  } finally {
    submitting.value = false
  }
}

const confirmDelete = (cls) => {
  ElMessageBox.confirm(
    `确定删除班级“${displayClassName(cls)}”？此操作不可恢复，班级内学生关系也会一并删除。`,
    '警告',
    {
      confirmButtonText: '确定删除',
      cancelButtonText: '取消',
      type: 'warning'
    }
  ).then(async () => {
    try {
      await deleteTeachingClass(cls.id)
      ElMessage.success('删除成功')
      await loadClasses()
    } catch (error) {
      ElMessage.error(error.message || '删除失败')
    }
  }).catch(() => {})
}

const copyCode = async (code) => {
  if (!code || code === '未生成') return
  try {
    await navigator.clipboard.writeText(code)
    ElMessage.success('班级号已复制')
  } catch {
    ElMessage.warning('复制失败，请手动复制')
  }
}

const enterClassSpace = (cls) => {
  userStore.setSelectedClass(toSelectedClass(cls))
  ElMessage.success(`已切换到 ${displayClassName(cls)}`)
  router.push('/teacher/dashboard')
}

const viewAnalysis = (cls) => {
  router.push(`/teacher/class-detailed-analysis/${cls.id}`)
}

const syncTagType = (status) => {
  const tagMap = {
    SUCCESS: 'success',
    RUNNING: 'warning',
    FAILED: 'danger',
    IDLE: 'info'
  }
  return tagMap[status] || 'info'
}

const syncStatusText = (status) => {
  const textMap = {
    SUCCESS: '已同步',
    RUNNING: '同步中',
    FAILED: '同步失败',
    IDLE: '未同步'
  }
  return textMap[status] || '未同步'
}

const triggerSyncForClass = async () => {
  const cls = syncDialogClass.value
  if (!cls) return
  const username = syncForm.ptaUsername.trim()
  const password = syncForm.ptaPassword
  if ((username && !password) || (!username && password)) {
    ElMessage.warning('请输入完整的 PTA 账号和密码，或保持两项都为空。')
    return
  }
  syncingMap[cls.id] = true
  try {
    if (username) {
      await updateTeacherPtaCredentials({ ptaUsername: username, ptaPassword: password })
      boundPtaUsername.value = username
      hasBoundPtaCredentials.value = true
    }
    await triggerPtaSync(cls.id, username ? { ptaUsername: username, ptaPassword: password } : {})
    cls.syncStatus = 'RUNNING'
    syncDialogVisible.value = false
    syncForm.ptaUsername = ''
    syncForm.ptaPassword = ''
    ElMessage.success('同步任务已提交')
  } catch (error) {
    ElMessage.error(error.message || '同步失败')
  } finally {
    syncingMap[cls.id] = false
  }
}

const manageStudents = async (cls) => {
  currentClass.value = cls
  studentDialogVisible.value = true
  studentsLoading.value = true
  try {
    const res = await getClassStudents(cls.id)
    students.value = extract(res) || []
  } catch (error) {
    ElMessage.error(error.message || '加载学生列表失败')
  } finally {
    studentsLoading.value = false
  }
}

const importStudentsForClass = async (cls) => {
  importingMap[cls.id] = true
  try {
    const res = await importPtaStudents(cls.id)
    const data = extract(res) || {}
    const matched = Number(data.matchedStudentCount || 0)
    const created = Number(data.createdCount || 0)
    const updated = Number(data.updatedCount || 0)

    if (matched === 0) {
      ElMessage.warning(`未找到 ${displayClassName(cls)} 的已同步 PTA 学生数据`)
    } else {
      ElMessage.success(`已导入 ${created} 人，更新 ${updated} 人`)
    }

    if (currentClass.value?.id === cls.id && studentDialogVisible.value) {
      const studentRes = await getClassStudents(cls.id)
      students.value = extract(studentRes) || []
    }
    await loadClasses()
  } catch (error) {
    ElMessage.error(error.message || '导入 PTA 学生失败')
  } finally {
    importingMap[cls.id] = false
  }
}

const openAddStudentDialog = () => {
  addStudentForm.studentName = ''
  addStudentForm.studentNum = ''
  addStudentVisible.value = true
}

const doAddStudent = async () => {
  if (!addStudentForm.studentName.trim()) {
    ElMessage.warning('请输入学生姓名')
    return
  }

  addingStudent.value = true
  try {
    await addClassStudent(currentClass.value.id, { ...addStudentForm })
    ElMessage.success('添加成功')
    addStudentVisible.value = false
    const res = await getClassStudents(currentClass.value.id)
    students.value = extract(res) || []
    await loadClasses()
  } catch (error) {
    ElMessage.error(error.message || '添加学生失败')
  } finally {
    addingStudent.value = false
  }
}

const confirmRemoveStudent = (row) => {
  ElMessageBox.confirm(`确定移除学生“${row.studentName}”吗？`, '提示', {
    confirmButtonText: '移除',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    try {
      await removeClassStudent(currentClass.value.id, row.id)
      students.value = students.value.filter(item => item.id !== row.id)
      ElMessage.success('已移除')
      await loadClasses()
    } catch (error) {
      ElMessage.error(error.message || '移除失败')
    }
  }).catch(() => {})
}

const formatTime = (value) => {
  if (!value) return ''
  return new Date(value).toLocaleString('zh-CN')
}

const checkCookieStatus = async () => {
  try {
    const res = await getPtaCookieStatus()
    const data = extract(res)
    cookieStatus.value = data?.status || 'UNKNOWN'
  } catch {
    cookieStatus.value = 'UNKNOWN'
  }
}

const loadBoundCredentials = async () => {
  try {
    const res = await getTeacherPtaCredentials()
    const data = extract(res) || {}
    boundPtaUsername.value = data?.ptaUsername || ''
    hasBoundPtaCredentials.value = !!data?.bound
  } catch {
    boundPtaUsername.value = ''
    hasBoundPtaCredentials.value = false
  }
}

const openSyncDialog = (cls) => {
  syncDialogClass.value = cls
  syncForm.ptaUsername = ''
  syncForm.ptaPassword = ''
  syncDialogVisible.value = true
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
    const data = extract(res)
    cookieSubmitResult.value = data
    if (data?.valid) {
      cookieStatus.value = 'OK'
      ElMessage.success('Cookie 更新成功，数据同步已恢复')
      setTimeout(() => {
        cookieDialogVisible.value = false
      }, 1500)
    }
  } catch (error) {
    cookieSubmitResult.value = {
      valid: false,
      message: `提交失败：${error.message || '未知错误'}`
    }
  } finally {
    cookieSubmitting.value = false
  }
}

onMounted(() => {
  loadClasses()
  checkCookieStatus()
  loadBoundCredentials()
})
</script>

<style scoped>
.class-list {
  height: 100%;
  overflow-y: auto;
}

.my-page-header {
  margin-bottom: 10px;
}

.class-list :deep(.el-button--primary) {
  border-radius: 999px;
}

.cookie-alert {
  margin-bottom: 18px;
  border-radius: 18px;
}

.cookie-alert__content {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  font-size: 13px;
  line-height: 1.7;
}

.class-cards {
  padding-bottom: 24px;
}

.class-grid {
  display: flex;
  flex-wrap: wrap;
}

.class-grid__item {
  display: flex;
  margin-bottom: 20px;
}

.class-card {
  width: 100%;
  min-height: 380px;
  display: flex;
  flex-direction: column;
  border: 1px solid #dce5f0;
  border-radius: 24px;
  background:
    radial-gradient(circle at top right, rgba(26, 115, 232, 0.08), transparent 30%),
    linear-gradient(180deg, #ffffff 0%, #f8fbff 100%);
  box-shadow: 0 14px 36px rgba(38, 61, 89, 0.07);
}

.class-card :deep(.el-card__header) {
  padding: 22px 24px 14px;
}

.class-card :deep(.el-card__body) {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 18px;
  padding: 0 24px 24px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
}

.card-header__main {
  min-width: 0;
  flex: 1;
}

.class-name {
  margin: 0;
  font-size: 26px;
  line-height: 1.2;
  color: #16314a;
  word-break: break-word;
}

.class-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}

.meta-pill {
  display: inline-flex;
  align-items: center;
  padding: 6px 12px;
  border-radius: 999px;
  background: rgba(18, 112, 216, 0.1);
  color: #1860b7;
  font-size: 12px;
  font-weight: 600;
}

.meta-pill--soft {
  background: rgba(126, 157, 183, 0.12);
  color: #5c7188;
}

.card-menu {
  cursor: pointer;
  color: #71839a;
  font-size: 18px;
}

.class-summary {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.summary-chip {
  min-width: min(280px, 100%);
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 12px 14px;
  border-radius: 16px;
  background: rgba(244, 248, 253, 0.92);
  border: 1px solid #e3ebf5;
  color: #34475d;
  line-height: 1.6;
  word-break: break-word;
}

.summary-chip__label {
  color: #8091a5;
  font-size: 12px;
  white-space: nowrap;
}

.summary-chip__time {
  font-size: 12px;
  color: #8b9bae;
}

.info-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.info-block {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 14px 16px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.78);
  border: 1px solid #e8eef6;
  min-height: 108px;
}

.info-label {
  font-size: 12px;
  font-weight: 600;
  color: #8092a6;
}

.info-value {
  color: #24384f;
  font-size: 14px;
  line-height: 1.7;
  word-break: break-word;
}

.card-actions {
  margin-top: auto;
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  padding-top: 6px;
}

.form-help {
  margin-top: 6px;
  font-size: 12px;
  color: #7b8ba0;
}

.switch-hint {
  margin-left: 10px;
  font-size: 13px;
  color: #7b8ba0;
}

.student-toolbar {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.student-toolbar__search {
  max-width: 260px;
}

.student-count {
  margin-top: 14px;
  font-size: 13px;
  color: #7f90a4;
}

.cookie-steps {
  margin-bottom: 18px;
}

.cookie-helper {
  margin-bottom: 16px;
  border-radius: 14px;
}

.cookie-helper__title {
  font-weight: 600;
}

.cookie-helper__list {
  margin: 8px 0 0;
  padding-left: 18px;
  color: #4d6077;
  line-height: 1.8;
}

.cookie-textarea :deep(.el-textarea__inner) {
  font-family: Consolas, 'Courier New', monospace;
  font-size: 12px;
}

.cookie-result {
  margin-top: 14px;
}

.sync-dialog__bound {
  margin: 12px 0 16px;
  padding: 10px 12px;
  border-radius: 12px;
  background: #e6f4ea;
  color: #1e8e3e;
  font-size: 13px;
}

.sync-dialog__bound--warning {
  background: #fef7e0;
  color: #b26a00;
}

@media (max-width: 900px) {
  .info-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .class-card {
    min-height: auto;
    border-radius: 20px;
  }

  .class-card :deep(.el-card__header) {
    padding: 18px 18px 12px;
  }

  .class-card :deep(.el-card__body) {
    padding: 0 18px 18px;
  }

  .class-name {
    font-size: 22px;
  }

  .summary-chip {
    width: 100%;
  }

  .student-toolbar {
    flex-direction: column;
  }

  .student-toolbar__search {
    max-width: none;
  }
}
</style>

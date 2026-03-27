<template>
  <div class="student-profile">
    <page-header
      class="my-page-header"
      title="个人信息"
      description="查看当前登录学生的基本信息、学习概况与账户安全设置。"
    />

    <loading-state :loading="loading">
      <div class="profile-layout">
        <section class="profile-main">
          <el-card class="profile-card">
            <div class="profile-card__top">
              <el-avatar :size="88" class="profile-card__avatar">
                {{ avatarText }}
              </el-avatar>
              <div class="profile-card__meta">
                <h2>{{ displayName }}</h2>
                <p>{{ className || '未绑定教学班' }}</p>
                <div class="profile-tags">
                  <el-tag effect="plain" type="primary">学生</el-tag>
                  <el-tag effect="plain" type="success">{{ gradeText }}</el-tag>
                </div>
              </div>
            </div>

            <div class="profile-info">
              <div class="info-item">
                <span class="info-label">用户名</span>
                <span class="info-value">{{ currentUser.username || '-' }}</span>
              </div>
              <div class="info-item">
                <span class="info-label">学号</span>
                <span class="info-value">{{ studentId || '-' }}</span>
              </div>
              <div class="info-item">
                <span class="info-label">班级</span>
                <span class="info-value">{{ className || '-' }}</span>
              </div>
              <div class="info-item">
                <span class="info-label">邮箱</span>
                <span class="info-value">{{ currentUser.email || '未设置' }}</span>
              </div>
              <div class="info-item">
                <span class="info-label">手机号</span>
                <span class="info-value">{{ currentUser.phone || '未设置' }}</span>
              </div>
            </div>

            <div class="profile-actions">
              <el-button type="primary" @click="openProfileDialog">更新展示信息</el-button>
              <el-button @click="openPasswordDialog">修改密码</el-button>
            </div>
          </el-card>

          <el-card class="security-card">
            <template #header>
              <div class="card-header">
                <span>账户设置</span>
              </div>
            </template>

            <div class="setting-list">
              <div class="setting-item">
                <div>
                  <strong>系统通知</strong>
                  <p>接收实验发布、截止时间和课堂更新提醒。</p>
                </div>
                <el-switch v-model="settings.notifications" />
              </div>
              <div class="setting-item">
                <div>
                  <strong>截止提醒</strong>
                  <p>在实验截止前推送提醒，避免漏交。</p>
                </div>
                <el-switch v-model="settings.deadlineReminder" />
              </div>
              <div class="setting-item">
                <div>
                  <strong>AI 反馈提示</strong>
                  <p>实验报告生成评语和学习建议时同步提醒。</p>
                </div>
                <el-switch v-model="settings.aiFeedback" />
              </div>
            </div>
          </el-card>
        </section>

        <section class="profile-side">
          <el-card class="overview-card">
            <template #header>
              <div class="card-header">
                <span>学习概况</span>
              </div>
            </template>

            <div class="overview-grid">
              <div class="overview-item">
                <span>实验总数</span>
                <strong>{{ stats.totalExperiments }}</strong>
              </div>
              <div class="overview-item">
                <span>已完成</span>
                <strong>{{ stats.completedExperiments }}</strong>
              </div>
              <div class="overview-item">
                <span>平均成绩</span>
                <strong>{{ stats.averageScore }}</strong>
              </div>
              <div class="overview-item">
                <span>进行中</span>
                <strong>{{ stats.inProgressExperiments }}</strong>
              </div>
            </div>
          </el-card>

          <el-card class="activity-card">
            <template #header>
              <div class="card-header">
                <span>最近动态</span>
              </div>
            </template>

            <el-timeline>
              <el-timeline-item
                v-for="item in activityList"
                :key="`${item.title}-${item.time}`"
                :timestamp="item.time"
                :color="item.color"
              >
                <h4 class="activity-title">{{ item.title }}</h4>
                <p class="activity-content">{{ item.content }}</p>
              </el-timeline-item>
            </el-timeline>
          </el-card>
        </section>
      </div>
    </loading-state>

    <el-dialog v-model="profileDialogVisible" title="更新展示信息" width="480px">
      <el-form ref="profileFormRef" :model="profileForm" :rules="profileRules" label-width="90px">
        <el-form-item label="姓名" prop="name">
          <el-input v-model="profileForm.name" placeholder="请输入展示姓名" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="profileForm.email" placeholder="请输入邮箱" />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="profileForm.phone" placeholder="请输入手机号" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="profileDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveProfile">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="passwordDialogVisible" title="修改密码" width="480px">
      <el-form ref="passwordFormRef" :model="passwordForm" :rules="passwordRules" label-width="100px">
        <el-form-item label="当前密码" prop="oldPassword">
          <el-input v-model="passwordForm.oldPassword" type="password" show-password />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="passwordForm.newPassword" type="password" show-password />
        </el-form-item>
        <el-form-item label="确认新密码" prop="confirmPassword">
          <el-input v-model="passwordForm.confirmPassword" type="password" show-password />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="passwordDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submittingPassword" @click="changePassword">确认修改</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import axios from 'axios'
import { ElMessage } from 'element-plus'
import PageHeader from '../../components/PageHeader.vue'
import LoadingState from '../../components/LoadingState.vue'
import { useUserStore } from '../../store'
import api from '../../api'
import { API_BASE_URL } from '../../config/runtime'

const userStore = useUserStore()
const loading = ref(true)
const profileDialogVisible = ref(false)
const passwordDialogVisible = ref(false)
const submittingPassword = ref(false)
const profileFormRef = ref(null)
const passwordFormRef = ref(null)

const settings = reactive({
  notifications: true,
  deadlineReminder: true,
  aiFeedback: true
})

const stats = reactive({
  totalExperiments: 0,
  completedExperiments: 0,
  inProgressExperiments: 0,
  averageScore: 0
})

const activityList = ref([
  {
    title: '等待同步学习数据',
    content: '当前页面会优先展示最近一次登录后的本地信息，学习数据加载后会自动更新。',
    time: '刚刚',
    color: '#409eff'
  }
])

const profileForm = reactive({
  name: '',
  email: '',
  phone: ''
})

const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

const currentUser = computed(() => userStore.userInfo || {})
const displayName = computed(() => currentUser.value.name || currentUser.value.username || '学生')
const studentId = computed(() => currentUser.value.usernum || currentUser.value.id || '')
const className = computed(() => currentUser.value.class || currentUser.value.classname || '')
const gradeText = computed(() => {
  const match = String(className.value || '').match(/(\d{2}|\d{4})/)
  return match ? `${match[1]}级` : '当前学期'
})
const avatarText = computed(() => displayName.value.slice(0, 1).toUpperCase())

const profileRules = {
  name: [
    { required: true, message: '请输入姓名', trigger: 'blur' },
    { min: 2, max: 20, message: '姓名长度保持在 2 到 20 个字符之间', trigger: 'blur' }
  ],
  email: [
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' }
  ],
  phone: [
    { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }
  ]
}

const validateConfirmPassword = (_rule, value, callback) => {
  if (value !== passwordForm.newPassword) {
    callback(new Error('两次输入的新密码不一致'))
    return
  }
  callback()
}

const passwordRules = {
  oldPassword: [{ required: true, message: '请输入当前密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, message: '新密码至少 6 位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    { validator: validateConfirmPassword, trigger: 'blur' }
  ]
}

function openProfileDialog() {
  profileForm.name = currentUser.value.name || currentUser.value.username || ''
  profileForm.email = currentUser.value.email || ''
  profileForm.phone = currentUser.value.phone || ''
  profileDialogVisible.value = true
}

async function saveProfile() {
  const valid = await profileFormRef.value?.validate().catch(() => false)
  if (!valid) return

  userStore.updateUserInfo({
    name: profileForm.name,
    username: currentUser.value.username || profileForm.name,
    email: profileForm.email,
    phone: profileForm.phone
  })

  profileDialogVisible.value = false
  ElMessage.success('展示信息已更新')
}

function openPasswordDialog() {
  passwordForm.oldPassword = ''
  passwordForm.newPassword = ''
  passwordForm.confirmPassword = ''
  passwordDialogVisible.value = true
}

async function changePassword() {
  const valid = await passwordFormRef.value?.validate().catch(() => false)
  if (!valid) return

  submittingPassword.value = true
  try {
    const res = await axios.post(`${API_BASE_URL}/api/user/password`, {
      oldPassword: passwordForm.oldPassword,
      newPassword: passwordForm.newPassword
    }, {
      withCredentials: true
    })

    const data = res?.data || res
    if (data?.success) {
      passwordDialogVisible.value = false
      ElMessage.success('密码修改成功，请使用新密码重新登录')
      return
    }

    ElMessage.error(data?.message || '密码修改失败')
  } catch (error) {
    ElMessage.error(`密码修改失败：${error.response?.data?.message || error.message}`)
  } finally {
    submittingPassword.value = false
  }
}

async function loadProfile() {
  loading.value = true
  try {
    const [profileRes, experimentsRes] = await Promise.allSettled([
      api.getStudentInfo(),
      api.getExperimentList()
    ])

    if (profileRes.status === 'fulfilled') {
      const profileData = profileRes.value?.data || profileRes.value || {}
      userStore.updateUserInfo({
        name: profileData.studentName || profileData.name || currentUser.value.name,
        username: profileData.username || currentUser.value.username,
        usernum: profileData.studentId || currentUser.value.usernum,
        class: profileData.className || currentUser.value.class,
        classname: profileData.className || currentUser.value.classname,
        email: profileData.email || currentUser.value.email,
        phone: profileData.phone || currentUser.value.phone
      })
    }

    if (experimentsRes.status === 'fulfilled') {
      const list = experimentsRes.value?.data || experimentsRes.value || []
      if (Array.isArray(list)) {
        stats.totalExperiments = list.length
        stats.completedExperiments = list.filter(item => item.status === 'completed').length
        stats.inProgressExperiments = list.filter(item => item.status === 'in_progress').length
        const scored = list.filter(item => Number(item.score) > 0)
        stats.averageScore = scored.length
          ? Math.round(scored.reduce((sum, item) => sum + Number(item.score), 0) / scored.length)
          : 0

        activityList.value = list.slice(0, 4).map(item => ({
          title: item.name || '实验任务',
          content: item.status === 'completed'
            ? `已完成，当前成绩 ${item.score ?? '待评分'}`
            : item.status === 'in_progress'
              ? '进行中，建议继续补充报告和代码内容。'
              : '尚未开始，可前往实验列表查看要求。',
          time: item.deadline || item.createdTime || '近期',
          color: item.status === 'completed' ? '#67c23a' : item.status === 'in_progress' ? '#e6a23c' : '#909399'
        }))
      }
    }
  } catch {
    // Ignore and keep the fallback data already shown on the page.
  } finally {
    loading.value = false
  }
}

onMounted(loadProfile)
</script>

<style scoped>
.student-profile {
  height: 100%;
}

.my-page-header {
  margin-bottom: 8px;
}

.profile-layout {
  display: grid;
  grid-template-columns: minmax(320px, 420px) minmax(0, 1fr);
  gap: 20px;
}

.profile-main,
.profile-side {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.profile-card,
.security-card,
.overview-card,
.activity-card {
  border-radius: 20px;
  border: 1px solid #dbe5ef;
  box-shadow: 0 14px 34px rgba(22, 48, 79, 0.06);
}

.profile-card__top {
  display: flex;
  align-items: center;
  gap: 18px;
  margin-bottom: 20px;
}

.profile-card__avatar {
  background: linear-gradient(135deg, #1f7ae0, #45b2ff);
  color: #fff;
  font-size: 28px;
  font-weight: 700;
}

.profile-card__meta h2 {
  margin: 0;
  font-size: 28px;
  color: #173153;
}

.profile-card__meta p {
  margin: 8px 0 0;
  color: #64809b;
}

.profile-tags {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 14px;
}

.profile-info {
  display: grid;
  gap: 12px;
}

.info-item {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding: 12px 14px;
  border-radius: 14px;
  background: #f7fbff;
}

.info-label {
  color: #6b8198;
}

.info-value {
  color: #173153;
  font-weight: 600;
  text-align: right;
  word-break: break-word;
}

.profile-actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  margin-top: 20px;
}

.card-header {
  font-weight: 700;
  color: #1c3554;
}

.setting-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.setting-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 0;
  border-bottom: 1px solid #edf2f7;
}

.setting-item:last-child {
  border-bottom: none;
  padding-bottom: 0;
}

.setting-item strong {
  color: #173153;
}

.setting-item p {
  margin: 6px 0 0;
  color: #6b8198;
  font-size: 13px;
  line-height: 1.6;
}

.overview-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.overview-item {
  padding: 18px 16px;
  border-radius: 16px;
  background: linear-gradient(180deg, #f7fbff 0%, #eef6ff 100%);
}

.overview-item span {
  display: block;
  color: #6b8198;
  font-size: 13px;
}

.overview-item strong {
  display: block;
  margin-top: 10px;
  font-size: 28px;
  color: #173153;
}

.activity-title {
  margin: 0 0 6px;
  color: #173153;
  font-size: 15px;
}

.activity-content {
  margin: 0;
  color: #607792;
  line-height: 1.6;
}

@media (max-width: 1080px) {
  .profile-layout {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .profile-card__top {
    flex-direction: column;
    align-items: flex-start;
  }

  .overview-grid {
    grid-template-columns: 1fr;
  }

  .info-item {
    flex-direction: column;
  }

  .info-value {
    text-align: left;
  }
}
</style>

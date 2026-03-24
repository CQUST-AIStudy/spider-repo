<template>
  <div class="teacher-profile">
    <page-header class="my-page-header" title="个人信息" description="查看和编辑您的个人信息" />

    <el-row :gutter="20">
      <el-col :span="8">
        <el-card class="profile-card">
          <div class="profile-header">
            <el-avatar :size="100" :src="avatarUrl" />
            <h3>{{ displayName }}</h3>
            <p>{{ roleText }}</p>
          </div>

          <div class="profile-info">
            <div class="info-item">
              <span class="info-label">用户名</span>
              <span class="info-value">{{ userInfo.username }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">角色</span>
              <span class="info-value">{{ roleText }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">工号/学号</span>
              <span class="info-value">{{ userInfo.usernum || '未设置' }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">电子邮箱</span>
              <span class="info-value">{{ userInfo.email || '未设置' }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">班级</span>
              <span class="info-value">{{ userInfo.class || '未设置' }}</span>
            </div>
          </div>
        </el-card>
      </el-col>

      <el-col :span="16">
        <el-card class="form-card">
          <template #header>
            <div class="card-header"><span>修改密码</span></div>
          </template>

          <el-form ref="passwordFormRef" :model="passwordForm" :rules="passwordRules" label-width="100px">
            <el-form-item label="当前密码" prop="oldPassword">
              <el-input v-model="passwordForm.oldPassword" type="password" show-password placeholder="请输入当前密码" />
            </el-form-item>
            <el-form-item label="新密码" prop="newPassword">
              <el-input v-model="passwordForm.newPassword" type="password" show-password placeholder="请输入新密码（至少6位）" />
            </el-form-item>
            <el-form-item label="确认新密码" prop="confirmPassword">
              <el-input v-model="passwordForm.confirmPassword" type="password" show-password placeholder="请再次输入新密码" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="changingPassword" @click="changePassword">修改密码</el-button>
              <el-button @click="resetPasswordForm">重置</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, reactive, computed } from 'vue'
import { ElMessage } from 'element-plus'
import axios from 'axios'
import { getUserInfo } from '../../constants/auth'
import PageHeader from '../../components/PageHeader.vue'
import { API_BASE_URL_WITH_SLASH } from '../../config/runtime'

const apiClient = axios.create({
  baseURL: API_BASE_URL_WITH_SLASH,
  timeout: 10000,
  withCredentials: true,
  headers: { 'Content-Type': 'application/json' }
})

const userInfo = computed(() => {
  return getUserInfo() || {}
})

const displayName = computed(() => userInfo.value.username || '教师用户')
const avatarUrl = 'https://cube.elemecdn.com/3/7c/3ea6beec64369c2642b92c6726f1epng.png'
const roleText = computed(() => {
  const map = { teacher: '教师', student: '学生', admin: '管理员' }
  return map[userInfo.value.role] || '用户'
})

const passwordFormRef = ref(null)
const changingPassword = ref(false)
const passwordForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })

const validateConfirm = (_rule, value, callback) => {
  if (value !== passwordForm.newPassword) callback(new Error('两次输入的密码不一致'))
  else callback()
}

const passwordRules = {
  oldPassword: [{ required: true, message: '请输入当前密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, message: '密码长度不能小于6位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请确认新密码', trigger: 'blur' },
    { validator: validateConfirm, trigger: 'blur' }
  ]
}

const changePassword = () => {
  passwordFormRef.value.validate(async (valid) => {
    if (!valid) return
    changingPassword.value = true
    try {
      const res = await apiClient.post('/api/user/password', {
        oldPassword: passwordForm.oldPassword,
        newPassword: passwordForm.newPassword
      })
      const data = res.data || res
      if (data.success) {
        ElMessage.success('密码修改成功')
        resetPasswordForm()
      } else {
        ElMessage.error(data.message || '密码修改失败')
      }
    } catch (e) {
      ElMessage.error('密码修改失败: ' + (e.response?.data?.message || e.message))
    } finally {
      changingPassword.value = false
    }
  })
}

const resetPasswordForm = () => {
  passwordForm.oldPassword = ''
  passwordForm.newPassword = ''
  passwordForm.confirmPassword = ''
}
</script>

<style scoped>
.teacher-profile { height: 100%; }
.profile-card { margin-bottom: 20px; padding: 20px; }
.profile-header {
  display: flex; flex-direction: column; align-items: center;
  padding-bottom: 20px; border-bottom: 1px solid #f0f0f0;
}
.profile-header h3 { margin: 10px 0 5px; font-size: 18px; }
.profile-header p { margin: 0; font-size: 14px; color: #9aa0a6; }
.profile-info { margin-top: 20px; }
.info-item { display: flex; justify-content: space-between; margin-bottom: 15px; }
.info-label { color: #9aa0a6; }
.info-value { color: #202124; font-weight: 500; }
.form-card { margin-bottom: 20px; }
.card-header { font-weight: 600; }
.my-page-header { padding: 20px; }
</style>

<template>
  <div class="g-login-page">
    <div class="g-login-card">
      <!-- Logo + Title -->
      <div class="g-brand">
        <div class="g-logo-ring">
          <svg width="36" height="36" viewBox="0 0 36 36" fill="none">
            <circle cx="18" cy="18" r="16" stroke="#1a73e8" stroke-width="2.5" opacity="0.2"/>
            <path d="M12 18l4 4 8-8" stroke="#1a73e8" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
        </div>
        <h1 class="g-brand-title">智能教辅平台</h1>
        <p class="g-brand-sub">{{ isRegister ? '创建新账号' : '登录以继续' }}</p>
      </div>

      <!-- Tab -->
      <div class="g-tab">
        <button :class="{ active: !isRegister }" @click="isRegister = false">登录</button>
        <button :class="{ active: isRegister }" @click="isRegister = true">注册</button>
      </div>

      <!-- Login Form -->
      <template v-if="!isRegister">
        <div class="g-roles">
          <div v-for="r in roles" :key="r.value"
               class="g-role-chip" :class="{ selected: selectedRole === r.value }"
               @click="selectedRole = r.value">
            <el-icon :size="18"><component :is="r.icon" /></el-icon>
            <span>{{ r.label }}</span>
          </div>
        </div>

        <el-form ref="loginFormRef" :model="loginForm" :rules="loginRules" class="g-form" @keyup.enter="handleLogin">
          <el-form-item prop="username">
            <el-input v-model="loginForm.username" placeholder="用户名" size="large">
              <template #prefix><el-icon><User /></el-icon></template>
            </el-input>
          </el-form-item>
          <el-form-item prop="password">
            <el-input v-model="loginForm.password" type="password" placeholder="密码" show-password size="large">
              <template #prefix><el-icon><Lock /></el-icon></template>
            </el-input>
          </el-form-item>
          <el-button type="primary" :loading="loading" class="g-submit" size="large" @click="handleLogin">
            {{ roleButtonText }}
          </el-button>
        </el-form>
      </template>

      <!-- Register Form -->
      <el-form v-else ref="registerFormRef" :model="registerForm" :rules="registerRules" class="g-form" @keyup.enter="handleRegister">
        <el-form-item prop="username">
          <el-input v-model="registerForm.username" placeholder="用户名" size="large">
            <template #prefix><el-icon><User /></el-icon></template>
          </el-input>
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="registerForm.password" type="password" placeholder="密码" show-password size="large">
            <template #prefix><el-icon><Lock /></el-icon></template>
          </el-input>
        </el-form-item>
        <el-form-item prop="confirmPassword">
          <el-input v-model="registerForm.confirmPassword" type="password" placeholder="确认密码" show-password size="large">
            <template #prefix><el-icon><Lock /></el-icon></template>
          </el-input>
        </el-form-item>
        <el-form-item prop="role">
          <el-radio-group v-model="registerForm.role" disabled>
            <el-radio label="student">学生</el-radio>
            <el-radio label="teacher">教师</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item prop="usernum">
          <el-input v-model="registerForm.usernum" placeholder="学号（用于绑定PTA数据）" size="large">
            <template #prefix><el-icon><Postcard /></el-icon></template>
          </el-input>
        </el-form-item>
        <el-form-item prop="classname">
          <el-input v-model="registerForm.classname" placeholder="班级（如：计科23）" size="large">
            <template #prefix><el-icon><School /></el-icon></template>
          </el-input>
        </el-form-item>
        <el-button type="primary" :loading="loading" class="g-submit" size="large" @click="handleRegister">注册</el-button>
      </el-form>

      <div class="g-footer-text">智能学情分析与个性化实验能力提升平台</div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, markRaw, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock, Postcard, School, Reading, Notebook, Setting } from '@element-plus/icons-vue'
import { useUserStore } from '../store'
import api from '../api'

const router = useRouter()
const userStore = useUserStore()
const loginFormRef = ref(null)
const registerFormRef = ref(null)
const loading = ref(false)
const isRegister = ref(false)
const selectedRole = ref('teacher')

const roles = [
  { value: 'teacher', label: '教师', icon: markRaw(Reading) },
  { value: 'student', label: '学生', icon: markRaw(Notebook) },
  { value: 'admin', label: '管理员', icon: markRaw(Setting) }
]

const roleButtonText = computed(() => {
  const map = { teacher: '教师登录', student: '学生登录', admin: '管理员登录' }
  return map[selectedRole.value] || '登录'
})

const defaultAccounts = {
  teacher: { username: 'teacher1', password: 'password123' },
  student: { username: 'student1', password: 'password123' },
  admin: { username: 'admin1', password: 'password123' }
}
const loginForm = reactive({ username: 'teacher1', password: 'password123' })

// 切换角色时自动填充默认账号
watch(selectedRole, (role) => {
  const acc = defaultAccounts[role]
  if (acc) {
    loginForm.username = acc.username
    loginForm.password = acc.password
  }
})
const loginRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码长度不能小于6位', trigger: 'blur' }
  ]
}

const handleLogin = () => {
  loginFormRef.value.validate(async (valid) => {
    if (!valid) return
    loading.value = true
    try {
      const result = await userStore.login(loginForm.username, loginForm.password)
      if (result && result.success) {
        const userInfo = result.user || result.userInfo
        const targetRole = userInfo?.role || selectedRole.value
        // 教师登录时清除已选班级，强制重新选择
        if (targetRole === 'teacher') {
          userStore.setSelectedClass(null)
        }
        ElMessage.success('登录成功')
        setTimeout(async () => {
          loading.value = false
          if (targetRole === 'teacher') await router.push('/teacher/select-class')
          else if (targetRole === 'admin') await router.push('/admin/dashboard')
          else await router.push('/student/dashboard')
        }, 300)
      } else {
        ElMessage.error(result?.message || '登录失败')
        loading.value = false
      }
    } catch (e) {
      ElMessage.error('登录异常: ' + (e.message || '未知错误'))
      loading.value = false
    }
  })
}

const registerForm = reactive({ username: '', password: '', confirmPassword: '', role: 'student', usernum: '', classname: '' })
const validateConfirmPassword = (_rule, value, callback) => {
  if (value !== registerForm.password) callback(new Error('两次输入的密码不一致'))
  else callback()
}
const registerRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }, { min: 6, message: '密码长度不能小于6位', trigger: 'blur' }],
  confirmPassword: [{ required: true, message: '请确认密码', trigger: 'blur' }, { validator: validateConfirmPassword, trigger: 'blur' }],
  role: [{ required: true, message: '请选择角色', trigger: 'change' }],
  usernum: [{ required: true, message: '请输入学号', trigger: 'blur' }],
  classname: [{ required: true, message: '请输入班级', trigger: 'blur' }]
}

const handleRegister = () => {
  registerFormRef.value.validate(async (valid) => {
    if (!valid) return
    loading.value = true
    registerForm.role = 'student'
    try {
      const res = await api.register(registerForm)
      if (res && res.success) {
        ElMessage.success('注册成功，请登录')
        isRegister.value = false
        loginForm.username = registerForm.username
        loginForm.password = ''
      } else { ElMessage.error(res?.message || '注册失败') }
    } catch (e) { ElMessage.error('注册异常: ' + (e.message || '未知错误')) }
    finally { loading.value = false }
  })
}
</script>

<style scoped>
.g-login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: url('~@/assets/2725ca8fc000d3685a4639abc080e69d_720.jpg') no-repeat center center / cover;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
  position: relative;
}
.g-login-page::before {
  content: '';
  position: absolute;
  inset: 0;
  background: rgba(0, 0, 0, 0.35);
  backdrop-filter: blur(2px);
}

.g-login-card {
  width: 420px;
  background: rgba(255, 255, 255, 0.95);
  border-radius: 16px;
  padding: 40px 40px 32px;
  box-shadow: 0 4px 24px rgba(0,0,0,0.2);
  border: 1px solid rgba(255,255,255,0.6);
  position: relative;
  z-index: 1;
  backdrop-filter: blur(12px);
}

.g-brand { text-align: center; margin-bottom: 28px; }
.g-logo-ring { margin-bottom: 16px; }
.g-brand-title {
  font-size: 24px;
  font-weight: 400;
  color: #202124;
  margin: 0 0 6px;
}
.g-brand-sub {
  font-size: 15px;
  color: #5f6368;
  margin: 0;
}

.g-tab {
  display: flex;
  gap: 0;
  margin-bottom: 24px;
  border-bottom: 1px solid #e8eaed;
}
.g-tab button {
  flex: 1;
  background: none;
  border: none;
  padding: 10px 0;
  font-size: 14px;
  font-weight: 500;
  color: #5f6368;
  cursor: pointer;
  border-bottom: 2px solid transparent;
  transition: all 0.2s;
}
.g-tab button.active {
  color: #1a73e8;
  border-bottom-color: #1a73e8;
}
.g-tab button:hover:not(.active) { color: #202124; }

.g-roles {
  display: flex;
  gap: 10px;
  margin-bottom: 24px;
}
.g-role-chip {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 10px 0;
  border: 1px solid #dadce0;
  border-radius: 100px;
  cursor: pointer;
  font-size: 13px;
  color: #5f6368;
  transition: all 0.2s;
  background: #fff;
}
.g-role-chip:hover { background: #f8f9fa; border-color: #bdc1c6; }
.g-role-chip.selected {
  background: #e8f0fe;
  border-color: #1a73e8;
  color: #1a73e8;
}

.g-form :deep(.el-input__wrapper) {
  border-radius: 8px;
  box-shadow: 0 0 0 1px #dadce0;
  padding: 4px 12px;
}
.g-form :deep(.el-input__wrapper:hover) { box-shadow: 0 0 0 1px #bdc1c6; }
.g-form :deep(.el-input__wrapper.is-focus) { box-shadow: 0 0 0 2px #1a73e8; }
.g-form :deep(.el-form-item) { margin-bottom: 18px; }

.g-submit {
  width: 100%;
  border-radius: 100px;
  background: #1a73e8;
  border: none;
  font-size: 15px;
  font-weight: 500;
  height: 44px;
  margin-top: 4px;
  letter-spacing: 0.3px;
}
.g-submit:hover { background: #1765cc; box-shadow: 0 1px 3px rgba(26,115,232,0.3); }

.g-footer-text {
  text-align: center;
  margin-top: 24px;
  font-size: 12px;
  color: #9aa0a6;
}
</style>

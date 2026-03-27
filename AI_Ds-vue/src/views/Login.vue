<template>
  <div class="login-page">
    <div class="login-card">
      <div class="login-header">
        <h1>智能教辅平台</h1>
        <p>使用账号登录系统</p>
      </div>

      <el-tabs v-model="activeTab" stretch class="login-tabs">
        <el-tab-pane label="登录" name="login">
          <el-form
            ref="loginFormRef"
            :model="loginForm"
            :rules="loginRules"
            label-position="top"
            @keyup.enter="handleLogin"
          >
            <el-form-item label="角色">
              <el-radio-group v-model="selectedRole">
                <el-radio-button label="teacher">教师</el-radio-button>
                <el-radio-button label="student">学生</el-radio-button>
                <el-radio-button label="admin">管理员</el-radio-button>
              </el-radio-group>
            </el-form-item>

            <el-form-item prop="username" label="用户名">
              <el-input v-model="loginForm.username" placeholder="请输入用户名" size="large" />
            </el-form-item>

            <el-form-item prop="password" label="密码">
              <el-input
                v-model="loginForm.password"
                type="password"
                placeholder="请输入密码"
                show-password
                size="large"
              />
            </el-form-item>

            <el-button type="primary" size="large" class="submit-btn" :loading="loading" @click="handleLogin">
              登录
            </el-button>
          </el-form>
        </el-tab-pane>

        <el-tab-pane label="注册" name="register">
          <el-form
            ref="registerFormRef"
            :model="registerForm"
            :rules="registerRules"
            label-position="top"
            @keyup.enter="handleRegister"
          >
            <el-form-item prop="username" label="用户名">
              <el-input v-model="registerForm.username" placeholder="请输入用户名" size="large" />
            </el-form-item>

            <el-form-item prop="password" label="密码">
              <el-input
                v-model="registerForm.password"
                type="password"
                placeholder="请输入密码"
                show-password
                size="large"
              />
            </el-form-item>

            <el-form-item prop="confirmPassword" label="确认密码">
              <el-input
                v-model="registerForm.confirmPassword"
                type="password"
                placeholder="请再次输入密码"
                show-password
                size="large"
              />
            </el-form-item>

            <el-form-item prop="usernum" label="学号">
              <el-input v-model="registerForm.usernum" placeholder="请输入学号" size="large" />
            </el-form-item>

            <el-form-item prop="classname" label="班级">
              <el-input v-model="registerForm.classname" placeholder="例如：计科23" size="large" />
            </el-form-item>

            <el-button type="primary" size="large" class="submit-btn" :loading="loading" @click="handleRegister">
              注册学生账号
            </el-button>
          </el-form>
        </el-tab-pane>
      </el-tabs>

      <p v-if="isDevelopment" class="dev-hint">开发环境已自动填充默认测试账号。</p>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '../store'
import api from '../api'

const router = useRouter()
const userStore = useUserStore()

const activeTab = ref('login')
const loginFormRef = ref(null)
const registerFormRef = ref(null)
const loading = ref(false)
const selectedRole = ref('teacher')
const isDevelopment = process.env.NODE_ENV === 'development'

const defaultAccounts = isDevelopment ? {
  teacher: { username: 'teacher1', password: 'password123' },
  student: { username: 'student1', password: 'password123' },
  admin: { username: 'admin1', password: 'password123' }
} : {}

const loginForm = reactive({
  username: defaultAccounts.teacher?.username || '',
  password: defaultAccounts.teacher?.password || ''
})

const registerForm = reactive({
  username: '',
  password: '',
  confirmPassword: '',
  usernum: '',
  classname: ''
})

watch(selectedRole, (role) => {
  if (!isDevelopment) return
  const account = defaultAccounts[role]
  if (!account) return
  loginForm.username = account.username
  loginForm.password = account.password
})

const loginRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码长度不能少于 6 位', trigger: 'blur' }
  ]
}

function validateConfirmPassword(_rule, value, callback) {
  if (value !== registerForm.password) {
    callback(new Error('两次输入的密码不一致'))
    return
  }
  callback()
}

const registerRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码长度不能少于 6 位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    { validator: validateConfirmPassword, trigger: 'blur' }
  ],
  usernum: [{ required: true, message: '请输入学号', trigger: 'blur' }],
  classname: [{ required: true, message: '请输入班级', trigger: 'blur' }]
}

function handleLogin() {
  loginFormRef.value?.validate(async (valid) => {
    if (!valid) return
    loading.value = true
    try {
      const result = await userStore.login(loginForm.username, loginForm.password, selectedRole.value)
      if (!(result && result.success)) {
        ElMessage.error(result?.message || '登录失败')
        return
      }

      const userInfo = result.user || result.userInfo
      const targetRole = userInfo?.role || selectedRole.value
      if (targetRole === 'teacher') {
        userStore.setSelectedClass(null)
      }

      ElMessage.success('登录成功')
      if (targetRole === 'teacher') {
        await router.push('/teacher/select-class')
      } else if (targetRole === 'admin') {
        await router.push('/admin/dashboard')
      } else {
        await router.push('/student/dashboard')
      }
    } catch (error) {
      ElMessage.error(`登录异常: ${error.message || '未知错误'}`)
    } finally {
      loading.value = false
    }
  })
}

function handleRegister() {
  registerFormRef.value?.validate(async (valid) => {
    if (!valid) return
    loading.value = true
    try {
      const result = await api.register({
        ...registerForm,
        role: 'student'
      })
      if (!(result && result.success)) {
        ElMessage.error(result?.message || '注册失败')
        return
      }

      ElMessage.success('注册成功，请登录')
      activeTab.value = 'login'
      loginForm.username = registerForm.username
      loginForm.password = ''
      registerForm.username = ''
      registerForm.password = ''
      registerForm.confirmPassword = ''
      registerForm.usernum = ''
      registerForm.classname = ''
    } catch (error) {
      ElMessage.error(`注册异常: ${error.message || '未知错误'}`)
    } finally {
      loading.value = false
    }
  })
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: linear-gradient(180deg, #f7f8fb 0%, #eef2f7 100%);
}

.login-card {
  width: 100%;
  max-width: 420px;
  padding: 32px 28px 24px;
  border-radius: 18px;
  background: #ffffff;
  border: 1px solid #e7eaf0;
  box-shadow: 0 18px 50px rgba(15, 23, 42, 0.08);
}

.login-header {
  margin-bottom: 20px;
  text-align: center;
}

.login-header h1 {
  margin: 0 0 8px;
  font-size: 28px;
  color: #1f2937;
}

.login-header p {
  margin: 0;
  color: #6b7280;
  font-size: 14px;
}

.login-tabs :deep(.el-tabs__header) {
  margin-bottom: 20px;
}

.submit-btn {
  width: 100%;
  margin-top: 8px;
  border-radius: 12px;
}

.dev-hint {
  margin: 16px 0 0;
  text-align: center;
  color: #909399;
  font-size: 12px;
}

@media (max-width: 480px) {
  .login-card {
    padding: 24px 18px 20px;
    border-radius: 14px;
  }

  .login-header h1 {
    font-size: 24px;
  }
}
</style>

<template>
  <div class="admin-profile">
    <page-header
        class="my-page-header"
      title="个人信息"
      description="查看和编辑您的个人信息"
    />

    <el-row :gutter="20">
      <el-col :span="8">
        <el-card class="profile-card">
          <div class="profile-header">
            <el-avatar :size="100" :src="userInfo.avatar" />
            <h3>{{ userInfo.name }}</h3>
            <p>{{ userInfo.role === 'admin' ? '系统管理员' : '未知角色' }}</p>
          </div>

          <div class="profile-info">
            <div class="info-item">
              <span class="info-label">用户ID</span>
              <span class="info-value">{{ userInfo.id }}</span>
            </div>

            <div class="info-item">
              <span class="info-label">部门</span>
              <span class="info-value">{{ userInfo.department }}</span>
            </div>

            <div class="info-item">
              <span class="info-label">电子邮箱</span>
              <span class="info-value">{{ userInfo.email }}</span>
            </div>

            <div class="info-item">
              <span class="info-label">联系电话</span>
              <span class="info-value">{{ userInfo.phone }}</span>
            </div>
          </div>
        </el-card>
      </el-col>

      <el-col :span="16">
        <el-card class="form-card">
          <template #header>
            <div class="card-header">
              <span>修改个人信息</span>
            </div>
          </template>

          <el-form ref="formRef" :model="form" label-width="100px">
            <el-form-item label="用户名">
              <el-input v-model="form.name" />
            </el-form-item>

            <el-form-item label="电子邮箱">
              <el-input v-model="form.email" />
            </el-form-item>

            <el-form-item label="联系电话">
              <el-input v-model="form.phone" />
            </el-form-item>

            <el-form-item label="部门">
              <el-input v-model="form.department" />
            </el-form-item>

            <el-form-item>
              <el-button type="primary" @click="saveProfile">保存修改</el-button>
              <el-button @click="resetForm">重置</el-button>
            </el-form-item>
          </el-form>
        </el-card>

        <el-card class="form-card">
          <template #header>
            <div class="card-header">
              <span>修改密码</span>
            </div>
          </template>

          <el-form ref="passwordFormRef" :model="passwordForm" label-width="100px">
            <el-form-item label="当前密码">
              <el-input v-model="passwordForm.currentPassword" type="password" show-password />
            </el-form-item>

            <el-form-item label="新密码">
              <el-input v-model="passwordForm.newPassword" type="password" show-password />
            </el-form-item>

            <el-form-item label="确认新密码">
              <el-input v-model="passwordForm.confirmPassword" type="password" show-password />
            </el-form-item>

            <el-form-item>
              <el-button type="primary" @click="changePassword">修改密码</el-button>
              <el-button @click="resetPasswordForm">重置</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import PageHeader from '../../components/PageHeader.vue'

// 获取用户信息
const userInfo = computed(() => {
  const userInfoStr = localStorage.getItem('userInfo')
  try {
    return userInfoStr ? JSON.parse(userInfoStr) : {
      name: '管理员',
      role: 'admin',
      avatar: '',
      id: '',
      department: '',
      email: '',
      phone: ''
    }
  } catch (error) {
    return {
      name: '管理员',
      role: 'admin',
      avatar: '',
      id: '',
      department: '',
      email: '',
      phone: ''
    }
  }
})

// 表单数据
const formRef = ref(null)
const form = reactive({
  name: '',
  email: '',
  phone: '',
  department: ''
})

// 密码表单
const passwordFormRef = ref(null)
const passwordForm = reactive({
  currentPassword: '',
  newPassword: '',
  confirmPassword: ''
})

// 保存个人信息
const saveProfile = () => {
  // 模拟保存操作
  ElMessage.success('个人信息已更新')

  // 更新本地存储的用户信息
  const updatedInfo = {
    ...userInfo.value,
    name: form.name,
    email: form.email,
    phone: form.phone,
    department: form.department
  }
  localStorage.setItem('userInfo', JSON.stringify(updatedInfo))
}

// 重置表单
const resetForm = () => {
  form.name = userInfo.value.name
  form.email = userInfo.value.email
  form.phone = userInfo.value.phone
  form.department = userInfo.value.department
}

// 修改密码
const changePassword = () => {
  if (!passwordForm.currentPassword) {
    ElMessage.warning('请输入当前密码')
    return
  }

  if (!passwordForm.newPassword) {
    ElMessage.warning('请输入新密码')
    return
  }

  if (passwordForm.newPassword !== passwordForm.confirmPassword) {
    ElMessage.warning('两次输入的新密码不一致')
    return
  }

  // 模拟修改密码操作
  ElMessage.success('密码已成功修改')
  resetPasswordForm()
}

// 重置密码表单
const resetPasswordForm = () => {
  passwordForm.currentPassword = ''
  passwordForm.newPassword = ''
  passwordForm.confirmPassword = ''
}

// 初始化表单数据
onMounted(() => {
  // 初始化个人信息表单
  form.name = userInfo.value.name
  form.email = userInfo.value.email
  form.phone = userInfo.value.phone
  form.department = userInfo.value.department
})
</script>

<style scoped>
.admin-profile {
  height: 100%;
}

.profile-card {
  margin-bottom: 20px;
  padding: 20px;
}

.profile-header {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding-bottom: 20px;
  border-bottom: 1px solid #f0f0f0;
}

.profile-header h3 {
  margin: 10px 0 5px;
  font-size: 18px;
}

.profile-header p {
  margin: 0;
  font-size: 14px;
  color: #909399;
}

.profile-info {
  margin-top: 20px;
}

.info-item {
  display: flex;
  justify-content: space-between;
  margin-bottom: 15px;
}

.info-label {
  color: #909399;
}

.info-value {
  color: #303133;
  font-weight: 500;
}

.form-card {
  margin-bottom: 20px;
}

.card-header {
  font-weight: 600;
}

.my-page-header {
  padding: 20px;
}

</style>

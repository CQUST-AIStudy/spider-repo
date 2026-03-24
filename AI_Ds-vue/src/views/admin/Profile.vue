<template>
  <div class="admin-profile">
    <page-header
        class="my-page-header"
      title="涓汉淇℃伅"
      description="鏌ョ湅鍜岀紪杈戞偍鐨勪釜浜轰俊鎭?
    />

    <el-row :gutter="20">
      <el-col :span="8">
        <el-card class="profile-card">
          <div class="profile-header">
            <el-avatar :size="100" :src="userInfo.avatar" />
            <h3>{{ userInfo.name }}</h3>
            <p>{{ userInfo.role === 'admin' ? '绯荤粺绠＄悊鍛? : '鏈煡瑙掕壊' }}</p>
          </div>

          <div class="profile-info">
            <div class="info-item">
              <span class="info-label">鐢ㄦ埛ID</span>
              <span class="info-value">{{ userInfo.id }}</span>
            </div>

            <div class="info-item">
              <span class="info-label">閮ㄩ棬</span>
              <span class="info-value">{{ userInfo.department }}</span>
            </div>

            <div class="info-item">
              <span class="info-label">鐢靛瓙閭</span>
              <span class="info-value">{{ userInfo.email }}</span>
            </div>

            <div class="info-item">
              <span class="info-label">鑱旂郴鐢佃瘽</span>
              <span class="info-value">{{ userInfo.phone }}</span>
            </div>
          </div>
        </el-card>
      </el-col>

      <el-col :span="16">
        <el-card class="form-card">
          <template #header>
            <div class="card-header">
              <span>淇敼涓汉淇℃伅</span>
            </div>
          </template>

          <el-form ref="formRef" :model="form" label-width="100px">
            <el-form-item label="鐢ㄦ埛鍚?>
              <el-input v-model="form.name" />
            </el-form-item>

            <el-form-item label="鐢靛瓙閭">
              <el-input v-model="form.email" />
            </el-form-item>

            <el-form-item label="鑱旂郴鐢佃瘽">
              <el-input v-model="form.phone" />
            </el-form-item>

            <el-form-item label="閮ㄩ棬">
              <el-input v-model="form.department" />
            </el-form-item>

            <el-form-item>
              <el-button type="primary" @click="saveProfile">淇濆瓨淇敼</el-button>
              <el-button @click="resetForm">閲嶇疆</el-button>
            </el-form-item>
          </el-form>
        </el-card>

        <el-card class="form-card">
          <template #header>
            <div class="card-header">
              <span>淇敼瀵嗙爜</span>
            </div>
          </template>

          <el-form ref="passwordFormRef" :model="passwordForm" label-width="100px">
            <el-form-item label="褰撳墠瀵嗙爜">
              <el-input v-model="passwordForm.currentPassword" type="password" show-password />
            </el-form-item>

            <el-form-item label="鏂板瘑鐮?>
              <el-input v-model="passwordForm.newPassword" type="password" show-password />
            </el-form-item>

            <el-form-item label="纭鏂板瘑鐮?>
              <el-input v-model="passwordForm.confirmPassword" type="password" show-password />
            </el-form-item>

            <el-form-item>
              <el-button type="primary" @click="changePassword">淇敼瀵嗙爜</el-button>
              <el-button @click="resetPasswordForm">閲嶇疆</el-button>
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
import { getUserInfo, setUserInfo } from '../../constants/auth'

const DEFAULT_ADMIN_USER_INFO = {
  name: '管理员',
  role: 'admin',
  avatar: '',
  id: '',
  department: '',
  email: '',
  phone: ''
}

// 鑾峰彇鐢ㄦ埛淇℃伅
const userInfo = computed(() => getUserInfo() || DEFAULT_ADMIN_USER_INFO)

// 琛ㄥ崟鏁版嵁
const formRef = ref(null)
const form = reactive({
  name: '',
  email: '',
  phone: '',
  department: ''
})

// 瀵嗙爜琛ㄥ崟
const passwordFormRef = ref(null)
const passwordForm = reactive({
  currentPassword: '',
  newPassword: '',
  confirmPassword: ''
})

// 淇濆瓨涓汉淇℃伅
const saveProfile = () => {
  // 妯℃嫙淇濆瓨鎿嶄綔
  ElMessage.success('涓汉淇℃伅宸叉洿鏂?)

  // 鏇存柊鏈湴瀛樺偍鐨勭敤鎴蜂俊鎭?
  const updatedInfo = {
    ...userInfo.value,
    name: form.name,
    email: form.email,
    phone: form.phone,
    department: form.department
  }
  setUserInfo(updatedInfo)
}

// 閲嶇疆琛ㄥ崟
const resetForm = () => {
  form.name = userInfo.value.name
  form.email = userInfo.value.email
  form.phone = userInfo.value.phone
  form.department = userInfo.value.department
}

// 淇敼瀵嗙爜
const changePassword = () => {
  if (!passwordForm.currentPassword) {
    ElMessage.warning('璇疯緭鍏ュ綋鍓嶅瘑鐮?)
    return
  }

  if (!passwordForm.newPassword) {
    ElMessage.warning('璇疯緭鍏ユ柊瀵嗙爜')
    return
  }

  if (passwordForm.newPassword !== passwordForm.confirmPassword) {
    ElMessage.warning('涓ゆ杈撳叆鐨勬柊瀵嗙爜涓嶄竴鑷?)
    return
  }

  // 妯℃嫙淇敼瀵嗙爜鎿嶄綔
  ElMessage.success('瀵嗙爜宸叉垚鍔熶慨鏀?)
  resetPasswordForm()
}

// 閲嶇疆瀵嗙爜琛ㄥ崟
const resetPasswordForm = () => {
  passwordForm.currentPassword = ''
  passwordForm.newPassword = ''
  passwordForm.confirmPassword = ''
}

// 鍒濆鍖栬〃鍗曟暟鎹?
onMounted(() => {
  // 鍒濆鍖栦釜浜轰俊鎭〃鍗?
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



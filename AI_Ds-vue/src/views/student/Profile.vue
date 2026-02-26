<template>
  <div class="profile-container">
    <page-header
        class="my-page-header"
      title="个人信息"
      description="查看和管理您的个人资料"
    />

    <loading-state :loading="loading">
      <div class="profile-content">
        <el-row :gutter="20">
          <el-col :span="8">
            <el-card class="user-card">
              <div class="user-avatar">
                <img class="user_pic" src="../../assets/User/Cat.jpg" />
                <div class="edit-avatar">
                  <el-button
                    type="primary"
                    circle
                    size="small"
                    class="edit-btn"
                  >
                    <el-icon><EditPen /></el-icon>
                  </el-button>
                </div>
              </div>

              <div class="user-info">
                <h2 class="user-name">{{ userInfo.username}}</h2>
                <div class="user-id">学号：{{ userInfo.usernum }}</div>
                <div class="user-class">班级：{{ userInfo.class }}</div>
                <div class="user-grade">年级：2023级</div>
                <div class="user-email">邮箱：{{ userInfo.email }}</div>
              </div>

              <div class="user-actions">
                <el-button type="primary" @click="openEditProfileDialog">修改资料</el-button>
                <el-button @click="openChangePasswordDialog">修改密码</el-button>
              </div>
            </el-card>

            <el-card class="account-settings">
              <template #header>
                <div class="card-header">
                  <span>账号设置</span>
                </div>
              </template>
              <div class="settings-list">
                <el-space direction="vertical" size="large" fill style="width: 100%">
                  <div class="setting-item">
                    <div class="setting-content">
                      <div class="setting-icon">
                        <el-icon><Bell /></el-icon>
                      </div>
                      <div class="setting-info">
                        <div class="setting-title">系统通知</div>
                        <div class="setting-desc">接收重要的实验和课程通知</div>
                      </div>
                    </div>
                    <div class="setting-action">
                      <el-switch v-model="settings.notifications" />
                    </div>
                  </div>

                  <div class="setting-item">
                    <div class="setting-content">
                      <div class="setting-icon">
                        <el-icon><AlarmClock /></el-icon>
                      </div>
                      <div class="setting-info">
                        <div class="setting-title">截止日期提醒</div>
                        <div class="setting-desc">实验截止前提前通知</div>
                      </div>
                    </div>
                    <div class="setting-action">
                      <el-switch v-model="settings.reminders" />
                    </div>
                  </div>

                  <div class="setting-item">
                    <div class="setting-content">
                      <div class="setting-icon">
                        <el-icon><Lock /></el-icon>
                      </div>
                      <div class="setting-info">
                        <div class="setting-title">双重认证</div>
                        <div class="setting-desc">增强账号安全性</div>
                      </div>
                    </div>
                    <div class="setting-action">
                      <el-switch v-model="settings.twoFactor" />
                    </div>
                  </div>

                  <div class="setting-item">
                    <div class="setting-content">
                      <div class="setting-icon">
                        <el-icon><ChatLineRound /></el-icon>
                      </div>
                      <div class="setting-info">
                        <div class="setting-title">评测反馈通知</div>
                        <div class="setting-desc">接收AI评测的结果和反馈</div>
                      </div>
                    </div>
                    <div class="setting-action">
                      <el-switch v-model="settings.feedback" />
                    </div>
                  </div>
                </el-space>
              </div>
            </el-card>
          </el-col>

          <el-col :span="16">
            <el-card class="activity-card">
              <template #header>
                <div class="card-header">
                  <span>最近活动</span>
                </div>
              </template>

              <el-timeline>
                <el-timeline-item
                  v-for="(activity, index) in activities"
                  :key="index"
                  :type="activity.type"
                  :color="activity.color"
                  :timestamp="activity.time"
                  :hollow="activity.hollow"
                >
                  <h4 class="activity-title">{{ activity.title }}</h4>
                  <p class="activity-content">{{ activity.content }}</p>
                </el-timeline-item>
              </el-timeline>
            </el-card>

            <el-row :gutter="20" class="stats-row">
              <el-col :span="12">
                <el-card class="stats-card">
                  <template #header>
                    <div class="card-header">
                      <span>学习统计</span>
                    </div>
                  </template>

                  <div class="stats-content">
                    <div class="stats-item">
                      <div class="stats-label">总实验数</div>
                      <div class="stats-value">5</div>
                    </div>

                    <div class="stats-item">
                      <div class="stats-label">已完成</div>
                      <div class="stats-value">2</div>
                    </div>

                    <div class="stats-item">
                      <div class="stats-label">平均成绩</div>
                      <div class="stats-value">90</div>
                    </div>

                    <div class="stats-item">
                      <div class="stats-label">完成实验总用时</div>
                      <div class="stats-value">16小时</div>
                    </div>
                  </div>
                </el-card>
              </el-col>

              <el-col :span="12">
                <el-card class="badges-card">
                  <template #header>
                    <div class="card-header">
                      <span>获得的徽章</span>
                    </div>
                  </template>

                  <div class="badges-container">
                    <div class="badge-item" v-for="(badge, index) in badges" :key="index">
                      <el-tooltip
                        :content="badge.description"
                        placement="top"
                        effect="light"
                      >
                        <div class="badge-icon" :class="{ 'locked': !badge.earned }">
                          <el-icon v-if="badge.earned"><component :is="badge.icon" /></el-icon>
                          <el-icon v-else><Lock /></el-icon>
                        </div>
                      </el-tooltip>
                      <div class="badge-name">{{ badge.name }}</div>
                    </div>
                  </div>
                </el-card>
              </el-col>
            </el-row>
          </el-col>
        </el-row>
      </div>
    </loading-state>

    <!-- 修改资料对话框 -->
    <el-dialog
      v-model="editProfileDialog"
      title="修改个人资料"
      width="500px"
    >
      <el-form
        :model="profileForm"
        :rules="profileRules"
        ref="profileFormRef"
        label-width="100px"
      >
        <el-form-item label="姓名" prop="username">
          <el-input v-model="profileForm.username" placeholder="请输入姓名" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="profileForm.email" placeholder="请输入邮箱" />
        </el-form-item>
        <el-form-item label="电话" prop="phone">
          <el-input v-model="profileForm.phone" placeholder="请输入电话号码" />
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="editProfileDialog = false">取消</el-button>
          <el-button type="primary" @click="submitProfileForm" :loading="submitting">确认修改</el-button>
        </span>
      </template>
    </el-dialog>

    <!-- 修改密码对话框 -->
    <el-dialog
      v-model="changePasswordDialog"
      title="修改密码"
      width="500px"
    >
      <el-form
        :model="passwordForm"
        :rules="passwordRules"
        ref="passwordFormRef"
        label-width="100px"
      >
        <el-form-item label="原密码" prop="oldPassword">
          <el-input v-model="passwordForm.oldPassword" type="password" placeholder="请输入原密码" show-password />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="passwordForm.newPassword" type="password" placeholder="请输入新密码" show-password />
        </el-form-item>
        <el-form-item label="确认新密码" prop="confirmPassword">
          <el-input v-model="passwordForm.confirmPassword" type="password" placeholder="请再次输入新密码" show-password />
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="changePasswordDialog = false">取消</el-button>
          <el-button type="primary" @click="submitPasswordForm" :loading="submitting">确认修改</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useUserStore } from '../../store'
import { useRouter } from 'vue-router' // 导入 router
import PageHeader from '../../components/PageHeader.vue'
import LoadingState from '../../components/LoadingState.vue'
import api from '../../api'
import { ElMessage } from 'element-plus'
import axios from 'axios'
import {
  EditPen,
  Bell,
  AlarmClock,
  Lock,
  ChatLineRound,
  TrophyBase,
  Medal,
  Star,
  Cpu,
  Histogram,
  DataLine
} from '@element-plus/icons-vue'

// 创建API客户端
const apiClient = axios.create({
  baseURL: 'http://localhost:8081',
  timeout: 10000,
  withCredentials: true,
  headers: {
    'Content-Type': 'application/json'
  }
});

const userStore = useUserStore()
const router = useRouter() // 初始化路由对象
const loading = ref(true)

// 用户信息
const userInfo = reactive({...userStore.userInfo})

// 账号设置
const settings = reactive({
  notifications: true,
  reminders: true,
  twoFactor: false,
  feedback: true
})

// 最近活动
const activities = [
  {
    title: '完成实验【栈与队列的应用】',
    content: '得分：88分，AI点评：栈和队列的基本操作实现正确，表达式求值算法可以处理基本运算，但对于复杂表达式和错误处理有待完善。',
    time: '2023-09-28 20:15',
    type: 'success',
    color: '#67c23a'
  },
  {
    title: '自我评估更新',
    content: '更新了对栈和队列的知识点自评，当前掌握度：75%',
    time: '2023-09-25 15:30',
    type: 'primary',
    color: '#409eff'
  },
  {
    title: '开始实验【栈与队列的应用】',
    content: '开始了新的实验任务',
    time: '2023-09-20 10:00',
    type: 'warning',
    color: '#e6a23c'
  },
  {
    title: '完成实验【线性表的实现与应用】',
    content: '得分：92分，AI点评：代码实现规范，能够正确完成各项基本操作，但约瑟夫环问题的时间复杂度可以进一步优化。',
    time: '2023-09-12 15:30',
    type: 'success',
    color: '#67c23a'
  },
  {
    title: '提交了自我评估',
    content: '更新了对线性表的知识点自评，当前掌握度：80%',
    time: '2023-09-10 16:45',
    type: 'primary',
    color: '#409eff',
    hollow: true
  }
]

// 徽章
const badges = [
  {
    name: '实验达人',
    description: '完成所有实验任务',
    icon: TrophyBase,
    earned: false
  },
  {
    name: '优秀学员',
    description: '实验平均分超过90分',
    icon: Medal,
    earned: true
  },
  {
    name: '勤学者',
    description: '连续7天登录学习',
    icon: Star,
    earned: true
  },
  {
    name: '编程能手',
    description: '代码查重率低于5%',
    icon: Cpu,
    earned: false
  },
  {
    name: '数据结构大师',
    description: '知识点掌握度全部达到80%以上',
    icon: DataLine,
    earned: false
  },
  {
    name: '学习达人',
    description: '完成50道练习题',
    icon: Histogram,
    earned: false
  }
]

// 修改资料对话框控制
const editProfileDialog = ref(false)
const profileFormRef = ref(null)
const submitting = ref(false)

// 修改资料表单数据
const profileForm = reactive({
  username: '',
  email: '',
  phone: ''
})

// 修改资料表单验证规则
const profileRules = {
  username: [
    { required: true, message: '请输入姓名', trigger: 'blur' },
    { min: 2, max: 20, message: '长度在2到20个字符之间', trigger: 'blur' }
  ],
  email: [
    { required: true, message: '请输入邮箱地址', trigger: 'blur' },
    { type: 'email', message: '请输入正确的邮箱格式', trigger: 'blur' }
  ],
  phone: [
    { pattern: /^1[3-9]\d{9}$/, message: '请输入正确的手机号码', trigger: 'blur' }
  ]
}

// 修改密码对话框控制
const changePasswordDialog = ref(false)
const passwordFormRef = ref(null)

// 修改密码表单数据
const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

// 修改密码表单验证规则
const passwordRules = {
  oldPassword: [
    { required: true, message: '请输入原密码', trigger: 'blur' }
  ],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, message: '密码长度不能少于6个字符', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        if (value !== passwordForm.newPassword) {
          callback(new Error('两次输入的密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ]
}

// 打开修改资料对话框
const openEditProfileDialog = () => {
  // 初始化表单数据
  profileForm.username = userInfo.username || ''
  profileForm.email = userInfo.email || ''
  profileForm.phone = userInfo.phone || ''
  editProfileDialog.value = true
}

// 提交修改资料表单
const submitProfileForm = async () => {
  if (!profileFormRef.value) return
  
  await profileFormRef.value.validate(async (valid) => {
    if (!valid) {
      return false
    }
    
    submitting.value = true
    try {
      // 构造提交数据
      const userData = {
        username: profileForm.username,
        email: profileForm.email,
        phone: profileForm.phone
      }

      // 调用API更新用户信息
      // // 假设后端API是 /api/user/profile
      // const response = await apiClient.put('/api/user/profile', userData)
      
      // 更新本地数据
      userInfo.username = profileForm.username
      userInfo.email = profileForm.email
      userInfo.phone = profileForm.phone
      
      // 关闭对话框
      editProfileDialog.value = false
      
      // 显示成功消息
      ElMessage.success('个人资料修改成功')
      
      // 更新用户信息到Vuex
      userStore.updateUserInfo({
        ...userStore.userInfo,
        username: profileForm.username,
        email: profileForm.email
      })
      
    } catch (error) {
      console.error('修改资料失败:', error)
      // ElMessage.error('修改资料失败: ' + (error.message || '未知错误'))
    } finally {
      submitting.value = false
    }
  })
}

// 打开修改密码对话框
const openChangePasswordDialog = () => {
  // 重置表单数据
  passwordForm.oldPassword = ''
  passwordForm.newPassword = ''
  passwordForm.confirmPassword = ''
  changePasswordDialog.value = true
}

// 提交修改密码表单
const submitPasswordForm = async () => {
  if (!passwordFormRef.value) return
  
  await passwordFormRef.value.validate(async (valid) => {
    if (!valid) {
      return false
    }
    
    submitting.value = true
    try {
      // 构造提交数据
      const passwordData = {
        oldPassword: passwordForm.oldPassword,
        newPassword: passwordForm.newPassword
      }
      
      // 调用API更新密码
      const response = await axios.post('http://localhost:8081/api/user/password', passwordData)

      console.log("跟新内容：",response)
      if(response.data.success){
        // 关闭对话框
        changePasswordDialog.value = false

        // 显示成功消息
        ElMessage.success('密码修改成功，请使用新密码重新登录')
        
        // 清除用户登录信息
        localStorage.removeItem('token')
        localStorage.removeItem('userInfo')
        
        // 延迟一秒后跳转到登录页面，让用户有时间看到成功消息
        setTimeout(() => {
          router.push('/login')
        }, 1500)
      }else {
        ElMessage.error(response.data.message)
      }

      // 可选：退出登录，让用户重新登录
      // 这里可以调用退出登录的方法
      // logout()
      
    } catch (error) {
      console.error('修改密码失败:', error)
      ElMessage.error('修改密码失败: ' + (error.message || '未知错误'))
    } finally {
      submitting.value = false
    }
  })
}

onMounted(() => {
  loading.value = false
})
</script>

<style scoped>
.my-page-header {
  padding: 20px;
}

.profile-container {
  height: 100%;
}

.profile-content {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

/* 用户信息卡片样式 */
.user-card {
  margin-bottom: 20px;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 30px 20px;
}

.user-avatar {
  position: relative;
  margin-bottom: 20px;
}

.edit-avatar {
  position: absolute;
  right: 0;
  bottom: 0;
}

.edit-btn {
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1);
}

.user-info {
  text-align: center;
  margin-bottom: 20px;
}

.user-name {
  font-size: 22px;
  margin: 0 0 10px 0;
  color: #303133;
}

.user-id,
.user-class,
.user-grade,
.user-email {
  font-size: 14px;
  color: #606266;
  margin-bottom: 5px;
}

.user-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 10px;
  margin-top: 10px;
}

/* 账号设置卡片样式 */
.account-settings {
  margin-bottom: 20px;
}

.setting-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 0;
}

.setting-content {
  display: flex;
  align-items: center;
}

.setting-icon {
  font-size: 24px;
  color: #409eff;
  margin-right: 15px;
}

.setting-title {
  font-size: 16px;
  font-weight: 500;
  color: #303133;
  margin-bottom: 5px;
}

.setting-desc {
  font-size: 12px;
  color: #909399;
}

/* 活动卡片样式 */
.activity-card {
  margin-bottom: 20px;
}

.activity-title {
  font-size: 16px;
  margin: 0 0 8px 0;
  color: #303133;
}

.activity-content {
  font-size: 14px;
  color: #606266;
  margin: 0;
  line-height: 1.6;
}

/* 统计卡片样式 */
.stats-row {
  margin-bottom: 20px;
}

.stats-card,
.badges-card {
  height: 100%;
}

.stats-content {
  padding: 10px 0;
}

.stats-item {
  display: flex;
  justify-content: space-between;
  margin-bottom: 15px;
}

.stats-label {
  color: #606266;
}

.stats-value {
  font-weight: 600;
  color: #303133;
}

/* 徽章卡片样式 */
.badges-container {
  display: flex;
  flex-wrap: wrap;
  gap: 20px;
  justify-content: flex-start;
}

.badge-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  width: calc(33.33% - 14px);
  margin-bottom: 10px;
}

.badge-icon {
  width: 50px;
  height: 50px;
  border-radius: 50%;
  background-color: #ecf5ff;
  display: flex;
  justify-content: center;
  align-items: center;
  margin-bottom: 8px;
  font-size: 24px;
  color: #409eff;
  transition: all 0.3s;
}

.badge-icon:hover {
  transform: scale(1.1);
}

.badge-icon.locked {
  background-color: #f5f7fa;
  color: #c0c4cc;
}

.badge-name {
  font-size: 12px;
  color: #606266;
  text-align: center;
}

.user_pic{
width: 120px;
height: 120px;
border-radius: 60px;
margin: 0 0 0 50px;
border: 7px solid rgb(91, 159, 227);
}
</style>

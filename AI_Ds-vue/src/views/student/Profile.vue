<template>
  <div class="profile-container">
    <page-header
        class="my-page-header"
      title="涓汉淇℃伅"
      description="鏌ョ湅鍜岀鐞嗘偍鐨勪釜浜鸿祫鏂?
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
                <div class="user-id">瀛﹀彿锛歿{ userInfo.usernum }}</div>
                <div class="user-class">鐝骇锛歿{ userInfo.class }}</div>
                <div class="user-grade">骞寸骇锛?023绾?/div>
                <div class="user-email">閭锛歿{ userInfo.email }}</div>
              </div>

              <div class="user-actions">
                <el-button type="primary" @click="openEditProfileDialog">淇敼璧勬枡</el-button>
                <el-button @click="openChangePasswordDialog">淇敼瀵嗙爜</el-button>
              </div>
            </el-card>

            <el-card class="account-settings">
              <template #header>
                <div class="card-header">
                  <span>璐﹀彿璁剧疆</span>
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
                        <div class="setting-title">绯荤粺閫氱煡</div>
                        <div class="setting-desc">鎺ユ敹閲嶈鐨勫疄楠屽拰璇剧▼閫氱煡</div>
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
                        <div class="setting-title">鎴鏃ユ湡鎻愰啋</div>
                        <div class="setting-desc">瀹為獙鎴鍓嶆彁鍓嶉€氱煡</div>
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
                        <div class="setting-title">鍙岄噸璁よ瘉</div>
                        <div class="setting-desc">澧炲己璐﹀彿瀹夊叏鎬?/div>
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
                        <div class="setting-title">璇勬祴鍙嶉閫氱煡</div>
                        <div class="setting-desc">鎺ユ敹AI璇勬祴鐨勭粨鏋滃拰鍙嶉</div>
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
                  <span>鏈€杩戞椿鍔?/span>
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
                      <span>瀛︿範缁熻</span>
                    </div>
                  </template>

                  <div class="stats-content">
                    <div class="stats-item">
                      <div class="stats-label">鎬诲疄楠屾暟</div>
                      <div class="stats-value">5</div>
                    </div>

                    <div class="stats-item">
                      <div class="stats-label">宸插畬鎴?/div>
                      <div class="stats-value">2</div>
                    </div>

                    <div class="stats-item">
                      <div class="stats-label">骞冲潎鎴愮哗</div>
                      <div class="stats-value">90</div>
                    </div>

                    <div class="stats-item">
                      <div class="stats-label">瀹屾垚瀹為獙鎬荤敤鏃?/div>
                      <div class="stats-value">16灏忔椂</div>
                    </div>
                  </div>
                </el-card>
              </el-col>

              <el-col :span="12">
                <el-card class="badges-card">
                  <template #header>
                    <div class="card-header">
                      <span>鑾峰緱鐨勫窘绔?/span>
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

    <!-- 淇敼璧勬枡瀵硅瘽妗?-->
    <el-dialog
      v-model="editProfileDialog"
      title="淇敼涓汉璧勬枡"
      width="500px"
    >
      <el-form
        :model="profileForm"
        :rules="profileRules"
        ref="profileFormRef"
        label-width="100px"
      >
        <el-form-item label="濮撳悕" prop="username">
          <el-input v-model="profileForm.username" placeholder="璇疯緭鍏ュ鍚? />
        </el-form-item>
        <el-form-item label="閭" prop="email">
          <el-input v-model="profileForm.email" placeholder="璇疯緭鍏ラ偖绠? />
        </el-form-item>
        <el-form-item label="鐢佃瘽" prop="phone">
          <el-input v-model="profileForm.phone" placeholder="璇疯緭鍏ョ數璇濆彿鐮? />
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="editProfileDialog = false">鍙栨秷</el-button>
          <el-button type="primary" @click="submitProfileForm" :loading="submitting">纭淇敼</el-button>
        </span>
      </template>
    </el-dialog>

    <!-- 淇敼瀵嗙爜瀵硅瘽妗?-->
    <el-dialog
      v-model="changePasswordDialog"
      title="淇敼瀵嗙爜"
      width="500px"
    >
      <el-form
        :model="passwordForm"
        :rules="passwordRules"
        ref="passwordFormRef"
        label-width="100px"
      >
        <el-form-item label="鍘熷瘑鐮? prop="oldPassword">
          <el-input v-model="passwordForm.oldPassword" type="password" placeholder="璇疯緭鍏ュ師瀵嗙爜" show-password />
        </el-form-item>
        <el-form-item label="鏂板瘑鐮? prop="newPassword">
          <el-input v-model="passwordForm.newPassword" type="password" placeholder="璇疯緭鍏ユ柊瀵嗙爜" show-password />
        </el-form-item>
        <el-form-item label="纭鏂板瘑鐮? prop="confirmPassword">
          <el-input v-model="passwordForm.confirmPassword" type="password" placeholder="璇峰啀娆¤緭鍏ユ柊瀵嗙爜" show-password />
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="changePasswordDialog = false">鍙栨秷</el-button>
          <el-button type="primary" @click="submitPasswordForm" :loading="submitting">纭淇敼</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useUserStore } from '../../store'
import { useRouter } from 'vue-router' // 瀵煎叆 router
import PageHeader from '../../components/PageHeader.vue'
import LoadingState from '../../components/LoadingState.vue'
import api from '../../api'
import { ElMessage } from 'element-plus'
import axios from 'axios'
import { clearAuthStorage } from '../../constants/auth'
import { API_BASE_URL, buildApiUrl } from '../../config/runtime'
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

// 鍒涘缓API瀹㈡埛绔?const apiClient = axios.create({
  baseURL: API_BASE_URL,
  timeout: 10000,
  withCredentials: true,
  headers: {
    'Content-Type': 'application/json'
  }
});

const userStore = useUserStore()
const router = useRouter() // 鍒濆鍖栬矾鐢卞璞?const loading = ref(true)

// 鐢ㄦ埛淇℃伅
const userInfo = reactive({...userStore.userInfo})

// 璐﹀彿璁剧疆
const settings = reactive({
  notifications: true,
  reminders: true,
  twoFactor: false,
  feedback: true
})

// 鏈€杩戞椿鍔?const activities = [
  {
    title: '瀹屾垚瀹為獙銆愭爤涓庨槦鍒楃殑搴旂敤銆?,
    content: '寰楀垎锛?8鍒嗭紝AI鐐硅瘎锛氭爤鍜岄槦鍒楃殑鍩烘湰鎿嶄綔瀹炵幇姝ｇ‘锛岃〃杈惧紡姹傚€肩畻娉曞彲浠ュ鐞嗗熀鏈繍绠楋紝浣嗗浜庡鏉傝〃杈惧紡鍜岄敊璇鐞嗘湁寰呭畬鍠勩€?,
    time: '2023-09-28 20:15',
    type: 'success',
    color: '#67c23a'
  },
  {
    title: '鑷垜璇勪及鏇存柊',
    content: '鏇存柊浜嗗鏍堝拰闃熷垪鐨勭煡璇嗙偣鑷瘎锛屽綋鍓嶆帉鎻″害锛?5%',
    time: '2023-09-25 15:30',
    type: 'primary',
    color: '#409eff'
  },
  {
    title: '寮€濮嬪疄楠屻€愭爤涓庨槦鍒楃殑搴旂敤銆?,
    content: '寮€濮嬩簡鏂扮殑瀹為獙浠诲姟',
    time: '2023-09-20 10:00',
    type: 'warning',
    color: '#e6a23c'
  },
  {
    title: '瀹屾垚瀹為獙銆愮嚎鎬ц〃鐨勫疄鐜颁笌搴旂敤銆?,
    content: '寰楀垎锛?2鍒嗭紝AI鐐硅瘎锛氫唬鐮佸疄鐜拌鑼冿紝鑳藉姝ｇ‘瀹屾垚鍚勯」鍩烘湰鎿嶄綔锛屼絾绾︾憻澶幆闂鐨勬椂闂村鏉傚害鍙互杩涗竴姝ヤ紭鍖栥€?,
    time: '2023-09-12 15:30',
    type: 'success',
    color: '#67c23a'
  },
  {
    title: '鎻愪氦浜嗚嚜鎴戣瘎浼?,
    content: '鏇存柊浜嗗绾挎€ц〃鐨勭煡璇嗙偣鑷瘎锛屽綋鍓嶆帉鎻″害锛?0%',
    time: '2023-09-10 16:45',
    type: 'primary',
    color: '#409eff',
    hollow: true
  }
]

// 寰界珷
const badges = [
  {
    name: '瀹為獙杈句汉',
    description: '瀹屾垚鎵€鏈夊疄楠屼换鍔?,
    icon: TrophyBase,
    earned: false
  },
  {
    name: '浼樼瀛﹀憳',
    description: '瀹為獙骞冲潎鍒嗚秴杩?0鍒?,
    icon: Medal,
    earned: true
  },
  {
    name: '鍕ゅ鑰?,
    description: '杩炵画7澶╃櫥褰曞涔?,
    icon: Star,
    earned: true
  },
  {
    name: '缂栫▼鑳芥墜',
    description: '浠ｇ爜鏌ラ噸鐜囦綆浜?%',
    icon: Cpu,
    earned: false
  },
  {
    name: '鏁版嵁缁撴瀯澶у笀',
    description: '鐭ヨ瘑鐐规帉鎻″害鍏ㄩ儴杈惧埌80%浠ヤ笂',
    icon: DataLine,
    earned: false
  },
  {
    name: '瀛︿範杈句汉',
    description: '瀹屾垚50閬撶粌涔犻',
    icon: Histogram,
    earned: false
  }
]

// 淇敼璧勬枡瀵硅瘽妗嗘帶鍒?const editProfileDialog = ref(false)
const profileFormRef = ref(null)
const submitting = ref(false)

// 淇敼璧勬枡琛ㄥ崟鏁版嵁
const profileForm = reactive({
  username: '',
  email: '',
  phone: ''
})

// 淇敼璧勬枡琛ㄥ崟楠岃瘉瑙勫垯
const profileRules = {
  username: [
    { required: true, message: '璇疯緭鍏ュ鍚?, trigger: 'blur' },
    { min: 2, max: 20, message: '闀垮害鍦?鍒?0涓瓧绗︿箣闂?, trigger: 'blur' }
  ],
  email: [
    { required: true, message: '璇疯緭鍏ラ偖绠卞湴鍧€', trigger: 'blur' },
    { type: 'email', message: '璇疯緭鍏ユ纭殑閭鏍煎紡', trigger: 'blur' }
  ],
  phone: [
    { pattern: /^1[3-9]\d{9}$/, message: '璇疯緭鍏ユ纭殑鎵嬫満鍙风爜', trigger: 'blur' }
  ]
}

// 淇敼瀵嗙爜瀵硅瘽妗嗘帶鍒?const changePasswordDialog = ref(false)
const passwordFormRef = ref(null)

// 淇敼瀵嗙爜琛ㄥ崟鏁版嵁
const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

// 淇敼瀵嗙爜琛ㄥ崟楠岃瘉瑙勫垯
const passwordRules = {
  oldPassword: [
    { required: true, message: '璇疯緭鍏ュ師瀵嗙爜', trigger: 'blur' }
  ],
  newPassword: [
    { required: true, message: '璇疯緭鍏ユ柊瀵嗙爜', trigger: 'blur' },
    { min: 6, message: '瀵嗙爜闀垮害涓嶈兘灏戜簬6涓瓧绗?, trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '璇峰啀娆¤緭鍏ユ柊瀵嗙爜', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        if (value !== passwordForm.newPassword) {
          callback(new Error('涓ゆ杈撳叆鐨勫瘑鐮佷笉涓€鑷?))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ]
}

// 鎵撳紑淇敼璧勬枡瀵硅瘽妗?const openEditProfileDialog = () => {
  // 鍒濆鍖栬〃鍗曟暟鎹?  profileForm.username = userInfo.username || ''
  profileForm.email = userInfo.email || ''
  profileForm.phone = userInfo.phone || ''
  editProfileDialog.value = true
}

// 鎻愪氦淇敼璧勬枡琛ㄥ崟
const submitProfileForm = async () => {
  if (!profileFormRef.value) return
  
  await profileFormRef.value.validate(async (valid) => {
    if (!valid) {
      return false
    }
    
    submitting.value = true
    try {
      // 鏋勯€犳彁浜ゆ暟鎹?      const userData = {
        username: profileForm.username,
        email: profileForm.email,
        phone: profileForm.phone
      }

      // 璋冪敤API鏇存柊鐢ㄦ埛淇℃伅
      // // 鍋囪鍚庣API鏄?/api/user/profile
      // const response = await apiClient.put('/api/user/profile', userData)
      
      // 鏇存柊鏈湴鏁版嵁
      userInfo.username = profileForm.username
      userInfo.email = profileForm.email
      userInfo.phone = profileForm.phone
      
      // 鍏抽棴瀵硅瘽妗?      editProfileDialog.value = false
      
      // 鏄剧ず鎴愬姛娑堟伅
      ElMessage.success('涓汉璧勬枡淇敼鎴愬姛')
      
      // 鏇存柊鐢ㄦ埛淇℃伅鍒癡uex
      userStore.updateUserInfo({
        ...userStore.userInfo,
        username: profileForm.username,
        email: profileForm.email
      })
      
    } catch (error) {
      console.error('淇敼璧勬枡澶辫触:', error)
      // ElMessage.error('淇敼璧勬枡澶辫触: ' + (error.message || '鏈煡閿欒'))
    } finally {
      submitting.value = false
    }
  })
}

// 鎵撳紑淇敼瀵嗙爜瀵硅瘽妗?const openChangePasswordDialog = () => {
  // 閲嶇疆琛ㄥ崟鏁版嵁
  passwordForm.oldPassword = ''
  passwordForm.newPassword = ''
  passwordForm.confirmPassword = ''
  changePasswordDialog.value = true
}

// 鎻愪氦淇敼瀵嗙爜琛ㄥ崟
const submitPasswordForm = async () => {
  if (!passwordFormRef.value) return
  
  await passwordFormRef.value.validate(async (valid) => {
    if (!valid) {
      return false
    }
    
    submitting.value = true
    try {
      // 鏋勯€犳彁浜ゆ暟鎹?      const passwordData = {
        oldPassword: passwordForm.oldPassword,
        newPassword: passwordForm.newPassword
      }
      
      // 璋冪敤API鏇存柊瀵嗙爜
      const response = await axios.post(buildApiUrl('/api/user/password'), passwordData)

      console.log("璺熸柊鍐呭锛?,response)
      if(response.data.success){
        // 鍏抽棴瀵硅瘽妗?        changePasswordDialog.value = false

        // 鏄剧ず鎴愬姛娑堟伅
        ElMessage.success('瀵嗙爜淇敼鎴愬姛锛岃浣跨敤鏂板瘑鐮侀噸鏂扮櫥褰?)
        
        // 娓呴櫎鐢ㄦ埛鐧诲綍淇℃伅
        clearAuthStorage()
        
        // 寤惰繜涓€绉掑悗璺宠浆鍒扮櫥褰曢〉闈紝璁╃敤鎴锋湁鏃堕棿鐪嬪埌鎴愬姛娑堟伅
        setTimeout(() => {
          router.push('/login')
        }, 1500)
      }else {
        ElMessage.error(response.data.message)
      }

      // 鍙€夛細閫€鍑虹櫥褰曪紝璁╃敤鎴烽噸鏂扮櫥褰?      // 杩欓噷鍙互璋冪敤閫€鍑虹櫥褰曠殑鏂规硶
      // logout()
      
    } catch (error) {
      console.error('淇敼瀵嗙爜澶辫触:', error)
      ElMessage.error('淇敼瀵嗙爜澶辫触: ' + (error.message || '鏈煡閿欒'))
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

/* 鐢ㄦ埛淇℃伅鍗＄墖鏍峰紡 */
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

/* 璐﹀彿璁剧疆鍗＄墖鏍峰紡 */
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

/* 娲诲姩鍗＄墖鏍峰紡 */
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

/* 缁熻鍗＄墖鏍峰紡 */
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

/* 寰界珷鍗＄墖鏍峰紡 */
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



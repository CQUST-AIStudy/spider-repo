import {
  delay,
  studentInfo,
  teacherInfo,
  adminInfo,
  experimentList,
  experimentDetails,
  learningAnalysisData,
  recommendedPractices,
  teacherExperimentList,
  studentSubmissionsList,
  classList,
  classAnalysisData,
  classDetailAnalysis
} from '../mock'

import axios from 'axios'
import {
  getSessionToken,
  setSessionToken,
  setTapToken,
  setTapUser,
  setUserInfo,
} from '../constants/auth'
import { API_BASE_URL_WITH_SLASH } from '../config/runtime'

////鍓嶇鍦ㄥ彂閫佽姹傛椂锛岃纭繚鎼哄甫 session 淇℃伅銆備互 axios 涓轰緥锛岄渶瑕佽缃?withCredentials 涓?true锛?
axios.defaults.withCredentials = true;


const USE_MOCK_DATA = false; // 璁剧疆涓?true 鍙娇鐢ㄦ湰鍦版ā鎷熸暟鎹?
// 鍒涘缓 axios 瀹炰緥
const apiClient = axios.create({
  baseURL: API_BASE_URL_WITH_SLASH,  // 淇敼涓哄悗绔湇鍔＄殑瀹為檯鍦板潃
  timeout: 30000,
  withCredentials: true,  // 娣诲姞杩欒纭繚鎵€鏈夎姹傞兘鍙戦€佸嚟璇?
  headers: {
    'Content-Type': 'application/json'
  }
});

// 璇锋眰鎷︽埅鍣?
apiClient.interceptors.request.use(
  config => {
    // 鑾峰彇token骞舵坊鍔犲埌璇锋眰澶?
    const token = getSessionToken();
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  error => {
    return Promise.reject(error);
  }
);

// 鍝嶅簲鎷︽埅鍣?
apiClient.interceptors.response.use(
  response => {
    // 姝ｅ父鍝嶅簲澶勭悊
    return response.data;
  },
  error => {
    // 閿欒澶勭悊
    // let errorMessage;
    //
    // if (error.response) {
    //   // 鏈嶅姟鍣ㄨ繑鍥炰簡閿欒鐘舵€佺爜
    //   const { status, data } = error.response;
    //
    //   switch (status) {
    //     case 401:
    //       errorMessage = '鏈巿鏉冿紝璇烽噸鏂扮櫥褰?;
    //       // 娓呴櫎token绛変俊鎭苟閲嶅畾鍚戝埌鐧诲綍椤甸潰
    //       localStorage.removeItem('token');
    //       localStorage.removeItem('userInfo');
    //       window.location.href = '/login';
    //       break;
    //     case 403:
    //       errorMessage = '鎷掔粷璁块棶';
    //       break;
    //     case 404:
    //       // errorMessage = '璇锋眰鐨勮祫婧愪笉瀛樺湪';
    //       break;
    //     case 500:
    //       errorMessage = '鏈嶅姟鍣ㄩ敊璇?;
    //       break;
    //     default:
    //       // errorMessage = data.message || `璇锋眰澶辫触 (${status})`;
    //   }
    // } else if (error.request) {
    //   // 璇锋眰宸插彂閫佷絾娌℃敹鍒板搷搴?
    //   errorMessage = '鏈嶅姟鍣ㄦ棤鍝嶅簲';
    // } else {
    //   // 璇锋眰璁剧疆鍑洪敊
    //   errorMessage = error.message;
    // }
    //
    // // 鏄剧ず閿欒娑堟伅
    // ElMessage.error(errorMessage);

    return Promise.reject(error);
  }
);

export default {
  // 鑾峰彇瀛︾敓淇℃伅
  async getStudentInfo() {
    if (process.env.NODE_ENV === 'development' && USE_MOCK_DATA) {
      await delay(300)
      return studentInfo
    }
    return apiClient.get('/api/student/info')
  },

  // 鑾峰彇鏁欏笀淇℃伅
  async getTeacherInfo() {
    if (process.env.NODE_ENV === 'development' && USE_MOCK_DATA) {
      await delay(300)
      return teacherInfo
    }
    return apiClient.get('/api/teacher/info')
  },

  // 鑾峰彇绠＄悊鍛樹俊鎭?
  async getAdminInfo() {
    if (process.env.NODE_ENV === 'development' && USE_MOCK_DATA) {
      await delay(300)
      return adminInfo
    }
    return apiClient.get('/api/admin/info')
  },

  async getAdminDashboardOverview() {
    return apiClient.get('/api/admin-dashboard/overview')
  },

  async triggerAdminClassSync(classId, payload = {}) {
    return apiClient.post(`/api/admin-dashboard/classes/${classId}/sync`, payload)
  },

  // 鐧诲綍
  async login(username, password, teacherLevel) {
    if (
      // process.env.NODE_ENV === 'development' &&
      USE_MOCK_DATA) {
      console.log('寮€鍙戠幆澧冪櫥褰曪紝鐢ㄦ埛鍚?', username, '鏁欏笀绾у埆:', teacherLevel)
      await delay(1000)

      // 妯℃嫙鐧诲綍娴佺▼
      let userInfo = null
      let token = null
      let success = true
      let message = '鐧诲綍鎴愬姛'

      try {

        if (username === 'student' && password === 'password123') {
          userInfo = {
            id: 'S2023001',
            name: '寮犱笁',
            role: 'student',
            avatar: 'https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png'
          }
          token = 'student_token_xxx'

        } else if (username === 'teacher' && password === 'password123') {
          userInfo = {
            id: 'T2023001',
            name: '鏉庢暀鎺?,
            role: 'teacher',
            level: teacherLevel || 'normal',
            permissions: getTeacherPermissions(teacherLevel || 'normal'),
            avatar: 'https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png'
          }
          token = 'teacher_token_xxx'

        } else if (username === 'admin' && password === 'password123') {
          userInfo = {
            id: 'A2023001',
            name: '绠＄悊鍛?,
            role: 'admin',
            avatar: 'https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png'
          }
          token = 'admin_token_xxx'

        } else {
          success = false
          message = '鐢ㄦ埛鍚嶆垨瀵嗙爜閿欒'
        }

        // 淇濆瓨鐧诲綍淇℃伅
        if (success) {
          try {
            setSessionToken(token)
            setUserInfo(userInfo)
            console.log('鐧诲綍淇℃伅宸蹭繚瀛樺埌localStorage')
          } catch (e) {
            console.error('淇濆瓨鐧诲綍淇℃伅澶辫触:', e)
            // 鍗充娇淇濆瓨澶辫触锛屼篃涓嶅奖鍝嶈繑鍥炵櫥褰曟垚鍔熺粨鏋?
          }
        }
        console.log('鐢ㄦ埛淇℃伅:', userInfo)
        return { success, message, userInfo, token }
      } catch (error) {
        console.error('鐧诲綍杩囩▼鍙戠敓閿欒:', error)
        return {
          success: false,
          message: '鐧诲綍杩囩▼鍙戠敓閿欒: ' + (error.message || '鏈煡閿欒'),
          userInfo: null,
          token: null
        }
      }
    }

    // 鑱旇皟娴嬭瘯
    try {
      console.log('鍙戦€佺櫥褰曡姹?(璇︾粏):', {
        username: username,
        password: '***', // 鍑轰簬瀹夊叏鑰冭檻涓嶆墦鍗板瘑鐮?
        role: teacherLevel,
        url: '/api/login'
      });

      // 纭繚浣跨敤姝ｇ‘鐨勮姹傛暟鎹牸寮?
      const requestData = {
        username: username,
        password: password
      };

      // 濡傛灉鏈夋暀甯堢骇鍒紝娣诲姞鍒拌姹備腑
      if (teacherLevel) {
        requestData.role = teacherLevel;
      }

      // 纭繚axios閰嶇疆姝ｇ‘
      const config = {
        withCredentials: true // 纭繚鍙戦€乧ookie
      };

      // 鍙戦€佽姹傚苟璁板綍璇︾粏鏃ュ織
      const response = await apiClient.post('/api/login', requestData, config);
      console.log('鐧诲綍鍘熷鍝嶅簲:', response);

      // 纭繚鍝嶅簲鏈夋晥
      if (response) {
        // 姝ｇ‘澶勭悊鍝嶅簲涓殑鐢ㄦ埛淇℃伅
        if (response.success) {
          // 淇濆瓨鐢ㄦ埛淇℃伅鍒版湰鍦板瓨鍌?
          try {
            const userInfo = response.user;
            if (userInfo) {

              setUserInfo(userInfo);
              console.log('鐢ㄦ埛淇℃伅宸蹭繚瀛樺埌localStorage');

              // 涓轰簡鍏煎鎬э紝涔熻缃畉oken (濡傛灉闇€瑕?
              const token = 'session_auth_' + new Date().getTime();
              setSessionToken(token);
            }
          } catch (e) {
            console.error('淇濆瓨鐢ㄦ埛淇℃伅澶辫触:', e);
          }

          // 灏濊瘯TAP骞冲彴鐧诲綍锛堥潤榛橈紝涓嶅奖鍝嶄富鐧诲綍娴佺▼锛?
          this.tryTapLogin(username, password);
        } else {
          console.warn('鐧诲綍鍝嶅簲鏄剧ず澶辫触:', response.message);
        }
      }

      return response;
    } catch (error) {
      console.error('API鐧诲綍璇锋眰澶辫触 (璇︾粏):', {
        message: error.message,
        status: error.response?.status,
        data: error.response?.data
      });

      return {
        success: false,
        message: error.response?.data?.message || error.message || '鐧诲綍璇锋眰澶辫触',
        userInfo: null
      };
    }
  },

  // 灏濊瘯TAP骞冲彴鐧诲綍锛堣幏鍙朖WT token鐢ㄤ簬鐝骇绠＄悊绛夊姛鑳斤級
  async tryTapLogin(username, password) {
    try {
      const res = await apiClient.post('/api/auth/login', { username, password });
      const data = res?.data ?? res;
      if (data?.accessToken) {
        setTapToken(data.accessToken);
        setTapUser({
          userId: data.userId,
          role: data.role,
          username
        });
        console.log('TAP骞冲彴鐧诲綍鎴愬姛');
      }
    } catch (e) {
      console.warn('TAP骞冲彴鑷姩鐧诲綍澶辫触锛堝彲蹇界暐锛?', e.message);
    }
  },

  // 鐧诲嚭
  async logout() {
    if (process.env.NODE_ENV === 'development' && USE_MOCK_DATA) {
      await delay(300)
      return { success: true }
    }
    return apiClient.post('/api/logout')
  },

  // 娉ㄥ唽
  async register(formData) {
    try {
      const response = await apiClient.post('/api/register', {
        username: formData.username,
        password: formData.password,
        role: 'student',
        usernum: formData.usernum || null,
        classname: formData.classname || null
      })
      return response
    } catch (error) {
      return {
        success: false,
        message: error.response?.data?.message || error.message || '娉ㄥ唽璇锋眰澶辫触'
      }
    }
  },

  // 鑾峰彇瀹為獙鍒楄〃
  async getExperimentList() {

    if (process.env.NODE_ENV === 'development' && USE_MOCK_DATA) {
      await delay(500)
      return experimentList
    }
    try {
      console.log('姝ｅ湪鍙戦€佽幏鍙栧疄楠屽垪琛ㄨ姹?..');
      const config = {
        withCredentials: true // 纭繚鍙戦€乧ookie
      };
      const response = await apiClient.get('/api/experiments', config);
      console.log('浠庡悗绔殑response锛?, response);
      return response;
    } catch (error) {
      console.error('鑾峰彇瀹為獙鍒楄〃澶辫触:', error);
      throw error;
    }
  },

  // 鑾峰彇瀹為獙鍒楄〃
  async getExperiments() {

    if (process.env.NODE_ENV === 'development' && USE_MOCK_DATA) {
      await delay(500)
      return experimentList
    }
    try {
      console.log('姝ｅ湪鍙戦€佽幏鍙栧疄楠屽垪琛ㄨ姹?..');
      const config = {
        withCredentials: true // 纭繚鍙戦€乧ookie
      };
      const response = await apiClient.get('/api/experiments1', config);
      console.log('浠庡悗绔殑response锛?, response);
      return response;
    } catch (error) {
      console.error('鑾峰彇瀹為獙鍒楄〃澶辫触:', error);
      throw error;
    }
  },

  // 鑾峰彇鏁欏笀鍒涘缓鐨勫疄楠屽垪琛?
  async getTeacherExperimentList() {
    if (process.env.NODE_ENV === 'development' && USE_MOCK_DATA) {
      await delay(500)
      console.log('浣跨敤妯℃嫙鏁版嵁杩斿洖鏁欏笀瀹為獙鍒楄〃');
      return teacherExperimentList
    }

    try {
      console.log('姝ｅ湪鍙戦€佽幏鍙栨暀甯堝疄楠屽垪琛ㄨ姹?..');
      const config = {
        withCredentials: true // 纭繚鍙戦€乧ookie
      };
      const response = await apiClient.get('/api/teacher/experiments', config);
      console.log('浠庡悗绔幏鍙栫殑鏁欏笀瀹為獙鍒楄〃锛?, response);
      return response;
    } catch (error) {
      console.error('鑾峰彇鏁欏笀瀹為獙鍒楄〃澶辫触:', error);
      throw error;
    }
  },

  // 鑾峰彇瀹為獙璇︽儏
  async getExperimentDetails(id) {
    if (process.env.NODE_ENV === 'development' && USE_MOCK_DATA) {
      await delay(700)
      return experimentDetails
    }
    try {
      console.log('姝ｅ湪鍙戦€佽幏鍙栧疄楠岀粏鑺傝姹?..');
      const response = await apiClient.get(`/api/experiments/${id}`);
      console.log('浠庡悗绔殑response瀹為獙缁嗚妭锛?, response);

      // 妫€鏌ヨ繑鍥炴暟鎹粨鏋勶紝纭繚涓庡叾浠栨帴鍙ｄ竴鑷?
      // 濡傛灉杩斿洖鐨勪笉鏄?{success: true, data: {...}} 杩欑缁撴瀯锛岃繘琛岃浆鎹?
      if (response.success && !response.data && typeof response === 'object') {
        // 灏嗘暣涓搷搴斿璞′綔涓篸ata瀛楁杩斿洖锛屼繚鎸佷笌getExperimentList鐩稿悓鐨勭粨鏋?
        return {
          success: response.success,
          data: response
        };
      }

      return response;
    } catch (error) {
      console.error('鑾峰彇瀹為獙缁嗚妭澶辫触:', error);
      throw error;
    }
  },

  // 鑾峰彇瀛︿範鍒嗘瀽鏁版嵁
  async getLearningAnalysis() {
    if (process.env.NODE_ENV === 'development' && USE_MOCK_DATA) {
      await delay(800)
      return learningAnalysisData
    }
    try {
      console.log('姝ｅ湪鍙戦€佽幏鍙栨帹鑽愰鐩姹?..');
      const response = await apiClient.get('/api/student/learning-analysis')
      console.log('浠庡悗绔殑response瀹為獙缁嗚妭锛?, response);

      // 妫€鏌ヨ繑鍥炴暟鎹粨鏋勶紝纭繚涓庡叾浠栨帴鍙ｄ竴鑷?
      // 濡傛灉杩斿洖鐨勪笉鏄?{success: true, data: {...}} 杩欑缁撴瀯锛岃繘琛岃浆鎹?
      if (response.success && !response.data && typeof response === 'object') {
        // 灏嗘暣涓搷搴斿璞′綔涓篸ata瀛楁杩斿洖锛屼繚鎸佷笌getExperimentList鐩稿悓鐨勭粨鏋?
        return {
          success: response.success,
          data: response
        };
      }

      return response;
    } catch (error) {
      console.error('鑾峰彇瀹為獙缁嗚妭澶辫触:', error);
      throw error;
    }
  },

  // 鑾峰彇LeetCode棰樼洰璇︽儏
  async getLeetCodeProblem(problemId) {
    return apiClient.get(`/api/leetcode/problem/${problemId}`)
  },

  // 杩愯LeetCode浠ｇ爜
  async runLeetCodeSolution(data) {
    return apiClient.post('/api/leetcode/run', data)
  },

  // 鎻愪氦LeetCode瑙ｇ瓟
  async submitLeetCodeSolution(data) {
    return apiClient.post('/api/leetcode/submit', data, {
      timeout: 90000
    })
  },

  async recordLeetCodeRecommendationFeedback(data) {
    return apiClient.post('/api/recommendations/leetcode/feedback', null, {
      params: data
    })
  },

  // 鑾峰彇鎺ㄨ崘缁冧範
  async getRecommendedPractices() {
    if (process.env.NODE_ENV === 'development' && USE_MOCK_DATA) {
      await delay(500)
      return recommendedPractices
    }

    return apiClient.get('/api/current/recommendedPractices')
  },

  // 鑾峰彇瀛︾敓鎻愪氦鍒楄〃
  async getStudentSubmissions(experimentId) {
    if (process.env.NODE_ENV === 'development' && USE_MOCK_DATA) {
      await delay(600)
      if (experimentId) {
        return studentSubmissionsList.filter(s => s.experimentId === experimentId)
      }
      return studentSubmissionsList
    }

    const url = experimentId
      ? `/api/submissions?experimentId=${experimentId}`
      : '/api/submissions';

    return apiClient.get(url);
  },

  // 鑾峰彇鎵€鏈夊鐢熺殑瀹為獙鎻愪氦鎯呭喌
  async getAllStudentExperiments() {
    if (process.env.NODE_ENV === 'development' && USE_MOCK_DATA) {
      await delay(600)
      return studentSubmissionsList
    }

    try {
      console.log('姝ｅ湪鑾峰彇鎵€鏈夊鐢熷疄楠屾暟鎹?..');
      const response = await apiClient.get('/api/teacher/allStudentExperiments');
      console.log('鑾峰彇鍒版墍鏈夊鐢熷疄楠屾暟鎹?', response);

      if (response.success) {
        return response.data;
      } else {
        throw new Error(response.message || '鑾峰彇鏁版嵁澶辫触');
      }
    } catch (error) {
      console.error('鑾峰彇鎵€鏈夊鐢熷疄楠屾暟鎹け璐?', error);
      throw error;
    }
  },

  // 鑾峰彇鎻愪氦璇︽儏
  async getSubmissionDetail(submissionId) {
    if (process.env.NODE_ENV === 'development' && USE_MOCK_DATA) {
      await delay(600)
      // 杩斿洖妯℃嫙鐨勬彁浜よ鎯呮暟鎹?

      console.log('浣跨敤妯℃嫙鏁版嵁杩斿洖鎻愪氦璇︽儏, 鎻愪氦ID:', submissionId);
      return {
        id: submissionId,
        studentId: '2019443672',
        studentName: '鏄撴槦璐?,
        experimentId: 'exp001',
        experimentName: '绾挎€ц〃瀹為獙',
        status: 'submitted',
        score: null,
        code: '"绗?1 棰樺涓?\n//// 鍒涘缓绌虹殑椤哄簭琛?\n// List MakeEmpty ()\n// {\n// List list = malloc (sizeof (struct LNode));\n// //if (list == NULL)\n// // return false;\n// list->Last = -1;\n// return list;\n// }\n// 鍏冪礌 x 鎻掑叆浣嶇疆 p\n//bool Insert (List L, ElementType X, Position P)\n// {\n// if (L->Last>= MAXSIZE-1)\n// {\n// printf ("FULL");\n// return 0;\n// }\n// for (int i = L->Last+1; i >P; i--)\n// {\n// L->Data [i] = L->Data [i-1];\n// }\n// L->Data [P] = X;\n// L->Last++;\n// //printf ("last==% d", L->Data [L->Last]);\n// return 1;\n// }\n// 鏌ユ壘 x 鐨勪綅缃?\n// Position Find (List L, ElementType X)\n// {\n// for (int i = 0; i <= L->Last; i++)\n// {\n// if (L->Data [i] == X)\n// {\n// return i;\n// }\n// }\n// return ERROR;\n// }\n// 灏嗕綅缃?P 鐨勫厓绱犲垹闄?\n//bool Delete (List L, Position P)\n// {\n// if (P > L->Last||P<0)\n// {\n// printf ("POSITION % d EMPTY",P);\n// return 0;\n// }\n// for (int i = P; i <= L->Last; i++)\n// {\n// L->Data [i] = L->Data [i + 1];\n// }\n// L->Last--;\n// return 1;\n// }\nList MakeEmpty ()\n List L;\n L=(List) malloc (sizeof (struct LNode));\n L->Last=-1;\n return L;\nPosition Find ( List L, ElementType X )\n int i;\n for (i=0;i<=L->Last;i++)\n {\n if (L->Data [i]==X)\n return i;\n }\n return ERROR;\nbool Insert ( List L, ElementType X, Position P )\n int i;\n if (L->Last==MAXSIZE-1)\n {\n printf ("FULL");\n return false;\n }\n if (P>L->Last+1||P<0)\n {\n printf ("ILLEGAL POSITION");\n return false;\n }\n for ( i=L->Last;i>=P;i-- )\n {\n L->Data [i+1]=L->Data [i];\n }\n L->Data [P]=X;\n L->Last++;\n return true;\nbool Delete ( List L, Position P )\n Position j;\n if (P<0||P>L->Last){\n printf ("POSITION % d EMPTY",P);\n return false;\n }\n for (j=P;j < L->Last;j++)\n L->Data [j]=L->Data [j+1];\n L->Last--;\n return true;\n| 娴嬭瘯鐐?| 缁撴灉 | 娴嬭瘯鐐瑰緱鍒?| 鑰楁椂 | 鍐呭瓨 |\n| --- | --- | --- | --- | --- |\n| 0 | 绛旀姝ｇ‘ | 15 | 1.00 ms | 364 KB |\n| 1 | 绛旀姝ｇ‘ | 15 | 2.00 ms | 356 KB |\n| 2 | 绛旀姝ｇ‘ | 10 | 1.00 ms | 364 KB |\n 绗?2 棰樺涓?\nList Delete (List L, ElementType minD, ElementType maxD)\n for (int i = 0; i <= L->Last; i++)\n {\n if (L->Data [i]>minD&&L->Data [i]<maxD)\n {\n for (int j = i; j < L->Last; j++)\n {\n L->Data [j] = L->Data [j + 1];\n }\n L->Last--;\n i--;\n }\n }\n return L;\n| 娴嬭瘯鐐?| 缁撴灉 | 娴嬭瘯鐐瑰緱鍒?| 鑰楁椂 | 鍐呭瓨 |\n| --- | --- | --- | --- | --- |\n| 0 | 绛旀姝ｇ‘ | 15 | 1.00 ms | 364 KB |\n| 1 | 绛旀姝ｇ‘ | 15 | 2.00 ms | 356 KB |\n| 2 | 绛旀姝ｇ‘ | 10 | 1.00 ms | 364 KB |\n 绗?3 棰樺涓?\nvoid Del_negative (SqList* L)\n int x = 0;\n for (int i = 0; i < L->length; i++)\n {\n if (L->items [i] < 0)\n {\n SqListDelete (L, i+1, &x);\n i--;\n }\n }\n| 娴嬭瘯鐐?| 缁撴灉 | 娴嬭瘯鐐瑰緱鍒?| 鑰楁椂 | 鍐呭瓨 |\n| --- | --- | --- | --- | --- |\n| 0 | 绛旀姝ｇ‘ | 15 | 1.00 ms | 364 KB |\n| 1 | 绛旀姝ｇ‘ | 15 | 2.00 ms | 356 KB |\n| 2 | 绛旀姝ｇ‘ | 10 | 1.00 ms | 364 KB |\n 绗?4 棰樺涓?\n#define _CRT_SECURE_NO_WARNINGS 0\n#define _CRT_SECURE_NO_WARNINGS 0\n#include<stdio.h>\nint main ()\n int n, m, count = 0;\n int array [100] = { 0 };\n scanf ("% d % d", &n, &m);\n for (int i = 0; i < n; i++)\n {\n scanf ("% d", &array [i]);\n //printf ("% d", array [i]);\n }\n for (int i = 0; i < m; i++)\n {\n count = array [0];\n for (int j = 0; j < n - 1; j++)\n {\n //count=array [0];\n array [j] = array [j + 1];\n }\n array [n - 1] = count;\n }\n for (int i = 0; i < n; i++)\n {\n printf ("% d", array [i]);\n if (i < n - 1)\n {\n printf ("");\n }\n }\n return 0;\n| 娴嬭瘯鐐?| 缁撴灉 | 娴嬭瘯鐐瑰緱鍒?| 鑰楁椂 | 鍐呭瓨 |\n| --- | --- | --- | --- | --- |\n| 0 | 绛旀姝ｇ‘ | 15 | 1.00 ms | 364 KB |\n| 1 | 绛旀姝ｇ‘ | 15 | 2.00 ms | 356 KB |\n| 2 | 绛旀姝ｇ‘ | 10 | 1.00 ms | 364 KB |\n 绗?5 棰樺涓?\n#include<stdio.h>\nint main ()\n int A [100],B [100];\n int m,n,p=0;\n scanf ("% d % d",&m,&n);\n //scanf ("%")\n for (int i=0;i<m;i++)\n {\n scanf ("% d ",&A [i]);\n }\n for (int j=0;j<n;j++)\n {\n scanf ("% d ",&B [j]);\n }\n for (int i=0;i<m;i++)\n for (int j=0;j<n;j++)\n {\n if (A [i]==B [j])\n {\n if (p!=0)\n printf (" ");\n printf ("% d",A [i]);\n p++;\n }\n }\n if (p==0)\n printf ("NULL");\n return 0;\n| 娴嬭瘯鐐?| 缁撴灉 | 娴嬭瘯鐐瑰緱鍒?| 鑰楁椂 | 鍐呭瓨 |\n| --- | --- | --- | --- | --- |\n| 0 | 绛旀姝ｇ‘ | 15 | 1.00 ms | 364 KB |\n| 1 | 绛旀姝ｇ‘ | 15 | 2.00 ms | 356 KB |\n| 2 | 绛旀姝ｇ‘ | 10 | 1.00 ms | 364 KB |\n 绗?6 棰樺涓?\n// #define _CRT_SECURE_NO_WARNINGS 1\n// #include<stdio.h>\n//int main ()\n// {\n// int A [10000], B [10000], C [10000];\n// int m, n;\n// scanf ("% d % d", &m, &n);\n// for (int i = 0; i < m; i++)\n// {\n// scanf ("% d ", &A [i]);\n// }\n// for (int i = 0; i < n; i++)\n// {\n// scanf ("% d ", &B [i]);\n// }\n// //maopao (A, m);\n// //maopao (B, n);\n// int x = 0;\n// //for (int i = 0,j=0; i < m &&j<n;)\n// int count = 0;\n// for (int i = 0; i < m; i++)\n// {\n// for (int j = 0; j < n; j++)\n// {\n// if (A [i] == B [j])\n// {\n// count++;\n// continue;\n// }\n// }\n// if (count == 0)\n// C [x++] = A [i];\n// count = 0;\n// }\n// int n1 = 0;\n// for (int i = 0; i < x - 1; i++)\n// for (int j = 0; j < x - 1 - i; j++)\n// {\n// if (C [j] > C [j + 1])\n// {\n// n1 = C [j];\n// C [j] = C [j + 1];\n// C [j + 1] = n1;\n// }\n// } for (int i = 0; i < x; i++)\n// printf ("% d ", C [i]);\n// if (x == 0)\n// {\n// printf ("% d",0);\n// }\n// return 0;\n// }\n#define _CRT_SECURE_NO_WARNINGS 1\n#include <stdio.h>\n#include <stdlib.h>\n#define maxsize 10000\ntypedef struct\n int data [maxsize];\n int length;\n} sqlist;\nint main ()\n sqlist* c = (sqlist*) malloc (sizeof (sqlist));\n c->length = 0;\n int n, m;\n scanf ("% d % d", &n, &m);\n sqlist* a = (sqlist*) malloc (sizeof (sqlist));\n sqlist* b = (sqlist*) malloc (sizeof (sqlist));\n for (int i = 0; i < n; i++)\n scanf ("% d", &a->data [i]);\n a->length = n;\n for (int j = 0; j < m; j++)\n scanf ("% d", &b->data [j]);\n b->length = m;\n int i = 0, j = 0, x = 0;\n while (i < a->length && j < b->length)\n {\n if (a->data [i] < b->data [j])\n {\n c->data [x] = a->data [i];\n x++;\n i++;\n }\n else if (a->data [i] == b->data [j])\n {\n i++;\n j++;\n }\n else\n j++;\n }\n int y = i;\n for (int k = y; k < a->length; k++)\n {\n c->data [x++] = a->data [k];\n }\n c->length = x;\n if (c->length == 0)\n {\n printf ("0");\n }\n for (int i = 0; i < c->length; i++)\n {\n printf ("% d ", c->data [i]);\n }\n free (a);\n free (b);\n free (c);\n return 0;\n| 娴嬭瘯鐐?| 缁撴灉 | 娴嬭瘯鐐瑰緱鍒?| 鑰楁椂 | 鍐呭瓨 |\n| --- | --- | --- | --- | --- |\n| 0 | 绛旀姝ｇ‘ | 15 | 1.00 ms | 364 KB |\n| 1 | 绛旀姝ｇ‘ | 15 | 2.00 ms | 356 KB |\n| 2 | 绛旀姝ｇ‘ | 10 | 1.00 ms | 364 KB |\n 绗?7 棰樺涓?\n#define _CRT_SECURE_NO_WARNINGS 1\n#include<stdio.h>\nint main ()\n int A [100],B [100];\n int m, n,count=0,x;\n scanf ("% d", &m);\n x = m;\n for (int i = 0; i < m; i++)\n {\n scanf ("% d", &A [i]);\n }\n scanf ("% d", &n);\n for (int j = 0; j < n; j++)\n {\n scanf ("% d", &B [j]);\n for (int i = 0; i < m; i++)\n {\n if (B [j] == A [i])\n {\n count++;\n }\n }\n if (count == 0)\n {\n A [x++] = B [j];\n }\n count = 0;\n }\n for (int i = 0; i < x; i++)\n printf ("% d ", A [i]);\n return 0;',
        plagiarismRate: 0.05,
        class:'璁＄畻鏈虹瀛?鐝?,
        report: `# 绾挎€ц〃瀹為獙鎶ュ憡\n\n## 瀹為獙鐩殑\n瀹炵幇椤哄簭琛ㄧ殑鍩烘湰鎿嶄綔\n瀹炵幇閾捐〃鐨勫熀鏈搷浣淺n瀹屾垚绀轰緥搴旂敤绋嬪簭\n鎾板啓瀹為獙鎶ュ憡鍒嗘瀽鎬ц兘\n\n## 瀹為獙鐜\nVisual Studio Code, JavaScript\n\n## 瀹為獙鍐呭\n瀹為獙鍐呭锛氱嚎鎬ц〃鍩虹鎿嶄綔锛屽寘鎷『搴忚〃鐨勫垵濮嬪寲銆佹彃鍏ャ€佸垹闄ゃ€佹煡鎵惧拰閬嶅巻瀹炵幇锛屽寘鎷鍒犳敼鏌ョ瓑鍔熻兘銆俓n\n## 瀹為獙姝ラ\n1. 棣栧厛瀹氫箟绾挎€ц〃鐨勭粨鏋刓n2. 瀹炵幇澧炲姞鍏冪礌鐨勬柟娉昞n3. 瀹炵幇鍒犻櫎鍏冪礌鐨勬柟娉昞n4. 瀹炵幇鏌ユ壘鍏冪礌鐨勬柟娉昞n\n## 瀹為獙缁撴灉\n鎴愬姛瀹炵幇浜嗙嚎鎬ц〃鐨勫悇椤瑰姛鑳斤紝娴嬭瘯閫氳繃銆俓n\n## 瀹為獙鎬荤粨\n閫氳繃鏈瀹為獙锛屾垜娣卞叆鐞嗚В浜嗙嚎鎬ц〃鐨勫伐浣滃師鐞嗗拰瀹炵幇鏂规硶銆俙,
        aiRemarks: '绗竴棰樿瘎璇? 浠ｇ爜瀹炵幇姝ｇ‘锛岀鍚堥鎰忋€傜浜岄璇勮: 浠ｇ爜瀹炵幇姝ｇ‘锛岀鍚堥鎰忋€傜涓夐璇勮: 浠ｇ爜瀹炵幇姝ｇ‘锛岀鍚堥鎰忋€傛€昏瘎璇細浠ｇ爜璐ㄩ噺鑹ソ锛岀鍚堝疄楠岃姹傘€?,
        date: '2024-04-04',
      }
    }

    try {
      console.log('姝ｅ湪鑾峰彇鎻愪氦璇︽儏...');
      const response = await apiClient.get(`/api/submissions/${submissionId}`);
      console.log('getSubmissionDetail response:', response);
      if (response && response.success === false) {
        throw new Error(response.message || 'Failed to load submission detail')
      }

      if (typeof submissionId === 'string' && submissionId.includes('-')) {
        const [studentId, experimentId] = submissionId.split('-')

        if (!response?.code) {
          try {
            const codeRes = await apiClient.get(`/api/student/code/${studentId}/${experimentId}`)
            if (codeRes?.success && codeRes?.code?.code) {
              response.code = codeRes.code.code
            }
          } catch (e) {
            console.warn('Fallback code query failed:', e)
          }
        }

        if (!response?.studentName || !response?.studentId || !response?.experimentName) {
          try {
            const allRes = await apiClient.get('/api/teacher/allStudentExperiments')
            const allList = Array.isArray(allRes) ? allRes : allRes?.data || []
            const matched = allList.find(item =>
              String(item.studentId) === String(studentId) &&
              String(item.experimentId) === String(experimentId)
            )
            if (matched) {
              response.studentId = response.studentId || matched.studentId
              response.studentName = response.studentName || matched.studentName
              response.experimentId = response.experimentId || matched.experimentId
              response.experimentName = response.experimentName || matched.experimentName
              response.class = response.class || matched.className
              response.submitTime = response.submitTime || matched.submitTime || null
              response.date = response.date || matched.submitTime || null
              if (response.score === null || response.score === undefined) {
                response.score = matched.score > 0 ? matched.score : null
              }
            }
          } catch (e) {
            console.warn('Fallback student/experiment merge failed:', e)
          }
        }
      }

      return response;
    } catch (error) {
      console.error('鑾峰彇鎻愪氦璇︽儏澶辫触:', error);
      throw error;
    }
  },

  // 鑾峰彇鐝骇鍒楄〃
  async getClassList() {
    if (process.env.NODE_ENV === 'development' && USE_MOCK_DATA) {
      await delay(500)
      return classList
    }

    try {
      console.log('姝ｅ湪鑾峰彇鐝骇鍒楄〃...');
      const response = await apiClient.get('/api/teacher/class');
      console.log('鑾峰彇鍒扮彮绾у垪琛ㄦ暟鎹?', response);

      // 濡傛灉杩斿洖鍗曚釜鐝骇瀵硅薄鑰屼笉鏄暟缁勶紝灏嗗叾杞崲涓烘暟缁?
      if (response && !Array.isArray(response)) {
        // 妫€鏌ユ槸鍚︽湁宓屽鐨刣ata瀛楁
        if (response.data && Array.isArray(response.data)) {
          return response.data;
        }

        // 濡傛灉鏄崟涓彮绾у璞★紝杞崲涓烘暟缁?
        return [response];
      }

      return response;
    } catch (error) {
      console.error('鑾峰彇鐝骇鍒楄〃澶辫触:', error);
      throw error;
    }
  },

  // 鑾峰彇瀛︾敓鍒楄〃
  async getStudentList() {
    if (process.env.NODE_ENV === 'development' && USE_MOCK_DATA) {
      await delay(500);
      return {
        students: [
          {
            student_id: 2019443672,
            username: "2019443672",
            password: "password123",
            name: "鏄撴槦璐?,
            class_name: "璁＄畻鏈虹瀛?鐝?,
            createdAt: "2025-04-16T02:34:06.000+00:00"
          },
          {
            student_id: 2019444338,
            username: "2019444338",
            password: "password123",
            name: "寮犲郴璞?,
            class_name: "璁＄畻鏈虹瀛?鐝?,
            createdAt: "2025-04-16T02:34:06.000+00:00"
          },
          {
            student_id: 2020444155,
            username: "2020444155",
            password: "password123",
            name: "闄堝崕閲?,
            class_name: "璁＄畻鏈虹瀛?鐝?,
            createdAt: "2025-04-16T02:34:06.000+00:00"
          },
          {
            student_id: 2020444227,
            username: "2020444227",
            password: "password123",
            name: "褰鏈?,
            class_name: "璁＄畻鏈虹瀛?鐝?,
            createdAt: "2025-04-16T02:34:06.000+00:00"
          }
        ]
      };
    }

    try {
      console.log('姝ｅ湪鑾峰彇瀛︾敓鍒楄〃...');
      const response = await apiClient.get('/api/teacher/studentList');
      console.log('鑾峰彇鍒板鐢熷垪琛ㄦ暟鎹?', response);
      return response;
    } catch (error) {
      console.error('鑾峰彇瀛︾敓鍒楄〃澶辫触:', error);
      throw error;
    }
  },

  // 鑾峰彇鐝骇鍒嗘瀽鏁版嵁
  async getClassAnalysis(classId) {
    if (process.env.NODE_ENV === 'development' && USE_MOCK_DATA) {
      await delay(800);
      return {
        id: classId, name: '璁＄畻鏈虹瀛?鐝?, studentCount: 49, grade: '2023绾?,
        teacherName: '鐜嬭€佸笀', averageScore: 87, completionRate: 75
      };
    }

    try {
      // 浣跨敤鐪熷疄鏁版嵁锛氫粠鎻愪氦鏁版嵁涓绠楃彮绾у垎鏋?
      const allStudentExperiments = await this.getAllStudentExperiments();
      const experiments = await this.getTeacherExperimentList();
      const expList = experiments?.data || experiments || [];

      // 杩囨护褰撳墠鐝骇鐨勬暟鎹紙濡傛灉鏈塩lassId锛?
      const classSubmissions = classId
        ? allStudentExperiments.filter(s => s.classId === classId || s.className)
        : allStudentExperiments;

      const studentIds = new Set(classSubmissions.map(s => s.studentId));
      const scored = classSubmissions.filter(s => s.score > 0);
      const completed = classSubmissions.filter(s => s.status === 'completed');

      const scoreDistribution = { '90-100': 0, '80-89': 0, '70-79': 0, '60-69': 0, '<60': 0 };
      scored.forEach(s => {
        if (s.score >= 90) scoreDistribution['90-100']++;
        else if (s.score >= 80) scoreDistribution['80-89']++;
        else if (s.score >= 70) scoreDistribution['70-79']++;
        else if (s.score >= 60) scoreDistribution['60-69']++;
        else scoreDistribution['<60']++;
      });

      const experimentCompletion = (Array.isArray(expList) ? expList : []).map(e => {
        const subs = classSubmissions.filter(s => s.experimentId === e.id && s.status === 'completed');
        return { name: e.name, completion: studentIds.size > 0 ? Math.round((subs.length / studentIds.size) * 100) : 0 };
      });

      // 璁＄畻瀛︾敓鎺掑悕
      const studentScores = {};
      scored.forEach(s => {
        if (!studentScores[s.studentId]) studentScores[s.studentId] = { name: s.studentName, scores: [] };
        studentScores[s.studentId].scores.push(s.score);
      });
      const topStudents = Object.entries(studentScores)
        .map(([id, data]) => ({
          id, name: data.name,
          averageScore: Math.round(data.scores.reduce((a, b) => a + b, 0) / data.scores.length * 10) / 10
        }))
        .sort((a, b) => b.averageScore - a.averageScore)
        .slice(0, 5);

      return {
        id: classId,
        studentCount: studentIds.size,
        averageScore: scored.length > 0 ? Math.round(scored.reduce((sum, s) => sum + s.score, 0) / scored.length * 10) / 10 : 0,
        completionRate: studentIds.size > 0 && expList.length > 0
          ? Math.round((completed.length / (studentIds.size * expList.length)) * 100) : 0,
        scoreDistribution,
        experimentCompletion,
        topStudents
      };
    } catch (error) {
      console.error('鑾峰彇鐝骇鍒嗘瀽鏁版嵁澶辫触:', error);
      throw error;
    }
  },

  // 鍒涘缓瀹為獙
  async createExperiment(data) {
    if (process.env.NODE_ENV === 'development' && USE_MOCK_DATA) {
      await delay(800)
      return { success: true, id: Date.now() }
    }
    return apiClient.post('/api/experiments', data)
  },

  // 鏇存柊瀹為獙
  async updateExperiment(id, data) {
    if (process.env.NODE_ENV === 'development' && USE_MOCK_DATA) {
      await delay(700)
      return { success: true }
    }
    return apiClient.put(`/api/experiments/${id}`, data)
  },

  // 鎻愪氦瀹為獙
  async submitExperiment(id, data) {
    if (process.env.NODE_ENV === 'development' && USE_MOCK_DATA) {
      await delay(600)
      return { success: true, id: Date.now() }
    }
    return apiClient.post(`/api/experiments/${id}/submit`, data)
  },
  // 璇勫垎鎻愪氦
  async gradeSubmission(id, data) {
    if (process.env.NODE_ENV === 'development' && USE_MOCK_DATA) {
      await delay(500)
      return { success: true }
    }
    return apiClient.post(`/api/submissions/${id}/grade`, data)
  },

  // 淇濆瓨鏁欏笀璇勮
  async saveQuestionComment(submissionId, questionIndex, comment) {
    if (process.env.NODE_ENV === 'development' && USE_MOCK_DATA) {
      await delay(300)
      return { success: true }
    }
    return apiClient.post(`/api/submissions/${submissionId}/comments`, { questionIndex, comment })
  },

  // 鎷掔粷鎻愪氦
  async rejectSubmission(id) {
    if (process.env.NODE_ENV === 'development' && USE_MOCK_DATA) {
      await delay(500)
      return { success: true }
    }
    return apiClient.post(`/api/submissions/${id}/reject`)
  },

  // 娣诲姞鐢ㄦ埛
  async addUser(data) {
    if (process.env.NODE_ENV === 'development' && USE_MOCK_DATA) {
      await delay(800)
      return { success: true, id: `${data.role.charAt(0).toUpperCase()}${Date.now().toString().slice(-7)}` }
    }
    return apiClient.post('/api/users', data)
  },

  // 鏇存柊鐢ㄦ埛
  async updateUser(id, data) {
    if (process.env.NODE_ENV === 'development' && USE_MOCK_DATA) {
      await delay(700)
      return { success: true }
    }
    return apiClient.put(`/api/users/${id}`, data)
  },

  // 鍒犻櫎鐢ㄦ埛
  async deleteUser(id) {
    if (process.env.NODE_ENV === 'development' && USE_MOCK_DATA) {
      await delay(500)
      return { success: true }
    }
    return apiClient.delete(`/api/users/${id}`)
  }
}

// 鏍规嵁鏁欏笀鏉冮檺绛夌骇鑾峰彇瀵瑰簲鏉冮檺
function getTeacherPermissions(level) {
  switch (level) {
    case 'department_head':
      return [
        'view_all_courses',
        'view_all_teachers',
        'view_all_classes',
        'manage_department',
        'generate_teaching_ppt',
        'analyze_all_classes',
        ...getTeacherPermissions('course_leader')
      ]
    case 'course_leader':
      return [
        'manage_course_experiments',
        'view_course_teachers',
        'view_course_classes',
        'analyze_course_classes',
        ...getTeacherPermissions('normal')
      ]
    case 'normal':
    default:
      return [
        'view_own_classes',
        'manage_own_experiments',
        'view_student_reports',
        'analyze_own_classes',
        'ai_teaching_recommendation'
      ]
  }
}



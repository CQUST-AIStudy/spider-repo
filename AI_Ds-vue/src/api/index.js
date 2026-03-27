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
  setSessionToken,
  setTapToken,
  setTapUser,
  setUserInfo,
} from '../constants/auth'
import { API_BASE_URL_WITH_SLASH } from '../config/runtime'

axios.defaults.withCredentials = true;


const USE_MOCK_DATA = false;
const apiClient = axios.create({
  baseURL: API_BASE_URL_WITH_SLASH,
  timeout: 30000,
  withCredentials: true,
  headers: {
    'Content-Type': 'application/json'
  }
});

apiClient.interceptors.request.use(
  config => config,
  error => {
    return Promise.reject(error);
  }
);

apiClient.interceptors.response.use(
  response => {
    return response.data;
  },
  error => {

    return Promise.reject(error);
  }
);

export default {
  async getStudentInfo() {
    if (process.env.NODE_ENV === 'development' && USE_MOCK_DATA) {
      await delay(300)
      return studentInfo
    }
    return apiClient.get('/api/profile/me')
  },

  async getTeacherInfo() {
    if (process.env.NODE_ENV === 'development' && USE_MOCK_DATA) {
      await delay(300)
      return teacherInfo
    }
    return apiClient.get('/api/teacher/info')
  },

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

  async login(username, password, teacherLevel) {
    if (
      // process.env.NODE_ENV === 'development' &&
      USE_MOCK_DATA) {
      console.log('寮€鍙戠幆澧冪櫥褰曪紝鐢ㄦ埛鍚?', username, '鏁欏笀绾у埆:', teacherLevel)
      await delay(1000)

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
            name: '李教授',
            role: 'teacher',
            level: teacherLevel || 'normal',
            permissions: getTeacherPermissions(teacherLevel || 'normal'),
            avatar: 'https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png'
          }
          token = 'teacher_token_xxx'

        } else if (username === 'admin' && password === 'password123') {
          userInfo = {
            id: 'A2023001',
            name: '管理员',
            role: 'admin',
            avatar: 'https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png'
          }
          token = 'admin_token_xxx'

        } else {
          success = false
          message = '鐢ㄦ埛鍚嶆垨瀵嗙爜閿欒'
        }

        if (success) {
          try {
            setSessionToken(token)
            setUserInfo(userInfo)
            console.log('鐧诲綍淇℃伅宸蹭繚瀛樺埌localStorage')
          } catch (e) {
            console.error('淇濆瓨鐧诲綍淇℃伅澶辫触:', e)
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

    try {
      console.log('鍙戦€佺櫥褰曡姹?(璇︾粏):', {
        username: username,
        password: '***',
        role: teacherLevel,
        url: '/api/login'
      });

      const requestData = {
        username: username,
        password: password
      };

      if (teacherLevel) {
        requestData.role = teacherLevel;
      }

      const config = {
        withCredentials: true
      };

      const response = await apiClient.post('/api/login', requestData, config);
      console.log('鐧诲綍鍘熷鍝嶅簲:', response);

      if (response) {
        if (response.success) {
          try {
            const userInfo = response.user;
            if (userInfo) {

              setUserInfo(userInfo);
              console.log('鐢ㄦ埛淇℃伅宸蹭繚瀛樺埌localStorage');

              const token = 'legacy_session';
              setSessionToken(token);
            }
          } catch (e) {
            console.error('淇濆瓨鐢ㄦ埛淇℃伅澶辫触:', e);
          }

          this.tryTapLogin();
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

  async tryTapLogin() {
    try {
      const res = await apiClient.post('/api/auth/session', {});
      const data = res?.data ?? res;
      if (data?.accessToken) {
        setTapToken(data.accessToken);
        setTapUser({
          userId: data.userId,
          role: data.role,
          username: null
        });
        console.log('TAP session 换票成功');
      }
    } catch (e) {
      console.warn('TAP session 换票失败（可忽略）:', e.message);
    }
  },

  async logout() {
    if (process.env.NODE_ENV === 'development' && USE_MOCK_DATA) {
      await delay(300)
      return { success: true }
    }
    return apiClient.post('/api/logout')
  },

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

  async getExperimentList() {

    if (process.env.NODE_ENV === 'development' && USE_MOCK_DATA) {
      await delay(500)
      return experimentList
    }
    try {
      console.log('姝ｅ湪鍙戦€佽幏鍙栧疄楠屽垪琛ㄨ姹?..');
      const config = {
        withCredentials: true
      };
      const response = await apiClient.get('/api/experiments', config);
      console.log('从后端的 response:', response);
      return response;
    } catch (error) {
      console.error('鑾峰彇瀹為獙鍒楄〃澶辫触:', error);
      throw error;
    }
  },

  async getExperiments() {

    if (process.env.NODE_ENV === 'development' && USE_MOCK_DATA) {
      await delay(500)
      return experimentList
    }
    try {
      console.log('姝ｅ湪鍙戦€佽幏鍙栧疄楠屽垪琛ㄨ姹?..');
      const config = {
        withCredentials: true
      };
      const response = await apiClient.get('/api/experiments1', config);
      console.log('从后端的 response:', response);
      return response;
    } catch (error) {
      console.error('鑾峰彇瀹為獙鍒楄〃澶辫触:', error);
      throw error;
    }
  },

  async getTeacherExperimentList() {
    if (process.env.NODE_ENV === 'development' && USE_MOCK_DATA) {
      await delay(500)
      console.log('浣跨敤妯℃嫙鏁版嵁杩斿洖鏁欏笀瀹為獙鍒楄〃');
      return teacherExperimentList
    }

    try {
      console.log('姝ｅ湪鍙戦€佽幏鍙栨暀甯堝疄楠屽垪琛ㄨ姹?..');
      const config = {
        withCredentials: true
      };
      const response = await apiClient.get('/api/teacher/experiments', config);
      console.log('从后端获取的教师实验列表:', response);
      return response;
    } catch (error) {
      console.error('鑾峰彇鏁欏笀瀹為獙鍒楄〃澶辫触:', error);
      throw error;
    }
  },

  async getExperimentDetails(id) {
    if (process.env.NODE_ENV === 'development' && USE_MOCK_DATA) {
      await delay(700)
      return experimentDetails
    }
    try {
      console.log('姝ｅ湪鍙戦€佽幏鍙栧疄楠岀粏鑺傝姹?..');
      const response = await apiClient.get(`/api/experiments/${id}`);
      console.log('从后端的实验详情 response:', response);

      if (response.success && !response.data && typeof response === 'object') {
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

  async getLearningAnalysis() {
    if (process.env.NODE_ENV === 'development' && USE_MOCK_DATA) {
      await delay(800)
      return learningAnalysisData
    }
    try {
      console.log('姝ｅ湪鍙戦€佽幏鍙栨帹鑽愰鐩姹?..');
      const response = await apiClient.get('/api/student/learning-analysis')
      console.log('从后端的实验详情 response:', response);

      if (response.success && !response.data && typeof response === 'object') {
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

  async getLeetCodeProblem(problemId) {
    return apiClient.get(`/api/leetcode/problem/${problemId}`)
  },

  async runLeetCodeSolution(data) {
    return apiClient.post('/api/leetcode/run', data)
  },

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

  async getRecommendedPractices() {
    if (process.env.NODE_ENV === 'development' && USE_MOCK_DATA) {
      await delay(500)
      return recommendedPractices
    }

    return apiClient.get('/api/current/recommendedPractices')
  },

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

  async getAllStudentExperiments() {
    if (process.env.NODE_ENV === 'development' && USE_MOCK_DATA) {
      await delay(600)
      return studentSubmissionsList
    }

    try {
      console.log('姝ｅ湪鑾峰彇鎵€鏈夊鐢熷疄楠屾暟鎹?..');
      const response = await apiClient.get('/api/teacher/allStudentExperiments');
      console.log('获取到所有学生实验数据:', response);

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

  async getSubmissionDetail(submissionId) {
    if (process.env.NODE_ENV === 'development' && USE_MOCK_DATA) {
      await delay(600)

      console.log('浣跨敤妯℃嫙鏁版嵁杩斿洖鎻愪氦璇︽儏, 鎻愪氦ID:', submissionId);
      return {
        id: submissionId,
        studentId: '2019443672',
        studentName: '易星贵',
        experimentId: 'exp001',
        experimentName: '绾挎€ц〃瀹為獙',
        status: 'submitted',
        score: null,
        code: '"绗?1 棰樺涓?\n//// 鍒涘缓绌虹殑椤哄簭琛?\n// List MakeEmpty ()\n// {\n// List list = malloc (sizeof (struct LNode));\n// //if (list == NULL)\n// // return false;\n// list->Last = -1;\n// return list;\n// }\n// 鍏冪礌 x 鎻掑叆浣嶇疆 p\n//bool Insert (List L, ElementType X, Position P)\n// {\n// if (L->Last>= MAXSIZE-1)\n// {\n// printf ("FULL");\n// return 0;\n// }\n// for (int i = L->Last+1; i >P; i--)\n// {\n// L->Data [i] = L->Data [i-1];\n// }\n// L->Data [P] = X;\n// L->Last++;\n// //printf ("last==% d", L->Data [L->Last]);\n// return 1;\n// }\n// 鏌ユ壘 x 鐨勪綅缃?\n// Position Find (List L, ElementType X)\n// {\n// for (int i = 0; i <= L->Last; i++)\n// {\n// if (L->Data [i] == X)\n// {\n// return i;\n// }\n// }\n// return ERROR;\n// }\n// 灏嗕綅缃?P 鐨勫厓绱犲垹闄?\n//bool Delete (List L, Position P)\n// {\n// if (P > L->Last||P<0)\n// {\n// printf ("POSITION % d EMPTY",P);\n// return 0;\n// }\n// for (int i = P; i <= L->Last; i++)\n// {\n// L->Data [i] = L->Data [i + 1];\n// }\n// L->Last--;\n// return 1;\n// }\nList MakeEmpty ()\n List L;\n L=(List) malloc (sizeof (struct LNode));\n L->Last=-1;\n return L;\nPosition Find ( List L, ElementType X )\n int i;\n for (i=0;i<=L->Last;i++)\n {\n if (L->Data [i]==X)\n return i;\n }\n return ERROR;\nbool Insert ( List L, ElementType X, Position P )\n int i;\n if (L->Last==MAXSIZE-1)\n {\n printf ("FULL");\n return false;\n }\n if (P>L->Last+1||P<0)\n {\n printf ("ILLEGAL POSITION");\n return false;\n }\n for ( i=L->Last;i>=P;i-- )\n {\n L->Data [i+1]=L->Data [i];\n }\n L->Data [P]=X;\n L->Last++;\n return true;\nbool Delete ( List L, Position P )\n Position j;\n if (P<0||P>L->Last){\n printf ("POSITION % d EMPTY",P);\n return false;\n }\n for (j=P;j < L->Last;j++)\n L->Data [j]=L->Data [j+1];\n L->Last--;\n return true;\n| 娴嬭瘯鐐?| 缁撴灉 | 娴嬭瘯鐐瑰緱鍒?| 鑰楁椂 | 鍐呭瓨 |\n| --- | --- | --- | --- | --- |\n| 0 | 绛旀姝ｇ‘ | 15 | 1.00 ms | 364 KB |\n| 1 | 绛旀姝ｇ‘ | 15 | 2.00 ms | 356 KB |\n| 2 | 绛旀姝ｇ‘ | 10 | 1.00 ms | 364 KB |\n 绗?2 棰樺涓?\nList Delete (List L, ElementType minD, ElementType maxD)\n for (int i = 0; i <= L->Last; i++)\n {\n if (L->Data [i]>minD&&L->Data [i]<maxD)\n {\n for (int j = i; j < L->Last; j++)\n {\n L->Data [j] = L->Data [j + 1];\n }\n L->Last--;\n i--;\n }\n }\n return L;\n| 娴嬭瘯鐐?| 缁撴灉 | 娴嬭瘯鐐瑰緱鍒?| 鑰楁椂 | 鍐呭瓨 |\n| --- | --- | --- | --- | --- |\n| 0 | 绛旀姝ｇ‘ | 15 | 1.00 ms | 364 KB |\n| 1 | 绛旀姝ｇ‘ | 15 | 2.00 ms | 356 KB |\n| 2 | 绛旀姝ｇ‘ | 10 | 1.00 ms | 364 KB |\n 绗?3 棰樺涓?\nvoid Del_negative (SqList* L)\n int x = 0;\n for (int i = 0; i < L->length; i++)\n {\n if (L->items [i] < 0)\n {\n SqListDelete (L, i+1, &x);\n i--;\n }\n }\n| 娴嬭瘯鐐?| 缁撴灉 | 娴嬭瘯鐐瑰緱鍒?| 鑰楁椂 | 鍐呭瓨 |\n| --- | --- | --- | --- | --- |\n| 0 | 绛旀姝ｇ‘ | 15 | 1.00 ms | 364 KB |\n| 1 | 绛旀姝ｇ‘ | 15 | 2.00 ms | 356 KB |\n| 2 | 绛旀姝ｇ‘ | 10 | 1.00 ms | 364 KB |\n 绗?4 棰樺涓?\n#define _CRT_SECURE_NO_WARNINGS 0\n#define _CRT_SECURE_NO_WARNINGS 0\n#include<stdio.h>\nint main ()\n int n, m, count = 0;\n int array [100] = { 0 };\n scanf ("% d % d", &n, &m);\n for (int i = 0; i < n; i++)\n {\n scanf ("% d", &array [i]);\n //printf ("% d", array [i]);\n }\n for (int i = 0; i < m; i++)\n {\n count = array [0];\n for (int j = 0; j < n - 1; j++)\n {\n //count=array [0];\n array [j] = array [j + 1];\n }\n array [n - 1] = count;\n }\n for (int i = 0; i < n; i++)\n {\n printf ("% d", array [i]);\n if (i < n - 1)\n {\n printf ("");\n }\n }\n return 0;\n| 娴嬭瘯鐐?| 缁撴灉 | 娴嬭瘯鐐瑰緱鍒?| 鑰楁椂 | 鍐呭瓨 |\n| --- | --- | --- | --- | --- |\n| 0 | 绛旀姝ｇ‘ | 15 | 1.00 ms | 364 KB |\n| 1 | 绛旀姝ｇ‘ | 15 | 2.00 ms | 356 KB |\n| 2 | 绛旀姝ｇ‘ | 10 | 1.00 ms | 364 KB |\n 绗?5 棰樺涓?\n#include<stdio.h>\nint main ()\n int A [100],B [100];\n int m,n,p=0;\n scanf ("% d % d",&m,&n);\n //scanf ("%")\n for (int i=0;i<m;i++)\n {\n scanf ("% d ",&A [i]);\n }\n for (int j=0;j<n;j++)\n {\n scanf ("% d ",&B [j]);\n }\n for (int i=0;i<m;i++)\n for (int j=0;j<n;j++)\n {\n if (A [i]==B [j])\n {\n if (p!=0)\n printf (" ");\n printf ("% d",A [i]);\n p++;\n }\n }\n if (p==0)\n printf ("NULL");\n return 0;\n| 娴嬭瘯鐐?| 缁撴灉 | 娴嬭瘯鐐瑰緱鍒?| 鑰楁椂 | 鍐呭瓨 |\n| --- | --- | --- | --- | --- |\n| 0 | 绛旀姝ｇ‘ | 15 | 1.00 ms | 364 KB |\n| 1 | 绛旀姝ｇ‘ | 15 | 2.00 ms | 356 KB |\n| 2 | 绛旀姝ｇ‘ | 10 | 1.00 ms | 364 KB |\n 绗?6 棰樺涓?\n// #define _CRT_SECURE_NO_WARNINGS 1\n// #include<stdio.h>\n//int main ()\n// {\n// int A [10000], B [10000], C [10000];\n// int m, n;\n// scanf ("% d % d", &m, &n);\n// for (int i = 0; i < m; i++)\n// {\n// scanf ("% d ", &A [i]);\n// }\n// for (int i = 0; i < n; i++)\n// {\n// scanf ("% d ", &B [i]);\n// }\n// //maopao (A, m);\n// //maopao (B, n);\n// int x = 0;\n// //for (int i = 0,j=0; i < m &&j<n;)\n// int count = 0;\n// for (int i = 0; i < m; i++)\n// {\n// for (int j = 0; j < n; j++)\n// {\n// if (A [i] == B [j])\n// {\n// count++;\n// continue;\n// }\n// }\n// if (count == 0)\n// C [x++] = A [i];\n// count = 0;\n// }\n// int n1 = 0;\n// for (int i = 0; i < x - 1; i++)\n// for (int j = 0; j < x - 1 - i; j++)\n// {\n// if (C [j] > C [j + 1])\n// {\n// n1 = C [j];\n// C [j] = C [j + 1];\n// C [j + 1] = n1;\n// }\n// } for (int i = 0; i < x; i++)\n// printf ("% d ", C [i]);\n// if (x == 0)\n// {\n// printf ("% d",0);\n// }\n// return 0;\n// }\n#define _CRT_SECURE_NO_WARNINGS 1\n#include <stdio.h>\n#include <stdlib.h>\n#define maxsize 10000\ntypedef struct\n int data [maxsize];\n int length;\n} sqlist;\nint main ()\n sqlist* c = (sqlist*) malloc (sizeof (sqlist));\n c->length = 0;\n int n, m;\n scanf ("% d % d", &n, &m);\n sqlist* a = (sqlist*) malloc (sizeof (sqlist));\n sqlist* b = (sqlist*) malloc (sizeof (sqlist));\n for (int i = 0; i < n; i++)\n scanf ("% d", &a->data [i]);\n a->length = n;\n for (int j = 0; j < m; j++)\n scanf ("% d", &b->data [j]);\n b->length = m;\n int i = 0, j = 0, x = 0;\n while (i < a->length && j < b->length)\n {\n if (a->data [i] < b->data [j])\n {\n c->data [x] = a->data [i];\n x++;\n i++;\n }\n else if (a->data [i] == b->data [j])\n {\n i++;\n j++;\n }\n else\n j++;\n }\n int y = i;\n for (int k = y; k < a->length; k++)\n {\n c->data [x++] = a->data [k];\n }\n c->length = x;\n if (c->length == 0)\n {\n printf ("0");\n }\n for (int i = 0; i < c->length; i++)\n {\n printf ("% d ", c->data [i]);\n }\n free (a);\n free (b);\n free (c);\n return 0;\n| 娴嬭瘯鐐?| 缁撴灉 | 娴嬭瘯鐐瑰緱鍒?| 鑰楁椂 | 鍐呭瓨 |\n| --- | --- | --- | --- | --- |\n| 0 | 绛旀姝ｇ‘ | 15 | 1.00 ms | 364 KB |\n| 1 | 绛旀姝ｇ‘ | 15 | 2.00 ms | 356 KB |\n| 2 | 绛旀姝ｇ‘ | 10 | 1.00 ms | 364 KB |\n 绗?7 棰樺涓?\n#define _CRT_SECURE_NO_WARNINGS 1\n#include<stdio.h>\nint main ()\n int A [100],B [100];\n int m, n,count=0,x;\n scanf ("% d", &m);\n x = m;\n for (int i = 0; i < m; i++)\n {\n scanf ("% d", &A [i]);\n }\n scanf ("% d", &n);\n for (int j = 0; j < n; j++)\n {\n scanf ("% d", &B [j]);\n for (int i = 0; i < m; i++)\n {\n if (B [j] == A [i])\n {\n count++;\n }\n }\n if (count == 0)\n {\n A [x++] = B [j];\n }\n count = 0;\n }\n for (int i = 0; i < x; i++)\n printf ("% d ", A [i]);\n return 0;',
        plagiarismRate: 0.05,
        class: '计算机科学1班',
        report: `# 线性表实验报告\n\n## 实验目的\n实现顺序表的基本操作\n实现链表的基本操作\n完成示例应用程序\n撰写实验报告分析性能\n\n## 实验环境\nVisual Studio Code, JavaScript\n\n## 实验内容\n实验内容：线性表基础操作，包括顺序表的初始化、插入、删除、查找和遍历实现，包括增删改查等功能。\n\n## 实验步骤\n1. 首先定义线性表的结构\n2. 实现增加元素的方法\n3. 实现删除元素的方法\n4. 实现查找元素的方法\n\n## 实验结果\n成功实现了线性表的各项功能，测试通过。\n\n## 实验总结\n通过本次实验，我深入理解了线性表的工作原理和实现方法。`,
        aiRemarks: '第一题评语: 代码实现正确，符合题意。第二题评语: 代码实现正确，符合题意。第三题评语: 代码实现正确，符合题意。总评语：代码质量良好，符合实验要求。',
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

  async getClassList() {
    if (process.env.NODE_ENV === 'development' && USE_MOCK_DATA) {
      await delay(500)
      return classList
    }

    try {
      console.log('姝ｅ湪鑾峰彇鐝骇鍒楄〃...');
      const response = await apiClient.get('/api/teacher/class');
      console.log('获取到班级列表数据:', response);

      if (response && !Array.isArray(response)) {
        if (response.data && Array.isArray(response.data)) {
          return response.data;
        }

        return [response];
      }

      return response;
    } catch (error) {
      console.error('鑾峰彇鐝骇鍒楄〃澶辫触:', error);
      throw error;
    }
  },

  async getStudentList() {
    if (process.env.NODE_ENV === 'development' && USE_MOCK_DATA) {
      await delay(500);
      return {
        students: [
          {
            student_id: 2019443672,
            username: "2019443672",
            password: "password123",
            name: "易星贵",
            class_name: "计算机科学1班",
            createdAt: "2025-04-16T02:34:06.000+00:00"
          },
          {
            student_id: 2019444338,
            username: "2019444338",
            password: "password123",
            name: "张峻豪",
            class_name: "计算机科学1班",
            createdAt: "2025-04-16T02:34:06.000+00:00"
          },
          {
            student_id: 2020444155,
            username: "2020444155",
            password: "password123",
            name: "陈华金",
            class_name: "计算机科学1班",
            createdAt: "2025-04-16T02:34:06.000+00:00"
          },
          {
            student_id: 2020444227,
            username: "2020444227",
            password: "password123",
            name: "彭科望",
            class_name: "计算机科学1班",
            createdAt: "2025-04-16T02:34:06.000+00:00"
          }
        ]
      };
    }

    try {
      console.log('姝ｅ湪鑾峰彇瀛︾敓鍒楄〃...');
      const response = await apiClient.get('/api/teacher/studentList');
      console.log('获取到学生列表数据:', response);
      return response;
    } catch (error) {
      console.error('鑾峰彇瀛︾敓鍒楄〃澶辫触:', error);
      throw error;
    }
  },

  async getClassAnalysis(classId) {
    if (process.env.NODE_ENV === 'development' && USE_MOCK_DATA) {
      await delay(800);
      return {
        id: classId, name: '计算机科学1班', studentCount: 49, grade: '2023级',
        teacherName: '鐜嬭€佸笀', averageScore: 87, completionRate: 75
      };
    }

    try {
      const allStudentExperiments = await this.getAllStudentExperiments();
      const experiments = await this.getTeacherExperimentList();
      const expList = experiments?.data || experiments || [];

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

  async createExperiment(data) {
    if (process.env.NODE_ENV === 'development' && USE_MOCK_DATA) {
      await delay(800)
      return { success: true, id: Date.now() }
    }
    return apiClient.post('/api/experiments', data)
  },

  async updateExperiment(id, data) {
    if (process.env.NODE_ENV === 'development' && USE_MOCK_DATA) {
      await delay(700)
      return { success: true }
    }
    return apiClient.put(`/api/experiments/${id}`, data)
  },

  async submitExperiment(id, data) {
    if (process.env.NODE_ENV === 'development' && USE_MOCK_DATA) {
      await delay(600)
      return { success: true, id: Date.now() }
    }
    return apiClient.post(`/api/experiments/${id}/submit`, data)
  },
  async gradeSubmission(id, data) {
    if (process.env.NODE_ENV === 'development' && USE_MOCK_DATA) {
      await delay(500)
      return { success: true }
    }
    return apiClient.post(`/api/submissions/${id}/grade`, data)
  },

  async saveQuestionComment(submissionId, questionIndex, comment) {
    if (process.env.NODE_ENV === 'development' && USE_MOCK_DATA) {
      await delay(300)
      return { success: true }
    }
    return apiClient.post(`/api/submissions/${submissionId}/comments`, { questionIndex, comment })
  },

  async rejectSubmission(id) {
    if (process.env.NODE_ENV === 'development' && USE_MOCK_DATA) {
      await delay(500)
      return { success: true }
    }
    return apiClient.post(`/api/submissions/${id}/reject`)
  },

  async addUser(data) {
    if (process.env.NODE_ENV === 'development' && USE_MOCK_DATA) {
      await delay(800)
      return { success: true, id: `${data.role.charAt(0).toUpperCase()}${Date.now().toString().slice(-7)}` }
    }
    return apiClient.post('/api/users', data)
  },

  async updateUser(id, data) {
    if (process.env.NODE_ENV === 'development' && USE_MOCK_DATA) {
      await delay(700)
      return { success: true }
    }
    return apiClient.put(`/api/users/${id}`, data)
  },

  async deleteUser(id) {
    if (process.env.NODE_ENV === 'development' && USE_MOCK_DATA) {
      await delay(500)
      return { success: true }
    }
    return apiClient.delete(`/api/users/${id}`)
  }
}

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



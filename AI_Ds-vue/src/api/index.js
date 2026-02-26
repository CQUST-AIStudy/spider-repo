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

////前端在发送请求时，要确保携带 session 信息。以 axios 为例，需要设置 withCredentials 为 true：
axios.defaults.withCredentials = true;


const USE_MOCK_DATA = false; // 设置为 true 可使用本地模拟数据
// 创建 axios 实例
const apiClient = axios.create({
  baseURL: 'http://localhost:8081/',  // 修改为后端服务的实际地址
  timeout: 30000,
  withCredentials: true,  // 添加这行确保所有请求都发送凭证
  headers: {
    'Content-Type': 'application/json'
  }
});

// 请求拦截器
apiClient.interceptors.request.use(
  config => {
    // 获取token并添加到请求头
    const token = localStorage.getItem('token');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  error => {
    return Promise.reject(error);
  }
);

// 响应拦截器
apiClient.interceptors.response.use(
  response => {
    // 正常响应处理
    return response.data;
  },
  error => {
    // 错误处理
    // let errorMessage;
    //
    // if (error.response) {
    //   // 服务器返回了错误状态码
    //   const { status, data } = error.response;
    //
    //   switch (status) {
    //     case 401:
    //       errorMessage = '未授权，请重新登录';
    //       // 清除token等信息并重定向到登录页面
    //       localStorage.removeItem('token');
    //       localStorage.removeItem('userInfo');
    //       window.location.href = '/login';
    //       break;
    //     case 403:
    //       errorMessage = '拒绝访问';
    //       break;
    //     case 404:
    //       // errorMessage = '请求的资源不存在';
    //       break;
    //     case 500:
    //       errorMessage = '服务器错误';
    //       break;
    //     default:
    //       // errorMessage = data.message || `请求失败 (${status})`;
    //   }
    // } else if (error.request) {
    //   // 请求已发送但没收到响应
    //   errorMessage = '服务器无响应';
    // } else {
    //   // 请求设置出错
    //   errorMessage = error.message;
    // }
    //
    // // 显示错误消息
    // ElMessage.error(errorMessage);

    return Promise.reject(error);
  }
);

export default {
  // 获取学生信息
  async getStudentInfo() {
    if (process.env.NODE_ENV === 'development') {
      await delay(300)
      return studentInfo
    }
    return apiClient.get('/api/student/info')
  },

  // 获取教师信息
  async getTeacherInfo() {
    if (process.env.NODE_ENV === 'development') {
      await delay(300)
      return teacherInfo
    }
    return apiClient.get('/api/teacher/info')
  },

  // 获取管理员信息
  async getAdminInfo() {
    if (process.env.NODE_ENV === 'development') {
      await delay(300)
      return adminInfo
    }
    return apiClient.get('/api/admin/info')
  },

  // 登录
  async login(username, password, teacherLevel) {
    if (
      // process.env.NODE_ENV === 'development' &&
      USE_MOCK_DATA) {
      console.log('开发环境登录，用户名:', username, '教师级别:', teacherLevel)
      await delay(1000)

      // 模拟登录流程
      let userInfo = null
      let token = null
      let success = true
      let message = '登录成功'

      try {

        if (username === 'student' && password === 'password123') {
          userInfo = {
            id: 'S2023001',
            name: '张三',
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
          message = '用户名或密码错误'
        }

        // 保存登录信息
        if (success) {
          try {
            localStorage.setItem('token', token)
            localStorage.setItem('userInfo', JSON.stringify(userInfo))
            console.log('登录信息已保存到localStorage')
          } catch (e) {
            console.error('保存登录信息失败:', e)
            // 即使保存失败，也不影响返回登录成功结果
          }
        }
        console.log('用户信息:', userInfo)
        return { success, message, userInfo, token }
      } catch (error) {
        console.error('登录过程发生错误:', error)
        return {
          success: false,
          message: '登录过程发生错误: ' + (error.message || '未知错误'),
          userInfo: null,
          token: null
        }
      }
    }

    // 联调测试
    try {
      console.log('发送登录请求 (详细):', {
        username: username,
        password: '***', // 出于安全考虑不打印密码
        role: teacherLevel,
        url: '/api/login'
      });

      // 确保使用正确的请求数据格式
      const requestData = {
        username: username,
        password: password
      };

      // 如果有教师级别，添加到请求中
      if (teacherLevel) {
        requestData.role = teacherLevel;
      }

      // 确保axios配置正确
      const config = {
        withCredentials: true // 确保发送cookie
      };

      // 发送请求并记录详细日志
      const response = await apiClient.post('/api/login', requestData, config);
      console.log('登录原始响应:', response);

      // 确保响应有效
      if (response) {
        // 正确处理响应中的用户信息
        if (response.success) {
          // 保存用户信息到本地存储
          try {
            const userInfo = response.user;
            if (userInfo) {

              localStorage.setItem('userInfo', JSON.stringify(userInfo));
              console.log('用户信息已保存到localStorage');

              // 为了兼容性，也设置token (如果需要)
              const token = 'session_auth_' + new Date().getTime();
              localStorage.setItem('token', token);
            }
          } catch (e) {
            console.error('保存用户信息失败:', e);
          }

          // 尝试TAP平台登录（静默，不影响主登录流程）
          this.tryTapLogin(username, password);
        } else {
          console.warn('登录响应显示失败:', response.message);
        }
      }

      return response;
    } catch (error) {
      console.error('API登录请求失败 (详细):', {
        message: error.message,
        status: error.response?.status,
        data: error.response?.data
      });

      return {
        success: false,
        message: error.response?.data?.message || error.message || '登录请求失败',
        userInfo: null
      };
    }
  },

  // 尝试TAP平台登录（获取JWT token用于班级管理等功能）
  async tryTapLogin(username, password) {
    try {
      const res = await apiClient.post('/api/auth/login', { username, password });
      const data = res?.data ?? res;
      if (data?.accessToken) {
        localStorage.setItem('tap_token', data.accessToken);
        localStorage.setItem('tap_user', JSON.stringify({
          userId: data.userId,
          role: data.role,
          username
        }));
        console.log('TAP平台登录成功');
      }
    } catch (e) {
      console.warn('TAP平台自动登录失败（可忽略）:', e.message);
    }
  },

  // 登出
  async logout() {
    if (process.env.NODE_ENV === 'development') {
      await delay(300)
      return { success: true }
    }
    return apiClient.post('/api/logout')
  },

  // 注册
  async register(formData) {
    try {
      const response = await apiClient.post('/api/register', {
        username: formData.username,
        password: formData.password,
        role: formData.role,
        usernum: formData.usernum || null,
        classname: formData.classname || null
      })
      return response
    } catch (error) {
      return {
        success: false,
        message: error.response?.data?.message || error.message || '注册请求失败'
      }
    }
  },

  // 获取实验列表
  async getExperimentList() {

    if (process.env.NODE_ENV === 'development' && USE_MOCK_DATA) {
      await delay(500)
      return experimentList
    }
    try {
      console.log('正在发送获取实验列表请求...');
      const config = {
        withCredentials: true // 确保发送cookie
      };
      const response = await apiClient.get('/api/experiments', config);
      console.log('从后端的response：', response);
      return response;
    } catch (error) {
      console.error('获取实验列表失败:', error);
      throw error;
    }
  },

  // 获取实验列表
  async getExperiments() {

    if (process.env.NODE_ENV === 'development' && USE_MOCK_DATA) {
      await delay(500)
      return experimentList
    }
    try {
      console.log('正在发送获取实验列表请求...');
      const config = {
        withCredentials: true // 确保发送cookie
      };
      const response = await apiClient.get('/api/experiments1', config);
      console.log('从后端的response：', response);
      return response;
    } catch (error) {
      console.error('获取实验列表失败:', error);
      throw error;
    }
  },

  // 获取教师创建的实验列表
  async getTeacherExperimentList() {
    if (process.env.NODE_ENV === 'development' && USE_MOCK_DATA) {
      await delay(500)
      console.log('使用模拟数据返回教师实验列表');
      return teacherExperimentList
    }

    try {
      console.log('正在发送获取教师实验列表请求...');
      const config = {
        withCredentials: true // 确保发送cookie
      };
      const response = await apiClient.get('/api/teacher/experiments', config);
      console.log('从后端获取的教师实验列表：', response);
      return response;
    } catch (error) {
      console.error('获取教师实验列表失败:', error);
      throw error;
    }
  },

  // 获取实验详情
  async getExperimentDetails(id) {
    if (process.env.NODE_ENV === 'development' && USE_MOCK_DATA) {
      await delay(700)
      return experimentDetails
    }
    try {
      console.log('正在发送获取实验细节请求...');
      const response = await apiClient.get(`/api/experiments/${id}`);
      console.log('从后端的response实验细节：', response);

      // 检查返回数据结构，确保与其他接口一致
      // 如果返回的不是 {success: true, data: {...}} 这种结构，进行转换
      if (response.success && !response.data && typeof response === 'object') {
        // 将整个响应对象作为data字段返回，保持与getExperimentList相同的结构
        return {
          success: response.success,
          data: response
        };
      }

      return response;
    } catch (error) {
      console.error('获取实验细节失败:', error);
      throw error;
    }
  },

  // 获取学习分析数据
  async getLearningAnalysis() {
    if (process.env.NODE_ENV === 'development') {
      await delay(800)
      return learningAnalysisData
    }
    try {
      console.log('正在发送获取推荐题目请求...');
      const response = await apiClient.get('/api/student/learning-analysis')
      console.log('从后端的response实验细节：', response);

      // 检查返回数据结构，确保与其他接口一致
      // 如果返回的不是 {success: true, data: {...}} 这种结构，进行转换
      if (response.success && !response.data && typeof response === 'object') {
        // 将整个响应对象作为data字段返回，保持与getExperimentList相同的结构
        return {
          success: response.success,
          data: response
        };
      }

      return response;
    } catch (error) {
      console.error('获取实验细节失败:', error);
      throw error;
    }
  },

  // 获取推荐练习
  async getRecommendedPractices() {
    if (process.env.NODE_ENV === 'development' && USE_MOCK_DATA) {
      await delay(500)
      return recommendedPractices
    }

    return apiClient.get('/api/current/recommendedPractices')
  },

  // 获取学生提交列表
  async getStudentSubmissions(experimentId) {
    if (process.env.NODE_ENV === 'development') {
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

  // 获取所有学生的实验提交情况
  async getAllStudentExperiments() {
    if (process.env.NODE_ENV === 'development' && USE_MOCK_DATA) {
      await delay(600)
      return studentSubmissionsList
    }

    try {
      console.log('正在获取所有学生实验数据...');
      const response = await apiClient.get('/api/teacher/allStudentExperiments');
      console.log('获取到所有学生实验数据:', response);

      if (response.success) {
        return response.data;
      } else {
        throw new Error(response.message || '获取数据失败');
      }
    } catch (error) {
      console.error('获取所有学生实验数据失败:', error);
      throw error;
    }
  },

  // 获取提交详情
  async getSubmissionDetail(submissionId) {
    if (process.env.NODE_ENV === 'development' && USE_MOCK_DATA) {
      await delay(600)
      // 返回模拟的提交详情数据

      console.log('使用模拟数据返回提交详情, 提交ID:', submissionId);
      return {
        id: submissionId,
        studentId: '2019443672',
        studentName: '易星贵',
        experimentId: 'exp001',
        experimentName: '线性表实验',
        status: 'submitted',
        score: null,
        code: '"第 1 题如下:\n//// 创建空的顺序表 \n// List MakeEmpty ()\n// {\n// List list = malloc (sizeof (struct LNode));\n// //if (list == NULL)\n// // return false;\n// list->Last = -1;\n// return list;\n// }\n// 元素 x 插入位置 p\n//bool Insert (List L, ElementType X, Position P)\n// {\n// if (L->Last>= MAXSIZE-1)\n// {\n// printf ("FULL");\n// return 0;\n// }\n// for (int i = L->Last+1; i >P; i--)\n// {\n// L->Data [i] = L->Data [i-1];\n// }\n// L->Data [P] = X;\n// L->Last++;\n// //printf ("last==% d", L->Data [L->Last]);\n// return 1;\n// }\n// 查找 x 的位置 \n// Position Find (List L, ElementType X)\n// {\n// for (int i = 0; i <= L->Last; i++)\n// {\n// if (L->Data [i] == X)\n// {\n// return i;\n// }\n// }\n// return ERROR;\n// }\n// 将位置 P 的元素删除 \n//bool Delete (List L, Position P)\n// {\n// if (P > L->Last||P<0)\n// {\n// printf ("POSITION % d EMPTY",P);\n// return 0;\n// }\n// for (int i = P; i <= L->Last; i++)\n// {\n// L->Data [i] = L->Data [i + 1];\n// }\n// L->Last--;\n// return 1;\n// }\nList MakeEmpty ()\n List L;\n L=(List) malloc (sizeof (struct LNode));\n L->Last=-1;\n return L;\nPosition Find ( List L, ElementType X )\n int i;\n for (i=0;i<=L->Last;i++)\n {\n if (L->Data [i]==X)\n return i;\n }\n return ERROR;\nbool Insert ( List L, ElementType X, Position P )\n int i;\n if (L->Last==MAXSIZE-1)\n {\n printf ("FULL");\n return false;\n }\n if (P>L->Last+1||P<0)\n {\n printf ("ILLEGAL POSITION");\n return false;\n }\n for ( i=L->Last;i>=P;i-- )\n {\n L->Data [i+1]=L->Data [i];\n }\n L->Data [P]=X;\n L->Last++;\n return true;\nbool Delete ( List L, Position P )\n Position j;\n if (P<0||P>L->Last){\n printf ("POSITION % d EMPTY",P);\n return false;\n }\n for (j=P;j < L->Last;j++)\n L->Data [j]=L->Data [j+1];\n L->Last--;\n return true;\n| 测试点 | 结果 | 测试点得分 | 耗时 | 内存 |\n| --- | --- | --- | --- | --- |\n| 0 | 答案正确 | 15 | 1.00 ms | 364 KB |\n| 1 | 答案正确 | 15 | 2.00 ms | 356 KB |\n| 2 | 答案正确 | 10 | 1.00 ms | 364 KB |\n 第 2 题如下:\nList Delete (List L, ElementType minD, ElementType maxD)\n for (int i = 0; i <= L->Last; i++)\n {\n if (L->Data [i]>minD&&L->Data [i]<maxD)\n {\n for (int j = i; j < L->Last; j++)\n {\n L->Data [j] = L->Data [j + 1];\n }\n L->Last--;\n i--;\n }\n }\n return L;\n| 测试点 | 结果 | 测试点得分 | 耗时 | 内存 |\n| --- | --- | --- | --- | --- |\n| 0 | 答案正确 | 15 | 1.00 ms | 364 KB |\n| 1 | 答案正确 | 15 | 2.00 ms | 356 KB |\n| 2 | 答案正确 | 10 | 1.00 ms | 364 KB |\n 第 3 题如下:\nvoid Del_negative (SqList* L)\n int x = 0;\n for (int i = 0; i < L->length; i++)\n {\n if (L->items [i] < 0)\n {\n SqListDelete (L, i+1, &x);\n i--;\n }\n }\n| 测试点 | 结果 | 测试点得分 | 耗时 | 内存 |\n| --- | --- | --- | --- | --- |\n| 0 | 答案正确 | 15 | 1.00 ms | 364 KB |\n| 1 | 答案正确 | 15 | 2.00 ms | 356 KB |\n| 2 | 答案正确 | 10 | 1.00 ms | 364 KB |\n 第 4 题如下:\n#define _CRT_SECURE_NO_WARNINGS 0\n#define _CRT_SECURE_NO_WARNINGS 0\n#include<stdio.h>\nint main ()\n int n, m, count = 0;\n int array [100] = { 0 };\n scanf ("% d % d", &n, &m);\n for (int i = 0; i < n; i++)\n {\n scanf ("% d", &array [i]);\n //printf ("% d", array [i]);\n }\n for (int i = 0; i < m; i++)\n {\n count = array [0];\n for (int j = 0; j < n - 1; j++)\n {\n //count=array [0];\n array [j] = array [j + 1];\n }\n array [n - 1] = count;\n }\n for (int i = 0; i < n; i++)\n {\n printf ("% d", array [i]);\n if (i < n - 1)\n {\n printf ("");\n }\n }\n return 0;\n| 测试点 | 结果 | 测试点得分 | 耗时 | 内存 |\n| --- | --- | --- | --- | --- |\n| 0 | 答案正确 | 15 | 1.00 ms | 364 KB |\n| 1 | 答案正确 | 15 | 2.00 ms | 356 KB |\n| 2 | 答案正确 | 10 | 1.00 ms | 364 KB |\n 第 5 题如下:\n#include<stdio.h>\nint main ()\n int A [100],B [100];\n int m,n,p=0;\n scanf ("% d % d",&m,&n);\n //scanf ("%")\n for (int i=0;i<m;i++)\n {\n scanf ("% d ",&A [i]);\n }\n for (int j=0;j<n;j++)\n {\n scanf ("% d ",&B [j]);\n }\n for (int i=0;i<m;i++)\n for (int j=0;j<n;j++)\n {\n if (A [i]==B [j])\n {\n if (p!=0)\n printf (" ");\n printf ("% d",A [i]);\n p++;\n }\n }\n if (p==0)\n printf ("NULL");\n return 0;\n| 测试点 | 结果 | 测试点得分 | 耗时 | 内存 |\n| --- | --- | --- | --- | --- |\n| 0 | 答案正确 | 15 | 1.00 ms | 364 KB |\n| 1 | 答案正确 | 15 | 2.00 ms | 356 KB |\n| 2 | 答案正确 | 10 | 1.00 ms | 364 KB |\n 第 6 题如下:\n// #define _CRT_SECURE_NO_WARNINGS 1\n// #include<stdio.h>\n//int main ()\n// {\n// int A [10000], B [10000], C [10000];\n// int m, n;\n// scanf ("% d % d", &m, &n);\n// for (int i = 0; i < m; i++)\n// {\n// scanf ("% d ", &A [i]);\n// }\n// for (int i = 0; i < n; i++)\n// {\n// scanf ("% d ", &B [i]);\n// }\n// //maopao (A, m);\n// //maopao (B, n);\n// int x = 0;\n// //for (int i = 0,j=0; i < m &&j<n;)\n// int count = 0;\n// for (int i = 0; i < m; i++)\n// {\n// for (int j = 0; j < n; j++)\n// {\n// if (A [i] == B [j])\n// {\n// count++;\n// continue;\n// }\n// }\n// if (count == 0)\n// C [x++] = A [i];\n// count = 0;\n// }\n// int n1 = 0;\n// for (int i = 0; i < x - 1; i++)\n// for (int j = 0; j < x - 1 - i; j++)\n// {\n// if (C [j] > C [j + 1])\n// {\n// n1 = C [j];\n// C [j] = C [j + 1];\n// C [j + 1] = n1;\n// }\n// } for (int i = 0; i < x; i++)\n// printf ("% d ", C [i]);\n// if (x == 0)\n// {\n// printf ("% d",0);\n// }\n// return 0;\n// }\n#define _CRT_SECURE_NO_WARNINGS 1\n#include <stdio.h>\n#include <stdlib.h>\n#define maxsize 10000\ntypedef struct\n int data [maxsize];\n int length;\n} sqlist;\nint main ()\n sqlist* c = (sqlist*) malloc (sizeof (sqlist));\n c->length = 0;\n int n, m;\n scanf ("% d % d", &n, &m);\n sqlist* a = (sqlist*) malloc (sizeof (sqlist));\n sqlist* b = (sqlist*) malloc (sizeof (sqlist));\n for (int i = 0; i < n; i++)\n scanf ("% d", &a->data [i]);\n a->length = n;\n for (int j = 0; j < m; j++)\n scanf ("% d", &b->data [j]);\n b->length = m;\n int i = 0, j = 0, x = 0;\n while (i < a->length && j < b->length)\n {\n if (a->data [i] < b->data [j])\n {\n c->data [x] = a->data [i];\n x++;\n i++;\n }\n else if (a->data [i] == b->data [j])\n {\n i++;\n j++;\n }\n else\n j++;\n }\n int y = i;\n for (int k = y; k < a->length; k++)\n {\n c->data [x++] = a->data [k];\n }\n c->length = x;\n if (c->length == 0)\n {\n printf ("0");\n }\n for (int i = 0; i < c->length; i++)\n {\n printf ("% d ", c->data [i]);\n }\n free (a);\n free (b);\n free (c);\n return 0;\n| 测试点 | 结果 | 测试点得分 | 耗时 | 内存 |\n| --- | --- | --- | --- | --- |\n| 0 | 答案正确 | 15 | 1.00 ms | 364 KB |\n| 1 | 答案正确 | 15 | 2.00 ms | 356 KB |\n| 2 | 答案正确 | 10 | 1.00 ms | 364 KB |\n 第 7 题如下:\n#define _CRT_SECURE_NO_WARNINGS 1\n#include<stdio.h>\nint main ()\n int A [100],B [100];\n int m, n,count=0,x;\n scanf ("% d", &m);\n x = m;\n for (int i = 0; i < m; i++)\n {\n scanf ("% d", &A [i]);\n }\n scanf ("% d", &n);\n for (int j = 0; j < n; j++)\n {\n scanf ("% d", &B [j]);\n for (int i = 0; i < m; i++)\n {\n if (B [j] == A [i])\n {\n count++;\n }\n }\n if (count == 0)\n {\n A [x++] = B [j];\n }\n count = 0;\n }\n for (int i = 0; i < x; i++)\n printf ("% d ", A [i]);\n return 0;',
        plagiarismRate: 0.05,
        class:'计算机科学1班',
        report: `# 线性表实验报告\n\n## 实验目的\n实现顺序表的基本操作\n实现链表的基本操作\n完成示例应用程序\n撰写实验报告分析性能\n\n## 实验环境\nVisual Studio Code, JavaScript\n\n## 实验内容\n实验内容：线性表基础操作，包括顺序表的初始化、插入、删除、查找和遍历实现，包括增删改查等功能。\n\n## 实验步骤\n1. 首先定义线性表的结构\n2. 实现增加元素的方法\n3. 实现删除元素的方法\n4. 实现查找元素的方法\n\n## 实验结果\n成功实现了线性表的各项功能，测试通过。\n\n## 实验总结\n通过本次实验，我深入理解了线性表的工作原理和实现方法。`,
        aiRemarks: '第一题评语: 代码实现正确，符合题意。第二题评语: 代码实现正确，符合题意。第三题评语: 代码实现正确，符合题意。总评语：代码质量良好，符合实验要求。',
        date: '2024-04-04',
      }
    }

    try {
      console.log('正在获取提交详情...');
      const response = await apiClient.get(`/api/submissions/${submissionId}`);
      console.log('获取到提交详情数据:', response);
      return response;
    } catch (error) {
      console.error('获取提交详情失败:', error);
      throw error;
    }
  },

  // 获取班级列表
  async getClassList() {
    if (process.env.NODE_ENV === 'development' && USE_MOCK_DATA) {
      await delay(500)
      return classList
    }

    try {
      console.log('正在获取班级列表...');
      const response = await apiClient.get('/api/teacher/class');
      console.log('获取到班级列表数据:', response);

      // 如果返回单个班级对象而不是数组，将其转换为数组
      if (response && !Array.isArray(response)) {
        // 检查是否有嵌套的data字段
        if (response.data && Array.isArray(response.data)) {
          return response.data;
        }

        // 如果是单个班级对象，转换为数组
        return [response];
      }

      return response;
    } catch (error) {
      console.error('获取班级列表失败:', error);
      throw error;
    }
  },

  // 获取学生列表
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
      console.log('正在获取学生列表...');
      const response = await apiClient.get('/api/teacher/studentList');
      console.log('获取到学生列表数据:', response);
      return response;
    } catch (error) {
      console.error('获取学生列表失败:', error);
      throw error;
    }
  },

  // 获取班级分析数据
  async getClassAnalysis(classId) {
    if (process.env.NODE_ENV === 'development' && USE_MOCK_DATA) {
      await delay(800);
      return {
        id: classId, name: '计算机科学1班', studentCount: 49, grade: '2023级',
        teacherName: '王老师', averageScore: 87, completionRate: 75
      };
    }

    try {
      // 使用真实数据：从提交数据中计算班级分析
      const allStudentExperiments = await this.getAllStudentExperiments();
      const experiments = await this.getTeacherExperimentList();
      const expList = experiments?.data || experiments || [];

      // 过滤当前班级的数据（如果有classId）
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

      // 计算学生排名
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
      console.error('获取班级分析数据失败:', error);
      throw error;
    }
  },

  // 创建实验
  async createExperiment(data) {
    if (process.env.NODE_ENV === 'development') {
      await delay(800)
      return { success: true, id: Date.now() }
    }
    return apiClient.post('/api/experiments', data)
  },

  // 更新实验
  async updateExperiment(id, data) {
    if (process.env.NODE_ENV === 'development') {
      await delay(700)
      return { success: true }
    }
    return apiClient.put(`/api/experiments/${id}`, data)
  },

  // 提交实验
  async submitExperiment(id, data) {
    if (process.env.NODE_ENV === 'development') {
      await delay(600)
      return { success: true, id: Date.now() }
    }
    return apiClient.post(`/api/experiments/${id}/submit`, data)
  },
  // 评分提交
  async gradeSubmission(id, data) {
    if (process.env.NODE_ENV === 'development') {
      await delay(500)
      return { success: true }
    }
    return apiClient.post(`/api/submissions/${id}/grade`, data)
  },

  // 保存教师评语
  async saveQuestionComment(submissionId, questionIndex, comment) {
    if (process.env.NODE_ENV === 'development') {
      await delay(300)
      return { success: true }
    }
    await delay(300)
    return { success: true }
  },

  // 拒绝提交
  async rejectSubmission(id) {
    if (process.env.NODE_ENV === 'development') {
      await delay(500)
      return { success: true }
    }
    return apiClient.post(`/api/submissions/${id}/reject`)
  },

  // 添加用户
  async addUser(data) {
    if (process.env.NODE_ENV === 'development') {
      await delay(800)
      return { success: true, id: `${data.role.charAt(0).toUpperCase()}${Date.now().toString().slice(-7)}` }
    }
    return apiClient.post('/api/users', data)
  },

  // 更新用户
  async updateUser(id, data) {
    if (process.env.NODE_ENV === 'development') {
      await delay(700)
      return { success: true }
    }
    return apiClient.put(`/api/users/${id}`, data)
  },

  // 删除用户
  async deleteUser(id) {
    if (process.env.NODE_ENV === 'development') {
      await delay(500)
      return { success: true }
    }
    return apiClient.delete(`/api/users/${id}`)
  }
}

// 根据教师权限等级获取对应权限
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
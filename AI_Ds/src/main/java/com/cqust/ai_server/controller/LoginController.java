package com.cqust.ai_server.controller;

import com.cqust.ai_server.entity.UserEntity;
import com.cqust.ai_server.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
public class LoginController {

    @Autowired
    private UserService userService;

    @GetMapping("/api/user/{username}")
    public UserEntity findUserByUsername(String username) {
        return userService.findByUsername(username);
    }
    @PostMapping("/api/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody UserEntity loginUser,HttpServletRequest request) {
        System.out.println("使用了登录方法");
        Map<String, Object> response = new HashMap<>();
        System.out.println("收到登录请求, 用户名: " + loginUser.getUsername() + " 密码: " + loginUser.getPassword());
        try {
            UserEntity user = userService.findByUsername(loginUser.getUsername());

            if (user == null) {
                response.put("success", false);
                response.put("message", "用户不存在");
                return ResponseEntity.ok(response);
            }
            
            // 简单密码验证，实际应用中应使用加密
            if (loginUser.getPassword().equals(user.getPassword())) {
                // 创建用户信息（不包含密码）
                Map<String, Object> userInfo = new HashMap<>();
                userInfo.put("id", user.getId());
                userInfo.put("username", user.getUsername());
                userInfo.put("role", user.getRole());///名字有换，！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！
                userInfo.put("email", user.getEmail());//！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！
                userInfo.put("usernum", user.getUsernum());//！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！
                userInfo.put("class", user.getClassname());

                // 获取 HttpSession 对象并存储用户信息
                HttpSession session = request.getSession(true);
                session.setAttribute("currentUser", user);
                session.setAttribute("username", user.getUsername());
                session.setAttribute("userId", user.getId());
                session.setAttribute("userRole", user.getRole());
                
                response.put("success", true);
                response.put("message", "登录成功");
                response.put("user", userInfo);
                System.out.println("登录成功");
                return ResponseEntity.ok(response);
            } else {
                response.put("success", false);
                response.put("message", "密码错误");
                return ResponseEntity.ok(response);
            }
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "登录失败(error): " + e.getMessage());
            return ResponseEntity.ok(response);
        }
    }
    
    @PostMapping("/api/register")
    public ResponseEntity<Map<String, Object>> register(@RequestBody Map<String, String> regData) {
        Map<String, Object> response = new HashMap<>();

        try {
            String username = regData.get("username");
            String password = regData.get("password");
            String role = regData.getOrDefault("role", "student");
            String usernum = regData.get("usernum");       // 学号/工号
            String classname = regData.get("classname");

            if (username == null || password == null || username.isBlank() || password.isBlank()) {
                response.put("success", false);
                response.put("message", "用户名和密码不能为空");
                return ResponseEntity.ok(response);
            }

            UserEntity existingUser = userService.findByUsername(username);

            if (existingUser != null) {
                response.put("success", false);
                response.put("message", "用户名已存在");
                return ResponseEntity.ok(response);
            }

            UserEntity user = new UserEntity();
            user.setUsername(username);
            user.setPassword(password);
            user.setRole(role);
            user.setUsernum(usernum);
            user.setClassname(classname);

            boolean saved = userService.saveUser(user);

            if (saved) {
                // 如果是学生且提供了学号，自动关联 student 表
                if ("student".equals(role) && usernum != null && !usernum.isBlank()) {
                    try {
                        // 检查 student 表中是否已有该学号
                        // 如果没有则创建，如果有则更新 username 关联
                        userService.bindStudentByUsernum(username, usernum, classname);
                    } catch (Exception e) {
                        System.out.println("学生绑定提示: " + e.getMessage());
                    }
                }
                response.put("success", true);
                response.put("message", "注册成功");
            } else {
                response.put("success", false);
                response.put("message", "注册失败");
            }

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "注册失败: " + e.getMessage());
            return ResponseEntity.ok(response);
        }
    }

    // 添加登出方法
    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout(HttpServletRequest request) {
        Map<String, Object> response = new HashMap<>();
        HttpSession session = request.getSession(false);

        if (session != null) {
            session.invalidate(); // 销毁会话
        }

        response.put("success", true);
        response.put("message", "已成功登出");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/api/user/password")
    public ResponseEntity<Map<String, Object>> updatePassword(@RequestBody Map<String, String> passwordData, 
                                                         HttpServletRequest request) {
        Map<String, Object> response = new HashMap<>();
        System.out.println(passwordData);
        try {
            // 获取当前会话中的用户
            HttpSession session = request.getSession(false);
            if (session == null || session.getAttribute("username") == null) {
                response.put("success", false);
                response.put("message", "未登录或会话已过期，请重新登录");
                return ResponseEntity.ok(response);
            }
            
            String username = (String) session.getAttribute("username");
            // 获取用户输入的原密码和新密码
            String oldPassword = passwordData.get("oldPassword");
            String newPassword = passwordData.get("newPassword");
            
            // 参数验证
            if (oldPassword == null || newPassword == null) {
                response.put("success", false);
                response.put("message", "原密码和新密码不能为空");
                return ResponseEntity.ok(response);
            }

            // 获取当前用户信息
            UserEntity user = userService.findByUsername(username);
            if (user == null) {
                response.put("success", false);
                response.put("message", "用户不存在");
                return ResponseEntity.ok(response);
            }
            
            // 验证原密码是否正确
            if (!oldPassword.equals(user.getPassword())) {
                response.put("success", false);
                response.put("message", "原密码不正确");
                return ResponseEntity.ok(response);
            }
            
            // 更新密码
            user.setPassword(newPassword);
            boolean updated = userService.updateUser(user);
            
            if (updated) {
                response.put("success", true);
                response.put("message", "密码修改成功");
            } else {
                response.put("success", false);
                response.put("message", "密码修改失败，请稍后再试");
            }
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "密码修改失败: " + e.getMessage());
            return ResponseEntity.ok(response);
        }
    }
}
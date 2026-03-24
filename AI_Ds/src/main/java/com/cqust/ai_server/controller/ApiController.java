package com.cqust.ai_server.controller;

import com.cqust.ai_server.dao.SubmissionDao;
import com.cqust.ai_server.entity.*;
import com.cqust.ai_server.security.LegacySessionAccessResolver;
import com.cqust.ai_server.security.StudentSessionResolver;

import com.cqust.ai_server.service.*;
import com.cqust.ai_server.entity.LeetCodeRecommendItem;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.tomcat.util.net.openssl.ciphers.Authentication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.awt.Color.red;

@RestController
public class ApiController {

    @Autowired
    private SubmissionDao submissionDao;

    @Autowired
    private StudentCodeService  studentCodeService;

    @Autowired
    private AIRemarksService aiRemarksService;

    @Autowired
    private ExperimentService experimentService;

    @Autowired
    private StudentService studentService;

    @Autowired
    private ScoreService scoreService;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private AISuggestedProblemService aiSuggestedProblemService;

    @Autowired
    private com.cqust.ai_server.service.ProfileService profileService;

    @Autowired
    private StudentSessionResolver studentSessionResolver;

    @Autowired
    private LegacySessionAccessResolver legacySessionAccessResolver;

    @Autowired
    @Qualifier("intelligentRecommendationService")
    private LeetCodeRecommendationService leetCodeRecommendationService;

    @Autowired
    private LeetCodeSyncService leetCodeSyncService;

    private static final String LEETCODE_CLEANED_DATA_PATH = "datasets/leetcode/solutions_cleaned.json";
    private volatile boolean leetCodeDataWarmupAttempted = false;

    @Value("${tap.ai.openai.api-key:}")
    private String deepseekApiKey;

    @Value("${tap.ai.openai.base-url:https://api.deepseek.com/v1}")
    private String deepseekBaseUrl;

    @Value("${tap.ai.openai.model:deepseek-chat}")
    private String deepseekModel;

    private static final Gson gsonInstance = new Gson();

    private final OkHttpClient aiHttpClient = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build();

    /**
     * 将Markdown格式的文本转换为HTML
     * @param markdown Markdown格式的文本
     * @return HTML格式的文本
     */
    private String convertMarkdownToHtml(String markdown) {
        if (markdown == null || markdown.isEmpty()) {
            return "";
        }

        String html = markdown;

        // 处理标题
        html = html.replaceAll("# (.*?)(?=\\n|$)", "<h1 style=\"color: skyblue;\">$1</h1>");
        html = html.replaceAll("## (.*?)(?=\\n|$)", "<h2>$1</h2>");
        html = html.replaceAll("### (.*?)(?=\\n|$)", "<h3>$1</h3>");
        html = html.replaceAll("#### (.*?)(?=\\n|$)", "<h4>$1</h4>");

        // 处理粗体和斜体
        html = html.replaceAll("\\*\\*(.*?)\\*\\*", "<strong>$1</strong>");
        html = html.replaceAll("\\*(.*?)\\*", "<em>$1</em>");

        // 处理列表
        Pattern listPattern = Pattern.compile("^(\\d+\\. .*)$|^(- .*)$", Pattern.MULTILINE);
        Matcher listMatcher = listPattern.matcher(html);
        StringBuffer listBuffer = new StringBuffer();

        while (listMatcher.find()) {
            String listItem = listMatcher.group();
            if (listItem.startsWith("- ")) {
                // 无序列表
                listMatcher.appendReplacement(listBuffer, "<li>" + listItem.substring(2) + "</li>");
            } else {
                // 有序列表
                listMatcher.appendReplacement(listBuffer, "<li>" + listItem.substring(listItem.indexOf(' ') + 1) + "</li>");
            }
        }
        listMatcher.appendTail(listBuffer);
        html = listBuffer.toString();

        // 将连续的列表项包装在<ul>或<ol>标签中
        html = html.replaceAll("(<li>.*?</li>)\\n(<li>.*?</li>)", "$1$2");
        html = html.replaceAll("(<li>\\d+\\..*?</li>)+", "<ol>$0</ol>");
        html = html.replaceAll("(<li>[^\\d].*?</li>)+", "<ul>$0</ul>");

        // 处理代码块
        StringBuffer codeBuffer = new StringBuffer();
        Pattern codePattern = Pattern.compile("```([\\s\\S]*?)```");
        Matcher codeMatcher = codePattern.matcher(html);

        while (codeMatcher.find()) {
            String codeContent = codeMatcher.group(1);
            codeMatcher.appendReplacement(codeBuffer,
                "<pre><code>" + codeContent.replace("$", "\\$") + "</code></pre>");
        }
        codeMatcher.appendTail(codeBuffer);
        html = codeBuffer.toString();

        // 处理行内代码
        html = html.replaceAll("`([^`]+)`", "<code>$1</code>");

        // 处理段落和换行
        html = html.replaceAll("(?m)^(?!<[hluoc])(.+)$", "<p>$1</p>");
        html = html.replaceAll("\n\n", "<br>");

        return html;
    }

    @GetMapping("/api/experiment")
    public List<Score> getUserScores() {
        String username = "2019443672";
        List<Score> userScores = scoreService.findPerExperimentSumScoresByUsername(username);
        return userScores;
    }


   //根据实验ID和学生id获取当前实验的平均抄袭率
    public double getPlagiarismRate(int studentId, int experimentId) {

        String pla = scoreService.getexperimentPlagiarismRate(studentId, experimentId);
        double averagePlagiarismRate = calculateAveragePlagiarismRate(pla);
        return averagePlagiarismRate;
    }

    @GetMapping("/api/experiments1")
    public List<Experiment> getExperiments() {
        return experimentService.findAllExperiments();
    }

    @GetMapping("/api/experiments")
    public ResponseEntity<Map<String, Object>> getExperimentList(HttpServletRequest request) {
        Map<String, Object> response = new HashMap<>();

        System.out.println("获取实验列表方法已启动！！！！！");
        try {
            // 从 Session 中获取当前用户名
            UserEntity currentUser = studentSessionResolver.requireStudent(request);
            String currentUsername;  // 固定用户名用于测试
            currentUsername = currentUser.getUsername();
            String currentStudentId = studentSessionResolver.requireStudentId(request);

            // 如果用户未登录，返回错误信息
            if (currentStudentId == null) {
                response.put("success", false);
                response.put("message", "用户未登录或会话已过期");
                return ResponseEntity.ok(response);
            }

            // 获取所有实验
            List<Experiment> experiments = experimentService.findAllExperiments();
            String scoreLookupUsername = (currentUsername != null && !currentUsername.isBlank())
                    ? currentUsername
                    : currentStudentId;

            // 获取当前用户的所有成绩记录
            Map<Integer, Score> userScoresByExperimentId = scoreService.findPerExperimentSumScoresByUsername(scoreLookupUsername)
                    .stream()
                    .collect(Collectors.toMap(Score::getExperiment_id, score -> score, (existing, replacement) -> existing));
            System.out.println("userScoresByExperimentId:" + userScoresByExperimentId);

            StudentController studentController = applicationContext.getBean(StudentController.class);
            Integer studentId = Integer.valueOf(currentStudentId);
            if (userScoresByExperimentId.isEmpty() && studentId != null) {
                String studentIdStr = String.valueOf(studentId);
                userScoresByExperimentId = scoreService.findPerExperimentSumScoresByUsername(studentIdStr)
                        .stream()
                        .collect(Collectors.toMap(Score::getExperiment_id, score -> score, (existing, replacement) -> existing));
                System.out.println("使用studentId查询成绩: " + userScoresByExperimentId);
            }


            // 获取 StudentCodeController 实例
            StudentCodeController studentCodeController = applicationContext.getBean(StudentCodeController.class);

            // 转换实验列表为前端所需的数据格式
            List<Map<String, Object>> experimentDataList = new ArrayList<>();

            for (Experiment experiment : experiments) {
                Map<String, Object> experimentData = new HashMap<>();
                int experimentId = experiment.getExperiment_id();

                // 获取学生的AIRemark
                ResponseEntity<Map<String, Object>> aiRemarkResponse = studentController.getAIRemark(studentId, experimentId);
                Map<String, Object> aiRemarkData = aiRemarkResponse.getBody();

                String aiComment = "暂时还没有生成AI点评哦，请耐心等待.......";
                if (aiRemarkData != null && (Boolean) aiRemarkData.getOrDefault("success", false)) {
                    // 从data字段获取AIRemarks对象
                    Object dataObj = aiRemarkData.get("data");
                    if (dataObj != null && dataObj instanceof com.cqust.ai_server.entity.AIRemarks) {
                        com.cqust.ai_server.entity.AIRemarks aiRemarks = (com.cqust.ai_server.entity.AIRemarks) dataObj;
                        String remarkContent = aiRemarks.getAiremark();
                        if (remarkContent != null && !remarkContent.isEmpty()) {
                            aiComment = remarkContent; // 返回原始Markdown，前端负责渲染
                            System.out.println("获取到学生ID: " + studentId + "，实验ID: " + experimentId + "的AI点评");
                        }
                    }
                } else {
                    System.out.println("未找到学生ID: " + studentId + "，实验ID: " + experimentId + "的AI点评");
                }

                // 基本实验信息
                experimentData.put("id", experimentId);
                experimentData.put("name", experiment.getName());
                experimentData.put("deadline", experiment.getDeadline());
                experimentData.put("description", experiment.getDescribe());

                // 解析requirements字段
                experimentData.put("requirements", parseRequirements(experiment.getRequirements()));

                // 如果成功获取了学生ID，调用 StudentCodeController 获取学生代码
                String studentCode = "示例代码111111";  // 默认代码
                if (studentId != null) {
                    try {
                        ResponseEntity<Map<String, Object>> codeResponse = studentCodeController.getStudentExperimentCode(studentId, experimentId);
                        Map<String, Object> codeData = codeResponse.getBody();

                        if (codeData != null && (Boolean) codeData.getOrDefault("success", false)) {
                            // 修复这里的类型转换问题 - StudentCode对象不能直接转换为Map
                            Object codeObj = codeData.get("code");
                            System.out.println("获取到的代码对象类型: " + (codeObj != null ? codeObj.getClass().getName() : "null"));

                            if (codeObj instanceof com.cqust.ai_server.entity.StudentCode) {
                                // 正确处理StudentCode对象
                                com.cqust.ai_server.entity.StudentCode studentCodeObj = (com.cqust.ai_server.entity.StudentCode) codeObj;
                                studentCode = studentCodeObj.getCode();
                                System.out.println("获取到学生代码，长度: " + (studentCode != null ? studentCode.length() : 0));
                            } else if (codeObj instanceof Map) {
                                // 如果返回的是Map，也处理一下
                                @SuppressWarnings("unchecked")
                                Map<String, Object> codeMap = (Map<String, Object>) codeObj;
                                if (codeMap.containsKey("code")) {
                                    studentCode = (String) codeMap.get("code");
                                    System.out.println("从Map获取到学生代码，长度: " + (studentCode != null ? studentCode.length() : 0));
                                }
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        System.out.println("获取学生代码时出错: " + e.getMessage());
                    }
                }

                // 设置代码及AI点评内容
                experimentData.put("code", studentCode);
                experimentData.put("aiComment", aiComment);



//                // 获取学生的实验报告
//                if (studentId != null) {
//                    try {
//                        // 注意这里需要注入ReportService
//                        ReportService reportService = applicationContext.getBean(ReportService.class);
//                        ExperimentReport latestReport = reportService.getLatestReportForExperiment(studentId.toString(), experimentId);
//
//                        if (latestReport != null) {
//                            // 报告存在，转换为Base64字符串以便前端处理
//                            String reportBase64 = Base64.getEncoder().encodeToString(latestReport.getReportData());
//                            experimentData.put("report", reportBase64);
//                            experimentData.put("reportName", latestReport.getReportName());
//                            experimentData.put("reportId", latestReport.getReportId());
//                            experimentData.put("reportTime", latestReport.getGeneratedTime());
//                        } else {
//                            // 报告不存在
//                            experimentData.put("report", null);
//                            experimentData.put("reportName", null);
//                            experimentData.put("reportId", null);
//                            experimentData.put("reportTime", null);
//                        }
//                    } catch (Exception e) {
//                        e.printStackTrace();
//                        System.out.println("获取学生报告时出错: " + e.getMessage());
//                        experimentData.put("report", null);
//                    }
//                } else {
//                    experimentData.put("report", null);
//                }

                experimentData.put("report","示例报告11111111");
                // 获取当前用户的提交信息
                Score userScore = userScoresByExperimentId.get(experimentId);

                if (userScore != null) {
                    experimentData.put("status", "completed");
                    experimentData.put("submitTime", userScore.getSubmit_time());
                    experimentData.put("score", userScore.getScore());
                    // 计算平均查重率
                    double avgPlagiarismRate = getPlagiarismRate(studentId, experimentId);
                    avgPlagiarismRate = Math.round(avgPlagiarismRate * 100) / 100.0;
                    experimentData.put("plagiarismRate", avgPlagiarismRate);
                } else {
                    experimentData.put("status", "not_started");
                    experimentData.put("submitTime", null);
                    experimentData.put("score", 0);
                    experimentData.put("plagiarismRate", 0.0);
                }

                experimentDataList.add(experimentData);
            }

            response.put("success", true);
            response.put("data", experimentDataList);

        } catch (Exception e) {
            e.printStackTrace();
            response.put("success", false);
            response.put("message", "获取实验列表失败: " + e.getMessage());
        }

        return ResponseEntity.ok(response);
    }

    // 新方法，根据experiment_id查找数据
    @GetMapping("/api/experiments/{experimentId}")
    public ResponseEntity<Map<String, Object>> getExperimentById(@PathVariable int experimentId, HttpServletRequest request) {
        ResponseEntity<Map<String, Object>> allExperimentsResponse = getExperimentList(request);
        Map<String, Object> allExperimentsData = allExperimentsResponse.getBody();

        if (allExperimentsData != null && allExperimentsData.containsKey("data")) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> experimentDataList = (List<Map<String, Object>>) allExperimentsData.get("data");
            Optional<Map<String, Object>> targetExperiment = experimentDataList.stream()
                    .filter(data -> data.get("id").equals(experimentId))
                    .findFirst();

            if (targetExperiment.isPresent()) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("data", targetExperiment.get());
                return ResponseEntity.ok(response);
            } else {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "未找到指定 experiment_id 的实验数据");
                return ResponseEntity.ok(response);
            }
        } else {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "获取实验列表时出错");
            return ResponseEntity.ok(response);
        }
    }

    private List<String> parseRequirements(String requirements) {
        if (requirements == null || requirements.isEmpty()) {
            return new ArrayList<>();
        }

        // 这里简单假设requirements是用换行符分隔的，你可以根据实际情况修改
        return Arrays.asList(requirements.split("\\r?\\n"));
    }

    // 计算平均查重率
    private double calculateAveragePlagiarismRate(String plagiarismRates) {
        if (plagiarismRates == null || plagiarismRates.isEmpty()) {
            return 0.0;
        }

        String[] rates = plagiarismRates.split(",");
        double sum = 0.0;
        int count = 0;

        for (String rate : rates) {
            // 跳过"-"值
            if (!rate.trim().equals("-")) {
                try {
                    // 移除百分号并转换为double
                    String cleanRate = rate.replace("%", "").trim();
                    sum += Double.parseDouble(cleanRate);

                } catch (NumberFormatException e) {
                    // 忽略无法解析的值
                    continue;
                }
            }
            count++;
        }

        return count > 0 ? sum / count : 0.0;
    }

    // 检查截止日期是否已过
    private boolean isDeadlinePassed(String deadlineStr) {
        try {
            // 支持多种日期格式解析
            List<String> dateFormats = Arrays.asList("yyyy-MM-dd", "yyyy/MM/dd", "MM/dd/yyyy");
            for (String format : dateFormats) {
                try {
                    SimpleDateFormat sdf = new SimpleDateFormat(format);
                    Date deadline = sdf.parse(deadlineStr);
                    if (deadline.before(new Date())) {
                        return true;
                    }
                } catch (Exception e) {
                    // 格式不匹配，继续尝试下一个格式
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    @GetMapping("/experiments/{id}")
    public ResponseEntity<Map<String, Object>> getExperiment(@PathVariable int id) {
        Map<String, Object> response = new HashMap<>();

        try {
            Experiment experiment = experimentService.findExperimentById(id);

            if (experiment == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
            }

            // 处理requirements字符串，转换为列表
            if (experiment.getRequirements() != null) {
                List<String> requirementsList = Arrays.asList(experiment.getRequirements().split("\n"));
                experiment.setRequirementsList(requirementsList);
            }

            return ResponseEntity.ok(Map.of(
                    "id", experiment.getExperiment_id(),
                    "name", experiment.getName(),
                    "deadline", experiment.getDeadline(),
                    "description", experiment.getDescribe(),
                    "requirements", experiment.getRequirementsList(),
                    "status", experiment.getStatus(),
                    "submitTime", experiment.getSubmitTime(),
                    "score", experiment.getScore(),
                    "plagiarismRate", experiment.getPlagiarismRate()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    @GetMapping("/student/{username}/experiments")
    public ResponseEntity<List<Map<String, Object>>> getStudentExperiments(
            @PathVariable String username,
            HttpServletRequest request
    ) {
        try {
            UserEntity currentUser = studentSessionResolver.requireStudent(request);
            String currentUsername = currentUser.getUsername();
            if (currentUsername == null || currentUsername.isBlank()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null);
            }
            if (!currentUsername.equals(username)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(null);
            }
            List<Map<String, Object>> experiments = experimentService.findExperimentsByUsername(currentUsername);
            return ResponseEntity.ok(experiments);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    @PostMapping("/experiments/{id}/submit")
    public ResponseEntity<Map<String, Object>> submitExperiment(
            @PathVariable int id,
            @RequestParam(required = false) String username,
            @RequestBody Map<String, String> submission,
            HttpServletRequest request) {

        Map<String, Object> response = new HashMap<>();

        try {
            UserEntity currentUser = studentSessionResolver.requireStudent(request);
            String currentUsername = currentUser.getUsername();
            if (currentUsername == null || currentUsername.isBlank()) {
                response.put("status", "error");
                response.put("message", "用户未登录");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
            }
            if (username != null && !username.isBlank() && !currentUsername.equals(username)) {
                response.put("status", "error");
                response.put("message", "无权提交其他用户的实验");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
            }
            String code = submission.get("code");
            String report = submission.get("report");

            boolean success = experimentService.submitExperiment(id, currentUsername, code, report);

            if (success) {
                response.put("status", "success");
                response.put("message", "实验提交成功");
                return ResponseEntity.ok(response);
            } else {
                response.put("status", "error");
                response.put("message", "实验提交失败");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "实验提交失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * 根据学生ID和实验ID获取该实验的推荐练习
     * @param studentId 学生ID
     * @param experimentId 实验ID
     * @return 推荐练习列表
     */
    private List<Map<String, Object>> getRecommendedPracticesByExperiment(int studentId, int experimentId) {
        AISuggestedProblem suggestedProblem = aiSuggestedProblemService.findByStudentIdAndExperimentId(studentId, experimentId);
        if (suggestedProblem != null) {
            List<Map<String, Object>> recommendedPractices = aiSuggestedProblemService.parseRecommendedPractices(suggestedProblem.getContent());
            
            // 处理每个练习，确保数据格式正确
            for (Map<String, Object> practice : recommendedPractices) {
                if (practice.containsKey("type") && "problem".equals(practice.get("type"))) {
                    System.out.println("处理题目: " + practice.get("number") + ". " + practice.get("title"));
                    System.out.println("URL: " + practice.get("url"));
                }
            }
            
            System.out.println("获取到学生ID: " + studentId + "，实验ID: " + experimentId + "的推荐练习，数量: " + recommendedPractices.size());
            return recommendedPractices;
        } else {
            System.out.println("未找到学生ID: " + studentId + "，实验ID: " + experimentId + "的推荐练习");
            return new ArrayList<>();
        }
    }

    /**
     * 获取学生的所有推荐练习
     * @param studentId 学生ID
     * @return 响应实体，包含所有推荐练习列表
     */
    @GetMapping("/api/student/{studentId}/recommendedPractices")
    public ResponseEntity<Map<String, Object>> getAllRecommendedPracticesByStudent(
            @PathVariable int studentId,
            HttpServletRequest request) {
        String authorizedStudentId = studentSessionResolver.requireAuthorizedStudentId(String.valueOf(studentId), request);
        return getAllRecommendedPracticesByStudent(Integer.parseInt(authorizedStudentId));
    }

    public ResponseEntity<Map<String, Object>> getAllRecommendedPracticesByStudent(int studentId) {
        Map<String, Object> response = new HashMap<>();

        try {
            // 优先尝试使用新的智能LeetCode推荐系统
            try {
                List<LeetCodeRecommendItem> leetCodeItems = leetCodeRecommendationService.generateRecommendationSync(studentId, 20);
                if (leetCodeItems == null || leetCodeItems.isEmpty()) {
                    int syncedCount = warmupLeetCodeDataIfNeeded();
                    if (syncedCount > 0) {
                        leetCodeItems = leetCodeRecommendationService.generateRecommendationSync(studentId, 20);
                    }
                }
                if (leetCodeItems != null && !leetCodeItems.isEmpty()) {
                    List<Map<String, Object>> allPractices = new ArrayList<>();
                    
                    for (LeetCodeRecommendItem item : leetCodeItems) {
                        Map<String, Object> practice = new HashMap<>();
                        practice.put("type", "leetcode_problem");
                        practice.put("id", item.getProblemId());
                        practice.put("problemId", item.getProblemId()); // 添加problemId字段
                        practice.put("title", item.getProblem().getTitleMain());
                        practice.put("difficulty", item.getProblem().getDifficulty());
                        practice.put("estimatedMinutes", item.getProblem().getEstimatedMinutes());
                        practice.put("score", item.getScoreTotal());
                        practice.put("reason", item.getReasonText());
                        practice.put("problemCode", item.getProblem().getProblemCode());
                        practice.put("source", "leetcode_recommendation");
                        // 不再需要URL字段，因为使用内置练习页面
                        
                        allPractices.add(practice);
                    }
                    
                    response.put("success", true);
                    response.put("data", allPractices);
                    response.put("source", "leetcode_recommendation");
                    System.out.println("使用LeetCode推荐系统为学生ID: " + studentId + "获取推荐，数量: " + allPractices.size());
                    return ResponseEntity.ok(response);
                }
            } catch (Exception e) {
                System.out.println("LeetCode推荐系统暂不可用，回退到旧系统: " + e.getMessage());
            }

            // 回退到旧的推荐系统
            List<AISuggestedProblem> suggestedProblems = aiSuggestedProblemService.findByStudentId(studentId);
            List<Map<String, Object>> allPractices = new ArrayList<>();

            System.out.println("获取推荐题目："+suggestedProblems);
            if (suggestedProblems != null && !suggestedProblems.isEmpty()) {
                for (AISuggestedProblem problem : suggestedProblems) {
                    List<Map<String, Object>> practices = aiSuggestedProblemService.parseRecommendedPractices(problem.getContent());
                    
                    for (Map<String, Object> practice : practices) {
                        // 添加实验ID信息
                        practice.put("experimentId", problem.getExperimentId());
                        practice.put("source", "legacy_recommendation");
                        
                        // 根据返回的类型进行处理
                        if (practice.containsKey("type")) {
                            String type = (String) practice.get("type");
                            if ("problem".equals(type)) {
                                // 这是一个题目，已经包含了number, title, description和url
                                System.out.println("处理题目: " + practice.get("number") + ". " + practice.get("title"));
                                System.out.println("URL: " + practice.get("url"));
                            } else if ("introduction".equals(type)) {
                                // 这是介绍部分
                                System.out.println("处理介绍部分");
                            } else if ("raw".equals(type)) {
                                // 这是原始内容
                                System.out.println("处理原始内容");
                            }
                        } else if (practice.containsKey("originalContent")) {
                            // 旧版格式，保持兼容
                            System.out.println("处理原始内容(兼容模式)");
                        }
                        
                        allPractices.add(practice);
                    }
                }

                response.put("success", true);
                response.put("data", allPractices);
                response.put("source", "legacy_recommendation");
                System.out.println("获取到学生ID: " + studentId + "的所有推荐练习，数量: " + allPractices.size());
            } else {
                response.put("success", true);
                response.put("data", new ArrayList<>());
                response.put("source", "none");
                System.out.println("未找到学生ID: " + studentId + "的推荐练习");
            }
        } catch (Exception e) {
            e.printStackTrace();
            response.put("success", false);
            response.put("message", "获取推荐练习失败: " + e.getMessage());
        }

        return ResponseEntity.ok(response);
    }

    private int warmupLeetCodeDataIfNeeded() {
        if (leetCodeDataWarmupAttempted) {
            return 0;
        }

        synchronized (this) {
            if (leetCodeDataWarmupAttempted) {
                return 0;
            }
            leetCodeDataWarmupAttempted = true;
            try {
                int synced = leetCodeSyncService.syncProblemsFromJson(LEETCODE_CLEANED_DATA_PATH);
                System.out.println("LeetCode 数据预热完成，导入数量: " + synced);
                return synced;
            } catch (Exception e) {
                System.out.println("LeetCode 数据预热失败: " + e.getMessage());
                return 0;
            }
        }
    }

    /**
     * 获取当前登录用户的所有推荐练习
     * @param request HTTP请求
     * @return 响应实体，包含所有推荐练习列表
     */
    @GetMapping("/api/current/recommendedPractices")
    public ResponseEntity<Map<String, Object>> getCurrentUserRecommendedPractices(HttpServletRequest request) {
        Map<String, Object> response = new HashMap<>();

        try {
            String studentId = studentSessionResolver.requireStudentId(request);
            return getAllRecommendedPracticesByStudent(Integer.parseInt(studentId));

        } catch (Exception e) {
            e.printStackTrace();
            response.put("success", false);
            response.put("message", "获取推荐练习失败: " + e.getMessage());
            return ResponseEntity.ok(response);
        }
    }

    /**
     * 获取特定学生特定实验的推荐练习
     * @param studentId 学生ID
     * @param experimentId 实验ID
     * @return 响应实体，包含推荐练习列表
     */
    @GetMapping("/api/student/{studentId}/experiment/{experimentId}/recommendedPractices")
    public ResponseEntity<Map<String, Object>> getRecommendedPracticesForExperiment(
            @PathVariable int studentId,
            @PathVariable int experimentId,
            HttpServletRequest request) {

        Map<String, Object> response = new HashMap<>();

        try {
            String authorizedStudentId = studentSessionResolver.requireAuthorizedStudentId(String.valueOf(studentId), request);
            studentId = Integer.parseInt(authorizedStudentId);
            List<Map<String, Object>> recommendedPractices = getRecommendedPracticesByExperiment(studentId, experimentId);
            
            // 对返回的数据进行处理，确保前端能正确显示
            for (Map<String, Object> practice : recommendedPractices) {
                if (practice.containsKey("type") && "problem".equals(practice.get("type"))) {
                    System.out.println("返回题目: " + practice.get("number") + ". " + practice.get("title"));
                    System.out.println("URL链接: " + practice.get("url"));
                }
            }

            response.put("success", true);
            response.put("data", recommendedPractices);
            response.put("studentId", studentId);
            response.put("experimentId", experimentId);

        } catch (Exception e) {
            e.printStackTrace();
            response.put("success", false);
            response.put("message", "获取推荐练习失败: " + e.getMessage());
        }

        return ResponseEntity.ok(response);
    }


    //根据实验提交id查询学生实验提交详情数据
    /**
     * 获取学生实验提交详情
     * @param submissionId 提交ID，格式为"学号-实验ID"，例如：2019443672-1
     * @return 包含提交详情的响应
     */
    @GetMapping("/api/submissions/{submissionId}")
    public ResponseEntity<Map<String, Object>> getSubmissionDetail(
            @PathVariable String submissionId,
            HttpServletRequest request) {
        Map<String, Object> response = new HashMap<>();

        try {
            legacySessionAccessResolver.requireTeacherOrAdmin(request);
            // 解析submissionId (格式: studentId-experimentId)
            String[] parts = submissionId.split("-");
            if (parts.length != 2) {
                response.put("success", false);
                response.put("message", "提交ID格式不正确，应为'学号-实验ID'");
                return ResponseEntity.badRequest().body(response);
            }

            int studentId = Integer.parseInt(parts[0]);
            int experimentId = Integer.parseInt(parts[1]);

            System.out.println("提交id信息"+studentId+"---"+experimentId);

            // 获取学生信息
            Student student = studentService.findByStudentId(studentId);
            if (student == null) {
                response.put("success", false);
                response.put("message", "未找到对应的学生信息");
                return ResponseEntity.ok(response);
            }

            String username = student.getUsername();

            // 获取实验信息
            Experiment experiment = experimentService.findExperimentById(experimentId);
            if (experiment == null) {
                response.put("success", false);
                response.put("message", "未找到对应的实验信息");
                return ResponseEntity.ok(response);
            }

            // 获取提交记录
            SubmissionDetailEntity submission = submissionDao.findDetailByUsernameAndExperimentId(username, experimentId);
            StudentCode studentCode = studentCodeService.findCodeByStudentIdAndExperimentId(studentId, experimentId);
            Score score = scoreService.findByUsernameAndExperimentNum(String.valueOf(studentId), experiment.getNum());
            if (score == null && username != null && !username.isBlank()) {
                score = scoreService.findByUsernameAndExperimentNum(username, experiment.getNum());
            }

            if (submission == null && studentCode == null && score == null) {
                response.put("success", false);
                response.put("message", "No submission data found for this student and experiment");
                return ResponseEntity.ok(response);
            }

            AIRemarks aiRemarks = aiRemarksService.getAIRemarkByStudentAndExperiment(studentId, experimentId);



            // 构建响应数据
            String mergedCode = submission != null ? submission.getCode() : null;
            if ((mergedCode == null || mergedCode.isBlank()) && studentCode != null) {
                mergedCode = studentCode.getCode();
            }
            Integer mergedScore = score != null ? score.getScore() : null;
            Date mergedSubmitTime = score != null ? score.getSubmit_time() : null;

            response.put("studentId", student.getStudent_id());
            response.put("studentName", student.getName());
            response.put("experimentId", experimentId);
            response.put("experimentName", experiment.getName());
            response.put("submitTime", mergedSubmitTime);
            response.put("class", student.getClass_name());
            response.put("code", mergedCode != null ? mergedCode : "");
            response.put("date", mergedSubmitTime);
            response.put("report", "# 线性表实验报告\\n\\n## 实验目的\\n掌握线性表的基本操作和实现方法。\\n\\n## 实验环境\\nVisual Studio Code, JavaScript\\n\\n## 实验内容\\n实现线性表的基本操作，包括增删改查等功能。\\n\\n## 实验步骤\\n1. 首先定义线性表的结构\\n2. 实现增加元素的方法\\n3. 实现删除元素的方法\\n4. 实现查找元素的方法\\n\\n## 实验结果\\n成功实现了线性表的各项功能，测试通过。\\n\\n## 实验总结\\n通过本次实验，我深入理解了线性表的工作原理和实现方法。");

//            Map<String, Object> submissionData = new HashMap<>();
//            submissionData.put("submissionId", submissionId);
//            submissionData.put("studentId", studentId);
//            submissionData.put("studentName", student.getName());
//            submissionData.put("studentUsername", username);
//            submissionData.put("className", student.getClass_name());
//            submissionData.put("experimentId", experimentId);
//            submissionData.put("experimentName", experiment.getName());
//            submissionData.put("deadline", experiment.getDeadline());

            double avgPlagiarismRate = 0.0;
            if (score != null) {
                avgPlagiarismRate = getPlagiarismRate(studentId, experimentId);
                avgPlagiarismRate = Math.round(avgPlagiarismRate * 100) / 100.0;
            }
            response.put("plagiarismRate", avgPlagiarismRate);
            response.put("score", mergedScore);
            response.put("status", mergedScore != null && mergedScore > 0 ? "graded"
                    : ((mergedCode != null && !mergedCode.isBlank()) ? "submitted" : "not_started"));
//            submissionData.put("code", submission.getCode());
//            submissionData.put("report", submission.getReport());
//            submissionData.put("submitTime", submission.getSubmit_time());

            // 添加成绩信息
//            if (score != null) {
//                submissionData.put("score", score.getScore());
//                submissionData.put("status", score.getStatus());
//                submissionData.put("plagiarismRate", score.getPlagiarism_rate());
//            } else {
//                submissionData.put("score", 0);
//                submissionData.put("status", "not_scored");
//                submissionData.put("plagiarismRate", 0.0);
//            }

            // 添加AI点评
            if (aiRemarks != null) {
                response.put("aiRemarks", aiRemarks.getAiremark());
            } else {
                response.put("aiRemarks", null);
            }

            response.put("success", true);

            return ResponseEntity.ok(response);

        } catch (NumberFormatException e) {
            response.put("success", false);
            response.put("message", "提交ID格式错误: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "获取提交详情失败: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    @GetMapping("/api/student/learning-analysis")
    public ResponseEntity<?> getLearningAnalysis(HttpServletRequest request) {
        try {
            HttpSession session = request.getSession(false);
            String username = studentSessionResolver.requireStudentId(request);
            if (username == null) {
                return ResponseEntity.ok(Map.of("success", false, "message", "用户未登录"));
            }
            // 通过username查找studentId
            StudentController studentController = applicationContext.getBean(StudentController.class);
            ResponseEntity<Map<String, Object>> sidResp = ResponseEntity.ok(Map.of("success", true, "studentId", username));
            Map<String, Object> sidData = sidResp.getBody();
            String studentId = null;
            if (sidData != null && Boolean.TRUE.equals(sidData.get("success"))) {
                Object sid = sidData.get("studentId");
                studentId = sid != null ? String.valueOf(sid) : null;
            }
            if (studentId == null) {
                return ResponseEntity.ok(Map.of("success", false, "message", "未找到学生信息"));
            }
            Map<String, Object> profile = profileService.getStudentProfile(studentId);
            if (profile.containsKey("error")) {
                return ResponseEntity.ok(Map.of("success", false, "message", profile.get("error")));
            }
            return ResponseEntity.ok(profile);
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("success", false, "message", "获取学习分析失败: " + e.getMessage()));
        }
    }

    /**
     * 按需生成/刷新单个实验的AI点评
     * 逻辑：如果 force=false 且DB已有缓存，直接返回；否则调用DeepSeek生成并存入DB
     */
    @PostMapping("/api/experiments/{experimentId}/ai-comment/generate")
    public ResponseEntity<Map<String, Object>> generateAiComment(
            @PathVariable int experimentId,
            @RequestParam(defaultValue = "false") boolean force,
            HttpServletRequest request) {

        Map<String, Object> response = new HashMap<>();
        try {
            HttpSession session = request.getSession(false);
            String currentUsername = studentSessionResolver.requireStudentId(request);
            if (currentUsername == null) {
                response.put("success", false);
                response.put("message", "用户未登录");
                return ResponseEntity.ok(response);
            }

            // 获取studentId
            StudentController studentController = applicationContext.getBean(StudentController.class);
            ResponseEntity<Map<String, Object>> sidResp = ResponseEntity.ok(Map.of("success", true, "studentId", Integer.valueOf(currentUsername)));
            Map<String, Object> sidData = sidResp.getBody();
            Integer studentId = null;
            if (sidData != null && Boolean.TRUE.equals(sidData.get("success"))) {
                studentId = (Integer) sidData.get("studentId");
            }
            if (studentId == null) {
                response.put("success", false);
                response.put("message", "未找到学生信息");
                return ResponseEntity.ok(response);
            }

            // 如果不是强制刷新，先查DB缓存
            if (!force) {
                AIRemarks cached = aiRemarksService.getAIRemarkByStudentAndExperiment(studentId, experimentId);
                if (cached != null && cached.getAiremark() != null && !cached.getAiremark().isBlank()) {
                    response.put("success", true);
                    response.put("aiComment", cached.getAiremark());
                    response.put("source", "cache");
                    return ResponseEntity.ok(response);
                }
            }

            // 获取学生代码
            StudentCodeController codeCtrl = applicationContext.getBean(StudentCodeController.class);
            ResponseEntity<Map<String, Object>> codeResp = codeCtrl.getStudentExperimentCode(studentId, experimentId);
            Map<String, Object> codeData = codeResp.getBody();
            String code = "";
            if (codeData != null && Boolean.TRUE.equals(codeData.get("success"))) {
                Object codeObj = codeData.get("code");
                if (codeObj instanceof com.cqust.ai_server.entity.StudentCode) {
                    code = ((com.cqust.ai_server.entity.StudentCode) codeObj).getCode();
                }
            }
            if (code == null || code.isBlank()) {
                response.put("success", false);
                response.put("message", "该实验暂无代码提交，无法生成AI点评");
                return ResponseEntity.ok(response);
            }

            // 获取实验名称
            Experiment experiment = experimentService.findExperimentById(experimentId);
            String expName = experiment != null ? experiment.getName() : "实验" + experimentId;

            // 获取学生姓名
            Student student = studentService.findByStudentId(studentId);
            String studentName = student != null ? student.getName() : "同学";

            // 调用DeepSeek生成AI点评
            String aiComment = callDeepSeekForCodeReview(code, expName, studentName);
            if (aiComment == null || aiComment.isBlank()) {
                response.put("success", false);
                response.put("message", "AI点评生成失败，请稍后重试");
                return ResponseEntity.ok(response);
            }

            // 保存到DB
            AIRemarks remarks = new AIRemarks(studentId, studentName, experimentId, expName, aiComment);
            aiRemarksService.saveOrUpdateAIRemark(remarks);

            response.put("success", true);
            response.put("aiComment", aiComment);
            response.put("source", "deepseek");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            e.printStackTrace();
            response.put("success", false);
            response.put("message", "生成AI点评失败: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * 调用DeepSeek为单个实验的代码生成AI点评（Markdown格式）
     */
    private String callDeepSeekForCodeReview(String code, String experimentName, String studentName) throws Exception {
        if (deepseekApiKey == null || deepseekApiKey.isBlank()) {
            return null;
        }

        // 截断过长的代码（保留前6000字符）
        if (code.length() > 6000) {
            code = code.substring(0, 6000) + "\n... (代码过长，已截断)";
        }

        String systemPrompt = "你是一位经验丰富的高校数据结构课程助教，负责对学生在PTA编程平台上提交的C语言代码进行专业点评。\n\n"
                + "## 点评要求\n"
                + "1. 使用Markdown格式输出\n"
                + "2. 语气友善、鼓励为主，同时指出不足\n"
                + "3. 必须结合代码的具体内容进行分析，不要泛泛而谈\n"
                + "4. 如果代码中包含多道题目（以'第X题如下'分隔），请逐题点评\n\n"
                + "## 输出结构（严格遵守）\n"
                + "### 📊 总体评价\n"
                + "（2-3句话概括代码整体质量、完成度）\n\n"
                + "### 📝 逐题分析\n"
                + "（针对每道题：指出算法思路是否正确、代码风格、潜在问题）\n\n"
                + "### ✅ 亮点\n"
                + "（列出代码中做得好的地方，如算法选择、边界处理等）\n\n"
                + "### ⚠️ 改进建议\n"
                + "（具体的改进方向，如内存管理、代码可读性、算法优化等）\n\n"
                + "### 💡 学习建议\n"
                + "（针对该实验涉及的知识点，给出1-2条可执行的学习建议）\n";

        String userPrompt = "请对以下学生的代码进行专业点评：\n\n"
                + "**学生**: " + studentName + "\n"
                + "**实验**: " + experimentName + "\n\n"
                + "```c\n" + code + "\n```";

        JsonObject reqBody = new JsonObject();
        reqBody.addProperty("model", deepseekModel);
        reqBody.addProperty("stream", false);
        reqBody.addProperty("max_tokens", 2000);
        reqBody.addProperty("temperature", 0.7);

        JsonArray messages = new JsonArray();
        JsonObject sysMsg = new JsonObject();
        sysMsg.addProperty("role", "system");
        sysMsg.addProperty("content", systemPrompt);
        messages.add(sysMsg);
        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        userMsg.addProperty("content", userPrompt);
        messages.add(userMsg);
        reqBody.add("messages", messages);

        okhttp3.RequestBody body = okhttp3.RequestBody.create(
                gsonInstance.toJson(reqBody),
                okhttp3.MediaType.parse("application/json; charset=utf-8"));

        Request httpReq = new Request.Builder()
                .url(deepseekBaseUrl + "/chat/completions")
                .addHeader("Authorization", "Bearer " + deepseekApiKey)
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build();

        try (Response httpResp = aiHttpClient.newCall(httpReq).execute()) {
            if (!httpResp.isSuccessful() || httpResp.body() == null) {
                System.err.println("[ApiController] DeepSeek请求失败: " + httpResp.code());
                return null;
            }
            String respStr = httpResp.body().string();
            JsonObject respJson = JsonParser.parseString(respStr).getAsJsonObject();
            JsonArray choices = respJson.getAsJsonArray("choices");
            if (choices != null && choices.size() > 0) {
                return choices.get(0).getAsJsonObject()
                        .getAsJsonObject("message")
                        .get("content").getAsString().trim();
            }
        }
        return null;
    }

}

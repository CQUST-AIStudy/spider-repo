package com.cqust.ai_server.service.impl;

import com.cqust.ai_server.dao.ExperimentDao;
import com.cqust.ai_server.dao.ScoreDao;
import com.cqust.ai_server.dao.SubmissionDao;
import com.cqust.ai_server.entity.Experiment;
import com.cqust.ai_server.entity.Score;
import com.cqust.ai_server.entity.Submission;
import com.cqust.ai_server.service.ExperimentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.SimpleDateFormat;
import java.util.*;

@Service
public class ExperimentServiceImpl implements ExperimentService {

    @Autowired
    private ExperimentDao experimentDao;

    @Autowired
    private ScoreDao scoreDao;

    @Autowired
    private SubmissionDao submissionDao;

    @Override
    public List<Experiment> findAllExperiments() {
        return experimentDao.findAllExperiments();
    }

    @Override
    public Experiment findExperimentById(int id) {
        return experimentDao.findExperimentById(id);
    }

    @Override
    public Experiment findExperimentByNum(int num) {
        return experimentDao.findExperimentByNum(num);
    }

    @Override
    public List<Experiment> findExperimentsByTeacherId(String teacherId) {
        return experimentDao.findExperimentsByTeacherId(teacherId);
    }

    @Override
    public boolean saveExperiment(Experiment experiment) {
        return experimentDao.saveExperiment(experiment) > 0;
    }

    @Override
    public boolean updateExperiment(Experiment experiment) {
        return experimentDao.updateExperiment(experiment) > 0;
    }

    @Override
    public boolean deleteExperiment(int id) {
        return experimentDao.deleteExperiment(id) > 0;
    }

    @Override
    @Transactional
    public boolean submitExperiment(int id, String username, String code, String report) {
        try {
//            // 获取实验信息
//            Experiment experiment = experimentDao.findExperimentById(id);
//            if (experiment == null) {
//                return false;
//            }
//
//            // 创建提交记录
//            Submission submission = new Submission();
//            submission.setUsername(username);
//            submission.setExperiment_id(id);
//            submission.setCode(code);
//            submission.setReport(report);
//            submission.setSubmit_time(new Date());
//
//            submissionDao.saveSubmission(submission);
//
//            // 更新成绩记录
//            Score score = scoreDao.findByUsernameAndExperimentNum(username, experiment.getNum());
//            if (score == null) {
//                score = new Score();
//                score.setUsername(username);
//                score.setExperiment_id(experiment.getNum());
//                score.setSubmit_time(new Date());
//                score.setStatus("completed");
//                // 初始分数和抄袭率将在后续由教师或AI评分
//                score.setScore(0);
//                score.setPlagiarism_rate(0.0);
//
//                scoreDao.saveScore(score);
//            } else {
//                score.setSubmit_time(new Date());
//                score.setStatus("completed");
//
//                scoreDao.updateScore(score);
//            }

            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public List<Map<String, Object>> findExperimentsByUsername(String username) {
        List<Map<String, Object>> result = new ArrayList<>();
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");

        // 获取所有实验
        List<Experiment> allExperiments = experimentDao.findAllExperiments();

        for (Experiment experiment : allExperiments) {
            // 获取该用户在该实验的成绩信息
            Score score = scoreDao.findByUsernameAndExperimentNum(username, experiment.getNum());

            Map<String, Object> experimentInfo = new HashMap<>();
            experimentInfo.put("id", experiment.getExperiment_id());
            experimentInfo.put("num", experiment.getNum());
            experimentInfo.put("name", experiment.getName());
            experimentInfo.put("deadline", experiment.getDeadline());
            experimentInfo.put("description", experiment.getDescribe());

            // 添加成绩信息
            if (score != null) {
                experimentInfo.put("status", score.getStatus());
                experimentInfo.put("score", score.getScore());
                experimentInfo.put("plagiarismRate", score.getPlagiarism_rate());

                if (score.getSubmit_time() != null) {
                    experimentInfo.put("submitTime", dateFormat.format(score.getSubmit_time()));
                }
            } else {
                experimentInfo.put("status", "not_started");
                experimentInfo.put("score", 0);
                experimentInfo.put("plagiarismRate", 0.0);
            }

            result.add(experimentInfo);
        }

        return result;
    }



}

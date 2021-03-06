/**
 * Copyright (c) 2015 The Opencron Project
 * <p>
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.opencron.server.controller;

import java.util.*;

import org.opencron.common.Constants;
import org.opencron.common.util.DigestUtils;
import org.opencron.common.util.StringUtils;
import org.opencron.common.util.collection.ParamsMap;
import org.opencron.server.domain.Job;
import org.opencron.server.support.OpencronTools;
import org.opencron.server.service.*;
import org.opencron.server.tag.PageBean;
import org.opencron.common.util.CommonUtils;
import org.opencron.server.domain.Agent;
import org.opencron.server.vo.CrontabInfo;
import org.opencron.server.vo.JobInfo;
import org.opencron.server.vo.Status;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import static org.opencron.common.util.CommonUtils.notEmpty;
import static org.opencron.common.util.WebUtils.*;

@Controller
@RequestMapping("job")
public class JobController extends BaseController {

    @Autowired
    private ExecuteService executeService;

    @Autowired
    private JobService jobService;

    @Autowired
    private AgentService agentService;

    @Autowired
    private RecordService recordService;

    @Autowired
    private SchedulerService schedulerService;

    @RequestMapping("view.htm")
    public String view(HttpSession session, HttpServletRequest request, PageBean pageBean, JobInfo job, Model model) {

        model.addAttribute("agents", agentService.getOwnerAgents(session));

        model.addAttribute("jobs", jobService.getAll());
        if (notEmpty(job.getAgentId())) {
            model.addAttribute("agentId", job.getAgentId());
        }
        if (notEmpty(job.getCronType())) {
            model.addAttribute("cronType", job.getCronType());
        }
        if (notEmpty(job.getJobType())) {
            model.addAttribute("jobType", job.getJobType());
        }
        if (notEmpty(job.getRedo())) {
            model.addAttribute("redo", job.getRedo());
        }
        jobService.getJobInfoPage(session, pageBean, job);
        if (request.getParameter("refresh") != null) {
            return "/job/refresh";
        }
        return "/job/view";
    }

    /**
     * 同一台执行器上不能有重复名字的job
     *
     * @param jobId
     * @param agentId
     * @param name
     */
    @RequestMapping(value = "checkname.do", method = RequestMethod.POST)
    @ResponseBody
    public Status checkName(Long jobId, Long agentId, String name) {
        return Status.create(!jobService.existsName(jobId, agentId, name));
    }

    @RequestMapping(value = "checkdel.do", method = RequestMethod.POST)
    @ResponseBody
    public String checkDelete(Long id) {
        return jobService.checkDelete(id);
    }

    @RequestMapping(value = "delete.do", method = RequestMethod.POST)
    @ResponseBody
    public Status delete(Long id) {
        try {
            jobService.delete(id);
            return Status.TRUE;
        } catch (Exception e) {
            e.printStackTrace();
            return Status.FALSE;
        }
    }

    @RequestMapping("add.htm")
    public String add(HttpSession session, Model model, Long id) {
        if (notEmpty(id)) {
            Agent agent = agentService.getAgent(id);
            model.addAttribute("agent", agent);
        }
        List<Agent> agents = agentService.getOwnerAgents(session);
        model.addAttribute("agents", agents);
        return "/job/add";
    }

    @RequestMapping(value = "save.do", method = RequestMethod.POST)
    public String save(HttpSession session, Job jobParam, HttpServletRequest request) throws Exception {
        jobParam.setCommand(DigestUtils.passBase64(jobParam.getCommand()));
        jobParam.setDeleted(false);
        if (jobParam.getJobId() != null) {
            Job job = jobService.getJob(jobParam.getJobId());

            if (!jobService.checkJobOwner(session, job.getUserId()))
                return "redirect:/job/view.htm";
            /**
             * 将数据库中持久化的作业和当前修改的合并,当前修改的属性覆盖持久化的属性...
             */
            BeanUtils.copyProperties(
                    job,
                    jobParam,
                    "jobName",
                    "cronType",
                    "cronExp",
                    "command",
                    "comment",
                    "successExit",
                    "redo",
                    "runCount",
                    "jobType",
                    "runModel",
                    "warning",
                    "mobiles",
                    "emailAddress",
                    "timeout"
            );
        }

        //单任务
        if (Constants.JobType.SINGLETON.getCode().equals(jobParam.getJobType())) {
            jobParam.setUserId(OpencronTools.getUserId(session));
            jobParam.setLastChild(false);
            jobParam = jobService.merge(jobParam);
        } else { //流程任务
            Map<String, String[]> map = request.getParameterMap();
            Object[] jobName = map.get("child.jobName");
            Object[] jobId = map.get("child.jobId");
            Object[] agentId = map.get("child.agentId");
            Object[] command = map.get("child.command");
            Object[] redo = map.get("child.redo");
            Object[] runCount = map.get("child.runCount");
            Object[] timeout = map.get("child.timeout");
            Object[] comment = map.get("child.comment");
            Object[] successExit = map.get("child.successExit");
            List<Job> children = new ArrayList<Job>(0);
            for (int i = 0; i < jobName.length; i++) {
                Job child = new Job();
                if (CommonUtils.notEmpty(jobId[i])) {
                    //子任务修改的..
                    Long jobid = Long.parseLong((String) jobId[i]);
                    child = jobService.getJob(jobid);
                }
                /**
                 * 新增并行和串行,子任务和最顶层的父任务一样
                 */
                child.setRunModel(jobParam.getRunModel());
                child.setJobName(StringUtils.htmlEncode((String) jobName[i]));
                child.setAgentId(Long.parseLong((String) agentId[i]));
                child.setCommand(DigestUtils.passBase64((String) command[i]));
                child.setJobType(Constants.JobType.FLOW.getCode());
                child.setComment(StringUtils.htmlEncode((String) comment[i]));
                child.setSuccessExit(StringUtils.htmlEncode((String) successExit[i]));
                child.setTimeout(Integer.parseInt((String) timeout[i]));
                child.setRedo(Integer.parseInt((String) redo[i]));
                child.setDeleted(false);
                if (child.getRedo() == 0) {
                    child.setRunCount(null);
                } else {
                    child.setRunCount(Integer.parseInt((String) runCount[i]));
                }
                children.add(child);
            }

            //流程任务必须有子任务,没有的话不保存
            if (CommonUtils.isEmpty(children)) {
                return "redirect:/job/view.htm";
            }

            if (jobParam.getUserId() == null) {
                jobParam.setUserId(OpencronTools.getUserId(session));
            }

            jobService.saveFlowJob(jobParam, children);
        }

        schedulerService.syncTigger(jobParam.getJobId());

        return "redirect:/job/view.htm";
    }

    @RequestMapping("editsingle.do")
    @ResponseBody
    public JobInfo editSingleJob(HttpSession session, HttpServletResponse response, Long id) {
        JobInfo job = jobService.getJobInfoById(id);
        if (job == null) {
            write404(response);
            return null;
        }
        if (!jobService.checkJobOwner(session, job.getUserId())) return null;
        return job;
    }

    @RequestMapping("editflow.htm")
    public String editFlowJob(HttpSession session, Model model, Long id) {
        JobInfo job = jobService.getJobInfoById(id);
        if (job == null) {
            return "/error/404";
        }
        if (!jobService.checkJobOwner(session, job.getUserId()))
            return "redirect:/job/view.htm";
        model.addAttribute("job", job);
        List<Agent> agents = agentService.getOwnerAgents(session);
        model.addAttribute("agents", agents);
        return "/job/edit";
    }


    @RequestMapping(value = "edit.do", method = RequestMethod.POST)
    @ResponseBody
    public Status edit(HttpSession session, Job job) throws Exception {
        Job dbJob = jobService.getJob(job.getJobId());
        if (!jobService.checkJobOwner(session, dbJob.getUserId())) return Status.FALSE;
        dbJob.setCronType(job.getCronType());
        dbJob.setCronExp(job.getCronExp());
        dbJob.setCommand(DigestUtils.passBase64(job.getCommand()));
        dbJob.setJobName(job.getJobName());
        dbJob.setSuccessExit(job.getSuccessExit());
        dbJob.setRedo(job.getRedo());
        dbJob.setRunCount(job.getRunCount());
        dbJob.setWarning(job.getWarning());
        dbJob.setTimeout(job.getTimeout());
        if (dbJob.getWarning()) {
            dbJob.setMobiles(job.getMobiles());
            dbJob.setEmailAddress(job.getEmailAddress());
        }
        dbJob.setComment(job.getComment());
        jobService.merge(dbJob);
        schedulerService.syncTigger(dbJob.getJobId());
        return Status.TRUE;
    }

    @RequestMapping(value = "editcmd.do", method = RequestMethod.POST)
    @ResponseBody
    public Status editCmd(HttpSession session, Long jobId, String command) throws Exception {
        command = DigestUtils.passBase64(command);
        Job dbJob = jobService.getJob(jobId);
        if (!jobService.checkJobOwner(session, dbJob.getUserId())) return Status.FALSE;
        dbJob.setCommand(command);
        jobService.merge(dbJob);
        schedulerService.syncTigger(Constants.JobType.FLOW.getCode().equals(dbJob.getJobType()) ? dbJob.getFlowId() : dbJob.getJobId());
        return Status.TRUE;
    }

    /**
     * 检测当前的job是否正在运行中,运行中true,未运行false
     *
     * @param id
     * @return
     */
    @RequestMapping(value = "running.do", method = RequestMethod.POST)
    @ResponseBody
    public Status jobisRunning(Long id) {
        return Status.create(recordService.isRunning(id));
    }

    @RequestMapping(value = "execute.do", method = RequestMethod.POST)
    @ResponseBody
    public Status remoteExecute(HttpSession session, Long id) {
        final JobInfo job = jobService.getJobInfoById(id);//找到要执行的任务
        if (!jobService.checkJobOwner(session, job.getUserId())) return Status.FALSE;
        //手动执行
        Long userId = OpencronTools.getUserId(session);
        job.setUserId(userId);
        job.setAgent(agentService.getAgent(job.getAgentId()));
        //无等待返回前台响应.
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    executeService.execute(job, Constants.ExecType.OPERATOR);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
        return Status.TRUE;
    }

    @RequestMapping(value = "scan.do", method = RequestMethod.POST)
    @ResponseBody
    public List<CrontabInfo> scan(Long agentId) {

        Agent agent = agentService.getAgent(agentId);

        String crontab = executeService.scan(agent);

        List<CrontabInfo> crontabs = new ArrayList<CrontabInfo>(0);

        if (crontab != null) {

            Scanner scanner = new Scanner(crontab);
            int index = 0;
            while (scanner.hasNext()) {
                String line = scanner.nextLine();
                if (CommonUtils.notEmpty(line)) {
                    line = line.trim();
                    //已注释打头...
                    if (line.startsWith("#")) {
                        continue;
                    }

                    String args[] = line.split("\\s+");
                    //无效的crontab表达式
                    if (args.length < 5) {
                        continue;
                    }
                    StringBuilder cronBuilder = new StringBuilder();
                    StringBuilder cmdBuilder = new StringBuilder();

                    for (int i = 0; i < args.length; i++) {
                        if (i <= 4) {
                            cronBuilder.append(args[i]).append(" ");
                        } else {
                            cmdBuilder.append(args[i]).append(" ");
                        }
                    }

                    String cmd = cmdBuilder.toString().trim();
                    if (cmd.startsWith("#")) {
                        continue;
                    }
                    String cron = cronBuilder.toString().trim();
                    CrontabInfo crontabInfo = new CrontabInfo(++index, cron, cmd);
                    crontabs.add(crontabInfo);
                }
            }
        }
        return crontabs;
    }

    @RequestMapping("goexec.htm")
    public String goExec(HttpSession session, Model model) {
        model.addAttribute("agents", agentService.getOwnerAgents(session));
        return "/job/exec";
    }

    @RequestMapping(value = "pause.do", method = RequestMethod.POST)
    @ResponseBody
    public Status pause(Job jobBean) {
        return Status.create(jobService.pauseJob(jobBean));
    }

    /**
     * 更新任务的api调用认证token
     *
     * @param jobId
     * @return
     */
    @RequestMapping(value = "token.do", method = RequestMethod.POST)
    @ResponseBody
    public ParamsMap token(Long jobId) {
        Job job = jobService.getJob(jobId);
        if (job != null) {
            job.setToken(CommonUtils.uuid());
            job = jobService.merge(job);
        }
        return ParamsMap.map().set("token", job.getToken());
    }

    @RequestMapping(value = "batchexec.do", method = RequestMethod.POST)
    @ResponseBody
    public Status batchExec(HttpSession session, String command, String agentIds) {
        if (notEmpty(agentIds) && notEmpty(command)) {
            command = DigestUtils.passBase64(command);
            Long userId = OpencronTools.getUserId(session);
            try {
                this.executeService.batchExecuteJob(userId, command, agentIds);
            } catch (Exception e) {
                e.printStackTrace();
                return Status.FALSE;
            }
        }
        return Status.TRUE;
    }

    @RequestMapping("detail/{id}.htm")
    public String showDetail(HttpSession session, Model model, @PathVariable("id") Long id) {
        JobInfo jobInfo = jobService.getJobInfoById(id);
        if (jobInfo == null) {
            return "/error/404";
        }
        if (!jobService.checkJobOwner(session, jobInfo.getUserId())) {
            return "redirect:/job/view.htm";
        }
        model.addAttribute("job", jobInfo);
        return "/job/detail";
    }

}

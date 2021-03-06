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

import org.opencron.common.Constants;
import org.opencron.common.util.collection.ParamsMap;
import org.opencron.server.domain.Job;
import org.opencron.server.service.AgentService;
import org.opencron.server.service.ExecuteService;
import org.opencron.server.service.JobService;
import org.opencron.server.vo.JobInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Map;

@Controller
@RequestMapping("api")
public class ApiController extends BaseController {

    @Autowired
    private JobService jobService;

    @Autowired
    private AgentService agentService;

    @Autowired
    private ExecuteService executeService;

    @RequestMapping(value = "run")
    @ResponseBody
    public Map run(Job jobParams, String url) {
        if (jobParams == null || jobParams.getJobId() == null) {
            return ParamsMap.map().put("status", "404", "message", "job not found");
        }

        final JobInfo job = jobService.getJobInfoById(jobParams.getJobId());
        if (job == null) {
            return ParamsMap.map().put("status", "404", "message", "job not found");
        }

        if (jobParams.getToken() == null) {
            return ParamsMap.map().put("status", "401", "message", "token is null,Unauthorized!");
        }

        if (!jobParams.getToken().equalsIgnoreCase(job.getToken())) {
            return ParamsMap.map().put("status", "401", "message", "token error,Unauthorized!");
        }

        job.setAgent(agentService.getAgent(job.getAgentId()));
        job.setCallbackURL(url);
        //无等待返回前台响应.

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    executeService.execute(job, Constants.ExecType.API);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
        return ParamsMap.map().put("status", "200", "message", "job started");
    }

}

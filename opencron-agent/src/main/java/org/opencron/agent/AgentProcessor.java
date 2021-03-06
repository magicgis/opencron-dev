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
package org.opencron.agent;

import com.alibaba.fastjson.JSON;
import org.apache.commons.exec.*;
import org.hyperic.sigar.SigarException;
import org.opencron.common.Constants;
import org.opencron.common.api.AgentJob;
import org.opencron.common.ext.ExtensionLoader;
import org.opencron.common.job.*;
import org.opencron.common.logging.LoggerFactory;
import org.opencron.common.util.*;
import org.opencron.rpc.Client;
import org.opencron.rpc.ServerHandler;
import org.slf4j.Logger;

import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.*;

import static org.opencron.common.util.CommonUtils.*;
import static org.opencron.common.util.ReflectUtils.isPrototype;


public class AgentProcessor implements ServerHandler, AgentJob {

    private Logger logger = LoggerFactory.getLogger(AgentProcessor.class);

    private Client client = null;

    private final String REPLACE_REX = "%s:\\sline\\s[0-9]+:";

    private String EXITCODE_KEY = "exitCode";

    private String EXITCODE_SCRIPT = String.format("\n\necho %s:$?", EXITCODE_KEY);

    private AgentMonitor agentMonitor = new AgentMonitor();

    @Override
    public Response handle(Request request) {
        Action action = request.getAction();
        if (Action.PATH.equals(action)) {
            return path(request);
        }
        //verify password...
        if (!SystemPropertyUtils.get(Constants.PARAM_OPENCRON_PASSWORD_KEY).equalsIgnoreCase(request.getPassword())) {
            return Response.response(request)
                    .setSuccess(false)
                    .setExitCode(Constants.StatusCode.ERROR_PASSWORD.getValue())
                    .setMessage(Constants.StatusCode.ERROR_PASSWORD.getDescription())
                    .end();
        }

        switch (action) {
            case PING:
                return ping(request);
            case EXECUTE:
                return execute(request);
            case PASSWORD:
                return password(request);
            case KILL:
                return kill(request);
            case GUID:
                return guid(request);
            case PROXY:
                return proxy(request);
            case MONITOR:
                return monitor(request);
            case CRONTAB:
                return scanCrontab(request);
            case RESTART:
                restart(request);
                break;

        }
        return null;
    }

    @Override
    public Response ping(Request request) {
        return Response.response(request).setSuccess(true).setExitCode(Constants.StatusCode.SUCCESS_EXIT.getValue()).end();
    }

    @Override
    public Response path(Request request) {
        //返回密码文件的路径...
        return Response.response(request).setSuccess(true)
                .setExitCode(Constants.StatusCode.SUCCESS_EXIT.getValue())
                .setMessage(Constants.OPENCRON_HOME)
                .end();
    }

    @Override
    public Response monitor(Request request) {
        Constants.ConnType connType = Constants.ConnType.getByName(request.getParams().get("connType"));
        Response response = Response.response(request);
        switch (connType) {
            case PROXY:
                try {
                    Monitor monitor = agentMonitor.monitor();
                    Map<String, String> map = monitor.toMap();
                    response.setResult(map)
                            .setSuccess(true)
                            .setExitCode(Constants.StatusCode.SUCCESS_EXIT.getValue())
                            .end();
                    return response;
                } catch (SigarException e) {
                    e.printStackTrace();
                }
            default:
                return null;
        }
    }

    @Override
    public Response execute(final Request request) {

        String command = request.getParams().get(Constants.PARAM_COMMAND_KEY);

        String pid = request.getParams().get(Constants.PARAM_PID_KEY);
        //以分钟为单位
        Long timeout = CommonUtils.toLong(request.getParams().get(Constants.PARAM_TIMEOUT_KEY), 0L);

        boolean timeoutFlag = timeout > 0;

        if (logger.isInfoEnabled()) {
            logger.info("[opencron]:execute:{},pid:{}", command, pid);
        }

        File shellFile = CommandUtils.createShellFile(command, pid, EXITCODE_SCRIPT);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        final Response response = Response.response(request);

        final ExecuteWatchdog watchdog = new ExecuteWatchdog(Integer.MAX_VALUE);

        final Timer timer = new Timer();

        DefaultExecuteResultHandler resultHandler = new DefaultExecuteResultHandler();

        Integer exitValue;

        String successExit = request.getParams().get(Constants.PARAM_SUCCESSEXIT_KEY);
        if (CommonUtils.isEmpty(successExit)) {
            exitValue = 0;//标准退住值:0
        } else {
            exitValue = Integer.parseInt(successExit);
        }

        try {

            CommandLine commandLine = CommandLine.parse(String.format("/bin/bash +x %s", shellFile.getAbsoluteFile()));

            final DefaultExecutor executor = new DefaultExecutor();

            ExecuteStreamHandler stream = new PumpStreamHandler(outputStream, outputStream);
            executor.setStreamHandler(stream);
            response.setStartTime(new Date().getTime());
            //成功执行完毕时退出值为0,shell标准的退出
            executor.setExitValue(exitValue);

            if (timeoutFlag) {
                //设置监控狗...
                executor.setWatchdog(watchdog);
                //监控超时的计时器
                timer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        //超时,kill...
                        if (watchdog.isWatching()) {
                            /**
                             * 调用watchdog的destroyProcess无法真正kill进程...
                             * watchdog.destroyProcess();
                             */
                            timer.cancel();
                            watchdog.stop();
                            //call  kill...
                            request.setAction(Action.KILL);
                            try {
                                kill(request);
                                response.setExitCode(Constants.StatusCode.TIME_OUT.getValue());
                            } catch (Exception e) {
                                e.printStackTrace();
                            }

                        }
                    }
                }, timeout * 60 * 1000);

                //正常执行完毕则清除计时器
                resultHandler = new DefaultExecuteResultHandler() {
                    @Override
                    public void onProcessComplete(int exitValue) {
                        super.onProcessComplete(exitValue);
                        timer.cancel();
                    }

                    @Override
                    public void onProcessFailed(ExecuteException e) {
                        super.onProcessFailed(e);
                        timer.cancel();
                    }
                };
            }

            executor.execute(commandLine, resultHandler);

            resultHandler.waitFor();

        } catch (Exception e) {
            if (e instanceof ExecuteException) {
                exitValue = ((ExecuteException) e).getExitValue();
            } else {
                exitValue = Constants.StatusCode.ERROR_EXEC.getValue();
            }
            if (Constants.StatusCode.KILL.getValue().equals(exitValue)) {
                if (timeoutFlag) {
                    timer.cancel();
                    watchdog.stop();
                }
                if (logger.isInfoEnabled()) {
                    logger.info("[opencron]:job has be killed!at pid :{}", request.getParams().get(Constants.PARAM_PID_KEY));
                }
            } else {
                if (logger.isInfoEnabled()) {
                    logger.info("[opencron]:job execute error:{}", e.getCause().getMessage());
                }
            }
        } finally {

            exitValue = resultHandler.getExitValue();

            if (CommonUtils.notEmpty(outputStream.toByteArray())) {
                try {
                    outputStream.flush();
                    String text = outputStream.toString();
                    if (notEmpty(text)) {
                        try {
                            text = text.replaceAll(String.format(REPLACE_REX, shellFile.getAbsolutePath()), "");
                            response.setMessage(text.substring(0, text.lastIndexOf(EXITCODE_KEY)));
                            exitValue = Integer.parseInt(text.substring(text.lastIndexOf(EXITCODE_KEY) + EXITCODE_KEY.length() + 1).trim());
                        } catch (IndexOutOfBoundsException e) {
                            response.setMessage(text);
                        }
                    }
                    outputStream.close();
                } catch (Exception e) {
                    if (logger.isErrorEnabled()) {
                        logger.error("[opencron]:error:{}", e);
                    }
                }
            }

            if (Constants.StatusCode.TIME_OUT.getValue() == response.getExitCode()) {
                response.setSuccess(false).end();
            } else {
                if (CommonUtils.isEmpty(successExit)) {
                    response.setExitCode(exitValue).setSuccess(exitValue == Constants.StatusCode.SUCCESS_EXIT.getValue()).end();
                } else {
                    response.setExitCode(exitValue).setSuccess(successExit.equals(exitValue.toString())).end();
                }
            }

        }

        if (CommonUtils.notEmpty(shellFile)) {
            shellFile.delete();
        }

        if (logger.isInfoEnabled()) {
            logger.info("[opencron]:execute result:{}", response.toString());
        }

        watchdog.stop();

        return response;
    }

    @Override
    public Response password(Request request) {
        String newPassword = request.getParams().get(Constants.PARAM_NEWPASSWORD_KEY);
        Response response = Response.response(request);
        if (isEmpty(newPassword)) {
            return response.setSuccess(false).setExitCode(Constants.StatusCode.SUCCESS_EXIT.getValue()).setMessage("密码不能为空").end();
        }
        SystemPropertyUtils.setProperty(Constants.PARAM_OPENCRON_PASSWORD_KEY, newPassword);
        IOUtils.writeText(Constants.OPENCRON_PASSWORD_FILE, newPassword, "UTF-8");
        return response.setSuccess(true).setExitCode(Constants.StatusCode.SUCCESS_EXIT.getValue()).end();
    }

    @Override
    public Response kill(Request request) {
        String pid = request.getParams().get(Constants.PARAM_PID_KEY);
        if (logger.isInfoEnabled()) {
            logger.info("[opencron]:kill pid:{}", pid);
        }

        Response response = Response.response(request);
        String text = CommandUtils.executeShell(Constants.OPENCRON_KILL_SHELL, pid, EXITCODE_SCRIPT);
        String message = "";
        Integer exitVal = 0;

        if (notEmpty(text)) {
            try {
                message = text.substring(0, text.lastIndexOf(EXITCODE_KEY));
                exitVal = Integer.parseInt(text.substring(text.lastIndexOf(EXITCODE_KEY) + EXITCODE_KEY.length() + 1).trim());
            } catch (StringIndexOutOfBoundsException e) {
                message = text;
            }
        }

        response.setExitCode(Constants.StatusCode.ERROR_EXIT.getValue().equals(exitVal) ? Constants.StatusCode.ERROR_EXIT.getValue() : Constants.StatusCode.SUCCESS_EXIT.getValue())
                .setMessage(message)
                .end();

        if (logger.isInfoEnabled()) {
            logger.info("[opencron]:kill result:{}" + response);
        }
        return response;
    }

    @Override
    public Response proxy(Request request) {

        if (this.client == null) {
            this.client = ExtensionLoader.load(Client.class);
        }

        String proxyHost = request.getParams().get(Constants.PARAM_PROXYHOST_KEY);
        String proxyPort = request.getParams().get(Constants.PARAM_PROXYPORT_KEY);
        String proxyAction = request.getParams().get(Constants.PARAM_PROXYACTION_KEY);
        String proxyPassword = request.getParams().get(Constants.PARAM_PROXYPASSWORD_KEY);

        //其他参数....
        String proxyParams = request.getParams().get(Constants.PARAM_PROXYPARAMS_KEY);
        Map<String, String> params = new HashMap<String, String>(0);
        if (CommonUtils.notEmpty(proxyParams)) {
            params = (Map<String, String>) JSON.parse(proxyParams);
        }

        Request proxyReq = Request.request(proxyHost, toInt(proxyPort), Action.findByName(proxyAction), proxyPassword, request.getTimeOut(), null).setParams(params);

        Response response = null;
        try {
            response = this.client.sentSync(proxyReq);
        } catch (Exception e) {
            e.printStackTrace();
            response = Response.response(request);
            response.setExitCode(Constants.StatusCode.ERROR_EXIT.getValue())
                    .setMessage("[opencron]:proxy error:" + e.getLocalizedMessage())
                    .setSuccess(false)
                    .end();
        }
        return response;
    }

    @Override
    public Response guid(Request request) {
        String guid = MacUtils.getMachineId();
        Response response = Response.response(request).end();
        if (notEmpty(guid)) {
            return response.setMessage(guid).setSuccess(true).setExitCode(Constants.StatusCode.SUCCESS_EXIT.getValue());
        }
        return response.setSuccess(false).setExitCode(Constants.StatusCode.ERROR_EXIT.getValue());
    }

    @Override
    public Response scanCrontab(Request request) {
        String crontab = CommandUtils.executeScript("crontab -l");
        return Response.response(request).setMessage(crontab).end();
    }

    /**
     * 重启前先检查密码,密码不正确返回Response,密码正确则直接执行重启
     *
     * @param request
     * @return
     * @throws InterruptedException
     */
    @Override
    public void restart(Request request) {

    }

}
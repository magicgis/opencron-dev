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
package org.opencron.common.job;

import org.opencron.common.util.CommonUtils;
import org.opencron.common.util.IdGenerator;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class Request implements Serializable {

    private RpcType rpcType = RpcType.ASYNC;
    private String host;
    private Long id;
    private Integer port;
    private String address;
    private Long timeOut;//调用超时时间限制(分钟)
    private Action action;
    private String password;
    private Long proxyAgent;
    private Map<String, String> params;

    public Request() {

    }

    public static Request request(String host, Integer port, Action action, String password, Long timeOut, Long proxyAgent) {
        return new Request()
                .setHost(host)
                .setPort(port)
                .setAction(action)
                .setPassword(password)
                .setTimeOut(timeOut)
                .setProxyAgent(proxyAgent)
                .setId(IdGenerator.getId());
    }

    public Request putParam(String key, String value) {
        if (this.params == null) {
            this.params = new HashMap<String, String>(0);
        }
        this.params.put(key, value);
        return this;
    }

    public String getHost() {
        return host;
    }

    public Request setHost(String host) {
        this.host = host;
        return this;
    }

    public int getPort() {
        return port;
    }

    public Request setPort(int port) {
        this.port = port;
        return this;
    }

    public Action getAction() {
        return action;
    }

    public Request setAction(Action action) {
        this.action = action;
        return this;
    }

    public Long getTimeOut() {
        //如果timeOut为设置,则返回24小时(1440分钟)
        if ( timeOut == null || timeOut<=0 ) {
            return 60 * 24L;
        }
        return timeOut;
    }

    public Long getMillisTimeOut() {
       return getTimeOut() * 60 * 1000;
    }

    public Request setTimeOut(Long timeOut) {
        this.timeOut = timeOut;
        return this;
    }

    public String getPassword() {
        return password;
    }

    public Request setPassword(String password) {
        this.password = password;
        return this;
    }

    public Map<String, String> getParams() {
        return params;
    }

    public Request setParams(Map<String, String> params) {
        this.params = params;
        return this;
    }

    public RpcType getRpcType() {
        return rpcType;
    }

    public Request setRpcType(RpcType rpcType) {
        this.rpcType = rpcType;
        return this;
    }

    public String getAddress() {
        if (CommonUtils.notEmpty(this.host, this.port)) {
            this.address = this.host + ":" + this.port;
        }
        return this.address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public Long getId() {
        return id;
    }

    public Request setId(Long id) {
        this.id = id;
        return this;
    }

    public Long getProxyAgent() {
        return proxyAgent;
    }

    public Request setProxyAgent(Long proxyAgent) {
        this.proxyAgent = proxyAgent;
        return this;
    }
}

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

package org.opencron.server.service;

import org.opencron.common.Constants;
import org.opencron.common.util.DigestUtils;
import org.opencron.common.util.PropertyPlaceholder;
import org.opencron.server.dao.QueryDao;
import org.opencron.server.domain.Log;
import org.opencron.server.domain.User;
import org.opencron.server.handler.SingleLoginListener;
import org.opencron.server.support.OpencronTools;
import org.opencron.server.support.TerminalSession;
import org.opencron.server.tag.PageBean;
import org.opencron.server.vo.LogInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import java.io.IOException;
import java.util.List;

import static org.opencron.common.util.CommonUtils.notEmpty;

/**
 * Created by ChenHui on 2016/2/17.
 */
@Service
@Transactional
public class HomeService {

    @Autowired
    private QueryDao queryDao;

    public int checkLogin(HttpServletRequest request, String username, String password) throws IOException {

        HttpSession httpSession = request.getSession();
        User user = queryDao.hqlUniqueQuery("from User where userName = ?", username);
        if (user == null) return 500;

        //拿到数据库的数据盐
        byte[] salt = DigestUtils.decodeHex(user.getSalt());
        String saltPassword = DigestUtils.encodeHex(DigestUtils.sha1(password.getBytes(), salt, 1024));

        if (saltPassword.equals(user.getPassword())) {
            if (user.getRoleId() == 999L) {
                httpSession.setAttribute(Constants.PARAM_PERMISSION_KEY, true);
            } else {
                httpSession.setAttribute(Constants.PARAM_PERMISSION_KEY, false);
            }

            String singlelogin = PropertyPlaceholder.get(Constants.PARAM_OPENCRON_SINGLELOGIN_KEY);
            if (singlelogin != null && singlelogin.trim().equalsIgnoreCase("true")) {
                Boolean logined = SingleLoginListener.logined(user);
                if (logined) {
                    HttpSession session = SingleLoginListener.getLoginedSession(user.getUserId());
                    if (session != null) {
                        session.setAttribute("loginMsg", "你的账号在其他地方登录,请重新登录");
                    }
                    //拿到已经登录的session,将其踢下线
                    SingleLoginListener.removeUserSession(user.getUserId());
                    //已经登录的用户开启的终端全部关闭...
                    TerminalSession.exit(request);
                }
                SingleLoginListener.addUserSession(httpSession);
            }
            OpencronTools.logined(request, user);
            return 200;
        } else {
            return 500;
        }
    }

    public PageBean<LogInfo> getLog(HttpSession session, PageBean pageBean, Long agentId, String sendTime) {
        String sql = "SELECT L.*,W.name AS agentName FROM T_LOG AS L " +
                "LEFT JOIN T_AGENT AS W " +
                "ON L.agentId = W.agentId " +
                "WHERE L.userId = " + OpencronTools.getUserId(session);
        if (notEmpty(agentId)) {
            sql += " AND L.agentId = " + agentId;
        }
        if (notEmpty(sendTime)) {
            sql += " AND L.sendTime LIKE '" + sendTime + "%' ";
        }
        sql += " ORDER BY L.sendTime DESC";
        queryDao.sqlPageQuery(pageBean, LogInfo.class, sql);
        return pageBean;
    }

    public List<LogInfo> getUnReadMessage(HttpSession session) {
        String hql = "from Log where isread=? AND type=?  and userId = ? order by sendTime desc";
        return queryDao.hqlPageQuery(hql, 1, 5, false, Constants.MsgType.WEBSITE.getValue(), OpencronTools.getUserId(session));
    }

    public Integer getUnReadCount(HttpSession session) {
        String hql = "select count(1) from Log where isread=? and type=? and userid = ?";
        return queryDao.hqlCount(hql, false, Constants.MsgType.WEBSITE.getValue(), OpencronTools.getUserId(session));
    }

    public void saveLog(Log log) {
        log.setLogId(null);
        queryDao.merge(log);
    }

    public Log getLogDetail(Long logId) {
        return queryDao.get(Log.class, logId);
    }

    public void updateAfterRead(Long logId) {
        String hql = "from Log where logId=? and type=?";
        Log log = queryDao.hqlUniqueQuery(hql, logId, Constants.MsgType.WEBSITE.getValue());
        if (log != null) {
            log.setIsread(true);
            queryDao.merge(log);
        }
    }

}

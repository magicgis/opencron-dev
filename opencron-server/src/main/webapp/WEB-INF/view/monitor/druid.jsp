<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%@ page contentType="text/html;charset=UTF-8" language="java" %>

<%@ taglib prefix="cron"  uri="http://www.opencron.org"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>
<!DOCTYPE html>
<html lang="en">
<meta name="author" content="author:benjobs,wechat:wolfboys,Created by 2016" />
<head>
    <script type="text/javascript">
        function reinitIframe(){
            var iframe = document.getElementById("druid");
            try{
                var bHeight = iframe.contentWindow.document.body.scrollHeight;
                var dHeight = iframe.contentWindow.document.documentElement.scrollHeight;
                var height = Math.max(bHeight, dHeight);
                iframe.height = height;
            }catch (ex){}
        }
        window.setInterval("reinitIframe()", 200);
    </script>
</head>

<body>
<!-- Content -->
<section id="content" class="container">

    <!-- Messages Drawer -->
    <jsp:include page="/WEB-INF/layouts/message.jsp"/>

    <!-- Breadcrumb -->
    <ol class="breadcrumb hidden-xs">
        <li class="icon">&#61753;</li>
        当前位置：
        <li><a href="">opencron</a></li>
        <li><a href="">监控中心</a></li>
        <li><a href="">Druid监控</a></li>
    </ol>
    <h4 class="page-title"><i class="fa fa-bar-chart" aria-hidden="true" style="font-style: 30px;"></i>&nbsp;修改设置</h4>

    <div style="float: right;margin-top: 5px">
        <a onclick="goback();" class="btn btn-sm m-t-10" style="margin-right: 16px;margin-bottom: -4px">&nbsp;返回</a>
    </div>

    <div class="block-area">
        <iframe src="${contextPath}/druid/index.html" frameborder="0" scrolling="no" id="druid" width="100%" onload="this.height=100"></iframe>
    </div>

</section>

</body>

</html>
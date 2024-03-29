<!--
  * Copyright (c) 2009 eXtensible Catalog Organization
  *
  * This program is free software; you can redistribute it and/or modify it under the terms of the MIT/X11 license. The text of the
  * license can be found at http://www.opensource.org/licenses/mit-license.php and copy of the license can be found on the project
  * website http://www.extensiblecatalog.org/.
  *
  -->

<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%@ taglib prefix="s" uri="/struts-tags" %>
<%@ taglib prefix="mst" uri="mst-tags"%>

<!--  document type -->
<c:import url="/st/inc/doctype-frag.jsp"/>

<html>
    <head>
        <title>Welcome To MST</title>
        <c:import url="/st/inc/meta-frag.jsp"/>

        <LINK href="page-resources/yui/reset-fonts-grids/reset-fonts-grids.css" rel="stylesheet" type="text/css" >
        <LINK href="page-resources/css/base-mst.css" rel="stylesheet" type="text/css" >
        <LINK href="page-resources/yui/menu/assets/skins/sam/menu.css"  rel="stylesheet" type="text/css" >
        <LINK href="page-resources/css/global.css" rel="stylesheet" type="text/css" >
        <LINK href="page-resources/css/main_menu.css" rel="stylesheet" type="text/css" >
        <LINK href="page-resources/css/tables.css" rel="stylesheet" type="text/css" >
    <LINK href="page-resources/css/header.css" rel="stylesheet" type="text/css">

        <SCRIPT LANGUAGE="JavaScript" SRC="page-resources/js/utilities.js"></SCRIPT>
        <SCRIPT LANGUAGE="JavaScript" src="page-resources/yui/yahoo-dom-event/yahoo-dom-event.js"></SCRIPT>
        <SCRIPT LANGUAGE="JavaScript" src="page-resources/yui/container/container-min.js"></SCRIPT>
      <SCRIPT LANGUAGE="JavaScript" SRC="page-resources/yui/element/element-beta-min.js"></script>
        <SCRIPT LANGUAGE="JavaScript" SRC="page-resources/yui/menu/menu-min.js"></SCRIPT>
        <SCRIPT LANGUAGE="JavaScript" SRC="page-resources/yui/button/button-min.js"></script>
        <SCRIPT LANGUAGE="JavaScript" SRC="page-resources/js/main_menu.js"></SCRIPT>
        <SCRIPT LANGUAGE="JavaScript" SRC="page-resources/js/user_registration.js"></SCRIPT>
    </head>

    <body class="yui-skin-sam">
        <!--  yahoo doc 2 template creates a page 950 pixles wide -->
        <div id="doc2">

    <!-- page header - this uses the yahoo page styling -->
    <div id="hd">

            <!--  this is the header of the page -->
            <c:import url="/st/inc/header.jsp"/>
              <div id="mainMenu" class="yuimenubar yuimenubarnav">
                <div class="bd">
                    <ul class="first-of-type">
                        <span class="wrenchImg">&nbsp;</span>
                    </ul>
                </div>
             </div>

            <!--  this is the header of the page -->
            <c:import url="/st/inc/menu.jsp"/>
            <div style="height:10px;">

            </div>
            <jsp:include page="/st/inc/breadcrumb.jsp">

                    <jsp:param name="bread" value="User Registration" />

            </jsp:include>
     </div>
    <!--  end header -->

    <!-- body -->
    <div id="bd">


          <!-- Display of error message -->
                 <c:if test="${errorType != null}">
                    <div id="server_error_div">
                    <div id="server_message_div" class="${errorType}">
                        <img  src="${pageContext.request.contextPath}/page-resources/img/${errorType}.jpg">
                        <span class="errorText">
                            <mst:fielderror error="${fieldErrors}">
                            </mst:fielderror>
                        </span>
                    </div>
                    </div>
                 </c:if>
                 <div id="error_div"></div>

                 <div class="clear">&nbsp;</div>

         <form name="registrationForm" method="post">

      <table class="basicTable" align="left">
      <tr>
                <td> <b>First Name</b> <br>

          <input type="text" id="user_first_name" name="newUser.firstName" value="${newUser.firstName}" maxlength="225"/>
        </td>
      </tr>
      <tr>
                <td> <b>Last Name</b> <br>

          <input type="text" id="user_last_name" name="newUser.lastName" value="${newUser.lastName}" maxlength="225"/>
        </td>
      </tr>

      <tr>
                <td> <b>Select Login Type</b> <br>

          <select id="login_server" name="serverName" onChange="Javascript:YAHOO.xc.mst.registeration.determinePasswordBoxDisplay();">

            <c:forEach items="${servers}" var="server">
              <option value = "${server.name}"
              <c:if test="${serverName == server.name}">
                selected
              </c:if>
              > ${server.name}</option>
            </c:forEach>
          </select>
        </td>

      </tr>
      <tr>
                <td> <b>User Name</b> <br>
          <input type="text" id="user_name" name="newUser.username" value="${newUser.username}" maxlength="225"/>
        </td>
      </tr>
      <tr>
                <td> <b>Password</b> <br>
          <input type="password" id="user_password" name="newUser.password" value="${newUser.password}" maxlength="63"/>
        </td>
      </tr>
      <tr>
                <td> <b>Password Confirmation</b> <br>
          <input type="password" id="user_password_confirmation" name="passwordConfirmation" maxlength="63"/>
        </td>
      </tr>
      <tr>
                <td> <b>Email</b> <br>
          <input type="text" id="user_email" name="newUser.email" value="${newUser.email}" maxlength="225" size="42"/>
        </td>
      </tr>
      <tr>
                <td> <b>Special Request to the Systems Administrator</b> <br>
          <textarea id="user_comments" style="width:450px;" name="comments" rows="5" cols="20">${comments}</textarea>
        </td>
      </tr>
      <tr>
        <td colspan="2">
          <button class="xc_button_small" name="cancel" onClick="Javascript:YAHOO.xc.mst.registeration.cancel();">Cancel</button>
          <c:if test="${!configurationError}">
            <button class="xc_button" type="button" name="save" onClick="Javascript:YAHOO.xc.mst.registeration.register();">Register</button>
          </c:if>
          <c:if test="${configurationError}">
            <button disabled type="button" class="xc_button_disabled" name="save" onClick="Javascript:YAHOO.xc.mst.registeration.register();">Register</button>
          </c:if>
        </td>
      </tr>
    </table>
    </form>
     </div>
    <!--  end body -->
            <!--  this is the footer of the page -->
            <c:import url="/st/inc/footer.jsp"/>
        </div>
        <!-- end doc -->
    </body>
</html>

<!--
  * Copyright (c) 2009 eXtensible Catalog Organization
  *
  * This program is free software; you can redistribute it and/or modify it under the terms of the MIT/X11 license. The text of the
  * license can be found at http://www.opensource.org/licenses/mit-license.php and copy of the license can be found on the project
  * website http://www.extensiblecatalog.org/.
  *
  -->

<%@ taglib prefix="c" uri="http://java.sun.com/jstl/core_rt"%>
<%@ taglib prefix="mst" uri="mst-tags"%>

<!--  document type -->
<c:import url="/st/inc/doctype-frag.jsp"/>

<LINK href="page-resources/css/header.css" rel="stylesheet" type="text/css">


<html>
    <head>
        <title>Edit LDAP User</title>
        <c:import url="/st/inc/meta-frag.jsp"/>

        <LINK href="page-resources/yui/reset-fonts-grids/reset-fonts-grids.css" rel="stylesheet" type="text/css" >
        <LINK href="page-resources/css/base-mst.css" rel="stylesheet" type="text/css" >
        <LINK href="page-resources/yui/menu/assets/skins/sam/menu.css"  rel="stylesheet" type="text/css" >
        <LINK href="page-resources/css/global.css" rel="stylesheet" type="text/css" >
        <LINK href="page-resources/css/main_menu.css" rel="stylesheet" type="text/css" >
        <LINK href="page-resources/css/tables.css" rel="stylesheet" type="text/css" >
    <LINK href="page-resources/css/header.css" rel="stylesheet" type="text/css">
    <LINK href="page-resources/css/bodylayout.css" rel="stylesheet" type="text/css">

        <SCRIPT LANGUAGE="JavaScript" SRC="page-resources/js/utilities.js"></SCRIPT>
        <SCRIPT LANGUAGE="JavaScript" src="page-resources/yui/yahoo-dom-event/yahoo-dom-event.js"></SCRIPT>
        <SCRIPT LANGUAGE="JavaScript" src="page-resources/yui/connection/connection-min.js"></SCRIPT>
        <SCRIPT LANGUAGE="JavaScript" src="page-resources/yui/container/container_core-min.js"></SCRIPT>
        <SCRIPT LANGUAGE="JavaScript" SRC="page-resources/yui/menu/menu-min.js"></SCRIPT>
        <SCRIPT LANGUAGE="JavaScript" SRC="page-resources/js/main_menu.js"></SCRIPT>
        <SCRIPT LANGUAGE="JavaScript" SRC="page-resources/js/edit_LDAPuser.js"></SCRIPT>

    </head>

    <body class="yui-skin-sam">
        <%@ taglib prefix="s" uri="/struts-tags" %>
        <!--  yahoo doc 2 template creates a page 950 pixles wide -->
        <div id="doc2">

    <!-- page header - this uses the yahoo page styling -->
    <div id="hd">

            <!--  this is the header of the page -->
            <c:import url="/st/inc/header.jsp"/>

            <!--  this is the header of the page -->
            <c:import url="/st/inc/menu.jsp"/>
            <jsp:include page="/st/inc/breadcrumb.jsp">

                    <jsp:param name="bread" value="Users & Groups | <a href='allUsers.action' style='text-decoration:none;'>All Users</a> | Edit LDAP User" />

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
            <br><br>
                <form action="editLDAPUser.action?UserId=${temporaryUser.id}" method="post" name="editLDAPUser">

                   <table style="margin-left:10px">
                       <tr>
                           <td valign="top"><b>Username</b> <br>
                           ${temporaryUser.username}<br><br></td>
                       </tr>
                      <tr>
                        <td valign="top"><b>First Name</b> <br>
                        <input type="text" id="firstName" name ="firstName" class="textfield" value="${temporaryUser.firstName}" maxlength="255" size="39"><br><br></td>
                      </tr>

                      <tr>
                        <td valign="top"><b>Last Name</b>  <br>
                        <input type="text" id="lastName" name ="lastName" class="textfield" value="${temporaryUser.lastName}" maxlength="255" size="39"><br><br></td>
                      </tr>
                       <tr>
                           <td valign="top"><b>Email </b> <br>
                           <input type="text" id="email" name ="email" class="textfield" value="${temporaryUser.email}" maxlength="255" size="39"><br><br></td>
                       </tr>



                           <tr>
                               <td><b>Select groups for user </b>

                                   <br>
                                   <select multiple size="8" id="groupsSelected" name ="groupsSelected" style="width:250px; height:125px;">
                                     <c:forEach var="m" varStatus="b" items="${groupList}">

                                         <c:set var="flag" value="${false}"/>
                                           <c:forEach var="n" varStatus="a" items="${temporaryUser.groups}">

                                                <c:if test="${m.id==n.id}">
                                                   <c:set var="flag" value="${true}"/>
                                                </c:if>
                                           </c:forEach>
                                           <c:choose>
                                               <c:when test="${flag==true}">

                                                   <OPTION SELECTED value="${m.id}"><c:out value="${m.name}"/></OPTION>
                                               </c:when>
                                               <c:otherwise>

                                                   <OPTION value="${m.id}"><c:out value="${m.name}"/></OPTION>
                                               </c:otherwise>
                                           </c:choose>
                                     </c:forEach>

                                   </select>
                                    <div class="smallText">
                                        (CTRL click to select multiple groups)
                                    </div>
                                   <BR><br><br>
                               </td>
                           </tr>




      <tr><td>
                        <div align="left">
                             <button style="vertical-align:bottom;" class="xc_button_small" type="button" name="cancel" onclick="javascript:YAHOO.xc.mst.user.editLDAP.cancel();">Cancel</button> &nbsp;&nbsp;&nbsp;

                             <button class="xc_button" type="button" name="editlocaluser" onclick="javascript:YAHOO.xc.mst.user.editLDAP.editLDAPUser();">Update LDAP User</button>
                        </div>
                        </td></tr>
    </table>
                </form>
          </div>
            <!--  this is the footer of the page -->
            <c:import url="/st/inc/footer.jsp"/>
      </div>
</body>
</html>

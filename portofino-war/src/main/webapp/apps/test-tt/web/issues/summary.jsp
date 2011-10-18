<%@ page import="com.manydesigns.elements.ElementsThreadLocals" %>
<%@ page import="com.manydesigns.portofino.application.Application" %>
<%@ page import="ognl.Ognl" %>
<%@ page import="java.util.List" %>
<%@ page contentType="text/html;charset=ISO-8859-1" language="java"
         pageEncoding="ISO-8859-1"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%@ taglib prefix="stripes" uri="http://stripes.sourceforge.net/stripes.tld"%>
<%@taglib prefix="mde" uri="/manydesigns-elements"%>
<jsp:useBean id="actionBean" scope="request" type="com.manydesigns.portofino.actions.JspAction"/>
<stripes:layout-render name="/skins/${skin}/portlet.jsp">
    <stripes:layout-component name="portletTitle">
        <c:out value="${actionBean.jspPage.title}"/>
    </stripes:layout-component>
    <stripes:layout-component name="portletHeaderButtons">
        <button name="configure" class="wrench">Configure</button>
    </stripes:layout-component>
    <stripes:layout-component name="portletBody">
        <ul>
            <%
                Application appl = (Application) request.getAttribute("application");
                List<?> objects = appl.getObjects(
                        "SELECT t.name, sum(1 - st.is_closed) as sum, count(*) as count " +
                        "FROM redmine_public_issues i, redmine_public_trackers t, redmine_public_issue_statuses st " +
                        "WHERE t.id = i.tracker_id " +
                        "  AND i.project_id = %{#project.id} " +
                        "  AND i.status_id = st.id " +
                        "GROUP BY t.name");
                for(Object obj : objects) {
                    Object[] obArr = (Object[]) obj;
                    out.print(obArr[0] + ": " + obArr[1] + " open / " + obArr[2] + "<br />");
                }

                Object projectId = Ognl.getValue("#project.id", ElementsThreadLocals.getOgnlContext(), (Object) null);
            %>
        </ul>
        <a href="<%= request.getContextPath() %>/projects/<%= projectId %>/issues">Show all issues</a>
    </stripes:layout-component>
    <stripes:layout-component name="portletFooter">
    </stripes:layout-component>
</stripes:layout-render>

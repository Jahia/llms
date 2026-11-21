<%@ page language="java" contentType="text/markdown;charset=UTF-8" trimDirectiveWhitespaces="true" %>
<%@ page import="org.jahia.modules.llms.render.MarkdownView" %>
<%-- The Markdown version of the page. Jahia renders the page as HTML, with its template, for a visitor
     without an account; the result is converted, and cached by Jahia until the page changes. --%>
<% out.print(MarkdownView.render(request, response)); %>

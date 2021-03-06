/**
 * Copyright (c) 2000-2012 Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */

package com.liferay.portal.struts;

import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.json.JSONFactoryUtil;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.servlet.HttpHeaders;
import com.liferay.portal.kernel.servlet.ServletContextPool;
import com.liferay.portal.kernel.util.ContentTypes;
import com.liferay.portal.kernel.util.GetterUtil;
import com.liferay.portal.kernel.util.ParamUtil;
import com.liferay.portal.kernel.util.SetUtil;
import com.liferay.portal.kernel.util.StringPool;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.portal.security.auth.AuthSettingsUtil;
import com.liferay.portal.security.auth.AuthTokenUtil;
import com.liferay.portal.security.auth.PrincipalException;
import com.liferay.portal.util.PortalUtil;
import com.liferay.portal.util.PropsValues;
import com.liferay.portal.util.WebKeys;

import java.io.OutputStream;

import java.util.Set;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.struts.action.Action;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;

/**
 * @author Ming-Gih Lam
 * @author Brian Wing Shun Chan
 * @author Tomas Polesovsky
 */
public abstract class JSONAction extends Action {

	@Override
	public ActionForward execute(
			ActionMapping mapping, ActionForm form, HttpServletRequest request,
			HttpServletResponse response)
		throws Exception {

		if (rerouteExecute(request, response)) {
			return null;
		}

		String callback = ParamUtil.getString(request, "callback");
		String instance = ParamUtil.getString(request, "inst");

		String json = null;

		try {
			checkAuthToken(request);

			json = getJSON(mapping, form, request, response);

			if (Validator.isNotNull(callback)) {
				json = callback + "(" + json + ");";
			}
			else if (Validator.isNotNull(instance)) {
				json = "var " + instance + "=" + json + ";";
			}
		}
		catch (PrincipalException pe) {
			if (_log.isWarnEnabled()) {
				_log.warn(pe.getMessage());
			}

			json = JSONFactoryUtil.serializeException(pe);
		}
		catch (Exception e) {
			PortalUtil.sendError(
				HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e, request,
				response);

			return null;
		}

		boolean refresh = ParamUtil.getBoolean(request, "refresh");

		if (refresh) {
			return mapping.findForward(ActionConstants.COMMON_REFERER);
		}
		else if (Validator.isNotNull(json)) {
			response.setCharacterEncoding(StringPool.UTF8);
			response.setContentType(ContentTypes.TEXT_JAVASCRIPT);
			response.setHeader(
				HttpHeaders.CACHE_CONTROL,
				HttpHeaders.CACHE_CONTROL_NO_CACHE_VALUE);

			OutputStream outputStream = response.getOutputStream();

			byte[] bytes = json.getBytes(StringPool.UTF8);

			outputStream.write(bytes);

			outputStream.close();
		}

		return null;
	}

	public abstract String getJSON(
			ActionMapping mapping, ActionForm form, HttpServletRequest request,
			HttpServletResponse response)
		throws Exception;

	public void setServletContext(ServletContext servletContext) {
		_servletContext = servletContext;
	}

	protected void checkAuthToken(HttpServletRequest request)
		throws PortalException {

		if (PropsValues.AUTH_TOKEN_CHECK_ENABLED &&
			PropsValues.JSON_SERVICE_AUTH_TOKEN_ENABLED) {

			if (!AuthSettingsUtil.isAccessAllowed(request, _hostsAllowed)) {
				AuthTokenUtil.check(request);
			}
		}
	}

	protected String getReroutePath() {
		return null;
	}

	protected boolean rerouteExecute(
			HttpServletRequest request, HttpServletResponse response)
		throws Exception {

		String reroutePath = getReroutePath();

		if (Validator.isNull(reroutePath)) {
			return false;
		}

		String requestServletContextName = ParamUtil.getString(
			request, "servletContextName");

		if (Validator.isNull(requestServletContextName)) {
			return false;
		}

		ServletContext servletContext = _servletContext;

		if (servletContext == null) {
			servletContext = (ServletContext)request.getAttribute(WebKeys.CTX);
		}

		String servletContextName = GetterUtil.getString(
			servletContext.getServletContextName());

		if (servletContextName.equals(requestServletContextName)) {
			return false;
		}

		ServletContext requestServletContext = ServletContextPool.get(
			requestServletContextName);

		if (requestServletContext == null) {
			return false;
		}

		RequestDispatcher requestDispatcher =
			requestServletContext.getRequestDispatcher(reroutePath);

		if (requestDispatcher == null) {
			return false;
		}

		requestDispatcher.forward(request, response);

		return true;
	}

	private static Log _log = LogFactoryUtil.getLog(JSONAction.class);

	private Set<String> _hostsAllowed = SetUtil.fromArray(
		PropsValues.JSON_SERVICE_AUTH_TOKEN_HOSTS_ALLOWED);
	private ServletContext _servletContext;

}
/*******************************************************************************
 * Copyright (c) 2019 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package com.ibm.microclimate.ui.internal.debug;

import java.net.URI;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.window.Window;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.internal.browser.BrowserManager;
import org.eclipse.ui.internal.browser.IBrowserDescriptor;
import org.json.JSONArray;
import org.json.JSONObject;

import com.ibm.microclimate.core.MicroclimateCorePlugin;
import com.ibm.microclimate.core.internal.HttpUtil;
import com.ibm.microclimate.core.internal.HttpUtil.HttpResult;
import com.ibm.microclimate.core.internal.IDebugLauncher;
import com.ibm.microclimate.core.internal.MCLogger;
import com.ibm.microclimate.core.internal.MicroclimateApplication;
import com.ibm.microclimate.ui.MicroclimateUIPlugin;
import com.ibm.microclimate.ui.internal.messages.Messages;

@SuppressWarnings("restriction") //$NON-NLS-1$
public class NodeJSDebugLauncher implements IDebugLauncher {
	
	private static final String DEBUG_INFO = "/json/list";
	private static final String DEVTOOLS_URL_FIELD = "devtoolsFrontendUrl";
	
	public IStatus launchDebugger(MicroclimateApplication app) {
		String urlString = null;
		Exception e = null;
		try {
			urlString = getDebugURL(app);
		} catch (Exception e1) {
			e = e1;
		}
		if (urlString == null) {
			MCLogger.logError("Failed to get the debug URL for the " + app.name + " application.", e); //$NON-NLS-1$ //$NON-NLS-2$
			return new Status(IStatus.ERROR, MicroclimateUIPlugin.PLUGIN_ID, NLS.bind(Messages.NodeJSDebugURLError, app.name), e);
		}
		return openNodeJSDebugger(urlString);
	}
	
	@Override
	public boolean canAttachDebugger(MicroclimateApplication app) {
		String host = app.host;
		int debugPort = app.getDebugPort();
		
		// If a debugger is already attached then the devtools url field will not be included in the result
		try {
			URI uri = new URI("http", null, host, debugPort, DEBUG_INFO, null, null); //$NON-NLS-1$
			HttpResult result = HttpUtil.get(uri);
			if (result.isGoodResponse) {
				String response = result.response;
				JSONArray array = new JSONArray(response);
				JSONObject info = array.getJSONObject(0);
				if (info.has(DEVTOOLS_URL_FIELD)) {
					String url = info.getString(DEVTOOLS_URL_FIELD);
					if (url != null && !url.isEmpty()) {
						return true;
					}
				}
			}
		} catch (Exception e) {
			MCLogger.log("Failed to retrieve the debug information for the " + app.name + " app: " + e.getMessage()); //$NON-NLS-1$  //$NON-NLS-2$
		}
		
		return false;
	}

	private String getDebugURL(MicroclimateApplication app) throws Exception {
		IPreferenceStore prefs = MicroclimateCorePlugin.getDefault().getPreferenceStore();
		int debugTimeout = prefs.getInt(MicroclimateCorePlugin.DEBUG_CONNECT_TIMEOUT_PREFSKEY);
		
		String host = app.host;
		int debugPort = app.getDebugPort();
		
		URI uri = new URI("http", null, host, debugPort, DEBUG_INFO, null, null); //$NON-NLS-1$
		
		Exception e = null;
		HttpResult result = null;
		
		for (int i = 0; i <= debugTimeout; i++) {
			try {
				result = HttpUtil.get(uri);
				if (result.isGoodResponse) {
					String response = result.response;
					JSONArray array = new JSONArray(response);
					JSONObject info = array.getJSONObject(0);
					String url = info.getString(DEVTOOLS_URL_FIELD);
					
					// Replace the host and port
					int start = url.indexOf("ws=") + 3; //$NON-NLS-1$
					int end = url.indexOf("/", start); //$NON-NLS-1$
					String subString = url.substring(start, end);
					url = url.replace(subString, host + ":" + Integer.toString(debugPort));
					return url;
				}
			} catch (Exception e1) {
				e = e1;
			}
			
			if (i <= debugTimeout) {
				try {
					Thread.sleep(1000);
				} catch (Exception e1) {
					// Ignore
				}
			}
		}
		
		if (result != null && !result.isGoodResponse) {
		    MCLogger.logError("Error getting debug information for the " + app.name + " application. Error code: " + result.responseCode + ", error: " + result.error + "."); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
		} else {
			MCLogger.logError("An exception occurred trying to retrieve the debug information for the " + app.name + " application.", e); //$NON-NLS-1$ //$NON-NLS-2$
		}
		
		if (e != null) {
		    throw e;
		}
		return null;
	}
	
	private IStatus openNodeJSDebugger(final String chromeDevToolsUrl) {
		IPreferenceStore prefs = MicroclimateCorePlugin.getDefault().getPreferenceStore();
		String browserName = prefs.getString(MicroclimateCorePlugin.NODEJS_DEBUG_BROWSER_PREFSKEY);
		if (browserName != null){
			MCLogger.log("Using the " + browserName + " browser from the preferences.");  //$NON-NLS-1$ //$NON-NLS-2$
			// If the previously saved browser is not valid, so load the message dialog again
			if (!foundValidBrowser(browserName)){
				browserName = null;
			}					
		}
		
		if (browserName == null) {
			final AtomicInteger returnCode =new AtomicInteger(0);
			final String[] result = new String[1];
			Display.getDefault().syncExec(new Runnable() {
				@Override
				public void run() {
					WebBrowserSelectionDialog browserSelection = new WebBrowserSelectionDialog(Display.getDefault().getActiveShell(), 
							Messages.BrowserSelectionTitle, 
							null, 
							Messages.BrowserSelectionDescription, 
							MessageDialog.CONFIRM, 
							new String[] { IDialogConstants.OK_LABEL, IDialogConstants.CANCEL_LABEL }, 
							0);
					
					browserSelection.create();
					browserSelection.open();
					returnCode.set(browserSelection.getReturnCode());
					if (returnCode.get() == Window.OK) {
						result[0] = browserSelection.getBrowserName();
						boolean isNodejsDefaultBrowserSet = browserSelection.isNodejsDefaultBrowserSet();
						if (isNodejsDefaultBrowserSet) {
							if (browserSelection.getBrowserName() != null) {
								IPreferenceStore prefs = MicroclimateCorePlugin.getDefault().getPreferenceStore();
								prefs.setValue(MicroclimateCorePlugin.NODEJS_DEBUG_BROWSER_PREFSKEY, browserSelection.getBrowserName());
					        }
						}
					}
				}
			});
			
			if (returnCode.get() == Window.OK){
				browserName = result[0];
			} else {
				// If it is cancel, then do not continue
				return Status.CANCEL_STATUS;
			}
		}
		return openBrowserDialog(chromeDevToolsUrl, browserName);
	}
	
	private IStatus openBrowserDialog(final String chromeDevToolsUrl, final String browserName ) {
		final AtomicInteger returnCode =new AtomicInteger(0);
		Display.getDefault().syncExec(new Runnable() {
			@Override
			public void run() {
				NodeJsBrowserDialog nodeDialog = new NodeJsBrowserDialog(Display.getDefault().getActiveShell(), 
						Messages.NodeJSOpenBrowserTitle, 
						null, 
						Messages.NodeJSOpenBrowserDesc, 
						MessageDialog.CONFIRM, 
						new String[] { IDialogConstants.OK_LABEL}, 
						0, chromeDevToolsUrl, browserName);
				nodeDialog.open();
				returnCode.set(nodeDialog.getReturnCode());
			}
		});
		
		if (returnCode.get() != Window.OK){
			// Cancelled
			return Status.CANCEL_STATUS;
		}		

		return Status.OK_STATUS;
	}
	
	private boolean foundValidBrowser(String browserName){
		BrowserManager bm = BrowserManager.getInstance();
        if (bm != null){
	        List<IBrowserDescriptor> browserList = bm.getWebBrowsers();
	        if (browserList != null){
		        
		        int len = browserList.size();
		        
		        for (int i=0;i<len;i++){
		        	IBrowserDescriptor tempBrowser = browserList.get(i);
		        	if (tempBrowser != null && tempBrowser.getLocation() != null && 
		        			!tempBrowser.getLocation().trim().equals("")){ //$NON-NLS-1$
		        		// The location is not empty
		        		String name = tempBrowser.getName();
		        		if (name != null && name.equalsIgnoreCase(browserName)){
		        			return true;
		        		}
		        	}
		        }
	        }
        }
        return false;
	}

}

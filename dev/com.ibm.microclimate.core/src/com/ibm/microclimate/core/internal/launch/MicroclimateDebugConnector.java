/*******************************************************************************
 * Copyright (c) 2018 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package com.ibm.microclimate.core.internal.launch;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.Map;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.model.IDebugTarget;
import org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants;
import org.eclipse.osgi.util.NLS;

import com.ibm.microclimate.core.MicroclimateCorePlugin;
import com.ibm.microclimate.core.internal.MCLogger;
import com.ibm.microclimate.core.internal.messages.Messages;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.connect.AttachingConnector;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.connect.IllegalConnectorArgumentsException;

@SuppressWarnings("restriction")
public class MicroclimateDebugConnector {
	/**
	 * From com.ibm.ws.st.core.internal.launch.BaseLibertyLaunchConfiguration.connectAndWait
	 */
    public static IDebugTarget connectDebugger(ILaunch launch, IProgressMonitor monitor)
    		throws IllegalConnectorArgumentsException, CoreException, IOException {

    	MCLogger.log("Beginning to try to connect debugger"); //$NON-NLS-1$

		ILaunchConfiguration config = launch.getLaunchConfiguration();
		String projectName = config.getAttribute(MicroclimateLaunchConfigDelegate.PROJECT_NAME_ATTR, (String)null);
		String host = config.getAttribute(MicroclimateLaunchConfigDelegate.HOST_ATTR, (String)null);
		int debugPort = config.getAttribute(MicroclimateLaunchConfigDelegate.DEBUG_PORT_ATTR, -1);
		if (projectName == null || host == null || debugPort <= 0) {
        	String msg = "The launch configuration did not contain the required attributes: " + config.getName();		// $NON-NLS-1$
            MCLogger.logError(msg);
            return null;
        }
		
    	MCLogger.log("Debugging on port " + debugPort); //$NON-NLS-1$

		int timeout = MicroclimateCorePlugin.getDefault().getPreferenceStore()
				.getInt(MicroclimateCorePlugin.DEBUG_CONNECT_TIMEOUT_PREFSKEY)
				* 1000;
		MCLogger.log("Debugger connect timeout is " + timeout + "ms"); //$NON-NLS-1$ //$NON-NLS-2$

		// Now prepare the Debug Connector, and try to attach it to the application
		AttachingConnector connector = LaunchUtilities.getAttachingConnector();
		if (connector == null) {
			MCLogger.logError("Could not create debug connector"); //$NON-NLS-1$
			return null;
		}

		Map<String, Connector.Argument> connectorArgs = connector.defaultArguments();
        connectorArgs = LaunchUtilities.configureConnector(connectorArgs, host, debugPort);

		boolean retry = false;
		do {
			try {
				VirtualMachine vm = null;
				Exception ex = null;
				int itr = timeout * 4; // We want to check 4 times per second

				if (itr <= 0) {
					itr = 2;
				}

				while (itr-- > 0) {
					if (monitor.isCanceled()) {
						MCLogger.log("User cancelled debugger connecting"); //$NON-NLS-1$
						return null;
					}
					try {
						vm = connector.attach(connectorArgs);
						itr = 0;
						ex = null;
					} catch (Exception e) {
						ex = e;
						if (itr % 8 == 0) {
							MCLogger.log("Waiting for debugger attach."); //$NON-NLS-1$
						}
					}
					try {
						Thread.sleep(250);
					} catch (InterruptedException e1) {
						// do nothing
					}
				}

				if (ex instanceof IllegalConnectorArgumentsException) {
					throw (IllegalConnectorArgumentsException) ex;
				}
				if (ex instanceof InterruptedIOException) {
					throw (InterruptedIOException) ex;
				}
				if (ex instanceof IOException) {
					throw (IOException) ex;
				}

				IDebugTarget debugTarget = null;
				if (vm != null) {
					LaunchUtilities.setDebugTimeout(vm);

					// This appears in the Debug view
					final String debugName = getDebugLaunchName(projectName, host, String.valueOf(debugPort));

					debugTarget = LaunchUtilities
							.createLocalJDTDebugTarget(launch, debugPort, null, vm, debugName, false);

					monitor.worked(1);
					monitor.done();
				}
				return debugTarget;
			} catch (InterruptedIOException e) {
				// timeout, consult status handler if there is one
				IStatus status = new Status(IStatus.ERROR, MicroclimateCorePlugin.PLUGIN_ID,
						IJavaLaunchConfigurationConstants.ERR_VM_CONNECT_TIMEOUT, "", e); //$NON-NLS-1$
				throw new CoreException(status);
			}
		} while (retry);
	}

	private static String getDebugLaunchName(String projectName, String host, String debugPort) {
		return NLS.bind(Messages.MicroclimateServerBehaviour_DebugLaunchConfigName,
				new Object[] {
						projectName,
						host,
						debugPort
				});
	}
}

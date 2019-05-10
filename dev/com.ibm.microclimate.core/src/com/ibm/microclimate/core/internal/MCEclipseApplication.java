/*******************************************************************************
 * Copyright (c) 2018, 2019 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package com.ibm.microclimate.core.internal;

import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationType;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.debug.core.model.IDebugTarget;
import org.eclipse.osgi.util.NLS;
import org.eclipse.ui.console.ConsolePlugin;
import org.eclipse.ui.console.IConsole;
import org.eclipse.ui.console.IConsoleManager;

import com.ibm.microclimate.core.MicroclimateCorePlugin;
import com.ibm.microclimate.core.internal.connection.MicroclimateConnection;
import com.ibm.microclimate.core.internal.console.ProjectLogInfo;
import com.ibm.microclimate.core.internal.console.SocketConsole;
import com.ibm.microclimate.core.internal.constants.ProjectCapabilities;
import com.ibm.microclimate.core.internal.constants.ProjectType;
import com.ibm.microclimate.core.internal.launch.MicroclimateLaunchConfigDelegate;
import com.ibm.microclimate.core.internal.messages.Messages;

/**
 * Eclipse specific code for a Microclimate application.  Anything related to Eclipse 
 * (launches, consoles, connecting the debugger, etc.) should go here and not in the
 * MicroclimateApplication class.
 */
public class MCEclipseApplication extends MicroclimateApplication {
	
	// Validation marker
	public static final String MARKER_TYPE = MicroclimateCorePlugin.PLUGIN_ID + ".validationMarker";
	public static final String CONNECTION_URL = "connectionUrl";
	public static final String PROJECT_ID = "projectId";
	public static final String QUICK_FIX_ID = "quickFixId";
	public static final String QUICK_FIX_DESCRIPTION = "quickFixDescription";
	
	// in seconds
	public static final int DEFAULT_DEBUG_CONNECT_TIMEOUT = 3;
	
	// Old style consoles, null if not showing
	private IConsole appConsole = null;
	private IConsole buildConsole = null;
	
	// New consoles
	private Set<SocketConsole> activeConsoles = new HashSet<SocketConsole>();
	
	// Debug launch, null if not debugging
	private ILaunch launch = null;

	MCEclipseApplication(MicroclimateConnection mcConnection,
			String id, String name, ProjectType projectType, String pathInWorkspace)
					throws MalformedURLException {
		super(mcConnection, id, name, projectType, pathInWorkspace);
	}
	
	public synchronized boolean hasAppConsole() {
		return appConsole != null;
	}
	
	public synchronized boolean hasBuildConsole() {
		return buildConsole != null;
	}
	
	public synchronized void setAppConsole(IConsole console) {
		this.appConsole = console;
	}
	
	public synchronized void setBuildConsole(IConsole console) {
		this.buildConsole = console;
	}
	
	public synchronized IConsole getAppConsole() {
		return appConsole;
	}
	
	public synchronized IConsole getBuildConsole() {
		return buildConsole;
	}
	
	public synchronized void addConsole(SocketConsole console) {
		activeConsoles.add(console);
	}
	
	public synchronized SocketConsole getConsole(ProjectLogInfo logInfo) {
		for (SocketConsole console : activeConsoles) {
			if (console.logInfo.isThisLogInfo(logInfo)) {
				return console;
			}
		}
		return null;
	}
	
	public synchronized void removeConsole(SocketConsole console) {
		if (console != null) {
			activeConsoles.remove(console);
		}
	}
	
	public synchronized void setLaunch(ILaunch launch) {
		this.launch = launch;
	}
	
	public synchronized ILaunch getLaunch() {
		return launch;
	}
	
	@Override
	public void clearDebugger() {
		if (launch != null) {
			IDebugTarget debugTarget = launch.getDebugTarget();
			if (debugTarget != null && !debugTarget.isDisconnected()) {
				try {
					debugTarget.disconnect();
				} catch (DebugException e) {
					MCLogger.logError("An error occurred while disconnecting the debugger for project: " + name, e); //$NON-NLS-1$
				}
			}
			ILaunchManager launchManager = DebugPlugin.getDefault().getLaunchManager();
			launchManager.removeLaunch(launch);
			ILaunchConfiguration launchConfig = launch.getLaunchConfiguration();
			if (launchConfig != null) {
				try {
					launchConfig.delete();
				} catch (CoreException e) {
					MCLogger.logError("An error occurred while deleting the launch configuration for project: " + name, e); //$NON-NLS-1$
				}
			}
		}
		setLaunch(null);
	}

	@Override
	public void connectDebugger() {
		final MCEclipseApplication app = this;
		Job job = new Job(Messages.ConnectDebugJob) {
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				try {
					if (app.projectType.isLanguage(ProjectType.LANGUAGE_JAVA)) {
						ILaunchManager launchManager = DebugPlugin.getDefault().getLaunchManager();
				        ILaunchConfigurationType launchConfigurationType = launchManager.getLaunchConfigurationType(MicroclimateLaunchConfigDelegate.LAUNCH_CONFIG_ID);
				        ILaunchConfigurationWorkingCopy workingCopy = launchConfigurationType.newInstance((IContainer) null, app.name);
				        MicroclimateLaunchConfigDelegate.setConfigAttributes(workingCopy, app);
				        ILaunchConfiguration launchConfig = workingCopy.doSave();
			            ILaunch launch = launchConfig.launch(ILaunchManager.DEBUG_MODE, monitor);
			            app.setLaunch(launch);
			            return Status.OK_STATUS;
					} else {
						IDebugLauncher launcher = MicroclimateCorePlugin.getDebugLauncher(app.projectType.language);
						if (launcher != null) {
							return launcher.launchDebugger(app);
						}
					}
				} catch (Exception e) {
					MCLogger.logError("An error occurred while trying to launch the debugger for project: " + app.name); //$NON-NLS-1$
					return new Status(IStatus.ERROR, MicroclimateCorePlugin.PLUGIN_ID,
							NLS.bind(Messages.DebugLaunchError, app.name), e);
				}
				return Status.CANCEL_STATUS;
			}
		};
		job.setPriority(Job.LONG);
		job.schedule();
	}

	@Override
	public void reconnectDebugger() {
		// First check if there is a launch and it is registered
		if (launch != null) {
			ILaunchManager launchManager = DebugPlugin.getDefault().getLaunchManager();
			for (ILaunch launchItem : launchManager.getLaunches()) {
				if (launch.equals(launchItem)) {
					// Check if the debugger is still attached (for Liberty, a small change to the app does not require a server restart)
					IDebugTarget debugTarget = launch.getDebugTarget();
					if (debugTarget == null || debugTarget.isDisconnected()) {
						// Clean up
						clearDebugger();
						// Reconnect the debugger
						connectDebugger();
					}
				}
			}
		}
	}
	
	public boolean canAttachDebugger() {
		if (projectType.isLanguage(ProjectType.LANGUAGE_JAVA)) {
			IDebugTarget debugTarget = getDebugTarget();
			return (debugTarget == null || debugTarget.isDisconnected());
		} else {
			IDebugLauncher launcher = MicroclimateCorePlugin.getDebugLauncher(projectType.language);
			if (launcher != null) {
				return launcher.canAttachDebugger(this);
			}
		}
		return false;
		
	}
	
	public void attachDebugger() {
		// Remove any existing launch such as for a disconnected debug target
		if (launch != null) {
			IDebugTarget debugTarget = launch.getDebugTarget();
			if (debugTarget != null && !debugTarget.isDisconnected()) {
				// Already attached
				return;
			}
			clearDebugger();
		}
		connectDebugger();
	}
	
	public IDebugTarget getDebugTarget() {
		if (launch != null) {
			ILaunchManager launchManager = DebugPlugin.getDefault().getLaunchManager();
			for (ILaunch launchItem : launchManager.getLaunches()) {
				if (launch.equals(launchItem)) {
					return launch.getDebugTarget();
				}
			}
		}
		return null;
	}
	
	@Override
	public void dispose() {
		// Clean up the launch
		clearDebugger();
		
		// Clean up the consoles
		List<IConsole> consoleList = new ArrayList<IConsole>();
		if (appConsole != null) {
			consoleList.add(appConsole);
		}
		if (buildConsole != null) {
			consoleList.add(buildConsole);
		}
		consoleList.addAll(activeConsoles);
		if (!consoleList.isEmpty()) {
			IConsoleManager consoleManager = ConsolePlugin.getDefault().getConsoleManager();
			consoleManager.removeConsoles(consoleList.toArray(new IConsole[consoleList.size()]));
		}
		super.dispose();
	}
	
	@Override
	public void resetValidation() {
		// Delete all Microclimate markers for a project
		IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(name);
		if (project != null && project.isAccessible()) {
			try {
				project.deleteMarkers(MARKER_TYPE, true, IResource.DEPTH_INFINITE);
			} catch (CoreException e) {
				MCLogger.logError("Failed to delete existing markers for the " + name + " project.", e); //$NON-NLS-1$
			}
		}
	}
	
	@Override
	public void validationError(String filePath, String message, String quickFixId, String quickFixDescription) {
		validationEvent(IMarker.SEVERITY_ERROR, filePath, message, quickFixId, quickFixDescription);
	}
	
	@Override
	public void validationWarning(String filePath, String message, String quickFixId, String quickFixDescription) {
		validationEvent(IMarker.SEVERITY_WARNING, filePath, message, quickFixId, quickFixDescription);
	}
	
    private void validationEvent(int severity, String filePath, String message, String quickFixId, String quickFixDescription) {
        // Create a marker and quick fix (if available) on the specific file if there is one or the project if not.
    	try {
        	IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(name);
        	if (project != null && project.isAccessible()) {
	        	IResource resource = project;
	        	if (filePath != null && !filePath.isEmpty()) {
		        	IPath path = new Path(filePath);
		        	if (filePath.startsWith(project.getName())) {
		        		path = path.removeFirstSegments(1);
		        	}
		        	IFile file = project.getFile(path);
		        	if (file != null && file.exists()) {
		        		resource = file;
		        	}
	        	}
	            final IMarker marker = resource.createMarker(MARKER_TYPE);
	            marker.setAttribute(IMarker.SEVERITY, IMarker.SEVERITY_ERROR);
	            marker.setAttribute(IMarker.MESSAGE, message);
	            if (quickFixId != null && !quickFixId.isEmpty()) {
	            	marker.setAttribute(CONNECTION_URL, mcConnection.baseUrl.toString());
	            	marker.setAttribute(PROJECT_ID, projectID);
	            	marker.setAttribute(QUICK_FIX_ID, quickFixId);
	            	marker.setAttribute(QUICK_FIX_DESCRIPTION, quickFixDescription);
	            }
        	}
        } catch (CoreException e) {
            MCLogger.logError("Failed to create a marker for the " + name + " application: " + message, e); //$NON-NLS-1$
        }
    }

	@Override
	public boolean supportsDebug() {
		// Only supported for certain project types
		if (projectType.isType(ProjectType.TYPE_LIBERTY) || projectType.isType(ProjectType.TYPE_SPRING) || projectType.isType(ProjectType.TYPE_NODEJS)) {
			// And only if the project supports it
			ProjectCapabilities capabilities = getProjectCapabilities();
			return (capabilities.supportsDebugMode() || capabilities.supportsDebugNoInitMode()) && capabilities.canRestart();
		}
		return false;
	}

	@Override
	public void buildComplete() {
		IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(name);
    	if (project != null && project.isAccessible()) {
    		Job job = new Job(NLS.bind(Messages.RefreshResourceJobLabel, project.getName())) {
				@Override
				protected IStatus run(IProgressMonitor monitor) {
					try {
						project.refreshLocal(IResource.DEPTH_INFINITE, monitor);
			            return Status.OK_STATUS;
					} catch (Exception e) {
						MCLogger.logError("An error occurred while refreshing the resource: " + project.getLocation()); //$NON-NLS-1$
						return new Status(IStatus.ERROR, MicroclimateCorePlugin.PLUGIN_ID,
								NLS.bind(Messages.RefreshResourceError, project.getLocation()), e);
					}
				}
			};
			job.setPriority(Job.LONG);
			job.schedule();
    	}
	}
    
}

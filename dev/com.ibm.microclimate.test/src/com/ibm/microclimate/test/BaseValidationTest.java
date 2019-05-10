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

package com.ibm.microclimate.test;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import com.ibm.microclimate.core.internal.MicroclimateApplication;
import com.ibm.microclimate.core.internal.constants.BuildStatus;
import com.ibm.microclimate.test.util.Condition;
import com.ibm.microclimate.test.util.MicroclimateUtil;
import com.ibm.microclimate.test.util.TestUtil;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public abstract class BaseValidationTest extends BaseTest {
	
	protected static String text;
	protected static String dockerfile;
	
    @Test
    public void test01_doSetup() throws Exception {
        TestUtil.print("Starting test: " + getName());
        doSetup();
    }
    
    @Test
    public void test02_checkApp() throws Exception {
    	checkApp(text);
    }
    
    @Test
    public void test03_disableAutoBuild() throws Exception {
    	setAutoBuild(false);
    }
    
    @Test
    public void test04_removeDockerfile() throws Exception {
    	IPath path = connection.getWorkspacePath().append(projectName);
    	path = path.append(dockerfile);
    	TestUtil.deleteFile(path.toOSString());
    	// Wait and check that the build is still marked as successful
    	Thread.sleep(2000);
    	MicroclimateApplication app = connection.getAppByName(projectName);
    	assertTrue("The application build should be successful for project: " + projectName, app.getBuildStatus() == BuildStatus.SUCCESS);
    	// Check that there are no markers
    	IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(projectName);
    	assertTrue("There should be no microclimate markers on project: " + projectName, getMarkers(project).length == 0);
    }
    
    @Test
    public void test05_runValidation() throws Exception {
    	runValidation();
    	IMarker[] markers = waitForMarkers(project);
    	assertTrue("There should be a marker for the missing dockerfile for project: " + projectName, markers.length > 0);
    }
    
    @Test
    public void test06_runQuickFix() throws Exception {
    	IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(projectName);
    	runQuickFix(project);
    	// Validation should get run again automatically when the quick fix is complete and
    	// this should clear the marker
    	waitForMarkersCleared(project);
    	// Check that the dockerfile is there
    	IPath path = connection.getWorkspacePath().append(projectName);
    	path = path.append(srcPath);
    	assertTrue("The dockerfile should be regenerated", path.toFile().exists());
    }

    @Test
    public void test99_tearDown() {
    	doTearDown();
    	TestUtil.print("Ending test: " + getName());
    }
    
    public IMarker[] waitForMarkers(IResource resource) throws Exception {
		TestUtil.wait(new Condition() {
			@Override
			public boolean test() {
				try {
					return getMarkers(resource).length > 0;
				} catch (Exception e) {
					return false;
				}
			}
		}, 10, 1);
		return getMarkers(resource);
	}
    
    public void waitForMarkersCleared(IResource resource) throws Exception {
    	TestUtil.wait(new Condition() {
			@Override
			public boolean test() {
				try {
					return getMarkers(resource).length == 0;
				} catch (Exception e) {
					return false;
				}
			}
		}, 10, 1);
    }
	

}

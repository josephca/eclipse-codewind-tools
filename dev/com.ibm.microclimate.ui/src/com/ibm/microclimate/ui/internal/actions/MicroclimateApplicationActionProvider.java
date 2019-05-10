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

package com.ibm.microclimate.ui.internal.actions;

import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.ui.navigator.CommonActionProvider;
import org.eclipse.ui.navigator.ICommonActionExtensionSite;
import org.eclipse.ui.navigator.ICommonMenuConstants;

/**
 * Action provider for Microclimate applications in the Microclimate view.
 */
public class MicroclimateApplicationActionProvider extends CommonActionProvider {
	
	private ValidateAction validateAction;
	private AttachDebuggerAction attachDebuggerAction;
	private OpenAppMonitorAction openAppMonitorAction;
	private DeleteProjectAction deleteProjectAction;
	
    @Override
    public void init(ICommonActionExtensionSite aSite) {
        super.init(aSite);
        ISelectionProvider selProvider = aSite.getStructuredViewer();
        validateAction = new ValidateAction(selProvider);
        attachDebuggerAction = new AttachDebuggerAction(selProvider);
        openAppMonitorAction = new OpenAppMonitorAction(selProvider);
        deleteProjectAction = new DeleteProjectAction(selProvider);
    }
    
    @Override
    public void fillContextMenu(IMenuManager menu) {
    	if (validateAction.showAction()) {
    		menu.appendToGroup(ICommonMenuConstants.GROUP_BUILD, validateAction);
    	}
    	if (attachDebuggerAction.showAction()) {
    		menu.appendToGroup(ICommonMenuConstants.GROUP_GENERATE, attachDebuggerAction);
    	}
    	if (openAppMonitorAction.showAction()) {
    		menu.appendToGroup(ICommonMenuConstants.GROUP_OPEN, openAppMonitorAction);
    	}
    	menu.appendToGroup(ICommonMenuConstants.GROUP_ADDITIONS, deleteProjectAction);
    	
    }

}

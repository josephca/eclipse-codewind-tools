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

package com.ibm.microclimate.ui.internal.console;

import org.eclipse.debug.internal.ui.IInternalDebugUIConstants;
import org.eclipse.debug.ui.DebugUITools;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;

import com.ibm.microclimate.core.internal.console.SocketConsole;
import com.ibm.microclimate.ui.MicroclimateUIPlugin;
import com.ibm.microclimate.ui.internal.messages.Messages;

public class ShowOnContentChangeAction extends Action {
	
	SocketConsole console;
	
	public ShowOnContentChangeAction(SocketConsole console) {
		super(Messages.ShowOnContentChangeAction, IAction.AS_CHECK_BOX);
		setToolTipText(Messages.ShowOnContentChangeAction);
		setId(MicroclimateUIPlugin.PLUGIN_ID + ".ShowOnContentChangeAction"); //$NON-NLS-1$
        setImageDescriptor(DebugUITools.getImageDescriptor(IInternalDebugUIConstants.IMG_ELCL_STANDARD_OUT));
		this.console = console;
	}

	@Override
	public void run() {
		console.setShowOnUpdate(isChecked());
	}
	
}

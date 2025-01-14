/*
 * JBoss, Home of Professional Open Source
 * Copyright 2006, Red Hat Middleware LLC, and individual contributors 
 * as indicated by the @author tags. 
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors. 
 * This copyrighted material is made available to anyone wishing to use,
 * modify, copy, or redistribute it subject to the terms and conditions
 * of the GNU Lesser General Public License, v. 2.1.
 * This program is distributed in the hope that it will be useful, but WITHOUT A 
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A 
 * PARTICULAR PURPOSE.  See the GNU Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public License,
 * v.2.1 along with this distribution; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, 
 * MA  02110-1301, USA.
 * 
 * (C) 2005-2006,
 * @author JBoss Inc.
 */
/*
 * Copyright (C) 2005,
 *
 * Arjuna Technologies Limited,
 * Newcastle upon Tyne,
 * Tyne and Wear,
 * UK.  	    
 *
 * $Id: LogWriteStateManager.java 2342 2006-03-30 13:06:17Z  $
 */

package com.arjuna.ats.internal.arjuna;

import com.arjuna.ats.arjuna.ObjectModel;
import com.arjuna.ats.arjuna.ObjectStatus;
import com.arjuna.ats.arjuna.ObjectType;
import com.arjuna.ats.arjuna.StateManager;
import com.arjuna.ats.arjuna.common.Uid;
import com.arjuna.ats.arjuna.coordinator.AddOutcome;
import com.arjuna.ats.arjuna.coordinator.BasicAction;
import com.arjuna.ats.arjuna.logging.tsLogger;
import com.arjuna.ats.arjuna.state.OutputObjectState;

/**
 * @author Mark Little (mark@arjuna.com)
 * @version $Id: LogWriteStateManager.java 2342 2006-03-30 13:06:17Z  $
 * @since JTS 4.1.
 */

/**
 * Needs further consideration and then completion.
 */

public class LogWriteStateManager extends StateManager
{

    /**
     * This operation enables (or disables) the write-to-log optimisation.
     * Since it is only called during the commit protocol, implementation
     * classes can set the value dynamically through some application
     * specific logic.
     */

    public boolean writeOptimisation ()
    {
	return true;
    }

    protected LogWriteStateManager (Uid objUid)
    {
	super(objUid);
    }
    
    protected LogWriteStateManager (Uid objUid, int ot)
    {
	super(objUid, ot, ObjectModel.SINGLE);
    }

    protected LogWriteStateManager (Uid objUid, int ot, int om)
    {
	super(objUid, ot, om);
    }    

    protected LogWriteStateManager ()
    {
	super(ObjectType.RECOVERABLE);
    }
    
    protected LogWriteStateManager (int ot)
    {
	super(ot);
    }
    
    protected synchronized boolean modified ()
    {
	if (tsLogger.logger.isTraceEnabled()) {
        tsLogger.logger.trace("StateManager::modified() for object-id " + get_uid());
    }

	if ((super.objectType() == ObjectType.RECOVERABLE) && (super.objectModel == ObjectModel.SINGLE))
	    return super.modified();
	
	BasicAction action = BasicAction.Current();
	
	if ((super.objectType() == ObjectType.NEITHER) || (super.status() == ObjectStatus.DESTROYED)) /*  NEITHER => no recovery info */
	{
	    return true;
	}
    
	if (super.status() == ObjectStatus.PASSIVE) {
        tsLogger.i18NLogger.warn_StateManager_10();

        activate();
    }
	
	/*
	 * Need not have gone through active if new object.
	 */

	if (status() == ObjectStatus.PASSIVE_NEW)
	    setStatus(ObjectStatus.ACTIVE_NEW);
    
	if (action != null)
	{
	    /*
	     * Check if this is the first call to modified in this action.
	     * BasicList insert returns FALSE if the entry is already
	     * present.
	     */

	    createLists();
	    
	    synchronized (modifyingActions)
	    {
		if ((!modifyingActions.isEmpty()) &&
		    (modifyingActions.get(action.get_uid()) != null))
		{
		    return true;
		}
		else
		    modifyingActions.put(action.get_uid(), action);
	    }
	
	    /* If here then its a new action */
	
	    OutputObjectState state = new OutputObjectState(objectUid, type());
	    int rStatus = AddOutcome.AR_ADDED;
	
	    if (save_state(state, ObjectType.RECOVERABLE))
	    {
		TxLogWritePersistenceRecord record = new TxLogWritePersistenceRecord(state, super.getStore(), this);
	    
		if ((rStatus = action.add(record)) != AddOutcome.AR_ADDED)
		{
		    synchronized(modifyingActions)
		    {
			modifyingActions.remove(action.get_uid());  // remember to unregister with action
		    }
		    
		    record = null;

		    return false;
		}
	    }
	    else
		return false;
	}
	
	return true;
    }
    
}

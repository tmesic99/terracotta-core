/*
 * All content copyright Terracotta, Inc., unless otherwise indicated. All rights reserved.
 */

package com.tc.objectserver.event;

import com.google.common.collect.Multimap;
import com.tc.net.ClientID;
import com.tc.object.gtx.GlobalTransactionID;
import com.tc.server.ServerEvent;

public interface ServerEventBuffer {

  public Multimap<ClientID, ServerEvent> getServerEvent(GlobalTransactionID gtxId);

}

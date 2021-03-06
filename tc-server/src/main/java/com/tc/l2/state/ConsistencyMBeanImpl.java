/*
 *
 *  The contents of this file are subject to the Terracotta Public License Version
 *  2.0 (the "License"); You may not use this file except in compliance with the
 *  License. You may obtain a copy of the License at
 *
 *  http://terracotta.org/legal/terracotta-public-license.
 *
 *  Software distributed under the License is distributed on an "AS IS" basis,
 *  WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License for
 *  the specific language governing rights and limitations under the License.
 *
 *  The Covered Software is Terracotta Core.
 *
 *  The Initial Developer of the Covered Software is
 *  Terracotta, Inc., a Software AG company
 *
 */
package com.tc.l2.state;

import com.tc.management.AbstractTerracottaMBean;
import org.terracotta.server.ServerEnv;

import java.util.Collection;
import java.util.Properties;
import java.util.stream.Collectors;

public class ConsistencyMBeanImpl extends AbstractTerracottaMBean implements ConsistencyMBean {

  private final ConsistencyManager consistencyManager;

  public ConsistencyMBeanImpl(ConsistencyManager consistencyManager) throws Exception {
    super(ConsistencyMBean.class, false);
    this.consistencyManager = consistencyManager;
  }

  @Override
  public boolean isBlocked() {
    return consistencyManager.lastTransitionSuspended();
  }

  @Override
  public boolean isStuck() {
    return consistencyManager.lastTransitionSuspended();
  }

  @Override
  public Collection<String> requestedActions() {
    return consistencyManager.requestedActions().stream().map(t->t.toString()).collect(Collectors.toList());
  }

  @Override
  public void allowRequestedTransition() {
    ServerEnv.getServer().audit("Allow server state transition invoked", new Properties());
    consistencyManager.allowLastTransition();
  }

  @Override
  public void reset() {
    //
  }
}

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
package com.tc.objectserver.persistence;

import java.io.Serializable;

/**
 * The data required to persist an entity in persistent storage.  It is broken down into key and value components to allow
 * for bulk retrieval of all entity data (this is why key data may be redundantly stored in the value) as well as quick
 * simple lookup in a key-value mapping.
 */
public class EntityData {
  private EntityData() {}
  
  public static class Key implements Serializable {
    public String className;
    public String entityName;
    
    @Override
    public int hashCode() {
      return this.className.hashCode() ^ this.entityName.hashCode();
    }
    @Override
    public boolean equals(Object obj) {
      boolean isEqual = false;
      if (obj instanceof EntityData.Key) {
        EntityData.Key other = (EntityData.Key)obj;
        isEqual = this.className.equals(other.className) && this.entityName.equals(other.entityName); 
      }
      return isEqual;
    }
  }
  
  public static class Value implements Serializable {
    public String className;
    public long version;
    public long consumerID;
    public String entityName;
    public byte[] configuration;
  }
}

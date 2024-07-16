package org.keycloak.jgroups.jpa;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

@Entity
@IdClass(JGroupsPingEntity.Key.class)
@Table(name="JGROUPSPING")
public class JGroupsPingEntity {
   @Id
   @Column(name="own_addr")
   String address;

   @Id
   @Column(name = "cluster_name")
   String clusterName;

   @Column(name = "ping_data")
   byte[] data;

   public String getAddress() {
      return address;
   }

   public void setAddress(String address) {
      this.address = address;
   }

   public String getClusterName() {
      return clusterName;
   }

   public void setClusterName(String clusterName) {
      this.clusterName = clusterName;
   }

   public byte[] getData() {
      return data;
   }

   public void setData(byte[] data) {
      this.data = data;
   }

   static class Key implements Serializable {
      String address;
      String clusterName;

      public Key() {}

      public Key(String address, String clusterName) {
         this.address = address;
         this.clusterName = clusterName;
      }

      @Override
      public boolean equals(Object o) {
         if (this == o) return true;
         if (o == null || getClass() != o.getClass()) return false;
         Key key = (Key) o;
         return Objects.equals(address, key.address) && Objects.equals(clusterName, key.clusterName);
      }

      @Override
      public int hashCode() {
         return Objects.hash(address, clusterName);
      }
   }
}

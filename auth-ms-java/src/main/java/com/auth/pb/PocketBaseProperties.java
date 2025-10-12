package com.auth.pb;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "pocketbase")
public class PocketBaseProperties {
  private String url;
  private String collection;
  private String merchantField = "merchantId";
  private String rolesField = "roles";
  private Timeout timeout;

  public static class Timeout {
    private int connect = 5000;
    private int read = 15000;
    private int write = 15000;

    public int getConnect() { return connect; }
    public void setConnect(int connect) { this.connect = connect; }
    public int getRead() { return read; }
    public void setRead(int read) { this.read = read; }
    public int getWrite() { return write; }
    public void setWrite(int write) { this.write = write; }
  }

  public String getUrl() { return url; }
  public void setUrl(String url) { this.url = url; }
  public String getCollection() { return collection; }
  public void setCollection(String collection) { this.collection = collection; }
  public String getMerchantField() { return merchantField; }
  public void setMerchantField(String merchantField) { this.merchantField = merchantField; }
  public String getRolesField() { return rolesField; }
  public void setRolesField(String rolesField) { this.rolesField = rolesField; }
  public Timeout getTimeout() { return timeout; }
  public void setTimeout(Timeout timeout) { this.timeout = timeout; }
}

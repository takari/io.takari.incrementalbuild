package io.takari.builder.enforcer;

public interface Policy {

  public void checkSocketPermission();

  public void checkPropertyPermission(String action, String name);

  public void checkExec(String cmd);

  public void checkRead(String file);

  public void checkWrite(String file);
  
}
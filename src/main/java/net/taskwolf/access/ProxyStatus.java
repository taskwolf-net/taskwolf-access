package net.taskwolf.access;

public enum ProxyStatus {
  ENABLED,
  DISABLED;

  public boolean isEnabled() {
    return this == ENABLED;
  }

  public boolean isDisabled() {
    return this == DISABLED;
  }
}

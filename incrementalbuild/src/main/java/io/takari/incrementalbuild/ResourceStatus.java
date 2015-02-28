package io.takari.incrementalbuild;

public enum ResourceStatus {

  /**
   * Resource is new in this build, i.e. it was not present in the previous build.
   */
  NEW,

  /**
   * Resource changed since previous build.
   */
  MODIFIED,

  /**
   * Resource did not changed since previous build.
   */
  UNMODIFIED,

  /**
   * Resource was removed since previous build.
   */
  REMOVED;
}

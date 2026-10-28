package com.abluepoint.summer.sample.physicalconnection.domain;
/** Execution binding is checked before any external call or permit consumption. */
public final class PhysicalExecutionRules {
 private PhysicalExecutionRules() {}
 public static void requireBinding(String service, String connection, String tool, String schema, String arguments,
   String expectedService, String expectedConnection, String expectedTool, String expectedSchema, String expectedArguments) {
  if (!expectedService.equals(service) || !expectedConnection.equals(connection) || !expectedTool.equals(tool)
    || !expectedSchema.equals(schema) || !expectedArguments.equals(arguments)) throw new PhysicalExecutionConflictException("Permit binding mismatch");
 }
 public static int retries(Integer retries) { return retries == null ? 0 : Math.max(0, Math.min(3,retries)); }
}

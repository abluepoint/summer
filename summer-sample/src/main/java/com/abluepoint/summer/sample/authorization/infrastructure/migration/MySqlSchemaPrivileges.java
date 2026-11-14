package com.abluepoint.summer.sample.authorization.infrastructure.migration;

/** GRANT database identifiers are patterns even inside backticks; quote wildcards literally. */
final class MySqlSchemaPrivileges {
  private MySqlSchemaPrivileges() {}

  static String exactObject(String schema) {
    if (schema==null || !schema.matches("[A-Za-z0-9_]{1,64}"))
      throw new IllegalArgumentException("Explicit literal schema required");
    return "`"+schema.replace("_","\\_")+"`.*";
  }
}

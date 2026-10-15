package com.abluepoint.summer.sample.organization;

class OrganizationUnitRequest {
    private boolean parentIdPresent;
    private String parentId;
    @com.fasterxml.jackson.annotation.JsonProperty private String unitType;
    @com.fasterxml.jackson.annotation.JsonProperty private String name;
    @com.fasterxml.jackson.annotation.JsonProperty private String code;
    @com.fasterxml.jackson.annotation.JsonProperty private String leaderMemberId;
    @com.fasterxml.jackson.annotation.JsonProperty private String description;
    @com.fasterxml.jackson.annotation.JsonProperty private Integer sortOrder;
    @com.fasterxml.jackson.annotation.JsonProperty private String status;
    @com.fasterxml.jackson.annotation.JsonSetter("parentId")
    public void setParentId(String value) { parentIdPresent = true; parentId = value; }
    boolean parentIdPresent() { return parentIdPresent; }
    String parentId() { return parentId; }
    String unitType() { return unitType; }
    String name() { return name; }
    String code() { return code; }
    String leaderMemberId() { return leaderMemberId; }
    String description() { return description; }
    Integer sortOrder() { return sortOrder; }
    String status() { return status; }
    @Override public String toString() { return java.util.Arrays.asList(parentIdPresent, parentId, unitType, name, code, leaderMemberId, description, sortOrder, status).toString(); }
}

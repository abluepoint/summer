package com.abluepoint.summer.sample.authorization.api.permission;

import com.abluepoint.summer.mvc.annotation.Field;
import com.abluepoint.summer.mvc.annotation.FieldList;
import com.abluepoint.summer.sample.authorization.application.permission.PlatformDirectoryService;
import com.abluepoint.summer.sample.authorization.application.permission.PlatformDirectoryService.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/platform")
public class PlatformDirectoryController {
  private final PlatformDirectoryService directory;

  public PlatformDirectoryController(PlatformDirectoryService directory) {
    this.directory = directory;
  }

  @GetMapping("/administrators")
  public Object roster(Authentication a) {
    return directory.roster(a);
  }

  @Field(param = "id", style = "platformDepartmentIdStyle")
  @FieldList(param = "administrators", required = false, maxSize = 10000,
      items = @Field(param = "username", style = "platformAccountNameStyle"))
  @PutMapping("/administrators/user-groups/{id}")
  public Object assignGroup(Authentication actor, @PathVariable String id,
      @RequestHeader(value = "If-Match", required = false) String version,
      @RequestBody GroupAdministrators input) {
    directory.assignGroup(actor, version, id, input);
    return java.util.Map.of("saved", true);
  }

  @Field(param = "q", style = "platformDirectoryTextStyle", required=false)
  @GetMapping("/administrators/candidates")
  public Object personal(Authentication a, @RequestParam(defaultValue="") String q) {
    return directory.candidates(a, q, true, null);
  }

  @Field(param = "username", style = "platformAccountNameStyle")
  @Field(param = "displayName", style = "platformDirectoryTextStyle", required = false, nullable = true)
  @PostMapping("/administrators")
  public Object add(
      Authentication a,
      @RequestBody AddAdministrator input,
      @RequestHeader(value = "If-Match", required = false) String version) {
    directory.add(a, version, input);
    return java.util.Map.of("saved", true);
  }

  @Field(param = "username", style = "platformAccountNameStyle")
  @DeleteMapping("/administrators/{username}")
  public Object remove(
      Authentication a,
      @PathVariable String username,
      @RequestHeader(value = "If-Match", required = false) String version) {
    directory.remove(a, version, username);
    return java.util.Map.of("saved", true);
  }

  @GetMapping("/platform-departments")
  public Object departments(Authentication a) {
    return directory.directory(a);
  }

  @Field(param = "q", style = "platformDirectoryTextStyle", required=false)
  @Field(param = "departmentId", style = "platformDepartmentIdStyle")
  @GetMapping("/platform-departments/candidates")
  public Object candidates(
      Authentication a, @RequestParam(defaultValue="") String q, @RequestParam String departmentId) {
    return directory.candidates(a, q, false, departmentId);
  }

  @Field(param = "id", style = "platformDepartmentIdStyle")
  @GetMapping("/platform-departments/{id}/deletion-impact")
  public Object impact(Authentication a, @PathVariable String id) {
    return directory.impact(a, id);
  }

  @Field(param = "name", style = "platformDirectoryTextStyle")
  @Field(param = "parentId", style = "platformDepartmentIdStyle", required = false, nullable = true)
  @Field(param = "sortOrder", style = "platformDepartmentOrderStyle")
  @Field(param = "enabled", style = "booleanStyle")
  @PostMapping("/platform-departments")
  public Object create(
      Authentication a,
      @RequestBody DepartmentEdit input,
      @RequestHeader(value = "If-Match", required = false) String version) {
    directory.saveDepartment(a, version, null, input);
    return java.util.Map.of("saved", true);
  }

  @Field(param = "id", style = "platformDepartmentIdStyle")
  @Field(param = "name", style = "platformDirectoryTextStyle")
  @Field(param = "parentId", style = "platformDepartmentIdStyle", required = false, nullable = true)
  @Field(param = "sortOrder", style = "platformDepartmentOrderStyle")
  @Field(param = "enabled", style = "booleanStyle")
  @PutMapping("/platform-departments/{id}")
  public Object edit(
      Authentication a,
      @PathVariable String id,
      @RequestBody DepartmentEdit input,
      @RequestHeader(value = "If-Match", required = false) String version) {
    directory.saveDepartment(a, version, id, input);
    return java.util.Map.of("saved", true);
  }

  @Field(param = "id", style = "platformDepartmentIdStyle")
  @DeleteMapping("/platform-departments/{id}")
  public Object delete(
      Authentication a,
      @PathVariable String id,
      @RequestHeader(value = "If-Match", required = false) String version) {
    directory.delete(a, version, id);
    return java.util.Map.of("saved", true);
  }

  @Field(param = "id", style = "platformDepartmentIdStyle")
  @Field(param = "username", style = "platformAccountNameStyle")
  @Field(param = "action", style = "platformMembershipActionStyle")
  @Field(param = "newPrimaryId", style = "platformDepartmentIdStyle", required = false, nullable = true)
  @PutMapping("/platform-departments/{id}/members/{username}")
  public Object member(
      Authentication a,
      @PathVariable String id,
      @PathVariable String username,
      @RequestBody MemberEdit input,
      @RequestHeader(value = "If-Match", required = false) String version) {
    directory.member(a, version, id, username, input);
    return java.util.Map.of("saved", true);
  }
}

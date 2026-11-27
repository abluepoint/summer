package com.abluepoint.summer.sample.authorization.api.permission;

import com.abluepoint.summer.mvc.annotation.*;
import com.abluepoint.summer.sample.authorization.application.permission.PlatformUserGroups;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/platform/user-groups")
public class PlatformUserGroupController {
  private final PlatformUserGroups groups;

  public PlatformUserGroupController(PlatformUserGroups groups) { this.groups = groups; }

  @GetMapping
  public Object list(Authentication actor) { return groups.list(actor); }

  @Field(param="description", style="groupDescriptionStyle", required=false)
  @Field(param="name", style="platformDirectoryTextStyle")
  @FieldList(param="roles", required=false, maxSize=10000, items=@Field(param="roleId", style="platformAccountNameStyle"))
  @PostMapping
  public Object create(Authentication actor,
      @RequestHeader(value="If-Match", required=false) String version, @RequestBody PlatformUserGroups.Edit input) {
    return Map.of("id", groups.save(actor,version,null,input));
  }

  @Field(param="id", style="platformDepartmentIdStyle")
  @Field(param="description", style="groupDescriptionStyle", required=false)
  @Field(param="operation", style="groupEditOperationStyle", required=false)
  @Field(param="name", style="platformDirectoryTextStyle", required=false)
  @FieldList(param="roles", required=false, maxSize=10000, items=@Field(param="roleId", style="platformAccountNameStyle"))
  @PutMapping("/{id}")
  public Object edit(Authentication actor, @PathVariable String id,
      @RequestHeader(value="If-Match", required=false) String version, @RequestBody PlatformUserGroups.Edit input) {
    return Map.of("id", groups.save(actor,version,id,input));
  }

  @Field(param="id", style="platformDepartmentIdStyle")
  @DeleteMapping("/{id}")
  public Object delete(Authentication actor, @PathVariable String id,
      @RequestHeader(value="If-Match", required=false) String version) {
    groups.delete(actor,version,id);
    return Map.of("deleted",true);
  }
}

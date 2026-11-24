package com.abluepoint.summer.sample.dictionary;

import com.abluepoint.summer.common.error.CommonErrorCode;
import com.abluepoint.summer.common.error.SummerException;
import com.abluepoint.summer.mvc.annotation.Field;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/platform/administrative-divisions")
class AdministrativeDivisionController {
    private final JdbcTemplate db;

    AdministrativeDivisionController(JdbcTemplate db) {
        this.db = db;
    }

    record Row(String id, String code, String name, int level, String parentId, LocalDate sourceDate,
               Instant updatedAt, long revision, boolean hasChildren) {}
    record Create(String code, String name, int level, String parentId) {}
    record Rename(String name, long revision) {}

    @GetMapping
    @Field(param = "parentId", required = false, style = "divisionIdStyle")
    @PreAuthorize("@businessMethodAccess.require(authentication, 'platform.dictionary.read') or hasAuthority('SCOPE_platform.dictionary.read')")
    List<Row> children(@RequestParam(required = false) String parentId) {
        String condition = parentId == null ? "d.parent_id IS NULL" : "d.parent_id = ?";
        return db.query("""
                SELECT d.*, EXISTS(SELECT 1 FROM summer_administrative_division c WHERE c.parent_id=d.id) AS has_children
                FROM summer_administrative_division d WHERE
                """ + condition + " ORDER BY d.code IS NULL, d.code, d.id", (rs, index) ->
                new Row(rs.getString("id"), rs.getString("code"), rs.getString("name"),
                        rs.getInt("area_level"), rs.getString("parent_id"), rs.getObject("source_date", LocalDate.class),
                        rs.getTimestamp("updated_at").toInstant(), rs.getLong("revision"), rs.getBoolean("has_children")),
                parentId == null ? new Object[0] : new Object[]{parentId});
    }

    @PostMapping
    @Field(param = "code", style = "divisionCodeStyle")
    @Field(param = "name", style = "divisionNameStyle")
    @Field(param = "level", style = "divisionLevelStyle")
    @Field(param = "parentId", required = false, nullable = true, style = "divisionIdStyle")
    @Transactional
    @PreAuthorize("@businessMethodAccess.require(authentication, 'platform.dictionary.create', 'platform.dictionary.write') or hasAnyAuthority('SCOPE_platform.dictionary.create','SCOPE_platform.dictionary.write')")
    void create(@RequestBody Create body) {
        String name = name(body.name());
        int level = body.level();
        if (level < 1 || level > 4 || body.code() == null || !body.code().matches(level == 4 ? "[0-9]{9}" : "[0-9]{6}")
                || (level == 1) != (body.parentId() == null)) {
            throw SummerException.withMessage(CommonErrorCode.INVALID_ARGUMENT, "请检查区划层级、上级和区划代码");
        }
        if (body.parentId() != null) {
            var parents = db.queryForList("SELECT area_level FROM summer_administrative_division WHERE id=? FOR UPDATE",
                    Integer.class, body.parentId());
            if (parents.isEmpty() || parents.getFirst() >= level) {
                throw SummerException.withMessage(CommonErrorCode.INVALID_ARGUMENT, "上级区划无效");
            }
        }
        try {
            db.update("INSERT INTO summer_administrative_division (id,code,name,area_level,parent_id) VALUES (?,?,?,?,?)",
                    UUID.randomUUID().toString(), body.code(), name, level, body.parentId());
        } catch (DataIntegrityViolationException conflict) {
            throw SummerException.withMessage(CommonErrorCode.CONFLICT, "区划代码已存在或上级区划已变更", conflict);
        }
    }

    @PutMapping("/{id}")
    @Field(param = "id", style = "divisionIdStyle")
    @Field(param = "name", style = "divisionNameStyle")
    @Field(param = "revision", style = "positiveRevisionStyle")
    @PreAuthorize("@businessMethodAccess.require(authentication, 'platform.dictionary.update', 'platform.dictionary.write') or hasAnyAuthority('SCOPE_platform.dictionary.update','SCOPE_platform.dictionary.write')")
    void rename(@PathVariable String id, @RequestBody Rename body) {
        changed(db.update("""
                UPDATE summer_administrative_division SET name=?, revision=revision+1, updated_at=CURRENT_TIMESTAMP(6)
                WHERE id=? AND revision=?
                """, name(body.name()), id, body.revision()));
    }

    @DeleteMapping("/{id}")
    @Field(param = "id", style = "divisionIdStyle")
    @Field(param = "revision", style = "positiveRevisionStyle")
    @PreAuthorize("@businessMethodAccess.require(authentication, 'platform.dictionary.write') or hasAuthority('SCOPE_platform.dictionary.write')")
    void delete(@PathVariable String id, @RequestParam long revision) {
        try {
            changed(db.update("DELETE FROM summer_administrative_division WHERE id=? AND revision=?", id, revision));
        } catch (DataIntegrityViolationException conflict) {
            throw SummerException.withMessage(CommonErrorCode.CONFLICT, "请先处理下级区划", conflict);
        }
    }

    private static String name(String value) {
        if (!DictionaryParameterConfiguration.validDivisionName(value)) {
            throw SummerException.withMessage(CommonErrorCode.INVALID_ARGUMENT, "区划名称应为 1 至 120 个字符");
        }
        return value.strip();
    }

    private static void changed(int count) {
        if (count != 1) throw SummerException.withMessage(CommonErrorCode.CONFLICT, "区划已变更，请刷新后重试");
    }
}

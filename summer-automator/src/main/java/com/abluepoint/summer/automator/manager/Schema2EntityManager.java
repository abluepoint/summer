package com.abluepoint.summer.automator.manager;

import com.abluepoint.summer.automator.data.SchemaRepository;
import com.abluepoint.summer.automator.model.data.Column;
import com.abluepoint.summer.automator.util.FreemarkerUtils;
import com.abluepoint.summer.automator.util.NameUtils;
import com.abluepoint.summer.automator.util.ProjectFileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;

/**
 * MySQL		JavaType	备注
 * char       String	    定长字符
 * varchar	String	    变长字符
 * tinyint	byte	    1字节
 * smallint   short	    2字节
 * int		int	        4字节
 * float		float	    4字节
 * bigint		long	    8字节
 * double		double	    8字节
 * bit		boolean	    布尔类型
 * <p>
 * date		java.util.Date	YYYY-MM-DD
 * time		java.util.Date	HH:MM:SS
 * timestamp	java.util.Date	YYYY-MM-DD HH:MM:SS(并不是严格的这种格式，比较乱)
 * text		String	文本类型（2^16 - 1字节）
 * longtext	String	长文本类型（2^32 - 1字节）
 */
public class Schema2EntityManager {

    private static final Logger logger = LoggerFactory.getLogger(Schema2EntityManager.class);

    private SchemaRepository schemaRepository;

    public Schema2EntityManager(SchemaRepository schemaRepository) {
        this.schemaRepository = schemaRepository;
    }

    public void generate(String targetProjectName, String basePackage, String dbName, String templateBasePath) {
        File root = ProjectFileUtils.getProjectRootFile();
        //
        File projectFile = new File(root, targetProjectName);

        String basePackagePath = basePackage.replaceAll("\\.", "/");
        File basePackageFile = new File(projectFile, "src/main/java/" + basePackagePath);

        if (!basePackageFile.exists()) {
            basePackageFile.mkdirs();
        }

        List<String> tables = schemaRepository.findTableNamesByDbName(dbName);
        Set<String> ignoredColumn = new HashSet<>();
        ignoredColumn.add("created_on");
        ignoredColumn.add("created_by");
        ignoredColumn.add("updated_on");
        ignoredColumn.add("updated_by");
        tables.forEach(tableName -> {
            List<Column> list = schemaRepository.findColumnByDbNameAndTableName(dbName,tableName);
            generateTable(tableName, list, basePackage, basePackageFile, ignoredColumn, templateBasePath);
//            if (tableName.startsWith("sys_")) {
//                generateTable(tableName, list, basePackage, basePackageFile, ignoredColumn, templateBasePath);
//            }
        });

    }

    public void generateTable(String targetProjectName, String basePackage, String dbName, String templateBasePath, String tableName) {
        File root = ProjectFileUtils.getProjectRootFile();
        //
        File projectFile = new File(root, targetProjectName);

        String basePackagePath = basePackage.replaceAll("\\.", "/");
        File baseSrcMainJavaFile = new File(projectFile, "src/main/java/" + basePackagePath);

        if (!baseSrcMainJavaFile.exists()) {
            baseSrcMainJavaFile.mkdirs();
        }

        Set<String> ignoredColumn = new HashSet<>();
        ignoredColumn.add("created_on");
        ignoredColumn.add("created_by");
        ignoredColumn.add("updated_on");
        ignoredColumn.add("updated_by");

        List<Column> list = schemaRepository.findColumnByDbNameAndTableName(dbName,tableName);
        generateTable(tableName, list, basePackage, baseSrcMainJavaFile, ignoredColumn, templateBasePath);

    }

    public void generateTable(String tableName, List<Column> list, String basePackage, File baseSrcMainJavaFile, Set<String> ignoredColumn, String templateBasePath) {

        Map<String, Object> context = new HashMap<>();
        context.put("tableName", tableName);
        String entityPackage = basePackage + ".entity";
        context.put("entityPackage", entityPackage);
        String repoPackage = basePackage + ".repository";
        context.put("repoPackage", repoPackage);
        String entityClassSimpleName = NameUtils.initCap(tableName) + "Entity";
        context.put("entityClassName", basePackage + ".entity." + entityClassSimpleName);
        context.put("entityClassSimpleName", entityClassSimpleName);
        String repoClassSimpleName = NameUtils.initCap(tableName) + "Repository";
        context.put("repoClassSimpleName", repoClassSimpleName);
        Set<String> fieldsImportType = new HashSet<>();
        List<Column> fields = new ArrayList();

        Set<String> ignoreColumn = new HashSet<>();
        ignoreColumn.add("id");
        ignoreColumn.addAll(ignoredColumn);

        list.forEach(item -> {
            if (!item.getJavaTypeName().startsWith("java.lang")) {
                fieldsImportType.add(item.getJavaTypeName());
            }
            if (!ignoreColumn.contains(item.getColumnName())) {
                fields.add(item);
            }
        });
        context.put("fields", fields);
        context.put("fieldsImportType", fieldsImportType);

        File entityFile = new File(baseSrcMainJavaFile, NameUtils.package2Path(entityPackage));
        if (!entityFile.exists()) {
            entityFile.mkdirs();
        }

        String entityClassFileName = new StringBuilder(entityClassSimpleName).append(".java").toString();
        logger.info("build file : {},in path : {}", entityClassFileName, entityFile.getAbsoluteFile());
        FreemarkerUtils.createFile(templateBasePath + "/entity/entity.ftl", context, entityFile, entityClassFileName);

        File repoFile = new File(baseSrcMainJavaFile, NameUtils.package2Path(repoPackage));
        if (!repoFile.exists()) {
            repoFile.mkdirs();
        }
        String repoClassFileName = new StringBuilder(repoClassSimpleName).append(".java").toString();
        logger.info("build file : {},in path : {}", repoClassFileName, repoFile.getAbsoluteFile());

        File repoClassFile = new File(repoFile, repoClassFileName);
        if (!repoClassFile.exists()) {
            FreemarkerUtils.createFile(templateBasePath + "/repo/repo.ftl", context, repoFile, repoClassFileName);
        }

    }
}
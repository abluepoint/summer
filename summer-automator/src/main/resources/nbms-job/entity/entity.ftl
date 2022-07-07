package ${entityPackage};

<#list fieldsImportType as row>
import ${row};
</#list>

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;

@Getter
@Setter
@Entity
@Table(name = "${tableName}")
public class ${entityClassSimpleName} extends BaseEntity{

<#list fields as row>
    /**
    *  ${row.columnComment}
    */
    @Column(name = "${row.columnName}")
    <#if (row.javaTypeSimpleName=="Date")>
    @Temporal(TemporalType.TIMESTAMP)
    </#if>
    <#if row.columnDataType=="blob">
    @Lob
    </#if>
    private ${row.javaTypeSimpleName} ${row.javaFieldName};

</#list>

}
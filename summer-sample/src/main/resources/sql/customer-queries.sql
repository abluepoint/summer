-- countActive
select count(*)
from summer_customer
where active = true

-- listCustomers
select id as customer_id,
       username,
       email,
       display_name
<#if detailProjection>
       , active
</#if>
from summer_customer
<#if filterByActive>
where active = :active
</#if>
order by
<#if sortByUsername>
  username
<#elseif sortByEmail>
  email
<#elseif sortByDisplayName>
  display_name
<#elseif sortByActive>
  active
<#else>
  username
</#if>
<#if descending>
  desc
<#else>
  asc
</#if>
, username asc
limit :limit offset :offset

-- countCustomers
select count(*)
from summer_customer
<#if filterByActive>
where active = :active
</#if>

-- listSystemParameters
select
<#if summaryProjection>
       id,
       parameter_key as parameter_key,
       value_type as value_type,
       status,
       public_flag as public_flag
<#else>
       id,
       parameter_key as parameter_key,
       value_type as value_type,
       value_json as value_json,
       required_flag as required_flag,
       min_value as min_value,
       max_value as max_value,
       value_pattern as value_pattern,
       public_flag as public_flag,
       description,
       status,
       version,
       tenant_id as tenant_id,
       created_at as created_at,
       updated_at as updated_at
</#if>
from summer_system_parameter
where tenant_id = :tenantId
<#if filterByKey>
  and parameter_key like :keyPattern
</#if>
<#if filterByType>
  and value_type = :type
</#if>
<#if filterByStatus>
  and status = :status
</#if>
<#if filterByPublic>
  and public_flag = :publicValue
</#if>
<#if scopeSelf || scopeCreator || scopeOwner || scopeOrganization>
  and (
  <#assign firstScope = true>
  <#if scopeSelf>
    created_by = :scopeSubject
    <#assign firstScope = false>
  </#if>
  <#if scopeCreator>
    <#if !firstScope><#if scopeAnd> and <#else> or </#if></#if>created_by = :scopeSubject
    <#assign firstScope = false>
  </#if>
  <#if scopeOwner>
    <#if !firstScope><#if scopeAnd> and <#else> or </#if></#if>owner_id = :scopeSubject
    <#assign firstScope = false>
  </#if>
  <#if scopeOrganization>
    <#if !firstScope><#if scopeAnd> and <#else> or </#if></#if>organization_id = :scopeOrganizationId
  </#if>)
</#if>
order by
<#if sortByKey>
  parameter_key
<#elseif sortByType>
  value_type
<#elseif sortByStatus>
  status
<#elseif sortByCreatedAt>
  created_at
<#elseif sortByUpdatedAt>
  updated_at
<#elseif sortByVersion>
  version
<#else>
  parameter_key
</#if>
<#if descending>
  desc
<#else>
  asc
</#if>
, parameter_key asc
limit :limit offset :offset

-- countSystemParameters
select count(*)
from summer_system_parameter
where tenant_id = :tenantId
<#if filterByKey>
  and parameter_key like :keyPattern
</#if>
<#if filterByType>
  and value_type = :type
</#if>
<#if filterByStatus>
  and status = :status
</#if>
<#if filterByPublic>
  and public_flag = :publicValue
</#if>
<#if scopeSelf || scopeCreator || scopeOwner || scopeOrganization>
  and (
  <#assign firstScope = true>
  <#if scopeSelf>
    created_by = :scopeSubject
    <#assign firstScope = false>
  </#if>
  <#if scopeCreator>
    <#if !firstScope><#if scopeAnd> and <#else> or </#if></#if>created_by = :scopeSubject
    <#assign firstScope = false>
  </#if>
  <#if scopeOwner>
    <#if !firstScope><#if scopeAnd> and <#else> or </#if></#if>owner_id = :scopeSubject
    <#assign firstScope = false>
  </#if>
  <#if scopeOrganization>
    <#if !firstScope><#if scopeAnd> and <#else> or </#if></#if>organization_id = :scopeOrganizationId
  </#if>)
</#if>

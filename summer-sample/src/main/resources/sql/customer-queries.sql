-- countActive
select count(*)
from summer_customer
where active = true

-- listCustomers
select id as customer_id,
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
<#if sortByDisplayName>
  display_name
<#elseif sortByActive>
  active
<#else>
  email
</#if>
<#if descending>
  desc
<#else>
  asc
</#if>
, id asc
limit :limit offset :offset

-- countCustomers
select count(*)
from summer_customer
<#if filterByActive>
where active = :active
</#if>

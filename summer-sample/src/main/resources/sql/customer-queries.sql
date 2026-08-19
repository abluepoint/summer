-- findByEmail
select id as customer_id,
       email,
       display_name,
       active
from summer_customer
where email = :email
<#if activeOnly>
  and active = true
</#if>

-- countActive
select count(*)
from summer_customer
where active = true

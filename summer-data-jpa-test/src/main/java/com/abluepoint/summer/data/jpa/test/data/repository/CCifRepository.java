package com.abluepoint.summer.data.jpa.test.data.repository;

import com.abluepoint.summer.data.jpa.test.data.dataview.CCifView;
import com.abluepoint.summer.data.jpa.test.data.entity.CCifEntity;
import com.abluepoint.summer.data.jpa.repository.BaseRepository;
import org.springframework.data.jpa.repository.Query;

public interface CCifRepository extends BaseRepository<CCifEntity, Long> {

    @Query(nativeQuery = true, value =
    """
    --- mysql
    select * from c_cif 
    <@where>
    <#if id??>
        and id = :id
    </#if>
    </@where>
    
    --- dm
    select * from c_cif
    """,countQuery =
    """
    ---mysql
    select count(*) from c_cif 
    """
    )
    public CCifView queryCCifViewById(Long id);

    public CCifView queryCCifViewById2(Long id);

}
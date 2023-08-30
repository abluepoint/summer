package com.abluepoint.summer.data.jpa.test.data.repository;

import com.abluepoint.summer.data.jpa.test.data.dataview.CCifView;
import com.abluepoint.summer.data.jpa.test.data.entity.CCifEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Optional;

@SpringBootTest
class CCifRepositoryTest {

    @Autowired
    private CCifRepository cCifRepository;

    @Test
    void getCifNameTest() {
        Optional<CCifEntity> cifOptional = cCifRepository.findById(1L);
        cifOptional.ifPresent(cCifEntity -> {
            System.out.println(cCifEntity.getCifName());
        });
    }

    @Test
    void queryCCifViewByIdTest() {
        CCifView cCifView = cCifRepository.queryCCifViewById(1L);
        System.out.println(cCifView.getCifNo());
    }
}
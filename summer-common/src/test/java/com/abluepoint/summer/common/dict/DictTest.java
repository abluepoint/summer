//package com.abluepoint.summer.common.dict;
//
//import org.junit.Test;
//
//import java.util.ArrayList;
//import java.util.HashMap;
//import java.util.List;
//import java.util.Map;
//
//import static org.junit.Assert.*;
//
///*
// * Copyright (c) 2020 abluepoint All Rights Reserved.
// * File:DictTest.java
// * Date:2020-03-20 14:51:20
// */
//
//public class DictTest {
//
//    /**
//     * id   name      code       parentId   desc
//     * 0    dict      idType     null       字典
//     * 1    dict      orgType    null
//     * 2    dict      trade      null       行业
//     * 3    dict      industry   2          二级行业
//     * 4    idType    01         0          身份证
//     * 5    trade     01         2
//     * 6    trade     02         2
//     * 7    industry  01         5
//     * 8    industry  02         5
//     */
//    private Map<Long, Dict> data;
//
//    @org.junit.Before
//    public void setUp() throws Exception {
//        data = new HashMap<>();
//        data.put(0L, Dict.builder().id(0L).name("dict").code("idType").parentId(null).build());
//        data.put(1L, Dict.builder().id(1L).name("dict").code("orgType").parentId(null).build());
//        data.put(2L, Dict.builder().id(2L).name("dict").code("trade").parentId(null).build());
//        data.put(3L, Dict.builder().id(3L).name("dict").code("industry").parentId(2L).build());
//        data.put(4L, Dict.builder().id(4L).name("idType").code("01").parentId(0L).build());
//        data.put(5L, Dict.builder().id(5L).name("trade").code("01").parentId(2L).build());
//        data.put(6L, Dict.builder().id(6L).name("trade").code("02").parentId(2L).build());
//        data.put(7L, Dict.builder().id(7L).name("industry").code("01").parentId(5L).build());
//        data.put(8L, Dict.builder().id(8L).name("industry").code("02").parentId(5L).build());
//    }
//
//
//}
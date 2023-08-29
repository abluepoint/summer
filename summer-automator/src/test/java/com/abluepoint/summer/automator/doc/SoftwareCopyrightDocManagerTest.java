package com.abluepoint.summer.automator.doc;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;

class SoftwareCopyrightDocManagerTest {

    @Test
    void generate() throws IOException {
        SoftwareCopyrightDocManager softwareCopyrightDocManager = new SoftwareCopyrightDocManager();
        //softwareCopyrightDocManager.generate(
        //        new java.io.File("/Users/bluepoint/Documents/teleinfo/2_项目/5_软硬件协同平台专项/20230523源代码/主被动标识载体应用接入系统&标识数据可视化应用服务系统-src.zip"),
        //        new java.io.File("/Users/bluepoint/Documents/teleinfo/2_项目/5_软硬件协同平台专项/20230523源代码/主被动标识载体应用接入系统&标识数据可视化应用服务系统-src.txt"),
        //        null);

        //softwareCopyrightDocManager.generate(
        //        new java.io.File("/Users/bluepoint/Documents/teleinfo/2_项目/5_软硬件协同平台专项/20230530源代码doc/泰尔英福/标识注册解析沙盒系统/源代码/id-test.zip"),
        //        new java.io.File("/Users/bluepoint/Documents/teleinfo/2_项目/5_软硬件协同平台专项/20230530源代码doc/泰尔英福/标识注册解析沙盒系统/源代码/id-test.txt"),
        //        new HashSet<>(){
        //            {
        //                add("id-test-automator");
        //                add("ftl");
        //            }});

        //String s = "/Users/bluepoint/Documents/teleinfo/02_项目/05_软硬件协同平台专项/20230530源代码doc/泰尔英福/工业互联网标识元数据管理系统项目/源代码/id-meta-master.zip";
        //softwareCopyrightDocManager.generate(
        //        new java.io.File(s),
        //        new java.io.File(s.replace(".zip", ".txt")),
        //        new HashSet<>(){
        //            {
        //                add("src/test");
        //                add("ftl");
        //            }});


        //String s1 = "/Users/bluepoint/Documents/teleinfo/02_项目/05_软硬件协同平台专项/20230530源代码doc/泰尔英福/基于工业互联网的嵌入式企业节点软件/源码.zip";
        //softwareCopyrightDocManager.generate(
        //        new java.io.File(s1),
        //        new java.io.File(s1.replace(".zip", ".txt")),
        //        new HashSet<>(){
        //            {
        //                add("src/test");
        //                add("core");
        //            }});

        String filepath = "/Users/bluepoint/Downloads/blade-system.zip";
        softwareCopyrightDocManager.generate(
                new java.io.File(filepath),
                new java.io.File(filepath.replace(".zip", ".txt")),
                new HashSet<>(){
                    {
                        add("src/test");
                        add("core");
                        add("__MACOSX");
                    }});
        //add("blade-service/blade-system/src/main");
    }

}
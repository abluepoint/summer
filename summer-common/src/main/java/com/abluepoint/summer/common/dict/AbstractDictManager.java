//package com.abluepoint.summer.common.dict;
//
//import com.abluepoint.summer.common.exception.SummerRuntimeException;
//import org.springframework.lang.NonNull;
//import org.springframework.util.Assert;
//
//import java.util.ArrayList;
//import java.util.List;
//
///*
// * Copyright (c) 2020 abluepoint All Rights Reserved.
// * File:AbstractDictManager.java
// * Date:2020-03-20 17:58:20
// */
//
//public abstract class AbstractDictManager implements DictManager {
//
//    public static final String ROOT_DICT_NAME = "dict";
//    public static final String PATH_DELIMITER = ".";
//
//    @Override
//    public String getDictPath(Dict dict) {
//        Assert.notNull(dict,"dict must not null");
//        if (dict.getParentId() == null) {
//            StringBuilder sb = new StringBuilder();
//            sb.append(dict.getName()).append(".").append(dict.getCode());
//            return sb.toString();
//        }
//
//        if (dict.getName().equals(ROOT_DICT_NAME)) {
//            List<Dict> list = getRootParentList(dict);
//            int len = list.size();
//
//            StringBuilder sb = new StringBuilder();
//            Dict temp = null;
//            sb.append(ROOT_DICT_NAME).append(PATH_DELIMITER);
//            for (int i = len - 1; i >= 0; i--) {
//                temp = list.get(i);
//                sb.append(list.get(i).getCode());
//                sb.append(PATH_DELIMITER);
//            }
//
//            if (sb.length() > 0) {
//                sb.deleteCharAt(sb.length() - 1);
//            }
//
//            return sb.toString();
//        } else {
//            List<Dict> list = getParentList(dict);
//
//            int len = list.size();
//
//            StringBuilder sb = new StringBuilder();
//            Dict temp = null;
//            sb.append(ROOT_DICT_NAME).append(PATH_DELIMITER);
//            for (int i = len - 2; i >= 0; i--) {
//                temp = list.get(i);
//                sb.append(list.get(i).getName());
//                sb.append(PATH_DELIMITER);
//                sb.append(list.get(i).getCode());
//                sb.append(PATH_DELIMITER);
//            }
//
//            if (sb.length() > 0) {
//                sb.deleteCharAt(sb.length() - 1);
//            }
//
//            return sb.toString();
//        }
//
//    }
//
//    private List<Dict> getParentList(Dict dict) {
//        List<Dict> list = new ArrayList<>();
//        list.add(dict);
//        handleParent(list, dict);
//        return list;
//    }
//
//    private List<Dict> getRootParentList(Dict dict) {
//        List<Dict> list = new ArrayList<>();
//        list.add(dict);
//        handleRootParent(list, dict);
//        return list;
//    }
//
//    private void handleParent(List<Dict> list, Dict dict) {
//        if (dict != null && dict.getParentId() != null) {
//            if (!dict.getName().equals(ROOT_DICT_NAME)) {
//                Dict parent = getDict(dict.getParentId());
//                if (parent == null) {
//                    throw new SummerRuntimeException("system.dict.miss", new Object[] { dict.getParentId() });
//                }
//                list.add(parent);
//                handleParent(list, parent);
//            }
//        }
//    }
//
//    private void handleRootParent(List<Dict> list, Dict dict) {
//        if (dict != null && dict.getParentId() != null) {
//            Dict parent = getDict(dict.getParentId());
//            if (parent == null) {
//                throw new SummerRuntimeException("system.dict.miss", new Object[] { dict.getParentId() });
//            }
//            list.add(parent);
//            handleRootParent(list, parent);
//        }
//    }
//
//
//}

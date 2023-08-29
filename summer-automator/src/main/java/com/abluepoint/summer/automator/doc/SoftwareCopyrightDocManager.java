package com.abluepoint.summer.automator.doc;

import org.springframework.util.AntPathMatcher;

import java.io.*;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * todo: (1)按照读取代码整理,(2)适配多种数据输入
 */
public class SoftwareCopyrightDocManager {


    /**
     * 遍历zip文件，生成文档
     */
    public void generate(File srcZipFile, File targetTxtFile, Set<String> ignoredSet) throws IOException {

        //String basePath = "irs-manager-c9c85bcd679e5c6248e77995d18c672ac4d75a58/";
        AntPathMatcher antPathMatcher = new AntPathMatcher();

        HashSet<String> ignored = new HashSet<>();
        ignored.add("pom.xml");
        ignored.add("README.md");
        ignored.add(".gitignore");
        if (ignoredSet != null) {
            ignored.addAll(ignoredSet);
        }

        if (targetTxtFile.exists()) {
            targetTxtFile.delete();
        }

        OutputStream out = new FileOutputStream(targetTxtFile);
        BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(out);


        InputStream input = new FileInputStream(srcZipFile);
        ZipInputStream zipInputStream = new ZipInputStream(new BufferedInputStream(input));

        // 定义ZipEntry,避免由于重复调用zipInputStream.getNextEntry造成的不必要的问题
        int lineCount = 0;
        ZipEntry ze = null;
        while ((ze = zipInputStream.getNextEntry()) != null && lineCount < 3000) {

            boolean isIgnored = false;
            for (String ignore : ignored) {
                if (ze.getName().contains(ignore)) {
                    isIgnored = true;
                    break;
                }
            }

            if (isIgnored || ze.getName().startsWith(".")) {
                continue;
            }
            if (!ze.getName().endsWith(".java") && !ze.getName().endsWith(".js") && !ze.getName().endsWith(".go")) {
                continue;
            }

            System.out.println(ze.getName());

            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(zipInputStream));
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                // tab转换为空格
                line = line.replaceAll("\t", "    ");
                // 判断line是否是空行
                if (line.trim().isEmpty()) {
                    continue;
                }

                lineCount++;
                line = line.replaceAll("Chill Zhuang","liuyan");
                line = line.replaceAll("庄骞","");
                line = line.replaceAll("\\(smallchill@163\\.com\\)","");
                line = line.replaceAll("Chill","liuyan");
                line = line.replaceAll("BladeX","");
                bufferedOutputStream.write(line.getBytes());
                bufferedOutputStream.write("\n".getBytes());
            }
        }
        //一定记得关闭流
        zipInputStream.closeEntry();
        input.close();
        bufferedOutputStream.close();
        out.close();

    }



    //private static int getLineCount(BufferedOutputStream bufferedOutputStream, String line) throws IOException {
    //    int lineCount = 0;
    //    if (line.length() > 70) {
    //        String s = line.substring(0, 70);
    //        lineCount++;
    //        bufferedOutputStream.write(s.getBytes());
    //        bufferedOutputStream.write("\n".getBytes());
    //
    //        line = line.substring(70);
    //        lineCount = lineCount + getLineCount(bufferedOutputStream, line);
    //    } else {
    //        lineCount++;
    //        bufferedOutputStream.write(line.getBytes());
    //        bufferedOutputStream.write("\n".getBytes());
    //
    //    }
    //    return lineCount;
    //}
}

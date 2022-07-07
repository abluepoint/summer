package com.abluepoint.summer.automator.util;

import java.io.File;

public abstract class ProjectFileUtils {
	
	public static File getFile(String path) {
		File f2 = new File(getProjectFile(),path);
		return f2;
	}
	public static File getProjectFile() {
		File f = new File(ProjectFileUtils.class.getResource("/").getPath());
		File f1 = f.getParentFile().getParentFile();
		return f1;
	}
	
	public static File getProjectRootFile() {
		return getProjectFile().getParentFile();
	}
	
}

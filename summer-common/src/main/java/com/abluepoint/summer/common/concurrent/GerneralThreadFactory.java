package com.abluepoint.summer.common.concurrent;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class GerneralThreadFactory implements ThreadFactory {

	private ThreadGroup threadGroup;
	private String threadName;

	private final AtomicInteger threadNumber = new AtomicInteger(1);

	public GerneralThreadFactory(ThreadGroup threadGroup) {
		this(threadGroup, "t");
	}

	public GerneralThreadFactory(ThreadGroup threadGroup, String threadName) {
		this.threadGroup = threadGroup;
		this.threadName = threadName;
	}

	public GerneralThreadFactory(String threadName) {
		this(null, threadName);
	}

	@Override
	public Thread newThread(Runnable r) {
		Thread thread = null;
		if (threadGroup != null) {
			thread = new Thread(threadGroup, r);
			thread.setName(new StringBuilder().append(threadGroup.getName()).append("-").append(threadName).append("-").append(threadNumber.getAndIncrement()).toString());
		} else {
			thread = new Thread(r);
			thread.setName(new StringBuilder().append(threadName).append("-").append(threadNumber.getAndIncrement()).toString());
		}
		return thread;
	}
}

package com.abluepoint.summer.sample.task.domain;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
public final class TaskTimedOutException extends RuntimeException {}

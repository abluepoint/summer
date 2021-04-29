package com.abluepoint.summer.common.context;

/*
 * Copyright (c) 2020 abluepoint All Rights Reserved.
 * File:StaticMessageSource.java
 * Date:2020-03-17 08:18:17
 */

import org.springframework.context.support.AbstractMessageSource;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class SimpleMessageSource extends AbstractMessageSource {

	/** Map from 'code + locale' keys to message Strings. */
	private final Map<String, String> messages = new HashMap<>();

	private final Map<String, MessageFormat> cachedMessageFormats = new HashMap<>();


	@Override
	protected String resolveCodeWithoutArguments(String code, Locale locale) {
		return this.messages.get(code + '_' + locale.toString());
	}

	@Override
	@Nullable
	protected MessageFormat resolveCode(String code, Locale locale) {
		String key = code + '_' + locale.toString();
		String msg = this.messages.get(key);
		if (msg == null) {
			return null;
		}
		synchronized (this.cachedMessageFormats) {
			MessageFormat messageFormat = this.cachedMessageFormats.get(key);
			if (messageFormat == null) {
				messageFormat = createMessageFormat(msg, locale);
				this.cachedMessageFormats.put(key, messageFormat);
			}
			return messageFormat;
		}
	}

	/**
	 * Associate the given message with the given code.
	 * @param code the lookup code
	 * @param locale the locale that the message should be found within
	 * @param msg the message associated with this lookup code
	 */
	public void addMessage(String code, Locale locale, String msg) {
		Assert.notNull(code, "Code must not be null");
		Assert.notNull(locale, "Locale must not be null");
		Assert.notNull(msg, "Message must not be null");
		this.messages.put(code + '_' + locale.toString(), msg);
		if (logger.isDebugEnabled()) {
			logger.debug("Added message [" + msg + "] for code [" + code + "] and Locale [" + locale + "]");
		}
	}

	/**
	 * Associate the given message values with the given keys as codes.
	 * @param messages the messages to register, with messages codes
	 * as keys and message texts as values
	 * @param locale the locale that the messages should be found within
	 */
	public void addMessages(Map<String, String> messages, Locale locale) {
		Assert.notNull(messages, "Messages Map must not be null");
		messages.forEach((code, msg) -> addMessage(code, locale, msg));
	}


	@Override
	public String toString() {
		return getClass().getName() + ": " + this.messages;
	}

}
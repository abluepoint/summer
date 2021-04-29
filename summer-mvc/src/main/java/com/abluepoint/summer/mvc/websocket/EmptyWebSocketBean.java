package com.abluepoint.summer.mvc.websocket;

import com.abluepoint.summer.common.exception.SummerRuntimeException;

import javax.websocket.Session;

public class EmptyWebSocketBean implements WebSocket{


	@Override
	public void onOpen(Session session) {

	}

	@Override
	public void onMessage(Session session, String message) {

	}

	@Override
	public void onClose(Session session) {

	}

	@Override
	public void onError(Session session, Throwable error) {

	}

}

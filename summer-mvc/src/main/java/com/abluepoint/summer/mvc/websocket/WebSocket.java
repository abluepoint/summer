package com.abluepoint.summer.mvc.websocket;

import javax.websocket.Session;

public interface WebSocket {
	public void onOpen(Session session);
	public void onMessage(Session session,String message);
	public void onClose(Session session);
	public void onError(Session session, Throwable error);
}

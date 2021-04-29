package com.abluepoint.summer.mvc.websocket;

import com.abluepoint.summer.common.util.AppContextUtil;

import javax.websocket.*;

/**
 * 继承这个抽象类,添加相应注解,必须定义空构造方法
 * @author bluepoint
 */
//@ServerEndpoint(value = "/ws")
public abstract class WebSocketAdaptor{
	
	private Session session;
	private WebSocket webSocket;
	private final String beanName;

	public WebSocketAdaptor(String beanName) {
		this.beanName = beanName;
	}

	/**
	 * @param session
	 */
	@OnOpen
	public void onOpen(Session session) {
		this.session = session;
		getWebSocket().onOpen(session);
	}
	
	@OnMessage
	public void onMessage(String message) {
		getWebSocket().onMessage(session,message);
	}
	
	@OnClose
	public void onClose() {
		getWebSocket().onClose(session);
	}
	
	@OnError
	public void onError(Session session, Throwable error) {
		getWebSocket().onError(session, error);
	}

	public WebSocket getWebSocket() {
		if(webSocket==null) {
			webSocket = AppContextUtil.getAppContext().getBean(beanName,WebSocket.class);
		}
		return webSocket;
	}

}

package jxsource.net.proxy.app;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.Socket;

import org.apache.log4j.Logger;

import jxsource.net.proxy.Controller;
import jxsource.net.proxy.UrlInfo;

public class ControllerRunnable implements Runnable{
	protected Logger logger;

	protected ControllerManagerTester mgr;
	protected int loopSize = 5;
	public ControllerRunnable(ControllerManagerTester mgr) {
		this.mgr = mgr;
		logger = Logger.getLogger(ControllerRunnable.class);
	}
	public void setLoopSize(int loopSize) {
		this.loopSize = loopSize;
	}
	public void run() {
		for(int i=0; i<loopSize; i++) {
			logger.debug("-------- loop "+i);
			try {
				transaction(new UrlInfo("http://localhost"));
			} catch(Exception e) {
				e.printStackTrace();
			}
		}
	}
	protected void transaction(UrlInfo urlInfo) throws IOException {
		synchronized(mgr) {
			while(mgr.lock) {
				try {
					mgr.wait();
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			mgr.lock = true;
		Controller controller = mgr.getControllerManager().getController(urlInfo);
		if(mgr.getWorkingController() == null) {
			mgr.setWorkingController(controller);
		}
		logger.debug("get controller: "+controller.hashCode());
		assertTrue("different controller", mgr.getWorkingController() == controller);
//		ControllerController socket = cm.getController(controller);
//		logger.debug("get Controller "+socket.hashCode()+","+socket);
		mgr.sleep();
		Socket socket = controller.getSocket();
		if(mgr.getWorkingSocket() == null) {
			mgr.setWorkingSocket(socket);
		}
		logger.debug("get socket: "+socket.hashCode());
		assertTrue("different socket: "+mgr.getWorkingSocket().hashCode()+", "+socket.hashCode(), mgr.getWorkingSocket() == socket);
		controller.releaseSocket(socket);
		mgr.lock = false;
		mgr.notifyAll();
		}
	}
	

}

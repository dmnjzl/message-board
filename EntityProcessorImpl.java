package jxsource.net.proxy.http.entity;

import java.io.IOException;
import java.io.InputStream;

import jxsource.net.proxy.Constants;
import jxsource.net.proxy.http.HttpConstants;
import jxsource.net.proxy.http.HttpHeaderUtils;
import jxsource.net.proxy.http.exception.EntityException;
import jxsource.net.proxy.http.exception.MessageHeaderException;
import jxsource.util.buffer.bytebuffer.ByteArray;

import org.apache.http.Header;
import org.apache.http.HttpMessage;
import org.apache.log4j.Logger;

public class EntityProcessorImpl implements EntityProcessor{
	
	private Logger logger = Logger.getLogger(EntityProcessorImpl.class);
	HttpHeaderUtils headerSearch = new HttpHeaderUtils();
	int capacity = HttpConstants.EntityBufferSize;
	boolean closed;

	void setEntityBufferSize(int capacity) {
		this.capacity = capacity;
	}
	public EntityStatus processEntity(HttpMessage message, InputStream from, EntityDestinationOutputStream to) throws IOException {
		// use a new selector for each entity
		// use a new TransferQueue for each entity
		headerSearch.setHttpMessage(message);

		long length = 0l;
		closed = false;
		if(message.containsHeader("Content-Length")) {
			Header contentLength = message.getFirstHeader("Content-Length");
			length = Long.parseLong(contentLength.getValue());
		}
		if(message.containsHeader("Connection")) {
			closed = message.getFirstHeader("Connection").getValue().equals("close");
		}

		try {
				if(headerSearch.hasHeaderWithValue("Transfer-Encoding", "chunked")) {
					long processed = procChunkedEntity(from, to);
					return new EntityStatus(processed, EntityComplete);
				} else 
				if(length > 0) {
					long processed = procLength(length, from, to);
					if(closed){
						logger.error("Rmote connection closed: requested="+length+", processed="+processed);
						return new EntityStatus(processed, EntityClosed, length );						
					} else
					if(length != processed) {
						logger.error("Different bytes in closed connection: requested="+length+", processed="+processed);
						return new EntityStatus(processed, EntityClosed, length );
					} 
					return new EntityStatus(processed, EntityComplete, length );
				} else {
					long processed = procLength(Constants.InfiniteInt, from, to);
					return new EntityStatus(processed, EntityClosed, Constants.InfiniteLong);
				}
		} finally {
			to.close();
		}
	}

	// length = entitySize - the value of Content-Length header
	public long procLength(long length, InputStream from, 
			EntityDestinationOutputStream to) throws IOException {
		if(length == 0) {
			return 0;
		}
		int processed = 0;
		while (processed < length) {
			byte[] buffer = new byte[Math.min(capacity,(int)length-processed)];
			try {
					int i = from.read(buffer);
					if(i > 0) {
						processed += i;
						to.writeContent(buffer, 0, i);
					} else {
						logger.error("InputStream read exception. read bytes = "+i+", processed = "+processed);
						if(i == -1) {
							break;
						}
					}
			} catch(IOException e) {
				if(closed) {
					System.err.println(e);
					break;
				} else {
					throw new EntityException(e);
				}
			}
		}
		if(processed != length) {
			logger.error("***** different content length: processed="+processed+", length="+length);
		}
		return processed;
	}

	public long procChunkedEntity(InputStream from, EntityDestinationOutputStream to) {
		long processed = 0;
		long entitySize = 0;
		boolean complete = false;
		boolean isChunk = true;
		while (!complete) {
			try {
						if(isChunk) {
							// proc chunk header
							ByteArray chunkHeader = getLine(from);
							byte[] buffer = chunkHeader.getArray();
							to.write(buffer);
							processed += chunkHeader.length();
							// proc chunk body
							long size = getChunkSize(chunkHeader);
							entitySize += size;
							// Re-Use procLength method.
							if(size > 0) {
								long readBytes = procLength(size, from, to);
								if(readBytes == size) {
									processed += readBytes;
									// process chunk ends (CRLF)
								
									int k = 0;
									byte[] crlf = new byte[2];
									while (k < 2) {
										int i = from.read(crlf);
										k += i;
										processed += i;
										to.write(crlf, 0, i);
//									to.writeContent(buffer, 0, i);
									}
								} else {
									// procLength closed read before getting expected bytes
									// TODO: not sure how to handle it.
									// throw exception to stop request process.
									throw new EntityException("Cannot complete chuncked read. expected = "+size+", read = "+readBytes);
								}
							} else {
								isChunk = false;
							}
						} else {
							// end chunk body
							ByteArray chunkTailer = getLine(from);
							boolean endEntity = isEndEntity(chunkTailer);
							byte[] buffer = chunkTailer.getArray();
							processed += chunkTailer.length();
							to.write(buffer);
							if(endEntity) {
								complete = true;
							}
						}
			} catch(MessageHeaderException mhe) {
				if(processed == 0) {
					throw new EntityException("Entity Input closed. isChunk="+isChunk,mhe);
				} else {
					// NOTE: chunk finishes - not formally specified in HTTP 1.1 specification
					// but used by some web application.
					complete = true;
				}
			} catch(IOException e) {
				throw new EntityException("Entity Error. isChunk="+isChunk,e);
			}
		}
		return entitySize;
	}
	private boolean isEndEntity(ByteArray buffer) {
		return buffer.get(0)==ByteArray.CR && buffer.get(1)== ByteArray.LF;
	}
	public ByteArray getLine(InputStream in) throws IOException {
		byte[] buffer = new byte[1];
		boolean validHeader = false;
		ByteArray byteArray = new ByteArray();
		// read one byte 
		int i = 0;
		try {
		while((i=in.read(buffer)) != -1) {
			if(i == 0) {
				logger.warn("read 0 byte from buffer");
				continue;
			}
			byteArray.append(buffer);
			if(byteArray.length() < 2) {
				// clear buffer for next read
				continue;
			}
			// start to compare the last two bytes
			int offset = byteArray.length()-2;
			byte[] last4bytes = byteArray.subArray(offset);
			boolean endHttpRequest = true;
			for(int k=0; k<2; k++) {
				if(last4bytes[k] != CRLF[k]) {
					// the last four bytes are not CRLECRLE
					endHttpRequest = false;
					// break for loop
					break;
				}
			}
			if(endHttpRequest) {
				validHeader = true;
				// break while loop
				break;
			}
		}
		if(validHeader) {
			return byteArray;
		} else {
			if(byteArray.length() == 0) {
				throw new MessageHeaderException("Zero bytes in socket channel when passing Http message header");
			} else {
				throw new MessageHeaderException("Invalid SocketChannel data for Message Header: "+byteArray);
			}
		}
		} catch(IOException e) {
			throw new MessageHeaderException("Error when processing Message Header. ", e);			
		}
	}

	public long getChunkSize(ByteArray entityHead) {
		if(entityHead.length() == 2) {
			// See note in getEntityLine() method.
			return 0L;
		}
		StringBuffer sb = new StringBuffer();
		int size = entityHead.length()-2;
		byte semicolon = 59;
		int index = entityHead.indexOf(semicolon);
//		int index = entityHead.indexOf(';');
		if(index != -1) {
			size = index;
		}
		for(int i=0; i<size; i++) {
			sb.append((char)entityHead.get(i));
		}
//		logger.debug("entity size: "+sb.toString());
		if(sb.length() == 0) {
			return 0L;
		} else {
			return Long.parseLong(sb.toString(),16);
		}
	}

}

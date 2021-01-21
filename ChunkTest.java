package jxsource.net.proxy.http;

import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.Arrays;

import jxsource.net.proxy.http.entity.EntityProcessorImpl;
import jxsource.net.proxy.http.entity.DefaultDestinationOutputStream;
import jxsource.util.buffer.bytebuffer.ByteArray;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

@PrepareForTest(SocketChannel.class)
@RunWith(PowerMockRunner.class)  
public class ChunkTest {
	byte[] CRLF = ByteArray.CRLF;
	EntityProcessorImpl ep = new EntityProcessorImpl();
	ByteArray chunkHeader;
	ByteArray chunkExtendedHeader;
	byte[] chunkBody = "1234567890".getBytes();
	
	@Before
	public void init() throws IOException {
		chunkHeader = createChunkHeader(100);
		chunkExtendedHeader = createChunkExtendedHeader(100);
	}
	private ByteArray createChunkHeader(long src) {
		String s = Long.toHexString(src);
		chunkHeader = new ByteArray();
		chunkHeader.append(s.getBytes());
		chunkHeader.append(CRLF);
		return chunkHeader;
	}
	private ByteArray createChunkExtendedHeader(long src) {
		String s = Long.toHexString(src)+";a=b";
		chunkExtendedHeader = new ByteArray();
		chunkExtendedHeader.append(s.getBytes());
		chunkExtendedHeader.append(CRLF);
		return chunkExtendedHeader;
	}
	
	private ByteArray createChunk(byte[] chunkBody) {
		ByteArray chunk = new ByteArray();
		chunk.append(createChunkHeader(chunkBody.length));
		chunk.append(chunkBody);
		return chunk;
		
	}

	private ByteArray createChunk(int len, byte val) {
		String head = Long.toHexString(len)+";a=b";
		ByteArray chunk = new ByteArray();
		chunk.append(head.getBytes());
		chunk.append(CRLF);
		if(len > 0) {
			byte[] body = new byte[len];
			Arrays.fill(body, val);
			chunk.append(body);
		}
		chunk.append(CRLF);
		return chunk;
	}

	@Test
	public void chunkHeadTest() {
		long target = ep.getChunkSize(chunkHeader);
		assertTrue(target == 100);
	}
	@Test
	public void chunkHeadwithExtensionTest() {
		long src = 100;
		String s = Long.toHexString(src)+";a=b";
		ByteArray ba = new ByteArray();
		ba.append(s.getBytes());
		ba.append(CRLF);
		long l = ep.getChunkSize(ba);
		long target = ep.getChunkSize(ba);
		assertTrue(target == 100);
	}
	@Test
	public void entityLineTest() throws IOException {
		ByteArrayInputStream in = new ByteArrayInputStream(chunkExtendedHeader.getArray());
		ByteArray ba = ep.getLine(in);
		assertTrue(ByteArray.equal(ba.getArray(), chunkExtendedHeader.getArray()));
		in = new ByteArrayInputStream(chunkHeader.getArray());
		ba = ep.getLine(in);
		assertTrue(ByteArray.equal(ba.getArray(), chunkHeader.getArray()));
	}

	@Test
	public void chunkSizeTest() {
		String size = Integer.toHexString(13);
		ByteArray ba = new ByteArray();
		ba.append(size.getBytes());
		ba.append(CRLF);
		long i = ep.getChunkSize(ba);
		String result = Long.toHexString(i);
		assertTrue(size.equals(result));
		ba = new ByteArray();
		ba.append(size.getBytes());
		ba.append(";a=b".getBytes());
		ba.append(CRLF);
		i = ep.getChunkSize(ba);
		result = Long.toHexString(i);
		assertTrue(size.equals(result));
	}
	/*
	 * This is special chunk test - entity dose not have the <last chunk> 
	 */
	@Test
	public void chunkTest() throws IOException {
		ByteArray chunkedEntity = createChunk(345, (byte)90);
		System.out.println(new String(chunkedEntity.getArray()));
		ByteArrayInputStream from = new ByteArrayInputStream(chunkedEntity.getArray());
		DefaultDestinationOutputStream ddos = new DefaultDestinationOutputStream();
		ByteArrayOutputStream to = new ByteArrayOutputStream();
		ddos.setOutputStream(to);
		long length = ep.procChunkedEntity(from, ddos);
		assertTrue(length == 345);
		from.close();
		to.close();
		assertTrue(ByteArray.equal(chunkedEntity.getArray(), to.toByteArray()));
	}
	@Test
	public void lastChunkTest() throws IOException {
		ByteArray chunkedEntity = createChunk(345, (byte)90);
		chunkedEntity.append("0".getBytes());
		chunkedEntity.append(CRLF);
		System.out.println(new String(chunkedEntity.getArray()));
		ByteArrayInputStream from = new ByteArrayInputStream(chunkedEntity.getArray());
		DefaultDestinationOutputStream ddos = new DefaultDestinationOutputStream();
		ByteArrayOutputStream to = new ByteArrayOutputStream();
		ddos.setOutputStream(to);
		long length = ep.procChunkedEntity(from, ddos);
		assertTrue(length == 345);
		from.close();
		to.close();
		assertTrue(ByteArray.equal(chunkedEntity.getArray(), to.toByteArray()));
	}

	/*
	 * The case that entity has tailer but without the <last chunk>
	 * is not tested. -- not allowed in specification.
	 */
	@Test
	public void chunkTailerTest() throws IOException {
		ByteArray chunkedEntity = createChunk(345, (byte)90);
		// last chunk
		chunkedEntity.append("0".getBytes());
		chunkedEntity.append(CRLF);
		//tailer
		chunkedEntity.append("d=t".getBytes());
		chunkedEntity.append(CRLF);
		ByteArrayInputStream from = new ByteArrayInputStream(chunkedEntity.getArray());
		DefaultDestinationOutputStream ddos = new DefaultDestinationOutputStream();
		ByteArrayOutputStream to = new ByteArrayOutputStream();
		ddos.setOutputStream(to);
		long length = ep.procChunkedEntity(from, ddos);
		assertTrue(length == 345);
		from.close();
		to.close();
		assertTrue(ByteArray.equal(chunkedEntity.getArray(), to.toByteArray()));
	}


	@Test
	public void lengthTest() throws IOException {
		byte[] body = "1234567890".getBytes();
	
		ByteArrayInputStream from = new ByteArrayInputStream(body);
		DefaultDestinationOutputStream ddos = new DefaultDestinationOutputStream();
		ByteArrayOutputStream to = new ByteArrayOutputStream();
		ddos.setOutputStream(to);
		long length = ep.procLength(body.length,from, ddos);
		assertTrue(length == body.length);
		from.close();
		to.close();
		assertTrue(ByteArray.equal(body, to.toByteArray()));
	}
}

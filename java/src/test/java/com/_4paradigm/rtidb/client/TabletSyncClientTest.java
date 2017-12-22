package com._4paradigm.rtidb.client;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Assert;
import org.junit.Test;

import com.google.protobuf.ByteString;

import io.brpc.client.RpcClient;



public class TabletSyncClientTest {

    private AtomicInteger id = new AtomicInteger(100);
    private static RpcClient rpcClient = null;
    private static TabletSyncClient client = null;
    static {
    	rpcClient = TabletClientBuilder.buildRpcClient("127.0.0.1", 9501, 10000, 3);
    	client = TabletClientBuilder.buildSyncClient(rpcClient);
    }
    
    @Test
    public void test0Create() {
    	int tid = id.incrementAndGet();
        boolean ok = client.createTable("tj0", tid, 0, 0, 8);
        Assert.assertTrue(ok);
        ok = client.createTable("tj0", tid, 0, 0, 8);
        Assert.assertFalse(ok);
        client.dropTable(tid, 0);
    }

    @Test
    public void test1Put() throws TimeoutException {
    	int tid = id.incrementAndGet();
        Assert.assertFalse(client.put(tid, 0, "pk", 9527, "test0"));
        boolean ok = client.createTable("tj1", tid, 0, 0, 8);
        Assert.assertTrue(ok);
        ok = client.put(tid, 0,"pk", 9527, "test0");
        Assert.assertTrue(ok);
        ByteString buffer = client.get(tid, 0, "pk");
        Assert.assertNotNull(buffer);
        Assert.assertEquals("test0", buffer.toString(Charset.forName("utf-8")));
        client.dropTable(tid, 0);
    }

    @Test
    public void test3Scan() throws TimeoutException {
    	int tid = id.incrementAndGet();
        KvIterator it = client.scan(tid, 0, "pk", 9527, 9526);
        Assert.assertNull(it);
        boolean ok = client.createTable("tj1", tid, 0, 0, 8);
        Assert.assertTrue(ok);
        ok = client.put(tid, 0,"pk", 9527, "test0");
        Assert.assertTrue(ok);
        it = client.scan(tid, 0, "pk", 9527l, 9526l);
        Assert.assertTrue(it != null);
        Assert.assertTrue(it.valid());
        Assert.assertEquals(9527l, it.getKey());
        ByteBuffer bb = it.getValue();
        Assert.assertEquals(5, bb.limit() - bb.position());
        byte[] buf = new byte[5];
        bb.get(buf);
        Assert.assertEquals("test0", new String(buf));
        it.next();
        client.dropTable(tid, 0);
    }

    @Test
    public void test4Drop() {
    	int tid = id.incrementAndGet();
        boolean ok = client.dropTable(tid, 0);
        Assert.assertFalse(ok);
        ok = client.createTable("tj1", tid, 0, 0, 8);
        Assert.assertTrue(ok);
        ok = client.dropTable(tid, 0);
        Assert.assertTrue(ok);
    }
}
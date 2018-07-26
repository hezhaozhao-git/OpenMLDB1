package com._4paradigm.rtidb.client.schema;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import org.joda.time.DateTime;

import com._4paradigm.rtidb.client.TabletException;
import org.joda.time.LocalDate;

public class RowCodec {
    private static Charset charset = Charset.forName("utf-8");

    public static ByteBuffer encode(Object[] row, List<ColumnDesc> schema) throws TabletException {
        if (row.length != schema.size()) {
            throw new TabletException("row length mismatch schema");
        }
        Object[] cache = new Object[row.length];
        // TODO limit the max size
        int size = getSize(row, schema, cache);
        ByteBuffer buffer = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put((byte) row.length);
        for (int i = 0; i < row.length; i++) {
            buffer.put((byte) schema.get(i).getType().getValue());
            if (row[i] == null) {
                buffer.put((byte) 0);
                continue;
            }
            switch (schema.get(i).getType()) {
            case kString:
                byte[] bytes = (byte[]) cache[i];
                if (bytes.length > 128) {
                    throw new TabletException("kString should be less than or equal 128");
                }
                buffer.put((byte) bytes.length);
                buffer.put(bytes);
                break;
            case kInt32:
                buffer.put((byte) 4);
                buffer.putInt((Integer) row[i]);
                break;
            case kUInt32:
                throw new TabletException("kUInt32 is not support on jvm platform");

            case kFloat:
                buffer.put((byte) 4);
                buffer.putFloat((Float) row[i]);
                break;

            case kInt64:
                buffer.put((byte) 8);
                buffer.putLong((Long) row[i]);
                break;

            case kUInt64:
                throw new TabletException("kUInt64 is not support on jvm platform");

            case kDouble:
                buffer.put((byte) 8);
                buffer.putDouble((Double) row[i]);
                break;

            case kTimestamp:
                buffer.put((byte)8);
                if (row[i] instanceof DateTime) {
                    DateTime time = (DateTime)row[i];
                    buffer.putLong(time.getMillis());   
                }else if (row[i] instanceof Timestamp) {
                    Timestamp ts = (Timestamp)row[i];
                    buffer.putLong(ts.getTime());
                }else {
                    throw new TabletException(row[i].getClass().getName() + "is not support for timestamp ");
                }
                break;
            case kInt16:
                buffer.put((byte)2);
                buffer.putShort((Short)row[i]);
                break;
            case kDate:
                buffer.put((byte)8);
                if (row[i] instanceof Date) {
                    Date date = (Date)row[i];
                    buffer.putLong(date.getTime());
                }else if (row[i] instanceof LocalDate) {
                    LocalDate date = (LocalDate)row[i];
                    buffer.putLong(date.toDate().getTime());
                }else {
                    throw new TabletException(row[i].getClass().getName() + "is not support for date");
                }
                break;
            case kBool:
                buffer.put((byte)1);
                Boolean bool = (Boolean)row[i];
                if (bool) {
                    buffer.put((byte)1);
                }else {
                    buffer.put((byte)0);
                }
                break;
            default:
                throw new TabletException(schema.get(i).getType().toString() + " is not support on jvm platform");
            }
        }
        return buffer;
    }
    
    public static void decode(ByteBuffer buffer, List<ColumnDesc> schema, Object[] row, int start, int length)  throws TabletException{
        if (buffer.order() == ByteOrder.BIG_ENDIAN) {
            buffer = buffer.order(ByteOrder.LITTLE_ENDIAN);
        }
        int colLength = buffer.get() & 0xFF;
        if (colLength > length) {
            colLength = length;
        }
        int index = start;
        int count = 0;
        while (buffer.position() < buffer.limit() && count < colLength) {
            byte type = buffer.get();
            int size = buffer.get() & 0xFF;
            if (size == 0) {
                row[index] = null;
                index++;
                count++;
                continue;
            }
            ColumnType ctype = ColumnType.valueOf((int) type);
            switch (ctype) {
            case kString:
                byte[] inner = new byte[size];
                buffer.get(inner);
                String val = new String(inner, charset);
                row[index] = val;
                break;
            case kInt32:
                row[index] = buffer.getInt();
                break;
            case kInt64:
                row[index] = buffer.getLong();
                break;
            case kDouble:
                row[index] = buffer.getDouble();
                break;
            case kFloat:
                row[index] = buffer.getFloat();
                break;
            case kTimestamp:
                long time = buffer.getLong();
                row[index] = new DateTime(time);
                break;
            case kInt16:
                row[index] = buffer.getShort();
                break;
            case kDate:
                long date = buffer.getLong();
                row[index] = new LocalDate(date);
                break;
            case kBool:
                int byteValue = buffer.get();
                if (byteValue == 0) {
                    row[index] = false;
                }else {
                    row[index] = true;
                }
                break;
            default:
                throw new TabletException(ctype.toString() + " is not support on jvm platform");
            }
            index++;
            count++;
        }
    }
    
    public static Object[] decode(ByteBuffer buffer, List<ColumnDesc> schema) throws TabletException {
        Object[] row = new Object[schema.size()];
        decode(buffer, schema, row, 0, row.length);
        return row;
    }

    private static int getSize(Object[] row, List<ColumnDesc> schema, Object[] cache) {
        int totalSize = 1;
        for (int i = 0; i < row.length; i++) {
            totalSize += 2;
            if (row[i] == null) {
                continue;
            }
            switch (schema.get(i).getType()) {
            case kString:
                byte[] bytes = ((String) row[i]).getBytes(charset);
                cache[i] = bytes;
                totalSize += bytes.length;
                break;
            case kBool:
                totalSize += 1;
                break;
            case kInt16:
                totalSize += 2;
                break;
            case kInt32:
            case kUInt32:
            case kFloat:
                totalSize += 4;
                break;
            case kInt64:
            case kUInt64:
            case kDouble:
            case kTimestamp:
            case kDate:
                totalSize += 8;
                break;
            default:
                break;
            }
        }
        return totalSize;
    }
}

package com.liquidware.lwplayer;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import android.os.AsyncTask;
import android.util.Log;

public class DemuxThread extends AsyncTask<Void, Integer, Void> {
	private static final String TAG = "Lwplay.DemuxThread"; 
	InputStream in;
	OutputStream out;
	static boolean ThreadInterrupted = false;
	
	public DemuxThread(InputStream in, OutputStream out) {
		super();
		this.in = in;
		this.out = out;
	}
	
	/**
	 * Gracefully stop the thread
	 */
	public void stop() {
		ThreadInterrupted = true;
		try {
			in.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
	
	protected void onProgressUpdate(Integer... progress) {
		Log.d(TAG,"Demux thread started id=" + Thread.currentThread().getId());
	}
	
	protected Void doInBackground(Void... params) {
		/* Demux the stream */
		publishProgress(0);
		demux_asf(in, out);
		return null;
	}
	
	public static void demux_asf(InputStream ins, OutputStream out) {
		int inBytes = 0;


		DataInputStream in = new DataInputStream(ins);
		/* Parse the V82_header */

		/* 
		 * There may be several formats of headers, but packets in most movies start with the V82_Header:
				Field	Type	Size (bytes)
				0x82	UINT8	1
				Always 0x0 (?)	UINT16	2
				Flags	UINT8	1
				Flags are bitwise OR of:
				0x40 Explicit packet size specified
				0x10 16-bit padding size specified
				0x08 8-bit padding size specified
				0x01 More than one segment
				Segment type ID	UINT8	1
				Packet size	UINT16	0 or 2 ( present if bit 0x40 is set in flags )
				Padding size	Variable	0, 1 or 2 ( depends on flags )
				Send time, milliseconds	UINT32	4
				Duration, milliseconds	UINT16	2
				Number of segments & segment properties	UINT8	0 or 1 ( depends on flags )
		 */
		while(!ThreadInterrupted) {
			try {
				int i=0;
				byte[] tmp = new byte[8];
				byte[] buf = new byte[1];
				int id;
				int padding_size = 0;
				int num_segments = 0;
				int err_cnt = 0;
				in.readFully(buf,0,buf.length);

				id = buf[0] & 0xFF;
				if ( id != 0x82) {
					Log.e(TAG,String.format("D:Error reading asf_file header id=[%x], purging...", id));
					err_cnt++;

					//purge until we find a something
					while((id != 0x82) && (!ThreadInterrupted)) {
						in.readFully(buf,0,buf.length);
						id = buf[0] & 0xFF;
					}
					//eof
					if ((err_cnt > 10) || (inBytes == -1)) break;
				}
				i=0;
				buf = new byte[4];
				in.readFully(buf,0,buf.length);

				System.arraycopy(buf, i, tmp, 0, 2); i+=2;
				int azero = Util.toInt16(tmp);
				System.arraycopy(buf, i, tmp, 0, 1); i+=1;
				int flags = Util.toInt8(tmp);
				System.arraycopy(buf, i, tmp, 0, 1); i+=1;
				int segment_id = Util.toInt8(tmp);
				if ((flags & 0x10) > 0) {
					//Log.e(TAG,"padding_size is 2 bytes");
					buf = new byte[2];
					in.readFully(buf,0,buf.length);
					padding_size = Util.toInt16(buf);
				} else if ((flags & 0x08) > 0) {
					//Log.e(TAG,"padding_size is 1 bytes");
					buf = new byte[1];
					in.readFully(buf,0,buf.length);
					padding_size = Util.toInt8(buf);
				}
				i = 0;
				buf = new byte[6];
				in.readFully(buf,0,buf.length);

				System.arraycopy(buf, i, tmp, 0, 4); i+=4;
				int send_time = Util.toInt32(tmp);
				System.arraycopy(buf, i, tmp, 0, 2); i+=2;
				int duration = Util.toInt16(tmp);
				if ((flags & 0x01) > 0) {
					buf = new byte[1];
					in.readFully(buf,0,buf.length);
					num_segments = Util.toInt8(buf);
				}
				/*
				Log.d(TAG, "||asf_file header||");
				Log.d(TAG,String.format("|id: %x|azero: %x|flags %x|segment_id %x|padding_size %x|send_time %x|duration %x|num_segments %x|", 
						id,
						azero,
						flags,
						segment_id,
						padding_size,
						send_time,
						duration,
						num_segments));
				 */

				// ?how do we tell how many segments?
				for (int x = 0; x < (num_segments & 0x0F); x++) {
					/* segment */
					buf = new byte[2];
					in.readFully(buf,0,buf.length);
					int stream_id = buf[0] & 0xFF;
					int sequence_number = buf[1] & 0xFF;

					/* segment specific */
					i = 0;
					buf = new byte[13];
					in.readFully(buf,0,buf.length);
					int data_length = 0;

					System.arraycopy(buf, i, tmp, 0, 4); i+=4;
					int fragment_offset = Util.toInt32(tmp);
					System.arraycopy(buf, i, tmp, 0, 1); i+=1;
					int fragment_flags = Util.toInt8(tmp);
					System.arraycopy(buf, i, tmp, 0, 4); i+=4;
					int object_length = Util.toInt32(tmp);
					System.arraycopy(buf, i, tmp, 0, 4); i+=4;
					int object_start_time = Util.toInt32(tmp);
					if ((num_segments & 0x40) > 0 ) {
						buf = new byte[1];
						in.readFully(buf,0,buf.length);
						//Log.d(TAG,"-data_length is 1 bytes");
						data_length = Util.toInt8(buf);
					} else if ((num_segments & 0x80) > 0 ) {
						buf = new byte[2];
						in.readFully(buf,0,buf.length);
						//Log.d(TAG,"-data_length is 2 bytes");
						data_length = Util.toInt16(buf);
					}

					//read the data
					buf = new byte[data_length];
					in.readFully(buf,0,buf.length);

					/* Write the demuxed payload */
					//out.write(buf);
					out.write(buf);

					//Log.e(TAG, "-asf_file segment specific");
					Log.d(TAG,String.format("D:|s_id %d|s_num %d|", stream_id, sequence_number));
					/*
					Log.e(TAG,String.format("|stream_id %d|sequence_number %d|fragment_offset: %x|fragment_flags %x|object_length %x|object_start_length %x|data_length %x|", 
							stream_id,
							sequence_number,
							fragment_offset,
							fragment_flags,
							object_length,
							object_start_time,
							data_length));
					 */
				}

				//padding
				buf = new byte[padding_size];
				in.readFully(buf,0,buf.length);
			} catch(Exception e) {
				Log.d(TAG, "D:Exception, closing demuxer");
				break;
			}
		}

		Log.d(TAG, "Demux complete.");
	}
}

package com.example.insclient;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.speech.tts.TextToSpeech;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

/*
 * Messages: what; 0 - misc, 1 - message read
 */

public class MainActivity extends Activity{

	static BluetoothAdapter mBluetoothAdapter;
	private Context context;
	private int REQUEST_ENABLE_BT;
	private Set<BluetoothDevice> pairedDevices;
	private ListView devs;
	private TextView tv, rec, ang, initang, turnang, diffang;
	private EditText eText;
	private String name;
	private ArrayList<BluetoothDevice> devList;
	private ArrayAdapter<String> adapter;
	private BluetoothDevice btDevice;
	private ConnectThread ct;
	private boolean connect;
    private Sounds sounds;
    private View screen;
    private Responder tResponder;
    private Tracker tracker;
    private boolean logging;
	
	public Handler handler;
	
	/**
	 * @see android.app.Activity#onCreate(android.os.Bundle)
	 * 
	 * Initialises layout IDs, Bluetooth, child classes such as Responder, Tracker and Sounds, and handler.
	 */
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		context = getApplicationContext();
		devs = (ListView) findViewById(R.id.listView1);
		devs.setVisibility(View.GONE);
		tv = (TextView) findViewById(R.id.textView1);
		eText = (EditText) findViewById(R.id.testee);
		rec = (TextView) findViewById(R.id.rec);
		ang = (TextView) findViewById(R.id.ang);
		initang = (TextView) findViewById(R.id.initang);
		turnang = (TextView) findViewById(R.id.turnang);
		diffang = (TextView) findViewById(R.id.diffang);
		
		mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		if(mBluetoothAdapter == null) {
			// Device does not support Bluetooth
			CharSequence text = "Device does not support Bluetooth!";
			int duration = Toast.LENGTH_SHORT;
			Toast toast = Toast.makeText(context, text, duration);
			toast.show();
		}
		if(!mBluetoothAdapter.isEnabled()) {
			Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
			startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
		}
		
		
		handler = new Handler() {
			/**
			 * @see android.os.Handler#handleMessage(android.os.Message)
			 * 
			 * What; 0 - Toast, 1 - message read, 2 - Standard sounds, 3 - status, 4 - start log, 5- current angle.
			 * 
			 */
			@Override
			public void handleMessage(Message msg) {
				int duration = Toast.LENGTH_SHORT;
				switch(msg.what) {
				case 0: 
					CharSequence text = (String) msg.obj;
					Toast toast = Toast.makeText(context, text, duration);
					toast.show();
					if(((String) msg.obj).contentEquals("Connected")) {
						connect = true;
						tracker.resumeTracker(); //Start accelerometer managers.
					}
					break;
				case 1: //arg1=bytes, obj=string
					if(checkScript((ArrayList<String>)msg.obj)){
						rec.setText("Message received: "+((ArrayList<String>)msg.obj).get(0)+" "+((ArrayList<String>)msg.obj).get(1)+" "+((ArrayList<String>)msg.obj).get(2)+" "+((ArrayList<String>)msg.obj).get(3));
						ct.wt.write(0);//Send not ready
						tResponder.expectTouch();//Maybe not here
					}else {
						CharSequence errtext = "Parsing script failed.";
						Toast errT = Toast.makeText(context, errtext, duration);
						errT.show();
						ct.wt.write(1);//Send ready
					}
					break;
				case 2:
					String s = (String)(msg.obj);
					if( s.contains("Sound:Stop") ) {
						tResponder.expectTouch();
						tracker.saveMsg("EStop");
						sounds.estop();
						ct.wt.write(0);//Send not ready
					} else if( s.contains("Sound:Feel") ) {
						tResponder.expectTouch();
						tracker.saveMsg("Feel");
						sounds.playText("Feel for", TextToSpeech.QUEUE_FLUSH, true);
						ct.wt.write(0);//Send not ready
					} else if( s.contains("Sound:Scan") ) {
						tResponder.expectTouch();
						tracker.saveMsg("Scan");
						sounds.playText("Scan for", TextToSpeech.QUEUE_FLUSH, true);
						ct.wt.write(0);//Send not ready
					} else if( s.contains("Sound:Align") ) {
						tResponder.expectTouch();
						tracker.saveMsg("Align");
						sounds.playText("Align with", TextToSpeech.QUEUE_FLUSH, true);
						ct.wt.write(0);//Send not ready
					} else if( s.contains("Sound:Pop") ) {
						tResponder.expectTouch();
						tracker.saveMsg("Pop");
						sounds.playText("Pop up", TextToSpeech.QUEUE_FLUSH, true);
						ct.wt.write(0);//Send not ready
					} else if( s.contains("Sound:Square") ) {
						tResponder.expectTouch();
						tracker.saveMsg("Square");
						sounds.playText("Sqaure off", TextToSpeech.QUEUE_FLUSH, true);
						ct.wt.write(0);//Send not ready
					}
					break;
				case 3:
					String status = (String)(msg.obj);
					setStatusMsg(status);
					break;
				case 4:
					String t = (String)(msg.obj);
					if(t.contains("Log:Start") ) {
						tracker.startLog();
						logging = true;
					}else if(t.contains("Log:Stop") ) {
						tracker.stopLog();
						logging = false;
					}
					break;
				case 5:
					String angle = (String)(msg.obj);
					ang.setText("Current angle: "+angle);
					break;
				case 6:
					Long diff = (Long)(msg.obj);
					diffang.setText("Diff: " + diff.toString());
				default: super.handleMessage(msg);
				}
				
			}

		};
		
		//Needs to be after handler is initialised
		sounds = new Sounds(context, handler);
		sounds.start();
		tracker = new Tracker(context, handler, this);
		tracker.start();
		screen = findViewById(android.R.id.content);
		tResponder = new Responder(context, sounds, tracker, this);
		screen.setOnTouchListener(tResponder);
	}

	/**
	 * @see android.app.Activity#onStop()
	 * 
	 * Release resources and stop threads.
	 */
	@Override
	protected void onStop() {
		super.onStop();
		if(connect) {
			ct.cancel();
			connect = false;
			sounds.release();
			tracker.stopTracker();
			logging = false;
		}
		rec.setText("Message received:");
	}
	
	@Override
	protected void onDestroy() {
		if(!sounds.isTTSnull()) {
			sounds.stopTTS();
			sounds.shutdownTTS();
		}
		super.onDestroy();
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.activity_main, menu);
		return true;
	}
	
	/**
	 * @param msg
	 * 
	 * Show status message on screen of TTS status.
	 */
	public void setStatusMsg(String msg){
		tv.setText(msg);
	}
	
	/**
	 * @param msg
	 * 
	 * Send information to server app about tracker status.
	 * Msg==2 -> Tracker live, msg==3 -> Tracker accuracy low, msg==4 -> Tracker stopped
	 */
	public void sendTrackerInfo(int msg) {
		if(connect)
			ct.wt.write(msg);
	}
	
	/**
	 * Send ready status to server app.
	 */
	public void setReady() {
		if (!connect) {
			CharSequence text = "Not Connected";
			int duration = Toast.LENGTH_SHORT;
			Toast toast = Toast.makeText(context, text, duration);
			toast.show();
		}else {
			CharSequence text = "Sending ready signal.";
			int duration = Toast.LENGTH_SHORT;
			Toast toast = Toast.makeText(context, text, duration);
			toast.show();
			ct.wt.write(1);//Send ready
		}
	}
	
	/**
	 * @param item
	 * 
	 * Upon menu item selection a list of paired Bluetooth devices will be displayed.
	 * The user can then select which device to connect to.
	 */
	public void connectDevice(MenuItem item) {
		devs.setVisibility(View.VISIBLE);
		pairedDevices = mBluetoothAdapter.getBondedDevices();
		if(pairedDevices.size() > 0) {
		}
		devList = new ArrayList<BluetoothDevice>();
		final ArrayList<String> devNames = new ArrayList<String>();
		for (BluetoothDevice device : pairedDevices) {
			devList.add(device);
			devNames.add(device.getName());
		}
		adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1,devNames);
		devs.setAdapter(adapter);
		
		devs.setOnItemClickListener(new OnItemClickListener() {
			  @Override
			  public void onItemClick(AdapterView<?> parent, View view,
			    int position, long id) {
				  btDevice = devList.get(position);
				  Toast.makeText(context,
						  "Connecting to device: " + btDevice.getName(), Toast.LENGTH_LONG)
						  .show();
				  adapter.clear();
				  adapter.notifyDataSetChanged();
				  
				  if(!connect) {
					  ct = new ConnectThread(btDevice);
					  ct.start();
					  devs.setVisibility(View.GONE);
				  }
			  }
		});
	}

	/**
	 * @param item
	 * 
	 * Allows the client to assign a name to the test data. Sends the name to tracker class.
	 */
	public void setName(MenuItem item) {
		name = eText.getText().toString();
		tracker.name(name);
	}
	
	/**
	 * @param item
	 * 
	 * Upon menu item selection. Connection to server is disconnected by cancelling thread.
	 * Tracker is also halted.
	 */
	public void disconnectDevice(MenuItem item) {
		if(adapter != null) {
			devs.setVisibility(View.GONE);
			adapter.clear();
			adapter.notifyDataSetChanged();
		}
		if(connect || ct != null) {
			ct.cancel();
			connect = false;
		}
		if(logging) {
			tracker.stopLog();
			logging = false;
		}
		tracker.stopTracker();
		CharSequence text = "Disconnected!";
		int duration = Toast.LENGTH_SHORT;
		Toast toast = Toast.makeText(context, text, duration);
		toast.show();
	}
	
	/**
	 * @param msg
	 * @return
	 * 
	 * Check script to see if further work is needed to be done for a turning action.
	 */
	public boolean checkScript(ArrayList<String> msg) {
		if(msg.size() < 7) return false;
		if(msg.get(4).contains("Turn")) {
			try{
				if(msg.get(5).contains("left")) {
					String d = msg.get(1); 
					int amt = Integer.parseInt(d);
					tracker.setDegrees(amt);
					tracker.setLR(1);
					initang.setText("Initial angle: " + tracker.setFace());
					turnang.setText("Turn: " + Integer.toString(amt));
				} else if (msg.get(5).contains("right")) {
					String d = msg.get(1); 
					int amt = Integer.parseInt(d);
					tracker.setDegrees(amt);
					tracker.setLR(2);
					initang.setText("Initial angle: " + tracker.setFace());
					turnang.setText("Turn: " + Integer.toString(amt));
				}
			} catch(IndexOutOfBoundsException e) {
				initang.setText(e.getMessage());
				return false;
			}
			tracker.saveMsg(msg.get(6));
			return sounds.playText(msg.get(6), TextToSpeech.QUEUE_FLUSH, true);
		}else {
			initang.setText((String) msg.get(4));
			tracker.saveMsg(msg.get(6));
			return sounds.playText(msg.get(6), TextToSpeech.QUEUE_FLUSH, true);
		}
	}
	
	public void saveLog(MenuItem menuitem) {
		if(logging) {
			tracker.stopLog();
			CharSequence text = "Log saved";
			int duration = Toast.LENGTH_SHORT;
			Toast toast = Toast.makeText(getApplicationContext(), text, duration);
			toast.show();
			logging = false;
		}
		return;		
	}
	
	/**
	 * @author Gabriel
	 *
	 * Separate thread for Bluetooth.
	 */
	private class ConnectThread extends Thread {
		private final BluetoothSocket mmSocket;
	    private final BluetoothDevice mmDevice;
		private final String MY_UUID = "e4e7dcc0-0d67-11e3-8ffd-0800200c9a66";
		private final String NAME = "Instructions";
		public Handler tHandler;
		private WorkerThread wt;
		
		
	    public ConnectThread(BluetoothDevice btdevice) {
	        // Use a temporary object that is later assigned to mmSocket,
	        // because mmSocket is final
	        BluetoothSocket tmp = null;
	        mmDevice = btdevice;
	 
	        // Get a BluetoothSocket to connect with the given BluetoothDevice
	        try {
	            // MY_UUID is the app's UUID string, also used by the server code
	            tmp = btdevice.createRfcommSocketToServiceRecord(UUID.fromString(MY_UUID));
	        } catch (IOException e) { }
	        mmSocket = tmp;
	    }
	 
	    /**
	     * @see java.lang.Thread#run()
	     * 
	     * Main method to attempt to connect to Bluetooth device.
	     */
	    public void run() {
	        // Cancel discovery because it will slow down the connection
	        mBluetoothAdapter.cancelDiscovery();
	        // Create and start the HandlerThread - it requires a custom name
	        HandlerThread handlerThread = new HandlerThread("MyHandlerThread");
	        handlerThread.start();
	        // Get the looper from the handlerThread
	        // Note: this may return null
	        Looper looper = handlerThread.getLooper();
	        // Create a new handler - passing in the looper to use and this class as
	        // the message handler
	        tHandler = new Handler(looper) { //what; 0 - misc
	        	public void handleMessage(Message msg) {
	        		Message msgS = Message.obtain(msg);
	        		if(!wt.wHandler.sendMessage(msgS))
	        			super.handleMessage(msg);
	        	}
	        };
	        
	        try {
	            // Connect the device through the socket. This will block
	            // until it succeeds or throws an exception
	            mmSocket.connect();
	        } catch (IOException connectException) {
	            // Unable to connect; close the socket and get out
	            try {
	                mmSocket.close();
	            } catch (IOException closeException) { }
	            return;
	        }
	 
	        // Do work to manage the connection (in a separate thread)
	        wt = new WorkerThread(mmSocket);
	        handler.obtainMessage(0, "Connected").sendToTarget();
    		wt.start();
	    }
	 
	    /** Will cancel an in-progress connection, and close the socket */
	    public void cancel() {
	        try {
	            mmSocket.close();
	        } catch (IOException e) { }
	    }
	    
	}
	
	/**
	 * @author Gabriel
	 *
	 * Worker thread that handles Bluetooth communication.
	 */
	private class WorkerThread extends Thread {
	    private final BluetoothSocket mmSocket;
	    private final InputStream mmInStream;
	    private final OutputStream mmOutStream;
	    public Handler wHandler;
	    private boolean ready = true;
	    String read;
	 
	    public WorkerThread(BluetoothSocket socket) {
	        mmSocket = socket;
	        InputStream tmpIn = null;
	        OutputStream tmpOut = null;
	 
	        // Get the input and output streams, using temp objects because
	        // member streams are final
	        try {
	            tmpIn = socket.getInputStream();
	            tmpOut = socket.getOutputStream();
	        } catch (IOException e) { }
	 
	        mmInStream = tmpIn;
	        mmOutStream = tmpOut;
	    }
	 
	    /**
	     * @see java.lang.Thread#run()
	     * Main method that accepts messages from UI thread though a handler, reads and sends messages from server application.
	     */
	    public void run() {
	        byte[] buffer = new byte[1024];  // buffer store for the stream
	        int bytes; // bytes returned from read()
	        // Create and start the HandlerThread - it requires a custom name
	        HandlerThread handlerThread = new HandlerThread("MyHandlerThread");
	        handlerThread.start();
	        // Get the looper from the handlerThread
	        // Note: this may return null
	        Looper looper = handlerThread.getLooper();
	        // Create a new handler - passing in the looper to use and this class as
	        // the message handler
	        wHandler = new Handler(looper) { //what; 0 - misc
	        	public void handleMessage(Message msg) {
	        		switch(msg.what) {//obj; 1- ready, 0- not ready
					case 0: 
						if((Integer) msg.obj == 1) {
							ready = true;
							write(1);
						} else if((Integer) msg.obj == 0) {
							ready = false;
							write(0);
						}
					break;
					default: super.handleMessage(msg);
					}
	        	}
	        };
	        write(1); //Tell server client is ready
	        // Keep listening to the InputStream until an exception occurs
	        while (true) {
	            try {
	                // Read from the InputStream
	                bytes = mmInStream.read(buffer);
	                read = new String(buffer);
	                // Send the obtained bytes to the UI activity
	                if(read.contains("Sound:"))
	                	handler.obtainMessage(2, bytes, -1, read.substring(0, bytes)).sendToTarget();
	                else if(read.contains("Log:"))
	                	handler.obtainMessage(4, bytes, -1, read.substring(0, bytes)).sendToTarget();
	                else if(read.contains("Handshake")) {
	                	write(5); //5 -> Handshake
	                }
	                else if(ready) { //Send script only if ready 
	                	ByteArrayInputStream bais = new ByteArrayInputStream(buffer);
	                	ObjectInputStream in = new ObjectInputStream(bais);
                		ArrayList<String> message = new ArrayList<String>();
	                	for (int i = 0; i < 7; i++) {
	                	    message.add((String)in.readObject());
	                	}
	                	handler.obtainMessage(1, bytes, -1, message).sendToTarget();
	                }
	            } catch (IOException e) {
	            	e.printStackTrace();
	            } catch (ClassNotFoundException e) {
	            	e.printStackTrace();
	            }
	        }
	    }
	 
	    /**
	     *  Call this from the main activity to send data to the remote device 
	     */
	    public void write(int b) { 
	    	if(b == 1) ready = true;
	    	else if(b == 0) ready = false;
	        try {
	            mmOutStream.write(b);
	        } catch (IOException e) { }
	    }
	 
	    /** 
	     * Call this from the main activity to shutdown the connection 
	     */
	    public void cancel() {
	        try {
	            mmSocket.close();
	        } catch (IOException e) { }
	    }
	}
}

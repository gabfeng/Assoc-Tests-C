package com.example.insclient;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Environment;
import android.os.Handler;
import android.os.Vibrator;

/**
 * @author Gabriel
 *
 * Tracker class is a separate thread that runs and records the telemetry data of the client's phone.
 * Recording, orientation and linear acceleration with timestamps. (GPS next)
 */
public class Tracker extends Thread implements SensorEventListener{

	public static final int DEGREE_ERROR = 10;
	public static final int VIB = 30;
	public static final int GAP = 30;
	
	MainActivity parent;
	private Context context;
	private SensorManager mSensorManager;
    private Sensor mAccelerometer;
	private Sensor mCompass;
	private Sensor mLAccel;
	private Vibrator vibrator;
	private Handler handler;
    private float[] valuesAccelerometer;
    private float[] valuesLAccel;
    private float[] valuesMagneticField;
    private float[] matrixR;
    private float[] matrixI;
    private float[] matrixValues;
    private LinkedList<float[]> logData;
    private LinkedList<Long> timeStamps;
    private LinkedList<String[]> msgs;
    private String[] text;
    private Date ts;
    private boolean logging;
    private boolean turnL, turnR;
	private int degrees;
	private long initO;
	private long[] pattern = {0,VIB,GAP,VIB,GAP,VIB,GAP};
	private String name;
	private Timer loggerT;
    private FileWriter writer;
    private boolean vib = false;
    
    /**
     * @param _context
     * @param _handle
     * @param _parent
     * 
     * Constructor, initialises variables and sensor managers. Also stores information from parent class.
     */
    public Tracker(Context _context, Handler _handle, MainActivity _parent) {
    	context = _context;
    	handler = _handle;
    	parent = _parent;
    	
    	
		vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
		vibrator.vibrate(100); //Confirmation start of tracker
    	// Get an instance of the SensorManager
        mSensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        mLAccel = mSensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
    	mCompass = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
    	mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
    	valuesAccelerometer = new float[3];
    	valuesLAccel = new float[3];
    	valuesMagneticField = new float[3];
    	matrixR = new float[9];
    	matrixI = new float[9];
    	matrixValues = new float[3];
    	logData = new LinkedList<float[]>();
		timeStamps = new LinkedList<Long>();
		msgs = new LinkedList<String[]>();
		ts = new Timestamp(System.currentTimeMillis());
		name = "No name";
    }
    
    public void resumeTracker() {
    	if(mSensorManager != null) {
    		mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_FASTEST);
    		mSensorManager.registerListener(this, mCompass, SensorManager.SENSOR_DELAY_FASTEST);
    		mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_FASTEST);
    		mSensorManager.registerListener(this, mLAccel, SensorManager.SENSOR_DELAY_FASTEST);
    		parent.sendTrackerInfo(2);
    	}
    }
    
    public void stopTracker() {
    	if(mSensorManager != null) {
    		mSensorManager.unregisterListener(this,mAccelerometer);
    		mSensorManager.unregisterListener(this,mCompass);  
    		mSensorManager.unregisterListener(this,mAccelerometer);  
    		mSensorManager.unregisterListener(this,mLAccel);
    		parent.sendTrackerInfo(4);
    	}
    	if(logging) {
    		stopLog();
    	}
    }
    
    /**
     * Starts the log scheduler to store the data into a linked list.
     * Also sends a message to the server app informing that the logger has started.
     */
    public void startLog() {
    	logging = true;
    	logData.clear(); msgs.clear();
    	loggerT = new Timer();
    	loggerT.scheduleAtFixedRate(new TimerTask() {
    		public void run() {
    	        //Called every 10 milliseconds(the period parameter)
    			float[] values = new float[6];
    			for(int i=0;i<6;i++) {
    				if(i<3)
    					values[i]=matrixValues[i];
    				else
    					values[i]=valuesLAccel[i-3];
    			}			
    			timeStamps.add(System.currentTimeMillis());
    			logData.add(values);
    	    }
    	}, 0, 10);
    	handler.obtainMessage(3,"Logger started...").sendToTarget();
    }
    
    /**
     * Logs the last entry before stopping the scheduler.
     */
    public void stopLog() {
    	log();
    	logging = false;
    	loggerT.cancel();
    	loggerT.purge();
    }
    
    /**
     * @param _name
     * 
     * Stores the name of the test client.
     */
    public void name(String _name) {
    	name = String.copyValueOf(_name.toCharArray());
    	handler.obtainMessage(0, "Name set to " + name).sendToTarget();
    }
    
    /**
     * @param msg
     * 
     * Save the script that is played to the test client, timestamped.
     */
    public void saveMsg(String msg) {
    	Date d = new Date(System.currentTimeMillis());
    	SimpleDateFormat sdf=new SimpleDateFormat("hh:mm:ss.SS", Locale.UK);
    	text = new String[2];
    	text[0] = msg;
    	text[1] = sdf.format(d);
    	msgs.add(text);
    }
    
    /**
     * @param deg
     * 
     * Store the integer amount of degrees the test client has to turn.
     */
    public void setDegrees(int deg) {
		degrees = deg;
	}
    
    /**
     * @param d
     * 
     * Set whether the test client has to turn left or right.
     */
    public void setLR (int d) {
    	if(d==1)
    		turnL=true;
    	else if(d==2)
    		turnR=true;
    }
	
    /**
     * @return
     * 
     * Set the integer value of orientation the test client is facing when the script is recieved.
     */
    public long setFace() {
    	initO = ((Long) Math.round(Math.toDegrees(matrixValues[0]))).intValue();
    	if(initO < 0) {
	  		initO = 360+initO;
	  	}
    	return initO;
    }
    
    /**
     * @param ori the uncorrected currently facing direction
     * 
     * Check if the test client has rotated right to the correct direction within some DEGREE_ERROR.
     */
    public void checkTurnR(long ori) {
    	long diff =	ori + (360-initO);
    	if(diff < 0) {
	  		diff += 360;
	  	} else if (diff > 360){
	  		diff -= 360;
	  	}
    	if(diff >= degrees-DEGREE_ERROR && diff <= degrees+DEGREE_ERROR && !vib) {
    		vibrator.vibrate(pattern,1);
    		vib = true;
    	} else if(diff < degrees-DEGREE_ERROR || diff > degrees+DEGREE_ERROR){
    		handler.obtainMessage(6, diff).sendToTarget();
    		vibrator.cancel();
    		vib = false;
    	}
    }
    
    /**
     * @param ori the uncorrected currently facing direction
     * 
     * Check if the test client has rotated left to the correct direction within some DEGREE_ERROR.
     */
    public void checkTurnL(long ori) {
    	long diff = initO;
    	diff+=(360-ori);
    	if(diff < 0) {
	  		diff += 360;
	  	} else if (diff > 360){
	  		diff -= 360;
	  	}
    	if(diff >= degrees-DEGREE_ERROR && diff <= degrees+DEGREE_ERROR && !vib) {
    		vibrator.vibrate(pattern,1);
    		vib = true;
    	} else if(diff < degrees-DEGREE_ERROR || diff > degrees+DEGREE_ERROR){
    		handler.obtainMessage(6, diff).sendToTarget();
    		vibrator.cancel();
    		vib = false;
    	}
    }
    
    /**
     * Called from Responder class. User would have touched the screen. Also stops class from checking further.
     */
    public void turnFin() {
    	turnL = false; turnR = false;
    }
    
	/**
	 * @see android.hardware.SensorEventListener#onAccuracyChanged(android.hardware.Sensor, int)
	 * 
	 * Sends information of accelerometer's accuracy to parent class (MainActivity) which in turn will send to the server app.
	 */
	@Override
	public void onAccuracyChanged(Sensor arg0, int arg1) {
		if (arg0 == mAccelerometer && arg1 == SensorManager.SENSOR_STATUS_UNRELIABLE) {
			parent.sendTrackerInfo(3);
		} else if (arg0 == mAccelerometer && arg1 != SensorManager.SENSOR_STATUS_UNRELIABLE) {
			parent.sendTrackerInfo(2);
		}
	}

	/**
	 * @see android.hardware.SensorEventListener#onSensorChanged(android.hardware.SensorEvent)
	 * 
	 * Stores orientation and acceleration values, also calls methods to check turning if required.
	 */
	@Override
	public void onSensorChanged(SensorEvent event) {
		switch(event.sensor.getType()){
		case Sensor.TYPE_ACCELEROMETER:
			for(int i =0; i < 3; i++){
				valuesAccelerometer[i] = event.values[i];
			}

			break;
		case Sensor.TYPE_MAGNETIC_FIELD:
			for(int i =0; i < 3; i++){
				valuesMagneticField[i] = event.values[i];
			}
			break;
		case Sensor.TYPE_LINEAR_ACCELERATION:
			for(int i =0; i < 3; i++){
				valuesLAccel[i] = event.values[i];
			}
			break;
		}
		boolean success = SensorManager.getRotationMatrix(
				matrixR,
				matrixI,
				valuesAccelerometer,
				valuesMagneticField);
		if(success)
			SensorManager.getOrientation(matrixR, matrixValues);
		handler.obtainMessage(5, String.valueOf(((Long) Math.round(Math.toDegrees(matrixValues[0]))).intValue()) ).sendToTarget();
		
		if(turnL) {
			checkTurnL(((Long) Math.round(Math.toDegrees(matrixValues[0]))).intValue());			
		}else if (turnR) {
			checkTurnR(((Long) Math.round(Math.toDegrees(matrixValues[0]))).intValue());
		}
		
		return; //else?
	}

	/**
	 * Stores the sensor data from the linked list logData as a csv file. Will provide a message once logging is done.
	 */
	public void log() {
		String appPath = Environment.getExternalStorageDirectory().toString();
		File appDir = new File(appPath + "/" + "logs");
		appDir.mkdirs();
		File appFile = new File(appDir.toString() + "/" + name +".csv");
		int x = 1;
		SimpleDateFormat sdf=new SimpleDateFormat("hh:mm:ss.SS", Locale.UK);
		while(appFile.exists()) {
			appFile = new File(appDir.toString() + "/" + name +"_" + Integer.toString(x) + ".csv");
			x++;
		}
		Date date = new Date(System.currentTimeMillis());
		float[] tmp = new float[6];
		String timeS = new String();
		ts= new Date(System.currentTimeMillis());
		try{
			writer = new FileWriter(appFile);
			writer.write("co-ords based on http://developer.android.com/reference/android/hardware/SensorEvent.html\n");
			writer.write("Date,"+date.toString()+"\n");
			writer.write("Name,"+name+"\n");
			writer.write("Time,Message\n");
			writeMsgs();
			writer.write("timestamp,azimuth,pitch,roll,(linear acceleration) x,y,z\n");
			while(!logData.isEmpty()) {
				tmp = logData.remove();
				ts.setTime(timeStamps.remove());
				timeS = sdf.format(ts);
				writeData(timeS,tmp[0],tmp[1],tmp[2],tmp[3],tmp[4],tmp[5]);
			}
			writer.flush();
			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

		handler.obtainMessage(3, "Finished Logging!").sendToTarget();
	}

	/**
	 * @param ts
	 * @param a
	 * @param p
	 * @param r
	 * @param x
	 * @param y
	 * @param z
	 * @throws IOException
	 * 
	 * Writes the data to the file based on the parameters provided.
	 */
	public void writeData(String ts, float a, float p, float r, float x, float y, float z) throws IOException {
		String line = String.format("%s,%f,%f,%f,%f,%f,%f\n", ts,a,p,r,x,y,z);
		writer.write(line);
	}

	/**
	 * @throws IOException
	 * 
	 * Writes the script messsages at the start of the log file.
	 */
	public void writeMsgs() throws IOException{
		int i = 0;
		while(!msgs.isEmpty()) {
			String[] tmp =msgs.remove();
			writer.write(tmp[1]+","+tmp[0]+"\n");
			i++;
		}
	}
}

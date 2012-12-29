package com.example.rtgraph;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.view.Menu;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.ToggleButton;

public class SwingerBluetooth extends Activity implements SensorEventListener {

	private ReentrantLock lock = new ReentrantLock();
	private boolean running = true;
	private boolean runningV = true;
	private boolean sendFlag = false, recivedData = false, dataReady = false;
	private byte reciver;
	private float[] buffor = new float[100];
	private float[] buffor2 = new float[100];
	public int[] bufforV = new int[100];
	public int[] bufforR = new int[100];
	private ToggleButton TgBT, TgSw;
	private Button SetBt, StopBt, UPBt, DOWNBt, ConnectBt, SendBt;
	private TextView message, messageOut, messageIn;
	private TextView textViewGear;
	private TextView textView1, textView2;
	private SurfaceView surfaceView, surfaceView2;
	private SurfaceHolder surfaceHolder, surfaceHolder2;
	private GraphThread graphThread;
	private GraphGearThread graphGearThread;
	private int gear = 0, gearSelect = 0;
	// Bluetooth
	private BluetoothAdapter mBluetoothAdapter = BluetoothAdapter
			.getDefaultAdapter();
	private BluetoothDevice remoteDevice;
	private static String SERVER_UUID = "00:12:09:21:01:25";
	private static String PC_UUID = "00:1B:B1:D6:7D:3D";
	private static String PC_AP_UUID = "04c6093b-0000-1000-8000-00805f9b34fb";
	private static final UUID MY_UUID = UUID
			.fromString("04c6093b-0000-1000-8000-00805f9b34fb"); // 00001101-0000-1000-8000-00805F9B34FB
	private static final int REQUEST_ENABLE_BT = 0x1;
	private BluetoothSocket activeBluetoothSocket;
	private ConnectThread connectThread;
	private ConnectedThread connectedThread;
	// accelerator
	private float X, Y;
	private boolean mInitialized;
	private SensorManager mSensorManager;
	private Sensor mAccelerometer;
	// interrupt thread
	private Thread thread;
	private Handler handler = new Handler();

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_rtgraphdrawing);
		// accelerator
		mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
		mAccelerometer = mSensorManager
				.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
		mSensorManager.registerListener(this, mAccelerometer,
				SensorManager.SENSOR_DELAY_NORMAL);

		// keeping device (phone) ON when ap's working
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); 

		StopBt = (Button) findViewById(R.id.button4);
		StopBt.setEnabled(false);
		SetBt = (Button) findViewById(R.id.button3);
		SetBt.setEnabled(false);
		UPBt = (Button) findViewById(R.id.button1);
		UPBt.setEnabled(false);
		DOWNBt = (Button) findViewById(R.id.button2);
		DOWNBt.setEnabled(false);
		TgSw = (ToggleButton) findViewById(R.id.toggleButton2);
		TgSw.setEnabled(false);
		TgBT = (ToggleButton) findViewById(R.id.toggleButton1);
		TgBT.setEnabled(true);
		textView1 = (TextView) findViewById(R.id.textView1);
		textView2 = (TextView) findViewById(R.id.textView2);
		messageOut = (TextView) findViewById(R.id.textView3);
		messageIn = (TextView) findViewById(R.id.textView5);
		ConnectBt = (Button) findViewById(R.id.button6);
		SendBt = (Button) findViewById(R.id.button5);
		SendBt.setEnabled(false);

		if (mBluetoothAdapter.isEnabled()) {
			TgBT.setChecked(true);
			ConnectBt.setEnabled(true);
		} else {
			TgBT.setChecked(false);
			ConnectBt.setEnabled(false);
		}

		thread = new Thread() {
			public void run() {
				interrupt();
				textView1.setText("Vz = " + bufforV[98]);
				//sending data from buffor and writing in textView 
				//on graph if sending was started
				if (sendFlag) {
					byte dane = (byte) bufforV[98];
					if (dataReady) {
						connectedThread.write(dane); // sending data
						dataReady = false;
						messageOut("Vz = " + bufforV[98]);
					}
				}
				//if useful data arrived (not -1 -> end of comm.)
				if (recivedData) {
					messageIn("Vr= " + reciver);
					textView2.setText("Vr= " + bufforR[99]);
					recivedData = false;
				}
				handler.postDelayed(this, 20);
			}
		};
	}

	public void onAccuracyChanged(Sensor sensor, int accuracy) {
		// can be safely ignored for this demo
	}

	protected void onResume() { // onResume is called on starting ap
		super.onResume();
		mSensorManager.registerListener(this, mAccelerometer,
				SensorManager.SENSOR_DELAY_NORMAL);
		running = true;
		runningV = true;
		// message("onResume");
		handler.removeCallbacks(thread); // cleaning thread's message queue
		handler.postDelayed(thread, 0); // starting thread after pause without
										// delay
										// and also starting thread on opening
										// ap
	}

	// accelerator
	protected void onPause() {
		super.onPause();
		mSensorManager.unregisterListener(this); // stop listening sensors
		running = false; // stop graph drawing thread
		runningV = false; // stop velocity counting thread
		handler.removeCallbacks(thread); // cleaning thread's message queue
	}

	// accelerator
	public void onSensorChanged(SensorEvent event) {
		// // TextView textView1 = (TextView) findViewById(R.id.textView1);
		// TextView textView2 = (TextView) findViewById(R.id.textView2);
		// float x = event.values[0];
		// float y = event.values[1];
		// if (!mInitialized) {
		// X = x;
		// // textView1.setText("X Axis: 0.0");
		// // textView1.setTextColor(Color.RED);
		// Y = x;
		// textView2.setText("Y Axis: 0.0");
		// textView2.setTextColor(Color.GREEN);
		// mInitialized = true;
		// } else {
		// // float deltaX = Math.abs(X - x);
		// X = x;
		// x = (float) (((float) ((int) (x * 1000))) / 1000);
		// // textView1.setText("X Axis: " + Float.toString(x));
		// for (int i = 0; i < 99; i++) {
		// buffor[i] = buffor[i + 1];
		// }
		// buffor[99] = x;
		// if (x > 10.0)
		// x = 10;
		// if (x < -10.0)
		// x = -10;
		//
		// Y = y;
		// y = (float) (((float) ((int) (y * 1000))) / 1000);
		// // textView2.setText("Y Axis: " + Float.toString(y));
		// for (int i = 0; i < 99; i++) {
		// buffor2[i] = buffor2[i + 1];
		// }
		// buffor2[99] = y;
		// if (y > 10.0)
		// y = 10;
		// if (y < -10.0)
		// y = -10;
		//
		// }
	}

	public void toggleSw(View view) {
		if (surfaceView == null)
			surfaceView = (SurfaceView) findViewById(R.id.surfaceView1);
		if (surfaceView2 == null)
			surfaceView2 = (SurfaceView) findViewById(R.id.surfaceView2);
		if (TgSw.isChecked()) {
			for (int i = 0; i < 100; i++) {
				bufforV[i] = 0;
				bufforR[i] = 0;
			}
			sendFlag = true;
			if (surfaceHolder == null)
				surfaceHolder = surfaceView.getHolder();
			if (surfaceHolder2 == null)
				surfaceHolder2 = surfaceView2.getHolder();
			if (graphGearThread == null)
				graphGearThread = new GraphGearThread();
			try {
				if (!graphGearThread.isAlive()) {
					graphGearThread.start();
					runningV = true;
				} else {
					graphGearThread.onResume();
				}
				SetBt.setEnabled(true);
				UPBt.setEnabled(true);
				DOWNBt.setEnabled(true);
				ConnectBt.setEnabled(false);
				SendBt.setEnabled(false);
			} catch (Exception e) {
				message(e.toString());
			}
		} else {
			graphGearThread.onPause();
			ConnectBt.setEnabled(true);
			SendBt.setEnabled(true);
			SetBt.setEnabled(false);
			UPBt.setEnabled(false);
			DOWNBt.setEnabled(false);
			StopBt.setEnabled(false);
		}
	}

	public void toggleBluetooth(View view) {
		if (TgBT.isChecked()) {
			try {
				mBluetoothAdapter.enable();
				// if (!mBluetoothAdapter.isEnabled()) {
				// Intent enableBtIntent = new
				// Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
				// startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
				// }
				ConnectBt.setEnabled(true);
			} catch (Exception e) {
				message(e.toString());
			}
		} else {
			try {
				mBluetoothAdapter.disable();
				ConnectBt.setEnabled(false);
			} catch (Exception e) {
				message(e.toString());
			}
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.activity_rtgraphdrawing, menu);
		return true;
	}

	public void buttonUP(View view) {
		gear += 10;
		if (gear >= 100)
			gear = 100;
		if (textViewGear == null)
			textViewGear = (TextView) findViewById(R.id.textView4);
		if (gear > 0)
			textViewGear.setText(" +" + gear + "%");
		else if (gear < 0)
			textViewGear.setText(" " + gear + "%");
		else
			textViewGear.setText("  " + gear + "%");
	}

	public void buttonDOWN(View view) {
		gear -= 10;
		if (gear <= -100)
			gear = -100;
		if (textViewGear == null)
			textViewGear = (TextView) findViewById(R.id.textView4);
		if (gear > 0)
			textViewGear.setText(" +" + gear + "%");
		else if (gear < 0)
			textViewGear.setText(" " + gear + "%");
		else
			textViewGear.setText("  " + gear + "%");
	}

	public void connectButton(View view) {
		if (connectThread == null) {
			try {
				getRemoteDevice();
				connectThread = new ConnectThread(remoteDevice);
				connectThread.start();
				connectThread.join();
				if (connectThread.connected) {
					connectedThread = new ConnectedThread(activeBluetoothSocket);
					connectedThread.start();
					ConnectBt.setText("DISCONNECT");
					TgSw.setEnabled(true);
					SendBt.setEnabled(true);
					TgBT.setEnabled(false);
				} else {
					connectThread = null;
					message("Target NOT CONNECTED!!!");
				}
			} catch (Exception e) {
				message(e.toString());
			}
		} else if (connectedThread.isAlive()) {
			try {
				connectedThread.cancel();
				connectedThread.join();
				connectThread.cancel();
				// graphGearThread.cancel();
				// graphGearThread.join();
				// graphGearThread=null;
				connectedThread = null;
				connectThread = null;
				ConnectBt.setText("CONNECT");
			} catch (Exception e) {
				message(e.toString());
			}
		}
	}

	public void buttonSEND(View view) {
		byte dane = 1;
		connectedThread.write(dane);
		message("Wys³ano: " + dane);
	}

	public void buttonSET(View view) {
		try {
			if (TgBT == null)
				TgBT = (ToggleButton) findViewById(R.id.toggleButton2);
			if (StopBt == null)
				StopBt = (Button) findViewById(R.id.button4);
			if (surfaceView == null) {
				surfaceView = (SurfaceView) findViewById(R.id.surfaceView1);
			}
			if (surfaceHolder == null)
				surfaceHolder = surfaceView.getHolder();
			if (graphGearThread == null)
				graphGearThread = new GraphGearThread();

			if (!graphGearThread.isAlive()) {
				graphGearThread.start();
				runningV = true;
				TgBT.setEnabled(false);
			}
			if (gear != 0 && !StopBt.isEnabled())
				StopBt.setEnabled(true);
			if (gear == 0)
				StopBt.setEnabled(false);
		} catch (Exception e) {
			message(e.toString() + " in buttonSET");
		}
		gearSelect = gear;
	}

	public void buttonSTOP(View view) {
		gearSelect = 0;
		gear = 0;
		textViewGear.setText("  " + gear + "%");
		view.setEnabled(false);
	}

	public void message(String string) {
		if (message == null) {
			message = (TextView) findViewById(R.id.messages);
		}
		String current = (String) message.getText();
		current = string + "\n" + current;
		if (current.length() > 300) {
			int truncPoint = current.lastIndexOf("\n");
			current = (String) current.subSequence(0, truncPoint);
		}
		message.setText(current);
	}

	public void messageOut(String string) {
		if (messageOut == null) {
			messageOut = (TextView) findViewById(R.id.textView3);
		}
		String current = (String) messageOut.getText();
		current = string + "\n" + current;
		if (current.length() > 100) {
			int truncPoint = current.lastIndexOf("\n");
			current = (String) current.subSequence(0, truncPoint);
		}
		messageOut.setText(current);
	}

	public void messageIn(String string) {
		if (messageIn == null) {
			messageIn = (TextView) findViewById(R.id.textView5);
		}
		String current = (String) messageIn.getText();
		current = string + "\n" + current;
		if (current.length() > 100) {
			int truncPoint = current.lastIndexOf("\n");
			current = (String) current.subSequence(0, truncPoint);
		}
		messageIn.setText(current);
	}

	public void doDraw(Canvas canvas) {
		Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
		paint.setStyle(Paint.Style.STROKE);
		paint.setStrokeWidth(3);
		paint.setColor(Color.RED);
		int surfaceView_width = surfaceView.getWidth();
		int surfaceView_height = surfaceView.getHeight();
		int k = (int) (surfaceView_width / 100);
		Path path = new Path();
		Path path1 = new Path();
		path.moveTo(0, (surfaceView_height / 2) + (surfaceView_height / 2)
				* (-buffor[0]) / 10);
		path1.moveTo(0, (surfaceView_height / 2));
		for (int sec = 1; sec < surfaceView_width; sec += k)
			path.lineTo(sec, (surfaceView_height / 2)
					+ (surfaceView_height / 2)
					* (-buffor[(int) (100 * sec / surfaceView_width)]) / 10);
		for (int i = 10; i < surfaceView_width; i += 20) {
			path1.lineTo(i, (surfaceView_height / 2));
			path1.rMoveTo(10, 0);
		}
		canvas.drawColor(Color.BLACK);
		canvas.drawPath(path1, paint);
		canvas.drawPath(path, paint);
	}

	public void doDraw2(Canvas canvas) {
		Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
		paint.setStyle(Paint.Style.STROKE);
		paint.setStrokeWidth(3);
		paint.setColor(Color.GREEN);
		int surfaceView_width = surfaceView.getWidth();
		int surfaceView_height = surfaceView.getHeight();
		int k = (int) (surfaceView_width / 100);
		Path path = new Path();
		Path path1 = new Path();
		path.moveTo(0, (surfaceView_height / 2) + (surfaceView_height / 2)
				* (-buffor2[0]) / 10);
		path1.moveTo(0, (surfaceView_height / 2));
		for (int sec = 1; sec < surfaceView_width; sec += k)
			path.lineTo(sec, (surfaceView_height / 2)
					+ (surfaceView_height / 2)
					* (-buffor2[(int) (100 * sec / surfaceView_width)]) / 10);
		for (int i = 10; i < surfaceView_width; i += 20) {
			path1.lineTo(i, (surfaceView_height / 2));
			path1.rMoveTo(10, 0);
		}
		canvas.drawColor(Color.BLACK);
		canvas.drawPath(path1, paint);
		canvas.drawPath(path, paint);
	}

	public void doDrawGear(Canvas canvas) {
		Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
		paint.setStyle(Paint.Style.STROKE);
		paint.setStrokeWidth(3);
		paint.setColor(Color.RED);
		int surfaceView_width = surfaceView.getWidth();
		int surfaceView_height = surfaceView.getHeight();
		int k = (int) (surfaceView_width / 100);
		Path path = new Path();
		Path path1 = new Path();
		path.moveTo(0, (surfaceView_height / 2) + (surfaceView_height / 2)
				* (-bufforV[0]) / 100);
		path1.moveTo(0, (surfaceView_height / 2));
		for (int i = 1; i < surfaceView_width; i += k)
			path.lineTo(i, (surfaceView_height / 2) + (surfaceView_height / 2)
					* (-bufforV[(int) (100 * i / surfaceView_width)]) / 100);
		for (int i = 10; i < surfaceView_width; i += 20) {
			path1.lineTo(i, (surfaceView_height / 2));
			path1.rMoveTo(10, 0);
		}
		canvas.drawColor(Color.BLACK);
		canvas.drawPath(path1, paint);
		canvas.drawPath(path, paint);
	}

	public void doDrawReciv(Canvas canvas) {
		Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
		paint.setStyle(Paint.Style.STROKE);
		paint.setStrokeWidth(3);
		paint.setColor(Color.GREEN);
		int surfaceView_width = surfaceView2.getWidth();
		int surfaceView_height = surfaceView2.getHeight();
		int k = (int) (surfaceView_width / 100);
		Path path = new Path();
		Path path1 = new Path();
		path.moveTo(0, (surfaceView_height / 2) + (surfaceView_height / 2)
				* (-bufforR[0]) / 100);
		path1.moveTo(0, (surfaceView_height / 2));
		for (int i = 1; i < surfaceView_width; i += k)
			path.lineTo(i, (surfaceView_height / 2) + (surfaceView_height / 2)
					* (-bufforR[(int) (100 * i / surfaceView_width)]) / 100);
		for (int i = 10; i < surfaceView_width; i += 20) {
			path1.lineTo(i, (surfaceView_height / 2));
			path1.rMoveTo(10, 0);
		}
		canvas.drawColor(Color.BLACK);
		canvas.drawPath(path1, paint);
		canvas.drawPath(path, paint);
	}

	private void getRemoteDevice() {
		remoteDevice = mBluetoothAdapter.getRemoteDevice(PC_UUID);
		try {
			message("SERVER NAME: " + remoteDevice.getName());
			message("SERVER MAC ADRESS: 	" + remoteDevice.getAddress());
		} catch (Exception e) {
			message(e.toString());
		}
	}

	private class GraphThread extends Thread {
		private int DELAY_IN_MS = 100;

		@Override
		public void run() {
			while (true) {
				while (running) {
					Canvas c = null;
					Canvas c2 = null;
					try {
						c = surfaceHolder.lockCanvas(null);
						synchronized (surfaceHolder) {
							doDraw(c);
						}
					} finally {
						if (c != null)
							surfaceHolder.unlockCanvasAndPost(c);
					}
					try {
						c2 = surfaceHolder2.lockCanvas(null);
						synchronized (surfaceHolder2) {
							doDraw2(c2);
						}
					} finally {
						if (c2 != null)
							surfaceHolder2.unlockCanvasAndPost(c2);
					}
					synchronized (this) {
						try {
							this.wait(DELAY_IN_MS);
						} catch (Exception e) {
							message(e.toString());
						}
					}
				}

				synchronized (this) {
					try {
						this.wait(DELAY_IN_MS);
					} catch (Exception e) {
						message(e.toString());
					}
				}

			}
		}
	}

	private class GraphGearThread extends Thread {
		private final int DELAY_IN_MS = 100;
		private int lastGear = 0;
		private float step;
		private int i = 1, d;
		private boolean pause, cancel;

		@Override
		public void run() {
			while (true) {
				bufforR[99] = (int) reciver;
				for (int i = 0; i < 99; i++) {
					bufforV[i] = bufforV[i + 1];
					bufforR[i] = bufforR[i + 1];
				}
				if (bufforV[98] != gearSelect
						&& (Math.abs(bufforV[98] - gearSelect) > 1)) {
					if (i == 1) {
						try {
							synchronized (this) {
								if (!lock.isLocked())
									lock.lock();
							}
						} catch (Exception e) {
							message(e.toString() + " in lock");
						}
						lastGear = bufforV[98];
						d = gearSelect - lastGear;
						step = (float) (2 * Math.PI / (Math.abs(d)));
					}
					bufforV[99] = lastGear
							- (int) ((d / 2) * (Math.sin((Math.PI / 2) + i
									* step) - 1));
					i++;
				} else {
					bufforV[99] = gearSelect;
					i = 1;
					try {
						synchronized (this) {
							if (lock.isLocked() && gearSelect == 0)
								lock.unlock();
						}
					} catch (Exception e) {
						message(e.toString() + " in unlock");
					}
				}
				dataReady = true; // allowing to send data in thread in onCreate
				Canvas c = null;
				Canvas c2 = null;
				try {
					c = surfaceHolder.lockCanvas(null);
					synchronized (surfaceHolder) {
						doDrawGear(c);
					}
				} finally {
					if (c != null)
						surfaceHolder.unlockCanvasAndPost(c);
				}
				try {
					c2 = surfaceHolder2.lockCanvas(null);
					synchronized (surfaceHolder2) {
						doDrawReciv(c2);
					}
				} finally {
					if (c2 != null)
						surfaceHolder2.unlockCanvasAndPost(c2);
				}
				synchronized (this) {
					try {
						this.wait(DELAY_IN_MS);
					} catch (Exception e) {
						message(e.toString());
					}
				}
				synchronized (this) {
					if (pause && bufforV[98] == 0 && i == 1) {
						try {
							pause = false;
							sendFlag = false;
							this.wait();
							if (cancel)
								break;
							sendFlag = true;
						} catch (Exception e) {
							message(e.toString());
						}
					}
				}
			}
		}

		public void onPause() {
			gear = 0;
			textViewGear.setText("  " + gear + "%");
			gearSelect = 0;
			pause = true;
		}

		public void onResume() {
			synchronized (this) {
				this.notify();
			}
		}

		// public void cancel(){
		// onResume();
		// cancel=true;
		// }
	}

	private class ConnectThread extends Thread {
		private final BluetoothSocket mmSocket;
		private final BluetoothDevice mmDevice;
		private boolean connected = true;

		public ConnectThread(BluetoothDevice device) {
			BluetoothSocket tmp = null;
			mmDevice = device;

			// Get a BluetoothSocket to connect with the given BluetoothDevice
			try {
				// MY_UUID is the app's UUID string, also used by the server
				// code
				tmp = mmDevice.createRfcommSocketToServiceRecord(MY_UUID);
			} catch (Exception e) {
				message(e.toString());
				connected = false;
				message("Client socket NOT opened.");
			}
			// Method m;
			// try {
			// m = mmDevice.getClass().getMethod("createRfcommSocket", new
			// Class[] {int.class});
			// tmp = (BluetoothSocket) m.invoke(mmDevice, 1);
			// message("Client socket opened.");
			// } catch (Exception e) {
			// message("Client socket NOT opened.");
			// message(e.toString());
			// }
			mmSocket = tmp;
		}

		@Override
		public void run() {
			// Cancel discovery because it will slow down the connection
			mBluetoothAdapter.cancelDiscovery();
			if (connected) {
				try {
					// Connect the device through the socket. This will block
					// until it succeeds or throws an exception
					mmSocket.connect();
					activeBluetoothSocket = mmSocket;
					message("Target CONNECTED.");
				} catch (Exception connectException) {
					message("SOCKET CONNECTION ERROR");
					message(connectException.toString() + " in socket.connect");
					connected = false;
					// Unable to connect; close the socket and get out
					try {
						mmSocket.close();
					} catch (IOException closeException) {
						message("SOCKET CLOSING ERROR");
						message(connectException.toString());
					}
					return;
				}

				// activeBluetoothSocket = mmSocket;
			}
			// Do work to manage the connection (in a separate thread)
			// manageConnectedSocket(mmSocket);
		}

		public void cancel() {
			try {
				mmSocket.close();
				TgSw.setEnabled(false);
				SendBt.setEnabled(false);
				TgBT.setEnabled(true);
				message("Target DISCONNECTED");
			} catch (IOException e) {
				message("Disconnecting failed.");
			}
		}
	}

	private class ConnectedThread extends Thread {
		private final BluetoothSocket mmSocket;
		private final InputStream mmInStream;
		private final OutputStream mmOutStream;
		private boolean running = true;
		private static final byte END_OF_COMUNICATION = -128;
		private static final int LOST_COMUNICATION = -1;

		public ConnectedThread(BluetoothSocket socket) {
			mmSocket = socket;
			InputStream tmpIn = null;
			OutputStream tmpOut = null;

			// Get the input and output streams, using temp objects because
			// member streams are final
			try {
				tmpIn = mmSocket.getInputStream();
			} catch (Exception e) {
				message("InputStream ERROR: " + e.toString());
			}
			try {
				tmpOut = mmSocket.getOutputStream();
			} catch (Exception e) {
				message("OutputStream ERROR: " + e.toString());
			}

			mmInStream = tmpIn;
			mmOutStream = tmpOut;
		}

		@Override
		public void run() {
			// byte[] buffer = new byte[1]; // buffer store for the stream
			int bytes; // bytes returned from read()
			// Keep listening to the InputStream until an exception occurs
			while (true) {
				try {
					// Read from the InputStream
					bytes = mmInStream.read();
					// Send the obtained bytes to the UI activity
					// mHandler.obtainMessage(MESSAGE_READ, bytes, -1, buffer)
					// .sendToTarget();
					if (bytes == LOST_COMUNICATION
							|| (byte) bytes == END_OF_COMUNICATION) {
						message("End of comunication.");
					} else {
						reciver = (byte) bytes;
						recivedData = true;
					}
				} catch (Exception e) {
					message(e.toString() + " while reading");
					break;
				}
				if (!running) {
					try {
						mmInStream.close();
					} catch (Exception e) {
						message(e.toString() + "while closing InputStream");
					}
					try {
						mmOutStream.close();
					} catch (Exception e) {
						message(e.toString() + "while closing OutputStream");
					}
					break;
				}
			}
		}

		// Call this from the main activity to send data to the remote device
		public void write(byte bytes) {

			try {
				mmOutStream.write(bytes);
			} catch (Exception e) {
				message("WRITE ERROR: " + e.toString());
			}

		}

		public void writeAccel() {
			byte x = (byte) (X * 13);
			// byte y = (byte)(Y*0.8);
			byte bytes = x; // (byte)((x<<4)|y);
			boolean blad = true;
			try {
				mmOutStream.write(bytes);
			} catch (Exception e) {
				blad = false;
				message("WRITE ERROR: " + e.toString());
			}
			if (blad) {
				message("*****\nWys³ano: " + bytes
						+ "\nSerwer powinien odebraæ: "
						+ (((short) bytes) & 0x00FF) + "\n*****");
			}
		}

		public void writeAccel2Byte() {
			byte[] bytes = null;
			bytes[0] = (byte) (X * 13);
			bytes[1] = (byte) (Y * 13);
			boolean blad = true;
			try {
				mmOutStream.write(bytes);
			} catch (Exception e) {
				blad = false;
				message("WRITE ERROR: " + e.toString());
			}
			if (blad) {
				message("*****\nWys³ano: \n" + bytes[0] + "\n" + bytes[1]
						+ "\nSerwer powinien odebraæ: "
						+ (((short) bytes[0]) & 0x00FF) + "\n"
						+ (((short) bytes[1]) & 0x00FF) + "\n*****");
			}
		}

		// Call this from the main activity to shutdown the connection
		public void cancel() {
			// try {
			// mmSocket.close();
			running = false;
			write(END_OF_COMUNICATION);
			// } catch (Exception e) {
			// message("DISCONNECTION ERROR: " + e.toString());
			// }
		}
	}
}
